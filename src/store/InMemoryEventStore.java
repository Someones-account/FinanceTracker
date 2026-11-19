package store;

import domain.AccountEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InMemoryEventStore implements EventStore {

    private final Map<UUID, List<StoredEvent>> streams = new HashMap<>();

    @Override
    public synchronized List<StoredEvent> load(UUID aggregateId) {
        return List.copyOf(streams.getOrDefault(aggregateId, List.of()));
    }

    @Override
    public synchronized void append(UUID aggregateId, int expectedVersion, List<AccountEvent> events) {
        List<StoredEvent> stream = streams.computeIfAbsent(aggregateId, id -> new ArrayList<>());
        if (stream.size() != expectedVersion) {
            throw new ConcurrencyException(aggregateId, expectedVersion);
        }
        int version = expectedVersion;
        for (AccountEvent event : events) {
            stream.add(new StoredEvent(aggregateId, ++version, event));
        }
    }
}
