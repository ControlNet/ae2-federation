package space.controlnet.ae2federation.storage.subscription;

public final class KeyRetentionOverflowException extends IllegalStateException {
    private final int maximumRetainedKeys;

    public KeyRetentionOverflowException(int maximumRetainedKeys) {
        super("Subscription key retention exceeded its hard limit of " + maximumRetainedKeys);
        this.maximumRetainedKeys = maximumRetainedKeys;
    }

    public int maximumRetainedKeys() {
        return maximumRetainedKeys;
    }
}
