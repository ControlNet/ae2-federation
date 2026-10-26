package space.controlnet.ae2federation.router;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import space.controlnet.ae2federation.identity.IdentityEpoch;

/**
 * Runs Routers' and Switches' work only when there is some, as {@link FederationCableTicks} does for cables: neither
 * block has a block entity ticker, so an idle one costs nothing per tick. A Router wakes when one of its faces must
 * resolve or its body changed; its work runs as its level's tick ends, before AE2's own end-of-level work, and only
 * where Minecraft would tick its block entity ({@link BlockEntityTicking}), as its ticker did before. Beam
 * Converters, Beam Formers and Quantum Cubes keep their own tickers.
 * <p>
 * Two looks need no event. A native network's identity can settle without any event on the faces that attach it, so
 * every {@link #EVIDENCE_TICKS} ticks, once the identities changed ({@link IdentityEpoch}), every Router compares its
 * evidence again. And in case some event was missed, every Router takes a turn once every {@link #SWEEP_TICKS} ticks,
 * on the tick its position picks, in which every face resolves again and a Switch face without a node looks at its
 * neighbour without its caches.
 */
public final class RouterTicks {
    /** Ticks between two evidence comparisons after the identities changed, as the block entity ticker had. */
    private static final int EVIDENCE_TICKS = 20;
    /** Ticks between two turns of each Router. */
    static final int SWEEP_TICKS = 100;

    private static final Map<ServerLevel, Set<RouterBlockEntity>> LOADED = new HashMap<>();
    private static final Map<ServerLevel, Set<RouterBlockEntity>> AWAKE = new HashMap<>();
    /** Each level's Routers by the tick of their turn; one no longer loaded leaves at its next turn. */
    private static final Map<ServerLevel, List<List<RouterBlockEntity>>> TURNS = new HashMap<>();
    /** The identity epoch each level's Routers last compared their evidence at. */
    private static final Map<ServerLevel, Long> EPOCHS = new HashMap<>();

    private RouterTicks() {
    }

    static void register(ServerLevel level, RouterBlockEntity router) {
        if (!LOADED.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(router)) return;
        var turns = TURNS.computeIfAbsent(level, ignored -> {
            var lists = new ArrayList<List<RouterBlockEntity>>(SWEEP_TICKS);
            for (int tick = 0; tick < SWEEP_TICKS; tick++) lists.add(new ArrayList<>());
            return lists;
        });
        turns.get(Math.floorMod(router.getBlockPos().hashCode(), SWEEP_TICKS)).add(router);
    }

    static void forget(ServerLevel level, RouterBlockEntity router) {
        remove(LOADED, level, router);
        remove(AWAKE, level, router);
    }

    /** The Router has work for its level's next tick end. */
    static void wake(ServerLevel level, RouterBlockEntity router) {
        AWAKE.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(router);
    }

    /** Whether the Router has work waiting. */
    public static boolean awake(RouterBlockEntity router) {
        if (!(router.getLevel() instanceof ServerLevel level)) return false;
        var set = AWAKE.get(level);
        return set != null && set.contains(router);
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) tick(level);
    }

    /**
     * Wakes the Routers whose evidence or turn is due, then runs every awake Router's work once. The batch is taken
     * first, so a Router woken meanwhile works on the next tick; one with work left, or whose block entity Minecraft
     * would not tick now, stays awake. A Router that was removed or unloaded meanwhile is dropped.
     */
    static void tick(ServerLevel level) {
        var loaded = LOADED.get(level);
        if (loaded != null && level.getGameTime() % EVIDENCE_TICKS == 0) {
            long epoch = IdentityEpoch.current();
            var last = EPOCHS.put(level, epoch);
            if (last == null || last != epoch) loaded.forEach(RouterBlockEntity::compareEvidence);
        }
        takeTurns(level, loaded);
        var batch = AWAKE.remove(level);
        if (batch == null) return;
        for (var router : batch) {
            if (!router.live(level)) continue;
            if (!BlockEntityTicking.ticksAt(level, router.getBlockPos()) || router.work(level)) wake(level, router);
        }
    }

    /** The Routers whose turn is this tick look at all their faces again; those no longer loaded leave the turns. */
    private static void takeTurns(ServerLevel level, Set<RouterBlockEntity> loaded) {
        var turns = TURNS.get(level);
        if (turns == null) return;
        int tick = (int) Math.floorMod(level.getGameTime(), (long) SWEEP_TICKS);
        var due = turns.get(tick);
        if (due.isEmpty()) return;
        var kept = new ArrayList<RouterBlockEntity>(due.size());
        var seen = Collections.newSetFromMap(new IdentityHashMap<RouterBlockEntity, Boolean>());
        for (var router : due) {
            if (loaded != null && loaded.contains(router) && seen.add(router)) kept.add(router);
        }
        turns.set(tick, kept);
        kept.forEach(RouterBlockEntity::sweep);
    }

    public static void closeLevel(ServerLevel level) {
        LOADED.remove(level);
        AWAKE.remove(level);
        TURNS.remove(level);
        EPOCHS.remove(level);
    }

    public static void closeServer(MinecraftServer server) {
        for (var sets : List.of(LOADED, AWAKE, TURNS, EPOCHS)) sets.keySet().removeIf(level -> level.getServer() == server);
    }

    private static void remove(Map<ServerLevel, Set<RouterBlockEntity>> sets, ServerLevel level, RouterBlockEntity router) {
        var set = sets.get(level);
        if (set == null) return;
        set.remove(router);
        if (set.isEmpty()) sets.remove(level);
    }
}
