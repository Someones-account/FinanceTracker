package store;
import com.fasterxml.jackson.databind.ObjectMapper;
import domain.AccountEvent;
import domain.AccountEvent.AccountOpened;
import domain.AccountEvent.MoneyDeposited;
import domain.AccountEvent.MoneyWithdrawn;
import domain.AccountEvent.TransactionReversed;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class PostgresEventStore implements EventStore, EventLog {

    private static final Map<String, Class<? extends AccountEvent>> TYPES = Map.of(
            "AccountOpened", AccountOpened.class,
            "MoneyDeposited", MoneyDeposited.class,
            "MoneyWithdrawn", MoneyWithdrawn.class,
            "TransactionReversed", TransactionReversed.class);

    private final DataSource ds;
    private final ObjectMapper mapper;

    public PostgresEventStore(DataSource ds, ObjectMapper mapper) {
        this.ds = ds;
        this.mapper = mapper;
    }

    @Override
    public List<StoredEvent> load(UUID aggregateId) {
        String sql = "SELECT version, event_type, payload FROM events WHERE aggregate_id = ? ORDER BY version";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, aggregateId);
            try (ResultSet rs = ps.executeQuery()) {
                List<StoredEvent> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new StoredEvent(aggregateId, rs.getInt("version"),
                            deserialize(rs.getString("event_type"), rs.getString("payload"))));
                }
                return out;
            }
        } catch (Exception e) {
            throw new EventStoreException("Load failed for " + aggregateId, e);
        }
    }

    @Override
    public void append(UUID aggregateId, int expectedVersion, List<AccountEvent> events) {
        String sql = "INSERT INTO events (aggregate_id, version, event_type, payload) VALUES (?, ?, ?, ?::jsonb)";
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                int version = expectedVersion;
                for (AccountEvent e : events) {
                    ps.setObject(1, aggregateId);
                    ps.setInt(2, ++version);
                    ps.setString(3, e.getClass().getSimpleName());
                    ps.setString(4, mapper.writeValueAsString(e));
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                if ("23505".equals(e.getSQLState())) {
                    throw new ConcurrencyException(aggregateId, expectedVersion);
                }
                throw e;
            } catch (Exception e) {
                c.rollback();
                throw e;
            }
        } catch (ConcurrencyException e) {
            throw e;
        } catch (Exception e) {
            throw new EventStoreException("Append failed for " + aggregateId, e);
        }
    }

    @Override
    public List<GlobalEvent> loadAfter(long globalSeq, int limit) {
        String sql = """
                SELECT global_seq, aggregate_id, version, event_type, payload
                FROM events
                WHERE global_seq > ?
                ORDER BY global_seq
                LIMIT ?
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, globalSeq);
            ps.setInt(2, limit);
            try (ResultSet rs = ps.executeQuery()) {
                List<GlobalEvent> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new GlobalEvent(
                            rs.getLong("global_seq"),
                            rs.getObject("aggregate_id", UUID.class),
                            rs.getInt("version"),
                            deserialize(rs.getString("event_type"), rs.getString("payload"))));
                }
                return out;
            }
        } catch (Exception e) {
            throw new EventStoreException("loadAfter failed at " + globalSeq, e);
        }
    }

    private AccountEvent deserialize(String type, String payload) throws Exception {
        Class<? extends AccountEvent> clazz = TYPES.get(type);
        if (clazz == null) {
            throw new EventStoreException("Unknown event type: " + type, null);
        }
        return mapper.readValue(payload, clazz);
    }
}