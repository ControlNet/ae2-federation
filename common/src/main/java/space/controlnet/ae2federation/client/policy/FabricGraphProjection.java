package space.controlnet.ae2federation.client.policy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import appeng.api.crafting.PatternDetailsHelper;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.client.fabric.FabricGraphLayer;
import space.controlnet.ae2federation.client.fabric.FabricGraphNodeKind;
import space.controlnet.ae2federation.client.fabric.FabricGraphSnapshot;
import space.controlnet.ae2federation.client.fabric.FabricPatternRow;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.observability.id.EndpointId;
import space.controlnet.ae2federation.observability.id.MemberId;
import space.controlnet.ae2federation.observability.id.ProviderId;
import space.controlnet.ae2federation.observability.state.FabricStateProjector;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.provider.ProviderObservationRegistry;

public final class FabricGraphProjection {
    private FabricGraphProjection() {
    }

    public static FabricGraphSnapshot snapshot(ServerLevel level, FabricReference scope,
            Optional<ProviderObservationRegistry.Entry> selectedProvider) {
        var registry = FabricRegistryAccess.get(level);
        if (!registry.isCurrent(scope)) {
            return FabricGraphSnapshot.empty();
        }
        var fabric = registry.fabric(scope.fabricId()).orElseThrow();
        var state = new FabricStateProjector(level).snapshot(scope);
        var nodes = new ArrayList<FabricGraphSnapshot.Node>();
        var edges = new ArrayList<FabricGraphSnapshot.Edge>();
        var memberIds = new HashMap<NetworkId, String>();
        state.members().forEach(member -> {
            memberIds.put(member.networkId(), member.id().value());
            nodes.add(new FabricGraphSnapshot.Node(member.id().value(), FabricGraphNodeKind.MEMBER, member.status()));
        });

        var providers = providerEntries(level, fabric);
        providers.forEach(entry -> {
            var id = providerId(scope, entry);
            var status = entry.runtime().lastResolution().state().name().toLowerCase(java.util.Locale.ROOT);
            nodes.add(new FabricGraphSnapshot.Node(id, FabricGraphNodeKind.PROVIDER, status));
            FabricRegistryAccess.confirmedNetworkId(entry.provider().getGrid()).map(memberIds::get).ifPresent(member ->
                    edges.add(new FabricGraphSnapshot.Edge(member, id, FabricGraphLayer.PHYSICAL, "provider")));
        });

        endpointEntries(level, fabric).forEach(binding -> {
            var id = endpointId(scope, binding);
            var status = binding.runtime().configuredMode().name().toLowerCase(java.util.Locale.ROOT);
            nodes.add(new FabricGraphSnapshot.Node(id, FabricGraphNodeKind.ENDPOINT, status));
            FabricRegistryAccess.confirmedNetworkId(binding.subnetNode().getGrid()).map(memberIds::get).ifPresent(member ->
                    edges.add(new FabricGraphSnapshot.Edge(member, id, FabricGraphLayer.PHYSICAL, status)));
            if (binding.claimState() instanceof ClaimState.Owned owned) {
                providers.stream().filter(entry -> entry.identity().equals(owned.ownerIdentity().provider())).findFirst()
                        .ifPresent(entry -> edges.add(new FabricGraphSnapshot.Edge(providerId(scope, entry), id,
                                FabricGraphLayer.CAPABILITY, "claim-" + owned.epoch().value())));
            }
        });
        var patterns = selectedProvider.map(entry -> patterns(level, entry)).orElse(List.of());
        return new FabricGraphSnapshot(state.topologyRevision(), Math.max(state.policyRevision(), state.dataRevision()),
                nodes, edges, patterns);
    }

    static List<ProviderObservationRegistry.Entry> providerEntries(ServerLevel level,
            space.controlnet.ae2federation.fabric.FabricSnapshot fabric) {
        return ProviderObservationRegistry.entries(level).stream()
                .filter(entry -> memberOf(fabric, entry.provider().getGrid()))
                .sorted(Comparator.comparing(entry -> providerId(fabric.reference(), entry))).toList();
    }

    static List<EndpointTargetBinding> endpointEntries(ServerLevel level,
            space.controlnet.ae2federation.fabric.FabricSnapshot fabric) {
        return EndpointTargetBinding.entries(level).stream()
                .filter(binding -> memberOf(fabric, binding.subnetNode().getGrid()))
                .sorted(Comparator.comparing(binding -> endpointId(fabric.reference(), binding))).toList();
    }

    static String providerId(FabricReference scope, ProviderObservationRegistry.Entry entry) {
        var identity = entry.identity();
        return ProviderId.of(scope.fabricId(), identity.id().value() + ":" + identity.instanceEpoch().value()).value();
    }

    static String endpointId(FabricReference scope, EndpointTargetBinding binding) {
        var identity = binding.endpointIdentity();
        return EndpointId.of(scope.fabricId(), identity.id().value() + ":" + identity.instanceEpoch().value()).value();
    }

    private static boolean memberOf(space.controlnet.ae2federation.fabric.FabricSnapshot fabric,
            appeng.api.networking.IGrid grid) {
        return FabricRegistryAccess.confirmedNetworkId(grid).filter(fabric.memberships()::containsKey).isPresent();
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
            rows.add(FabricPatternRow.format(slot, name, quantities, provider.lanesForSlot(slot)));
        }
        return List.copyOf(rows);
    }
}
