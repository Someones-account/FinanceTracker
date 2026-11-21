package projection;
import java.util.UUID;

public record AccountBalance(UUID accountId, String name, String currency, long balanceMinor) {}
