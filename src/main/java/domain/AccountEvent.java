package domain;
import java.time.Instant;
import java.util.UUID;

public sealed interface AccountEvent {
    UUID accountId();

    record AccountOpened(UUID accountId, String name, String currency) implements AccountEvent {}
    record MoneyDeposited(UUID accountId, long amountMinor, String category, Instant at) implements AccountEvent {}
    record MoneyWithdrawn(UUID accountId, long amountMinor, String category, Instant at) implements AccountEvent {}
}
