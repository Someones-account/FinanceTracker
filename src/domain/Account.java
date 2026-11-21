package domain;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record Account(UUID id, long balanceMinor, boolean open) {

    public static Account empty() { return new Account(null, 0, false); }

    public static Account evolve(Account acc, AccountEvent event) {
        return switch (event) {
            case AccountEvent.AccountOpened o   -> new Account(o.accountId(), 0, true);
            case AccountEvent.MoneyDeposited d  -> new Account(acc.id, acc.balanceMinor + d.amountMinor(), acc.open);
            case AccountEvent.MoneyWithdrawn w  -> new Account(acc.id, acc.balanceMinor - w.amountMinor(), acc.open);
        };
    }

    public static List<AccountEvent> decide(Account acc, AccountCommand comm, Instant now) throws DomainException {
        return switch (comm) {
            case AccountCommand.Open o -> {
                if (acc.open) throw new DomainException("Account already open");
                yield List.of(new AccountEvent.AccountOpened(o.accountId(), o.name(), o.currency()));
            }
            case AccountCommand.Deposit d -> {
                requireOpen(acc);
                if (d.amountMinor() <= 0) throw new DomainException("Amount must be positive");
                yield List.of(new AccountEvent.MoneyDeposited(acc.id, d.amountMinor(), d.category(), now));
            }
            case AccountCommand.Withdraw w -> {
                requireOpen(acc);
                if (w.amountMinor() <= 0) throw new DomainException("Amount must be positive");
                if (acc.balanceMinor < w.amountMinor()) throw new DomainException("Insufficient funds");
                yield List.of(new AccountEvent.MoneyWithdrawn(acc.id, w.amountMinor(), w.category(), now));
            }
        };
    }

    private static void requireOpen(Account a) throws DomainException {
        if (!a.open) throw new DomainException("Account is not open");
    }
}