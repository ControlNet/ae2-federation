package space.controlnet.ae2federation.crafting.projection;

import appeng.api.networking.IGrid;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.ObservedGrids;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyService;

public final class CraftingFederationDomainObserver {
    private final ServerLevel level;
    private final ObservedGrids observed = new ObservedGrids();

    public CraftingFederationDomainObserver(ServerLevel level) {
        this.level = level;
    }

    public void register(IGrid grid) {
        observed.add(grid);
    }

    public void register(Iterable<IGrid> grids) {
        grids.forEach(this::register);
    }

    /** Whether {@code grid} is a loaded Grid of an observed domain member. */
    public boolean observes(IGrid grid) {
        return observed.confirmed().containsValue(grid);
    }

    /** The loaded Grid that currently carries {@code networkId}, if any. */
    public java.util.Optional<IGrid> grid(NetworkId networkId) {
        return java.util.Optional.ofNullable(observed.confirmed().get(networkId));
    }

    public Map<PolicyKey, CraftingRelationship> relationships() {
        var loadedGrids = observed.confirmed();
        var result = new HashMap<PolicyKey, CraftingRelationship>();
        var registry = FederationDomainRegistryAccess.get(level);
        // By rule: every two networks of a domain would be N(N-1) pairs, and only pairs with a rule link anything.
        for (var rule : PolicyService.get(level).configuredRules(PolicyCapability.CRAFTING)) {
            var key = rule.key();
            var consumer = loadedGrids.get(key.consumerNetworkId());
            var provider = loadedGrids.get(key.providerNetworkId());
            if (consumer != null && provider != null && consumer != provider
                    && registry.shareFederationDomain(key.consumerNetworkId(), key.providerNetworkId())) {
                result.put(key, new CraftingRelationship(key, consumer, provider));
            }
        }
        return Map.copyOf(result);
    }

    public Set<FederationDomainReference> references(CraftingRelationship relationship) {
        return FederationDomainRegistryAccess.get(level).sharedFederationDomainReferences(
                relationship.key().consumerNetworkId(), relationship.key().providerNetworkId());
    }

    public long topologyRevision() {
        return FederationDomainRegistryAccess.get(level).topologyRevision();
    }

    public void clear() {
        observed.clear();
    }
}
