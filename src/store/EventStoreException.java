package store;

public class EventStoreException extends RuntimeException {
    public EventStoreException(String message, Exception e) {
        super(message);
    }
}
