package space.controlnet.ae2federation.storage.subscription;

import appeng.api.stacks.AEKey;

public record NativeStorageAmount(AEKey key, long absolute) {
    public NativeStorageAmount {
        java.util.Objects.requireNonNull(key);
        if (absolute < 0) {
            throw new IllegalArgumentException("Native absolute amount cannot be negative");
        }
    }
}
