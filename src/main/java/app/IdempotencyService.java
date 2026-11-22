package app;

import domain.AccountCommand;
import store.EventStoreException;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.UUID;

public class IdempotencyService {

    private record Stored(String requestHash, boolean completed) {}

    private final DataSource ds;

    public IdempotencyService(DataSource ds) {
        this.ds = ds;
    }

    public void execute(String key, UUID accountId, AccountCommand command, Runnable action) {
        String hash = fingerprint(accountId, command);

        for (int attempt = 0; attempt < 3; attempt++) {
            if (claim(key, accountId, hash)) {
                runClaimed(key, action);
                return;
            }
            Stored existing = find(key);
            if (existing == null) {
                continue;
            }
            if (!existing.requestHash().equals(hash)) {
                throw new IdempotencyException("Idempotency-Key was already used for a different request", false);
            }
            if (existing.completed()) {
                return;
            }
            throw new IdempotencyException("A request with this Idempotency-Key is still in progress", true);
        }
        throw new IdempotencyException("Could not claim the Idempotency-Key, please retry", true);
    }

    private void runClaimed(String key, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            release(key);
            throw e;
        }
        complete(key);
    }

    private boolean claim(String key, UUID accountId, String hash) {
        String sql = """
                INSERT INTO idempotency_keys (idempotency_key, account_id, request_hash, status)
                VALUES (?, ?, ?, 'IN_PROGRESS')
                ON CONFLICT (idempotency_key) DO NOTHING
                """;
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.setObject(2, accountId);
            ps.setString(3, hash);
            return ps.executeUpdate() == 1;
        } catch (SQLException e) {
            throw new EventStoreException("Idempotency claim failed", e);
        }
    }

    private Stored find(String key) {
        String sql = "SELECT request_hash, status FROM idempotency_keys WHERE idempotency_key = ?";
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new Stored(rs.getString("request_hash"), "COMPLETED".equals(rs.getString("status")));
            }
        } catch (SQLException e) {
            throw new EventStoreException("Idempotency lookup failed", e);
        }
    }

    private void complete(String key) {
        update("UPDATE idempotency_keys SET status = 'COMPLETED' WHERE idempotency_key = ?", key);
    }

    private void release(String key) {
        update("DELETE FROM idempotency_keys WHERE idempotency_key = ?", key);
    }

    private void update(String sql, String key) {
        try (Connection c = ds.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new EventStoreException("Idempotency update failed", e);
        }
    }

    private static String fingerprint(UUID accountId, AccountCommand command) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((accountId + ":" + command).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
