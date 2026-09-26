package space.controlnet.ae2federation.domain;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import space.controlnet.ae2federation.identity.NetworkId;

public record FederationDomainSnapshot(FederationDomainId federationDomainId, long generation, Set<FederationDomainNodeId> nodes,
        Map<NetworkId, Set<FederationDomainSourceId>> memberships) {
    public FederationDomainSnapshot {
        nodes = Collections.unmodifiableSet(new TreeSet<>(nodes));
        var copied = new TreeMap<NetworkId, Set<FederationDomainSourceId>>((left, right) -> left.toString().compareTo(right.toString()));
        memberships.forEach((network, sources) -> copied.put(network,
                Collections.unmodifiableSet(new TreeSet<>(sources))));
        memberships = Collections.unmodifiableMap(copied);
    }

    public FederationDomainReference reference() {
        return new FederationDomainReference(federationDomainId, generation);
    }
}
