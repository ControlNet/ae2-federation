package space.controlnet.ae2federation.fabric;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import space.controlnet.ae2federation.identity.NetworkId;

public record FabricSnapshot(FabricId fabricId, long generation, Set<FabricNodeId> nodes,
        Map<NetworkId, Set<FabricSourceId>> memberships) {
    public FabricSnapshot {
        nodes = Collections.unmodifiableSet(new TreeSet<>(nodes));
        var copied = new TreeMap<NetworkId, Set<FabricSourceId>>((left, right) -> left.toString().compareTo(right.toString()));
        memberships.forEach((network, sources) -> copied.put(network,
                Collections.unmodifiableSet(new TreeSet<>(sources))));
        memberships = Collections.unmodifiableMap(copied);
    }

    public FabricReference reference() {
        return new FabricReference(fabricId, generation);
    }
}
