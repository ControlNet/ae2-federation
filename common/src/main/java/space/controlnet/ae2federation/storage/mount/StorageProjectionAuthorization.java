package space.controlnet.ae2federation.storage.mount;

import appeng.api.stacks.AEKey;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.policy.PolicyOperation;
import java.util.Set;
import space.controlnet.ae2federation.fabric.FabricReference;

interface StorageProjectionAuthorization {
    boolean permits(PolicyOperation operation, AEKey key);

    boolean permitsView(AEKey key);

    boolean ready();

    /**
     * Evaluates source validity and relationship currency once and returns a per-resource filter bound to that
     * evaluation, or null when the relationship is not operational. Used by one enumeration so the (cheap but
     * non-trivial) revision checks run once instead of once per key; the per-key Policy resource filter still runs
     * for every key. The returned object must not be retained beyond the enumeration that obtained it.
     */
    @Nullable ResourceAuthorization readyAuthorization();

    Set<FabricReference> scopes();

    @FunctionalInterface
    interface ResourceAuthorization {
        boolean permits(PolicyOperation operation, AEKey key);
    }
}
