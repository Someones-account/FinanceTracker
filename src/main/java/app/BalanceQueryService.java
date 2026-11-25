package app;

import domain.AccountEvent;
import store.EventStore;
import store.StoredEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class BalanceQueryService {

    private final EventStore store;

    public BalanceQueryService(EventStore store) {
        this.store = store;
    }

    public Optional<Long> balanceAt(UUID accountId, Instant at) {
        List<StoredEvent> history = store.load(accountId);
        if (history.isEmpty()) {
            return Optional.empty();
        }

        long balance = 0;
        for (StoredEvent stored : history) {
            switch (stored.event()) {
                case AccountEvent.AccountOpened o -> { }
                case AccountEvent.MoneyDeposited d -> {
                    if (!d.at().isAfter(at)) balance += d.amountMinor();
                }
                case AccountEvent.MoneyWithdrawn w -> {
                    if (!w.at().isAfter(at)) balance -= w.amountMinor();
                }
            }
        }
        return Optional.of(balance);
    }
}
