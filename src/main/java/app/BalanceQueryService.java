package app;

import domain.AccountEvent.AccountOpened;
import domain.AccountEvent.MoneyDeposited;
import domain.AccountEvent.MoneyWithdrawn;
import domain.AccountEvent.TransactionReversed;
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
                case AccountOpened o -> { }
                case MoneyDeposited d -> {
                    if (!d.at().isAfter(at)) balance += d.amountMinor();
                }
                case MoneyWithdrawn w -> {
                    if (!w.at().isAfter(at)) balance -= w.amountMinor();
                }
                case TransactionReversed r -> {
                    if (!r.at().isAfter(at)) {
                        balance += r.wasDeposit() ? -r.amountMinor() : r.amountMinor();
                    }
                }
            }
        }
        return Optional.of(balance);
    }
}