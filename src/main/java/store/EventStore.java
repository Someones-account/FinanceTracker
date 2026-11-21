package store;
import domain.AccountEvent;
import java.util.List;
import java.util.UUID;


public interface EventStore {
    List<StoredEvent> load(UUID aggregateId);
    void append(UUID aggregateId, int expectedVersion, List<AccountEvent> events);
}
