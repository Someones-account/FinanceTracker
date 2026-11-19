package app;
import domain.Account;
import domain.AccountCommand;
import store.EventStore;
import store.StoredEvent;
import java.time.Clock;
import java.util.UUID;

public class AccountCommandHandler {
    EventStore store;
    Clock clock;

    public void handle(UUID accountId, AccountCommand command) {
        var stored = store.load(accountId);
        var state = stored.stream()
                .map(StoredEvent::event)
                .reduce(Account.empty(), Account::evolve, (a, b) -> b);
        int version = stored.size();

        var newEvents = Account.decide(state, command, clock.instant());
        store.append(accountId, version, newEvents);
    }
}
