package app;

import domain.AccountEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import store.InMemoryEventStore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BalanceQueryServiceTest {

    private static final Instant JAN = Instant.parse("2026-01-10T10:00:00Z");
    private static final Instant FEB = Instant.parse("2026-02-10T10:00:00Z");
    private static final Instant MAR = Instant.parse("2026-03-10T10:00:00Z");

    private final UUID id = UUID.randomUUID();
    private BalanceQueryService service;

    @BeforeEach
    void setUp() {
        InMemoryEventStore store = new InMemoryEventStore();
        store.append(id, 0, List.of(
                new AccountEvent.AccountOpened(id, "Main", "PLN"),
                new AccountEvent.MoneyDeposited(id, 10_000, "salary", JAN),
                new AccountEvent.MoneyWithdrawn(id, 2_500, "food", FEB),
                new AccountEvent.MoneyDeposited(id, 4_000, "salary", MAR)));
        service = new BalanceQueryService(store);
    }

    @Test
    void balance_reflects_only_events_up_to_the_given_time() {
        assertThat(service.balanceAt(id, Instant.parse("2026-01-31T00:00:00Z"))).contains(10_000L);
        assertThat(service.balanceAt(id, Instant.parse("2026-02-28T00:00:00Z"))).contains(7_500L);
        assertThat(service.balanceAt(id, Instant.parse("2026-12-31T00:00:00Z"))).contains(11_500L);
    }

    @Test
    void balance_before_first_transaction_is_zero() {
        assertThat(service.balanceAt(id, Instant.parse("2025-12-31T00:00:00Z"))).contains(0L);
    }

    @Test
    void event_exactly_at_the_given_time_is_included() {
        assertThat(service.balanceAt(id, JAN)).contains(10_000L);
    }

    @Test
    void unknown_account_gives_empty_result() {
        assertThat(service.balanceAt(UUID.randomUUID(), MAR)).isEmpty();
    }
}
