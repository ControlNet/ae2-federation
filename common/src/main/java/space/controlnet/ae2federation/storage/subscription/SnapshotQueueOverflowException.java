package space.controlnet.ae2federation.storage.subscription;

public final class SnapshotQueueOverflowException extends IllegalStateException {
    public SnapshotQueueOverflowException() {
        super("Source snapshot event boundary exceeded its bounded queue");
    }
}
