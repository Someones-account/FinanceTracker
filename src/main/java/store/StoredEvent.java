package store;
import java.util.UUID;
import domain.AccountEvent;

public record StoredEvent(UUID aggregateId, int version, AccountEvent event) {}
