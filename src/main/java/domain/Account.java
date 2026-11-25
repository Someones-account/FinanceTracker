package domain;

import domain.AccountCommand.Deposit;
import domain.AccountCommand.Open;
import domain.AccountCommand.Reverse;
import domain.AccountCommand.Withdraw;
import domain.AccountEvent.AccountOpened;
import domain.AccountEvent.MoneyDeposited;
import domain.AccountEvent.MoneyWithdrawn;
import domain.AccountEvent.TransactionReversed;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Account(UUID id, long balanceMinor, boolean open, int version, Map<Integer, Tx> transactions) {

    public record Tx(boolean deposit, long amountMinor, String category, Instant at, boolean reversed) {
        Tx markReversed() {
            return new Tx(deposit, amountMinor, category, at, true);
        }
    }

    public static Account empty() {
        return new Account(null, 0, false, 0, Map.of());
    }

    public static Account evolve(Account a, AccountEvent e) {
        int next = a.version + 1; // version = number of events applied so far, so the next event gets version + 1
        return switch (e) {
            case AccountOpened o -> new Account(o.accountId(), 0, true, next, a.transactions);
            case MoneyDeposited d -> new Account(a.id, a.balanceMinor + d.amountMinor(), a.open, next,
                    plus(a.transactions, next, new Tx(true, d.amountMinor(), d.category(), d.at(), false)));
            case MoneyWithdrawn w -> new Account(a.id, a.balanceMinor - w.amountMinor(), a.open, next,
                    plus(a.transactions, next, new Tx(false, w.amountMinor(), w.category(), w.at(), false)));
            case TransactionReversed r -> {
                Map<Integer, Tx> updated = new HashMap<>(a.transactions);
                Tx original = updated.get(r.reversedVersion());
                if (original != null) {
                    updated.put(r.reversedVersion(), original.markReversed());
                }
                long delta = r.wasDeposit() ? -r.amountMinor() : r.amountMinor();
                yield new Account(a.id, a.balanceMinor + delta, a.open, next, Map.copyOf(updated));
            }
        };
    }

    public static List<AccountEvent> decide(Account a, AccountCommand c, Instant now) {
        return switch (c) {
            case Open o -> {
                if (a.open) throw new DomainException("Account already open");
                yield List.of(new AccountOpened(o.accountId(), o.name(), o.currency()));
            }
            case Deposit d -> {
                requireOpen(a);
                if (d.amountMinor() <= 0) throw new DomainException("Amount must be positive");
                yield List.of(new MoneyDeposited(a.id, d.amountMinor(), d.category(), now));
            }
            case Withdraw w -> {
                requireOpen(a);
                if (w.amountMinor() <= 0) throw new DomainException("Amount must be positive");
                if (a.balanceMinor < w.amountMinor()) throw new DomainException("Insufficient funds");
                yield List.of(new MoneyWithdrawn(a.id, w.amountMinor(), w.category(), now));
            }
            case Reverse r -> {
                requireOpen(a);
                Tx tx = a.transactions.get(r.targetVersion());
                if (tx == null) {
                    throw new DomainException("No deposit or withdrawal at version " + r.targetVersion());
                }
                if (tx.reversed()) {
                    throw new DomainException("Transaction is already reversed");
                }
                if (tx.deposit() && a.balanceMinor < tx.amountMinor()) {
                    throw new DomainException("Insufficient funds to reverse this deposit");
                }
                yield List.of(new TransactionReversed(a.id, r.targetVersion(), tx.deposit(),
                        tx.amountMinor(), tx.category(), tx.at(), r.reason(), now));
            }
        };
    }

    private static Map<Integer, Tx> plus(Map<Integer, Tx> map, int key, Tx tx) {
        Map<Integer, Tx> copy = new HashMap<>(map);
        copy.put(key, tx);
        return Map.copyOf(copy);
    }

    private static void requireOpen(Account a) {
        if (!a.open) throw new DomainException("Account is not open");
    }
}