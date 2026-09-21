package space.controlnet.ae2federation.storage.mount;

import appeng.api.stacks.AEKey;
import space.controlnet.ae2federation.policy.PolicyOperation;
import java.util.Set;
import space.controlnet.ae2federation.fabric.FabricReference;

interface StorageProjectionAuthorization {
    boolean permits(PolicyOperation operation, AEKey key);

    boolean permitsView(AEKey key);

    boolean ready();

    Set<FabricReference> scopes();
}
