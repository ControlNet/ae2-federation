package space.controlnet.ae2federation.ae2.storage;

import appeng.api.storage.MEStorage;

public record NativeStorageSource(MEStorage storage, int priority) {
    public NativeStorageSource {
        if (storage == null) {
            throw new NullPointerException("storage");
        }
    }
}
