package projection;

import domain.AccountEvent;
import domain.AccountEvent.AccountOpened;
import domain.AccountEvent.MoneyDeposited;
import domain.AccountEvent.MoneyWithdrawn;
import store.EventLog;
import store.EventStoreException;
import store.GlobalEvent;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class BalanceProjection {

    public static final String NAME = "account_balances";
    private static final int BATCH_SIZE = 500;

    private final DataSource ds;
    private final EventLog log;

    public BalanceProjection(DataSource ds, EventLog log) {
        this.ds = ds;
        this.log = log;
    }

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
                try (PreparedStatement ps = c.prepareStatement("DELETE FROM account_balances")) {
                    ps.executeUpdate();
                }
                writeCheckpoint(c, 0);
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new EventStoreException("Projection reset failed", e);
        }
        return catchUp();
    }

    public Optional<AccountBalance> find(UUID accountId) {
        String sql = "SELECT account_id, name, currency, balance_minor FROM account_balances WHERE account_id = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new AccountBalance(
                        rs.getObject("account_id", UUID.class),
                        rs.getString("name"),
                        rs.getString("currency"),
                        rs.getLong("balance_minor")));
            }
        } catch (SQLException e) {
            throw new EventStoreException("Balance query failed for " + accountId, e);
        }
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
                    apply(c, e.event());
                }
                writeCheckpoint(c, events.get(events.size() - 1).globalSeq());
                c.commit();
                return events.size();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new EventStoreException("Projection batch failed", e);
        }
    }

    private void apply(Connection c, AccountEvent event) throws SQLException {
        switch (event) {
            case AccountOpened o -> execute(c, """
                    INSERT INTO account_balances (account_id, name, currency, balance_minor)
                    VALUES (?, ?, ?, 0)
                    ON CONFLICT (account_id) DO NOTHING
                    """, o.accountId(), o.name(), o.currency());
            case MoneyDeposited d -> execute(c, """
                    UPDATE account_balances SET balance_minor = balance_minor + ?, updated_at = now()
                    WHERE account_id = ?
                    """, d.amountMinor(), d.accountId());
            case MoneyWithdrawn w -> execute(c, """
                    UPDATE account_balances SET balance_minor = balance_minor - ?, updated_at = now()
                    WHERE account_id = ?
                    """, w.amountMinor(), w.accountId());
        }
    }

    private long readCheckpoint(Connection c) throws SQLException {
        String sql = "SELECT last_global_seq FROM projection_checkpoints WHERE projection_name = ? FOR UPDATE";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, NAME);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new EventStoreException("Missing checkpoint row for " + NAME, null);
                }
                return rs.getLong(1);
            }
        }
    }

    private void writeCheckpoint(Connection c, long seq) throws SQLException {
        execute(c, "UPDATE projection_checkpoints SET last_global_seq = ? WHERE projection_name = ?", seq, NAME);
    }

    private static void execute(Connection c, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            ps.executeUpdate();
        }
    }
}
