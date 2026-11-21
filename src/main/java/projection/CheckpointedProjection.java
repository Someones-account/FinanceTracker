package projection;

import store.EventLog;
import store.EventStoreException;
import store.GlobalEvent;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;


public abstract class CheckpointedProjection {

    private static final int BATCH_SIZE = 500;

    private final String name;
    private final DataSource ds;
    private final EventLog log;

    protected CheckpointedProjection(String name, DataSource ds, EventLog log) {
        this.name = name;
        this.ds = ds;
        this.log = log;
    }

    protected abstract void apply(Connection c, GlobalEvent event) throws SQLException;

    protected abstract void clear(Connection c) throws SQLException;

    public synchronized int catchUp() {
        int total = 0;
        int processed;
        do {
            processed = processBatch();
            total += processed;
        } while (processed == BATCH_SIZE);
        return total;
    }

    public synchronized int rebuild() {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                clear(c);
                writeCheckpoint(c, 0);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new EventStoreException("Projection reset failed: " + name, e);
        }
        return catchUp();
    }

    private int processBatch() {
        try (Connection c = ds.getConnection()) {
            c.setAutoCommit(false);
            try {
                long last = readCheckpoint(c);
                List<GlobalEvent> events = log.loadAfter(last, BATCH_SIZE);
                if (events.isEmpty()) {
                    c.rollback();
                    return 0;
                }
                for (GlobalEvent e : events) {
                    apply(c, e);
                }
                writeCheckpoint(c, events.getLast().globalSeq());
                c.commit();
                return events.size();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new EventStoreException("Projection batch failed: " + name, e);
        }
    }

    private long readCheckpoint(Connection c) throws SQLException {
        String sql = "SELECT last_global_seq FROM projection_checkpoints WHERE projection_name = ? FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new EventStoreException("Missing checkpoint row for " + name, null);
                }
                return rs.getLong(1);
            }
        }
    }

    private void writeCheckpoint(Connection c, long seq) throws SQLException {
        execute(c, "UPDATE projection_checkpoints SET last_global_seq = ? WHERE projection_name = ?", seq, name);
    }

    protected static void execute(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }
}
