package projection;

import domain.AccountEvent;
import store.EventLog;
import store.EventStoreException;
import store.GlobalEvent;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class CategoryReportProjection extends CheckpointedProjection {

    public static final String NAME = "category_totals";

    private static final String UPSERT = """
            INSERT INTO category_totals (account_id, month, category, income_minor, expense_minor)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT (account_id, month, category) DO UPDATE SET
                income_minor  = category_totals.income_minor  + EXCLUDED.income_minor,
                expense_minor = category_totals.expense_minor + EXCLUDED.expense_minor
            """;

    private final DataSource ds;

    public CategoryReportProjection(DataSource ds, EventLog log) {
        super(NAME, ds, log);
        this.ds = ds;
    }

    @Override
    protected void apply(Connection c, GlobalEvent e) throws SQLException {
        switch (e.event()) {
            case AccountEvent.AccountOpened o -> { }
            case AccountEvent.MoneyDeposited d ->
                    execute(c, UPSERT, d.accountId(), monthOf(d.at()), d.category(), d.amountMinor(), 0L);
            case AccountEvent.MoneyWithdrawn w ->
                    execute(c, UPSERT, w.accountId(), monthOf(w.at()), w.category(), 0L, w.amountMinor());
        }
    }

    @Override
    protected void clear(Connection c) throws SQLException {
        execute(c, "DELETE FROM category_totals");
    }

    public List<CategoryTotal> find(UUID accountId, YearMonth month) {
        String sql = """
                SELECT category, income_minor, expense_minor
                FROM category_totals
                WHERE account_id = ? AND month = ?
                ORDER BY expense_minor DESC, category
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, accountId);
            ps.setObject(2, month.atDay(1));
            try (ResultSet rs = ps.executeQuery()) {
                List<CategoryTotal> out = new ArrayList<>();
                while (rs.next()) {
                    out.add(new CategoryTotal(rs.getString("category"),
                            rs.getLong("income_minor"), rs.getLong("expense_minor")));
                }
                return out;
            }
        } catch (SQLException e) {
            throw new EventStoreException("Report query failed for " + accountId, e);
        }
    }

    private static java.time.LocalDate monthOf(Instant at) {
        return at.atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1);
    }
}