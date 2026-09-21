package space.controlnet.ae2federation.observability.state;

import java.util.List;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.observability.LevelObservabilityService;
import space.controlnet.ae2federation.observability.id.MemberId;
import space.controlnet.ae2federation.observability.id.PolicyId;
import space.controlnet.ae2federation.observability.id.ProviderId;
import space.controlnet.ae2federation.observability.id.EndpointId;
import space.controlnet.ae2federation.observability.id.LockId;
import space.controlnet.ae2federation.observability.id.TaskId;
import space.controlnet.ae2federation.persistence.PolicySavedData;
import space.controlnet.ae2federation.policy.PolicyRecord;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.provider.ProviderObservationRegistry;

public final class FabricStateProjector {
    private final ServerLevel level;

    public FabricStateProjector(ServerLevel level) {
        this.level = java.util.Objects.requireNonNull(level);
    }

    public FabricStateSnapshot snapshot(FabricReference scope) {
        var registry = FabricRegistryAccess.get(level);
        if (!registry.isCurrent(scope)) {
            throw new IllegalArgumentException("Cannot observe a stale or foreign Fabric generation");
        }
        var fabric = registry.fabric(scope.fabricId()).orElseThrow();
        var members = fabric.memberships().keySet().stream()
                .map(network -> new MemberState(scope, MemberId.forNetwork(scope.fabricId(), network), network, "online"))
                .toList();
        var policyStore = PolicySavedData.get(level).snapshot();
        var policies = policyStore.entries().values().stream()
                .filter(PolicyRecord.Configured.class::isInstance)
                .map(PolicyRecord.Configured.class::cast)
                .filter(record -> fabric.memberships().containsKey(record.key().consumerNetworkId())
                        && fabric.memberships().containsKey(record.key().providerNetworkId()))
                .map(record -> new PolicyState(scope, PolicyId.forKey(scope.fabricId(), record.key()),
                        record.rule().enabled() ? "enabled" : "disabled"))
                .toList();
        var providerEntries = ProviderObservationRegistry.entries(level).stream()
                .filter(entry -> memberOf(fabric, entry.provider().getGrid()))
                .toList();
        var providers = providerEntries.stream().map(entry -> new ProviderState(scope,
                ProviderId.of(scope.fabricId(), providerKey(entry)),
                entry.runtime().lastResolution().state().name().toLowerCase(java.util.Locale.ROOT))).toList();
        var locks = providerEntries.stream().flatMap(entry -> java.util.stream.IntStream
                .range(0, entry.provider().nativeLanes().size()).mapToObj(index -> {
                    var lane = entry.provider().nativeLane(index);
                    return new LockState(scope, LockId.of(scope.fabricId(), providerKey(entry) + ":lane:" + index),
                            lane.getCraftingLockedReason().name().toLowerCase(java.util.Locale.ROOT));
                })).toList();
        var tasks = providerEntries.stream().flatMap(entry -> java.util.stream.IntStream
                .range(0, entry.provider().nativeLanes().size()).mapToObj(index -> {
                    var lane = entry.provider().nativeLane(index);
                    var states = new java.util.ArrayList<String>();
                    if (lane.isBusy()) {
                        states.add("busy");
                    }
                    if (lane.hasPendingSend()) {
                        states.add("pending-send");
                    }
                    if (!lane.getReturnInv().isEmpty()) {
                        states.add("return-buffered");
                    }
                    var status = states.isEmpty() ? "idle" : String.join("+", states);
                    return new TaskState(scope, TaskId.of(scope.fabricId(), providerKey(entry) + ":lane:" + index),
                            status);
                })).toList();
        var endpoints = EndpointTargetBinding.entries(level).stream()
                .filter(binding -> memberOf(fabric, binding.subnetNode().getGrid()))
                .map(binding -> new EndpointState(scope,
                        EndpointId.of(scope.fabricId(), endpointKey(binding)),
                        binding.runtime().configuredMode().name().toLowerCase(java.util.Locale.ROOT)))
                .toList();
        var flowWindow = LevelObservabilityService.get(level).transportMeter().window(scope);
        return new FabricStateSnapshot(scope, registry.snapshot().topologyRevision(),
                policyStore.highWatermark().value(), flowWindow.dataRevision(), members, providers, endpoints, policies,
                locks, tasks, flowWindow.events());
    }

    private boolean memberOf(space.controlnet.ae2federation.fabric.FabricSnapshot fabric,
            appeng.api.networking.IGrid grid) {
        return FabricRegistryAccess.confirmedNetworkId(grid).filter(fabric.memberships()::containsKey).isPresent();
    }

    private static String providerKey(ProviderObservationRegistry.Entry entry) {
        return entry.identity().id().value() + ":" + entry.identity().instanceEpoch().value();
    }

    private static String endpointKey(EndpointTargetBinding binding) {
        var identity = binding.endpointIdentity();
        return identity.id().value() + ":" + identity.instanceEpoch().value();
    }
}
