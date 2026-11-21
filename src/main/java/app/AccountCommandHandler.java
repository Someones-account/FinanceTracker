package app;
import domain.Account;
import domain.AccountCommand;
import domain.AccountEvent;
import store.ConcurrencyException;
import store.EventStore;
import store.StoredEvent;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

public class AccountCommandHandler {

    private static final int MAX_ATTEMPTS = 3;

    private final EventStore store;
    private final Clock clock;

    public AccountCommandHandler(EventStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public void handle(UUID accountId, AccountCommand command) {
        for (int attempt = 1; ; attempt++) {
            try {
                handleOnce(accountId, command);
                return;
            } catch (ConcurrencyException e) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw e;
                }
            }
        }
    }

    private void handleOnce(UUID accountId, AccountCommand command) {
        List<StoredEvent> history = store.load(accountId);

        Account state = Account.empty();
        for (StoredEvent stored : history) {
            state = Account.evolve(state, stored.event());
        }

        List<AccountEvent> newEvents = Account.decide(state, command, clock.instant());
        store.append(accountId, history.size(), newEvents);
    }
}