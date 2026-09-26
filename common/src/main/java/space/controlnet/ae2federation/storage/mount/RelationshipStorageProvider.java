package space.controlnet.ae2federation.storage.mount;

import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;

public record RelationshipStorageProvider(MEStorage projection, int priority) implements
        space.controlnet.ae2federation.storage.provenance.FederationManagedStorageProvider {
    @Override
    public void mountInventories(IStorageMounts storageMounts) {
        storageMounts.mount(projection, priority);
    }
}
