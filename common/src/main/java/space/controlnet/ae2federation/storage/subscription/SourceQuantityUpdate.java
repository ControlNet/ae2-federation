package space.controlnet.ae2federation.storage.subscription;

public record SourceQuantityUpdate<K>(K key, long previousAmount, long absoluteAmount, long eventVersion,
        long snapshotVersion) {
    public SourceQuantityUpdate {
        java.util.Objects.requireNonNull(key);
        if (previousAmount < 0 || absoluteAmount < 0 || eventVersion < 1 || snapshotVersion < 1) {
            throw new IllegalArgumentException("Source quantity update values must be nonnegative and versioned");
        }
    }
}
