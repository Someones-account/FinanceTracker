import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tracker.TrackerApplication;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TrackerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class AccountApiTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    TestRestTemplate rest;

    private String openAccount() {
        ResponseEntity<Map> response = rest.postForEntity(
                "/accounts", Map.of("name", "Main", "currency", "PLN"), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("id");
    }

    @Test
    void open_deposit_withdraw_and_read_history() {
        String id = openAccount();

        var deposit = rest.postForEntity("/accounts/" + id + "/deposits",
                Map.of("amountMinor", 1000, "category", "salary"), Void.class);
        var withdrawal = rest.postForEntity("/accounts/" + id + "/withdrawals",
                Map.of("amountMinor", 300, "category", "food"), Void.class);

        assertThat(deposit.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(withdrawal.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<List> events = rest.getForEntity("/accounts/" + id + "/events", List.class);
        assertThat(events.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(events.getBody()).hasSize(3);
    }

    @Test
    void overdraft_is_rejected_with_422_and_stores_nothing() {
        String id = openAccount();

        var response = rest.postForEntity("/accounts/" + id + "/withdrawals",
                Map.of("amountMinor", 5000, "category", "food"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(rest.getForEntity("/accounts/" + id + "/events", List.class).getBody()).hasSize(1);
    }

    @Test
    void invalid_amount_is_rejected_with_400() {
        String id = openAccount();

        var response = rest.postForEntity("/accounts/" + id + "/deposits",
                Map.of("amountMinor", -10, "category", "x"), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void unknown_account_returns_404() {
        var response = rest.getForEntity("/accounts/" + java.util.UUID.randomUUID() + "/events", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}