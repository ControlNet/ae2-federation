package space.controlnet.ae2federation.crafting.binding;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.crafting.ICraftingService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityService;

final class NativeCraftingBackendRegistry {
    private final CraftingProviderGenerationLedger<ICraftingService, Object> generations =
            new CraftingProviderGenerationLedger<>();
    private final Map<NetworkId, NativeCraftingBackend> current = new HashMap<>();

    NativeCraftingBackend discover(IGrid grid) {
        var origin = FederationDomainRegistryAccess.confirmedNetworkId(grid).orElseThrow(() ->
                new CraftingBackendUnavailableException(space.controlnet.ae2federation.policy.BindingDiagnostic.Reason.IDENTITY_UNCONFIRMED));
        var identity = grid.getService(NetworkIdentityService.class);
        var providers = new ArrayList<NativeCraftingProviderSource>();
        for (var node : grid.getNodes()) {
            var provider = node.getService(ICraftingProvider.class);
            if (provider != null && node.isActive() && node.hasGridBooted() && node.getGrid() == grid) {
                providers.add(new NativeCraftingProviderSource(identity.lineage(node).nodeId(), node, provider));
            }
        }
        providers.sort((left, right) -> left.registrationNodeId().toString()
                .compareTo(right.registrationNodeId().toString()));
        var service = grid.getCraftingService();
        var cpus = service.getCpus();
        if (providers.isEmpty() || cpus.isEmpty()) {
            invalidate(origin);
            throw new CraftingBackendUnavailableException(providers.isEmpty()
                    ? space.controlnet.ae2federation.policy.BindingDiagnostic.Reason.CRAFTING_PROVIDER_MISSING
                    : space.controlnet.ae2federation.policy.BindingDiagnostic.Reason.CRAFTING_CPU_MISSING);
        }
        var identities = new ArrayList<Object>();
        providers.forEach(source -> identities.add(source.provider()));
        identities.addAll(cpus);
        var snapshot = generations.update(origin, service, identities);
        var active = current.get(origin);
        if (active != null && active.generation().equals(snapshot.generation()) && active.sourceGrid() == grid) {
            return active;
        }
        var backend = new NativeCraftingBackend(origin, snapshot.generation(), grid, service, providers, cpus);
        current.put(origin, backend);
        return backend;
    }

    boolean isCurrent(NativeCraftingBackend backend) {
        try {
            return current.get(backend.sourceNetworkId()) == backend && discover(backend.sourceGrid()) == backend;
        } catch (CraftingBackendUnavailableException exception) {
            return false;
        }
    }

    void clear() {
        current.clear();
        generations.clear();
    }

    private void invalidate(NetworkId origin) {
        if (current.remove(origin) != null) {
            generations.invalidate(origin);
        }
    }
}
