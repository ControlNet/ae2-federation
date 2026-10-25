package space.controlnet.ae2federation.router;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.domain.CableCapacityLimit;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;

/**
 * Runs Federation Cables' work only when there is some, as AE2 runs its cable buses' parts only on request: a cable has
 * no block entity ticker, so an idle cable costs nothing per tick. A cable wakes when one of its faces must resolve or
 * its evidence must be published; that work runs at the start of the binding tick, before the bindings reconcile. What
 * its beam shows (capacity count, unpowered) is synced once the domains have settled at the end of that tick: every
 * cable syncs when the registry's evidence or topology or the capacity rule changed, and a cable syncs on its own when
 * it initializes or its bundle changes.
 */
public final class FederationCableTicks {
    private static final Map<ServerLevel, Set<FederationCableBlockEntity>> LOADED = new HashMap<>();
    private static final Map<ServerLevel, Set<FederationCableBlockEntity>> AWAKE = new HashMap<>();
    private static final Map<ServerLevel, Set<FederationCableBlockEntity>> DISPLAY = new HashMap<>();
    /** What the cables of each server last synced against. */
    private static final Map<MinecraftServer, Shown> SHOWN = new WeakHashMap<>();

    private record Shown(long evidenceRevision, long topologyRevision, int capacity, OptionalInt limit) {
    }

    private FederationCableTicks() {
    }

    static void register(ServerLevel level, FederationCableBlockEntity cable) {
        LOADED.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(cable);
    }

    static void forget(ServerLevel level, FederationCableBlockEntity cable) {
        remove(LOADED, level, cable);
        remove(AWAKE, level, cable);
        remove(DISPLAY, level, cable);
    }

    /** The cable has face or evidence work for the next binding tick. */
    static void wake(ServerLevel level, FederationCableBlockEntity cable) {
        AWAKE.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(cable);
    }

    /** The cable must sync what its beam shows once the domains have settled. */
    static void syncDisplay(ServerLevel level, FederationCableBlockEntity cable) {
        DISPLAY.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(cable);
    }

    /** Whether the cable has work waiting, of either kind. */
    public static boolean awake(FederationCableBlockEntity cable) {
        return cable.getLevel() instanceof ServerLevel level
                && (contains(AWAKE, level, cable) || contains(DISPLAY, level, cable));
    }

    /**
     * Runs every awake cable's face and evidence work once. The batch is taken first, so a cable woken meanwhile works on
     * the next tick; a cable with work left stays awake.
     */
    public static void tickAll(MinecraftServer server) {
        for (var level : List.copyOf(AWAKE.keySet())) {
            if (level.getServer() != server) continue;
            var batch = AWAKE.remove(level);
            for (var cable : batch) {
                if (cable.work(level)) wake(level, cable);
            }
        }
    }

    /**
     * After the domains settled: has every cable sync its beam when the evidence, the topology (which an unpowered
     * verdict also advances) or the capacity rule changed, then syncs the cables that must. A count that must wait
     * for its throttle stays due.
     */
    public static void afterSettle(MinecraftServer server) {
        var overworld = server.overworld();
        if (overworld == null) return;
        var registry = FederationDomainRegistryAccess.get(overworld);
        if (registry.pending()) return;
        var shown = new Shown(registry.evidenceRevision(), registry.topologyRevision(), CableCapacityLimit.capacity(),
                CableCapacityLimit.current());
        if (!shown.equals(SHOWN.put(server, shown))) {
            LOADED.forEach((level, cables) -> {
                if (level.getServer() == server) cables.forEach(FederationCableBlockEntity::requestDisplaySync);
            });
        }
        for (var level : List.copyOf(DISPLAY.keySet())) {
            if (level.getServer() != server) continue;
            var batch = DISPLAY.remove(level);
            for (var cable : batch) {
                if (cable.syncDisplay(level)) syncDisplay(level, cable);
            }
        }
    }

    public static void closeLevel(ServerLevel level) {
        LOADED.remove(level);
        AWAKE.remove(level);
        DISPLAY.remove(level);
    }

    public static void closeServer(MinecraftServer server) {
        SHOWN.remove(server);
        for (var sets : List.of(LOADED, AWAKE, DISPLAY)) sets.keySet().removeIf(level -> level.getServer() == server);
    }

    private static void remove(Map<ServerLevel, Set<FederationCableBlockEntity>> sets, ServerLevel level,
            FederationCableBlockEntity cable) {
        var set = sets.get(level);
        if (set == null) return;
        set.remove(cable);
        if (set.isEmpty()) sets.remove(level);
    }

    private static boolean contains(Map<ServerLevel, Set<FederationCableBlockEntity>> sets, ServerLevel level,
            FederationCableBlockEntity cable) {
        var set = sets.get(level);
        return set != null && set.contains(cable);
    }
}
