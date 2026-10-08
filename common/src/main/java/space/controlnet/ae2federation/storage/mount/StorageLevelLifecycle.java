package space.controlnet.ae2federation.storage.mount;

import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;

public final class StorageLevelLifecycle {
    private StorageLevelLifecycle() {
    }

    /** Takes the level's nodes out of the server-wide registry, then ends the storage relationships that needed them. */
    public static CloseReceipt close(ServerLevel level) {
        var federationDomains = FederationDomainRegistryAccess.closeLevel(level);
        var mounts = StorageMountService.levelClosed(level);
        return new CloseReceipt(mounts.servicePresentBefore(), mounts.mountedProvidersBefore(),
                mounts.mountedProvidersRemoved(), mounts.servicePersists(), federationDomains.nodesRemoved(),
                federationDomains.bridgesRemoved(), federationDomains.nodesLeft());
    }

    /** What the level's dimension took out of the server-wide registry, and the mounts that ended with it. */
    public record CloseReceipt(boolean servicePresentBefore, int mountedProvidersBefore, int mountedProvidersRemoved,
            boolean servicePersists, int dimensionNodesRemoved, int dimensionBridgesRemoved, int dimensionNodesLeft) {
    }
}
