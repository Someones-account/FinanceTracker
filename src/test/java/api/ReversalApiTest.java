package api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tracker.TrackerApplication;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = TrackerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ReversalApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TestRestTemplate rest;

    private String openAccount() {
        var response = rest.postForEntity("/accounts", Map.of("name", "Main", "currency", "PLN"), Map.class);
        return (String) response.getBody().get("id");
    }

    private int post(String url, Object body, String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        return rest.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), String.class)
                .getStatusCode().value();
    }

    private int eventCount(String id) {
        return rest.getForEntity("/accounts/" + id + "/events", List.class).getBody().size();
    }

    @Test
    void same_idempotency_key_posts_only_once() {
        String id = openAccount();
        var body = Map.of("amountMinor", 1000, "category", "salary");

        assertThat(post("/accounts/" + id + "/deposits", body, "key-1")).isEqualTo(204);
        assertThat(post("/accounts/" + id + "/deposits", body, "key-1")).isEqualTo(204);

        assertThat(eventCount(id)).isEqualTo(2); // opened + ONE deposit
    }

    @Test
    void same_key_with_a_different_body_is_rejected() {
        String id = openAccount();

        assertThat(post("/accounts/" + id + "/deposits",
                Map.of("amountMinor", 1000, "category", "salary"), "key-2")).isEqualTo(204);
        assertThat(post("/accounts/" + id + "/deposits",
                Map.of("amountMinor", 5000, "category", "salary"), "key-2")).isEqualTo(422);

        assertThat(eventCount(id)).isEqualTo(2);
    }

    @Test
    void failed_command_does_not_burn_the_key() {
        String id = openAccount();

        assertThat(post("/accounts/" + id + "/withdrawals",
                Map.of("amountMinor", 500, "category", "food"), "key-3")).isEqualTo(422);
        post("/accounts/" + id + "/deposits", Map.of("amountMinor", 1000, "category", "salary"), null);
        assertThat(post("/accounts/" + id + "/withdrawals",
                Map.of("amountMinor", 500, "category", "food"), "key-3")).isEqualTo(204);
    }

    @Test
    void reversal_restores_the_balance_and_cannot_be_repeated() {
        String id = openAccount();
        post("/accounts/" + id + "/deposits", Map.of("amountMinor", 1000, "category", "salary"), null);
        post("/accounts/" + id + "/withdrawals", Map.of("amountMinor", 400, "category", "food"), null);

        assertThat(post("/accounts/" + id + "/transactions/3/reversal",
                Map.of("reason", "wrong category"), null)).isEqualTo(204);
        assertThat(post("/accounts/" + id + "/transactions/3/reversal",
                Map.of("reason", "again"), null)).isEqualTo(422);

        assertThat(eventCount(id)).isEqualTo(4);
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var response = rest.getForEntity("/accounts/" + id, Map.class);
            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(((Number) response.getBody().get("balanceMinor")).longValue()).isEqualTo(1000);
        });
    }
}
