package domain;
import java.util.UUID;

public sealed interface AccountCommand {
    record Open(UUID accountId, String name, String currency) implements AccountCommand {}
    record Deposit(long amountMinor, String category) implements AccountCommand {}
    record Withdraw(long amountMinor, String category) implements AccountCommand {}
}
