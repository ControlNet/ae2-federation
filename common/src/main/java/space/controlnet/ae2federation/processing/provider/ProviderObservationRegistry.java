package space.controlnet.ae2federation.processing.provider;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import space.controlnet.ae2federation.ae2.processing.NativeProviderLane;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.observability.LevelObservabilityService;
import space.controlnet.ae2federation.observability.meter.OperationEventId;
import space.controlnet.ae2federation.observability.state.FlowState;
import space.controlnet.ae2federation.observability.state.ResourceUnit;

public final class ProviderObservationRegistry {
    private static final Map<ServerLevel, Map<MappedPatternProvider, Entry>> ENTRIES = new WeakHashMap<>();
    private static final Map<NativeProviderLane, LaneEntry> LANES = new IdentityHashMap<>();

    private ProviderObservationRegistry() {
    }

    static synchronized void register(ServerLevel level, MappedPatternProvider provider, ProviderIdentity identity,
            ProviderRuntime runtime) {
        ENTRIES.computeIfAbsent(level, ignored -> new IdentityHashMap<>())
                .put(provider, new Entry(provider, identity, runtime, java.util.Optional.empty()));
        for (var laneIndex = 0; laneIndex < provider.nativeLanes().size(); laneIndex++) {
            LANES.put(provider.nativeLane(laneIndex), new LaneEntry(level, provider, identity, laneIndex));
        }
    }

    /** Attaches the production mapping controller to an already registered Provider. */
    public static synchronized void attachController(MappedPatternProvider provider,
            ProviderMappingController controller) {
        for (var entries : ENTRIES.values()) {
            entries.computeIfPresent(provider, (ignored, entry) -> new Entry(entry.provider(), entry.identity(),
                    entry.runtime(), java.util.Optional.of(controller)));
        }
    }

    static synchronized void registerLane(ServerLevel level, MappedPatternProvider provider, ProviderIdentity identity,
            int laneIndex) {
        LANES.put(provider.nativeLane(laneIndex), new LaneEntry(level, provider, identity, laneIndex));
    }

    static synchronized void unregister(MappedPatternProvider provider) {
        LANES.entrySet().removeIf(entry -> entry.getValue().provider() == provider);
        ENTRIES.values().removeIf(entries -> {
            entries.remove(provider);
            return entries.isEmpty();
        });
    }

    public static synchronized List<Entry> entries(ServerLevel level) {
        return List.copyOf(ENTRIES.getOrDefault(level, Map.of()).values());
    }

    public static synchronized void closeLevel(ServerLevel level) {
        ENTRIES.remove(level);
        LANES.entrySet().removeIf(entry -> entry.getValue().level() == level);
    }

    public static void recordSend(NativeProviderLane lane, KeyCounter[] inputs, List<GenericStack> before,
            List<GenericStack> after) {
        LaneEntry entry;
        synchronized (ProviderObservationRegistry.class) {
            entry = LANES.get(lane);
        }
        if (entry == null) {
            return;
        }
        var offered = new java.util.HashMap<AEKey, Long>();
        for (var counter : inputs) {
            for (var stack : counter) {
                offered.merge(stack.getKey(), stack.getLongValue(), Math::addExact);
            }
        }
        var pendingBefore = amounts(before);
        var pendingAfter = amounts(after);
        var root = OperationEventId.create();
        offered.forEach((resource, amount) -> {
            var remainder = Math.max(0L, pendingAfter.getOrDefault(resource, 0L)
                    - pendingBefore.getOrDefault(resource, 0L));
            var accepted = amount - Math.min(amount, remainder);
            if (accepted > 0) {
                var child = new OperationEventId(java.util.UUID.nameUUIDFromBytes(
                        (root.value() + ":" + resource.getId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                LevelObservabilityService.get(entry.level()).recordAccepted(scopes(entry), child,
                        resource.getId().toString(), accepted, unit(resource), FlowState.Attribution.EXACT_OPERATION);
            }
        });
    }

    public static void recordAggregateReturn(ServerLevel level, AuthorizedLaneIdentity lane, AEKey resource,
            long accepted) {
        LaneEntry entry;
        synchronized (ProviderObservationRegistry.class) {
            entry = LANES.values().stream().filter(candidate -> candidate.level() == level
                    && candidate.identity().equals(lane.lane().provider())
                    && candidate.laneIndex() == lane.lane().laneIndex()).findFirst().orElse(null);
        }
        if (entry != null && accepted > 0) {
            LevelObservabilityService.get(level).recordAccepted(scopes(entry), OperationEventId.create(),
                    resource.getId().toString(), accepted, unit(resource), FlowState.Attribution.AGGREGATE_LANE_RETURN);
        }
    }

    private static Map<AEKey, Long> amounts(List<GenericStack> stacks) {
        var result = new java.util.HashMap<AEKey, Long>();
        stacks.forEach(stack -> result.merge(stack.what(), stack.amount(), Math::addExact));
        return result;
    }

    private static List<space.controlnet.ae2federation.fabric.FabricReference> scopes(LaneEntry entry) {
        var registry = FabricRegistryAccess.get(entry.level());
        return FabricRegistryAccess.confirmedNetworkId(entry.provider().getGrid()).stream()
                .flatMap(network -> registry.fabricsFor(network).stream())
                .map(registry::fabric).flatMap(java.util.Optional::stream)
                .map(space.controlnet.ae2federation.fabric.FabricSnapshot::reference).toList();
    }

    private static ResourceUnit unit(AEKey resource) {
        return resource.getType().getId().getPath().contains("fluid") ? ResourceUnit.FLUID_DROPLET : ResourceUnit.ITEM;
    }

    public record Entry(MappedPatternProvider provider, ProviderIdentity identity, ProviderRuntime runtime,
            java.util.Optional<ProviderMappingController> controller) {
    }

    private record LaneEntry(ServerLevel level, MappedPatternProvider provider, ProviderIdentity identity,
            int laneIndex) {
    }
}
