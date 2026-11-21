import domain.Account;
import domain.AccountCommand;
import domain.AccountEvent;

import java.time.Instant;
import java.util.UUID;

public class Main {
    public static void main(String[] args) {
        final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
        final UUID id = UUID.randomUUID();
        Account account = Account.empty();
        AccountEvent event = new AccountEvent.AccountOpened(id, "random", "GBP");
        Account.evolve(account, event);
        var result = Account.decide(account, new AccountCommand.Withdraw(500, "random"), Instant.now());
    }
}