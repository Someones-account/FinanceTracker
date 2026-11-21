package store;
import java.util.UUID;

public class ConcurrencyException extends RuntimeException {
    public ConcurrencyException(UUID id, int expectedVersion) {
        super("Version conflict for " + id + " at version " + expectedVersion);
    }
}
