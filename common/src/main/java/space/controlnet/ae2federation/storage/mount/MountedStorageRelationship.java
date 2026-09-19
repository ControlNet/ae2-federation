package space.controlnet.ae2federation.storage.mount;

import space.controlnet.ae2federation.storage.provenance.MountGeneration;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomain;

record MountedStorageRelationship(StorageRelationship relationship, NativeSourceDomain domain,
        MountGeneration generation, RelationshipStorageProvider provider) {
    boolean sourceReady() {
        return domain.sourceNodes().stream().allMatch(node -> node.isActive() && node.hasGridBooted()
                && node.getGrid() == relationship.providerGrid());
    }
}
