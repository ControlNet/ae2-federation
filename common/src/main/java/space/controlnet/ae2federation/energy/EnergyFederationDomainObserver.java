package space.controlnet.ae2federation.energy;

import appeng.api.networking.IGrid;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.ObservedGrids;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;

final class EnergyFederationDomainObserver {
    private final ServerLevel level;
    private final ObservedGrids observed = new ObservedGrids();

    EnergyFederationDomainObserver(ServerLevel level) {
        this.level = level;
    }

    void register(IGrid grid) {
        observed.add(grid);
    }

    void register(Iterable<IGrid> grids) {
        grids.forEach(this::register);
    }

    Map<PolicyKey, EnergyRelationship> relationships() {
        var loadedGrids = observed.confirmed();
        var relationships = new HashMap<PolicyKey, EnergyRelationship>();
        for (var federationDomain : FederationDomainRegistryAccess.get(level).federationDomains()) {
            var members = federationDomain.memberships().keySet().stream().map(loadedGrids::get)
                    .filter(java.util.Objects::nonNull).toList();
            for (var consumer : members) {
                for (var provider : members) {
                    if (consumer != provider) {
                        var key = new PolicyKey(FederationDomainRegistryAccess.confirmedNetworkId(consumer).orElseThrow(),
                                FederationDomainRegistryAccess.confirmedNetworkId(provider).orElseThrow(),
                                PolicyCapability.ME_POWER);
                        relationships.put(key, new EnergyRelationship(key, consumer, provider));
                    }
                }
            }
        }
        return Map.copyOf(relationships);
    }

    long topologyRevision() {
        return FederationDomainRegistryAccess.get(level).topologyRevision();
    }

    void clear() {
        observed.clear();
    }
}
