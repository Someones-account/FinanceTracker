import app.AccountCommandHandler;
import domain.AccountCommand;
import domain.AccountEvent;
import domain.DomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import store.ConcurrencyException;
import store.EventStore;
import store.InMemoryEventStore;
import store.StoredEvent;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountCommandHandlerTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);

    private InMemoryEventStore store;
    private AccountCommandHandler handler;
    private UUID id;

    @BeforeEach
    void setUp() {
        store = new InMemoryEventStore();
        handler = new AccountCommandHandler(store, CLOCK);
        id = UUID.randomUUID();
    }

    @Test
    void commands_are_persisted_as_events_with_increasing_versions() {
        handler.handle(id, new AccountCommand.Open(id, "Main", "PLN"));
        handler.handle(id, new AccountCommand.Deposit(1_000, "salary"));
        handler.handle(id, new AccountCommand.Withdraw(300, "food"));

        List<StoredEvent> history = store.load(id);

        assertThat(history).extracting(StoredEvent::version).containsExactly(1, 2, 3);
        assertThat(history.get(0).event()).isInstanceOf(AccountEvent.AccountOpened.class);
    }

    @Test
    void rejected_command_stores_nothing() {
        handler.handle(id, new AccountCommand.Open(id, "Main", "PLN"));

        assertThatThrownBy(() -> handler.handle(id, new AccountCommand.Withdraw(500, "food")))
                .isInstanceOf(DomainException.class);

        assertThat(store.load(id)).hasSize(1);
    }

    @Test
    void conflict_is_retried_and_succeeds() {
        handler.handle(id, new AccountCommand.Open(id, "Main", "PLN"));

        EventStore racing = new EventStore() {
            private final AtomicInteger appends = new AtomicInteger();

            @Override
            public List<StoredEvent> load(UUID aggregateId) {
                return store.load(aggregateId);
            }

            @Override
            public void append(UUID aggregateId, int expectedVersion, List<AccountEvent> events) {
                if (appends.getAndIncrement() == 0) {
                    store.append(aggregateId, expectedVersion,
                            List.of(new AccountEvent.MoneyDeposited(aggregateId, 100, "other", CLOCK.instant())));
                }
                store.append(aggregateId, expectedVersion, events);
            }
        };

        new AccountCommandHandler(racing, CLOCK).handle(id, new AccountCommand.Deposit(1_000, "salary"));

        assertThat(store.load(id)).hasSize(3);
    }

    @Test
    void gives_up_after_max_attempts() {
        EventStore alwaysConflicts = new EventStore() {
            @Override
            public List<StoredEvent> load(UUID aggregateId) {
                return List.of();
            }

            @Override
            public void append(UUID aggregateId, int expectedVersion, List<AccountEvent> events) {
                throw new ConcurrencyException(aggregateId, expectedVersion);
            }
        };

        var h = new AccountCommandHandler(alwaysConflicts, CLOCK);

        assertThatThrownBy(() -> h.handle(id, new AccountCommand.Open(id, "Main", "PLN")))
                .isInstanceOf(ConcurrencyException.class);
    }
}