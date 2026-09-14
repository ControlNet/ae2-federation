package space.controlnet.ae2federation.fabric;

import java.util.Map;
import java.util.Set;
import space.controlnet.ae2federation.identity.NetworkId;

public record FabricRegistrySnapshot(Map<FabricId, FabricSnapshot> fabrics,
        Map<NetworkId, Set<FabricId>> networkIndex,
        Map<FabricNodeId, FabricInvalidationReason> invalidations,
        long topologyRevision) {
    public FabricRegistrySnapshot {
        fabrics = Map.copyOf(fabrics);
        networkIndex = Map.copyOf(networkIndex);
        invalidations = Map.copyOf(invalidations);
    }
}
