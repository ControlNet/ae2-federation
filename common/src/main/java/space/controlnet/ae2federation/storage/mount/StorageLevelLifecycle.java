package space.controlnet.ae2federation.storage.mount;

import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;

public final class StorageLevelLifecycle {
    private StorageLevelLifecycle() {
    }

    public static CloseReceipt close(ServerLevel level) {
        var mounts = StorageMountService.closeLevel(level);
        var federationDomains = FederationDomainRegistryAccess.closeLevel(level);
        return new CloseReceipt(mounts.servicePresentBefore(), mounts.mountedProvidersBefore(),
                mounts.mountedProvidersRemoved(), mounts.serviceRemoved(), federationDomains.nodesRemoved(),
                federationDomains.bridgesRemoved(), federationDomains.nodesLeft());
    }

    /** The level's mounts, and what its dimension took out of the server-wide domain registry. */
    public record CloseReceipt(boolean servicePresentBefore, int mountedProvidersBefore, int mountedProvidersRemoved,
            boolean serviceRemoved, int dimensionNodesRemoved, int dimensionBridgesRemoved, int dimensionNodesLeft) {
    }
}
