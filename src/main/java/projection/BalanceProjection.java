package projection;

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
import java.util.Optional;
import java.util.UUID;

public class BalanceProjection extends CheckpointedProjection {

    public static final String NAME = "account_balances";

    private final DataSource ds;

    public BalanceProjection(DataSource ds, EventLog log) {
        super(NAME, ds, log);
        this.ds = ds;
    }

    @Override
    protected void apply(Connection c, GlobalEvent e) throws SQLException {
        switch (e.event()) {
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

    @Override
    protected void clear(Connection c) throws SQLException {
        execute(c, "DELETE FROM account_balances");
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
}
