import app.AccountCommandHandler;
import domain.AccountCommand.Deposit;
import domain.AccountCommand.Open;
import domain.AccountCommand.Withdraw;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import projection.AccountBalance;
import projection.BalanceProjection;
import tracker.TrackerApplication;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TrackerApplication.class)
@Testcontainers
class BalanceProjectionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    AccountCommandHandler handler;

    @Autowired
    BalanceProjection projection;

    private UUID accountWithHistory() {
        UUID id = UUID.randomUUID();
        handler.handle(id, new Open(id, "Main", "PLN"));
        handler.handle(id, new Deposit(1_000, "salary"));
        handler.handle(id, new Withdraw(300, "food"));
        return id;
    }

    @Test
    void balance_is_derived_from_events() {
        UUID id = accountWithHistory();

        projection.catchUp();

        assertThat(projection.find(id))
                .contains(new AccountBalance(id, "Main", "PLN", 700));
    }

    @Test
    void catching_up_twice_does_not_double_count() {
        UUID id = accountWithHistory();

        projection.catchUp();
        projection.catchUp();

        assertThat(projection.find(id).orElseThrow().balanceMinor()).isEqualTo(700);
    }

    @Test
    void rebuild_produces_the_same_result_as_incremental_updates() {
        UUID id = accountWithHistory();
        projection.catchUp();
        AccountBalance before = projection.find(id).orElseThrow();

        projection.rebuild();

        assertThat(projection.find(id)).contains(before);
    }

    @Test
    void unknown_account_has_no_balance() {
        assertThat(projection.find(UUID.randomUUID())).isEmpty();
    }
}