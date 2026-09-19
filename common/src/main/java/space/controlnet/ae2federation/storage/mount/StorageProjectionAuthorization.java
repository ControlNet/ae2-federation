package space.controlnet.ae2federation.storage.mount;

import appeng.api.stacks.AEKey;
import space.controlnet.ae2federation.policy.PolicyOperation;

interface StorageProjectionAuthorization {
    boolean permits(PolicyOperation operation, AEKey key);

    boolean permitsView(AEKey key);

    boolean ready();
}
