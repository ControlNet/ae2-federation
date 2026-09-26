package space.controlnet.ae2federation.client.policy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import appeng.api.crafting.PatternDetailsHelper;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphLayer;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphNodeKind;
import space.controlnet.ae2federation.client.domain.FederationDomainGraphSnapshot;
import space.controlnet.ae2federation.client.domain.FederationDomainPatternRow;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.observability.id.EndpointId;
import space.controlnet.ae2federation.observability.id.MemberId;
import space.controlnet.ae2federation.observability.id.ProviderId;
import space.controlnet.ae2federation.observability.state.FederationDomainStateProjector;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.provider.ProviderObservationRegistry;

public final class FederationDomainGraphProjection {
    private FederationDomainGraphProjection() {
    }

    public static FederationDomainGraphSnapshot snapshot(ServerLevel level, FederationDomainReference scope,
            Optional<ProviderObservationRegistry.Entry> selectedProvider) {
        var registry = FederationDomainRegistryAccess.get(level);
        if (!registry.isCurrent(scope)) {
            return FederationDomainGraphSnapshot.empty();
        }
        var federationDomain = registry.federationDomain(scope.federationDomainId()).orElseThrow();
        var state = new FederationDomainStateProjector(level).snapshot(scope);
        var nodes = new ArrayList<FederationDomainGraphSnapshot.Node>();
        var edges = new ArrayList<FederationDomainGraphSnapshot.Edge>();
        var memberIds = new HashMap<NetworkId, String>();
        state.members().forEach(member -> {
            memberIds.put(member.networkId(), member.id().value());
            nodes.add(new FederationDomainGraphSnapshot.Node(member.id().value(), FederationDomainGraphNodeKind.MEMBER, member.status()));
        });

        var providers = providerEntries(level, federationDomain);
        providers.forEach(entry -> {
            var id = providerId(scope, entry);
            var status = entry.runtime().lastResolution().state().name().toLowerCase(java.util.Locale.ROOT);
            nodes.add(new FederationDomainGraphSnapshot.Node(id, FederationDomainGraphNodeKind.PROVIDER, status));
            FederationDomainRegistryAccess.confirmedNetworkId(entry.provider().getGrid()).map(memberIds::get).ifPresent(member ->
                    edges.add(new FederationDomainGraphSnapshot.Edge(member, id, FederationDomainGraphLayer.PHYSICAL, "provider")));
        });

        endpointEntries(level, federationDomain).forEach(binding -> {
            var id = endpointId(scope, binding);
            var status = binding.runtime().configuredMode().name().toLowerCase(java.util.Locale.ROOT);
            nodes.add(new FederationDomainGraphSnapshot.Node(id, FederationDomainGraphNodeKind.ENDPOINT, status));
            FederationDomainRegistryAccess.confirmedNetworkId(binding.subnetNode().getGrid()).map(memberIds::get).ifPresent(member ->
                    edges.add(new FederationDomainGraphSnapshot.Edge(member, id, FederationDomainGraphLayer.PHYSICAL, status)));
            if (binding.claimState() instanceof ClaimState.Owned owned) {
                providers.stream().filter(entry -> entry.identity().equals(owned.ownerIdentity().provider())).findFirst()
                        .ifPresent(entry -> edges.add(new FederationDomainGraphSnapshot.Edge(providerId(scope, entry), id,
                                FederationDomainGraphLayer.CAPABILITY, "claim-" + owned.epoch().value())));
            }
        });
        var patterns = selectedProvider.map(entry -> patterns(level, entry)).orElse(List.of());
        return new FederationDomainGraphSnapshot(state.topologyRevision(), Math.max(state.policyRevision(), state.dataRevision()),
                nodes, edges, patterns);
    }

    static List<ProviderObservationRegistry.Entry> providerEntries(ServerLevel level,
            space.controlnet.ae2federation.domain.FederationDomainSnapshot federationDomain) {
        return ProviderObservationRegistry.entries(level).stream()
                .filter(entry -> memberOf(federationDomain, entry.provider().getGrid()))
                .sorted(Comparator.comparing(entry -> providerId(federationDomain.reference(), entry))).toList();
    }

    static List<EndpointTargetBinding> endpointEntries(ServerLevel level,
            space.controlnet.ae2federation.domain.FederationDomainSnapshot federationDomain) {
        return EndpointTargetBinding.entries(level).stream()
                .filter(binding -> memberOf(federationDomain, binding.subnetNode().getGrid()))
                .sorted(Comparator.comparing(binding -> endpointId(federationDomain.reference(), binding))).toList();
    }

    static String providerId(FederationDomainReference scope, ProviderObservationRegistry.Entry entry) {
        var identity = entry.identity();
        return ProviderId.of(scope.federationDomainId(), identity.id().value() + ":" + identity.instanceEpoch().value()).value();
    }

    static String endpointId(FederationDomainReference scope, EndpointTargetBinding binding) {
        var identity = binding.endpointIdentity();
        return EndpointId.of(scope.federationDomainId(), identity.id().value() + ":" + identity.instanceEpoch().value()).value();
    }

    private static boolean memberOf(space.controlnet.ae2federation.domain.FederationDomainSnapshot federationDomain,
            appeng.api.networking.IGrid grid) {
        return FederationDomainRegistryAccess.confirmedNetworkId(grid).filter(federationDomain.memberships()::containsKey).isPresent();
    }

    private static List<String> patterns(ServerLevel level, ProviderObservationRegistry.Entry entry) {
        var provider = entry.provider();
        var inventory = provider.patternInventory();
        var rows = new ArrayList<String>(inventory.size());
        for (var slot = 0; slot < inventory.size(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            var name = stack.isEmpty() ? "empty" : stack.getHoverName().getString();
            var details = stack.isEmpty() ? null : PatternDetailsHelper.decodePattern(stack, level);
            var quantities = details == null ? "" : java.util.Arrays.stream(details.getInputs())
                    .map(input -> Long.toString(Math.multiplyExact(input.getPossibleInputs()[0].amount(),
                            input.getMultiplier())))
                    .collect(java.util.stream.Collectors.joining(","));
            rows.add(FederationDomainPatternRow.format(slot, name, quantities, provider.lanesForSlot(slot)));
        }
        return List.copyOf(rows);
    }
}
