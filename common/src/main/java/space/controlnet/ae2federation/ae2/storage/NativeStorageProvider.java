package space.controlnet.ae2federation.ae2.storage;

import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NativeStorageProvider implements
        space.controlnet.ae2federation.storage.provenance.FederationManagedStorageProvider {
    private final NativeStorageProvenance owner;
    private final Map<MEStorage, Integer> mounts = new LinkedHashMap<>();

    NativeStorageProvider(NativeStorageProvenance owner) {
        this.owner = owner;
    }

    @Override
    public void mountInventories(appeng.api.storage.IStorageMounts storageMounts) {
        mounts.forEach((storage, priority) -> storageMounts.mount(storage, priority));
    }

    public MEStorage mountProjection(MEStorage delegate, int priority) {
        var projection = owner.createProjection(delegate);
        mounts.put(projection, priority);
        return projection;
    }

    public MEStorage mountRoute(String route, MEStorage nativeSource, int priority) {
        var alias = owner.createRoute(route, nativeSource);
        mounts.put(alias, priority);
        return alias;
    }

    NativeStorageProvenance owner() {
        return owner;
    }
}
