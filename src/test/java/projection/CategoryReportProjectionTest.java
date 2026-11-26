package projection;

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
import tracker.TrackerApplication;

import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = TrackerApplication.class)
@Testcontainers
class CategoryReportProjectionTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    AccountCommandHandler handler;

    @Autowired
    CategoryReportProjection projection;

    private UUID accountWithHistory() {
        UUID id = UUID.randomUUID();
        handler.handle(id, new Open(id, "Main", "PLN"));
        handler.handle(id, new Deposit(10_000, "salary"));
        handler.handle(id, new Withdraw(2_000, "food"));
        handler.handle(id, new Withdraw(500, "food"));
        handler.handle(id, new Withdraw(1_200, "transport"));
        return id;
    }

    private static YearMonth thisMonth() {
        return YearMonth.now(ZoneOffset.UTC);
    }

    @Test
    void totals_are_grouped_by_category() {
        UUID id = accountWithHistory();

        projection.catchUp();

        assertThat(projection.find(id, thisMonth())).containsExactlyInAnyOrder(
                new CategoryTotal("salary", 10_000, 0),
                new CategoryTotal("food", 0, 2_500),
                new CategoryTotal("transport", 0, 1_200));
    }

    @Test
    void other_months_are_empty() {
        UUID id = accountWithHistory();
        projection.catchUp();

        assertThat(projection.find(id, YearMonth.of(2020, 1))).isEmpty();
    }

    @Test
    void rebuild_gives_the_same_report() {
        UUID id = accountWithHistory();
        projection.catchUp();
        var before = projection.find(id, thisMonth());

        projection.rebuild();

        assertThat(projection.find(id, thisMonth())).containsExactlyElementsOf(before);
    }
}