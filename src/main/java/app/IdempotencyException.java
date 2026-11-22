package app;

public class IdempotencyException extends RuntimeException {

    private final boolean inProgress;

    public IdempotencyException(String message, boolean inProgress) {
        super(message);
        this.inProgress = inProgress;
    }

    public boolean isInProgress() {
        return inProgress;
    }
}