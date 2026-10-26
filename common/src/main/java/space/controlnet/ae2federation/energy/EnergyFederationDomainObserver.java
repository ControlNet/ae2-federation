package space.controlnet.ae2federation.energy;

import appeng.api.networking.IGrid;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.ObservedGrids;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyService;

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
        var registry = FederationDomainRegistryAccess.get(level);
        // By rule: every two networks of a domain would be N(N-1) pairs, and only pairs with a rule link anything.
        for (var rule : PolicyService.get(level).configuredRules(PolicyCapability.ME_POWER)) {
            var key = rule.key();
            var consumer = loadedGrids.get(key.consumerNetworkId());
            var provider = loadedGrids.get(key.providerNetworkId());
            if (consumer != null && provider != null && consumer != provider
                    && registry.shareFederationDomain(key.consumerNetworkId(), key.providerNetworkId())) {
                relationships.put(key, new EnergyRelationship(key, consumer, provider));
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
