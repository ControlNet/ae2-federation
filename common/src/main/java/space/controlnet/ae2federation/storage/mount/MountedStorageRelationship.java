package space.controlnet.ae2federation.storage.mount;

import space.controlnet.ae2federation.storage.provenance.MountGeneration;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomain;
import space.controlnet.ae2federation.storage.dependency.EffectiveSourceRelationshipKey;

record MountedStorageRelationship(StorageRelationship relationship, NativeSourceDomain domain,
        MountGeneration generation, RelationshipStorageProvider provider, EffectiveSourceRelationshipKey effectiveKey) {
}
