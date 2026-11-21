package store;
import domain.AccountEvent;
import java.util.List;
import java.util.UUID;

public class PostgresEventStore implements EventStore {

    @Override
    public List<StoredEvent> load(UUID aggregateId) {
        return List.of();
    }

    @Override
    public void append(UUID aggregateId, int expectedVersion, List<AccountEvent> events) {

    }
}