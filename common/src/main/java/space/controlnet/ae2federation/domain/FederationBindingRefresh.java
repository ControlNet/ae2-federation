package space.controlnet.ae2federation.domain;

import appeng.api.networking.IGrid;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.energy.EnergySharingService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;

/**
 * Coalesces the Storage, Crafting and Energy reconciliations that Federation blocks ask for when their domain evidence
 * changes. A block records the request and, optionally, where its member Grids can be read; the level reconciles each
 * service once when its tick ends (and the server once more after player actions), so a chunk of cables that loads in
 * one tick reconciles once instead of three times per cable. Both flushes run before AE2's end-of-tick Grid ticks, so a
 * supplied network draws energy through the binding a placement made possible in the same tick; a flush never runs
 * inside an energy demand, where announcing a new provider would fail AE2's running extraction. Held storage projections
 * fail closed on stale authority in the meantime.
 */
public final class FederationBindingRefresh {
    private static final Map<ServerLevel, List<Supplier<? extends Collection<IGrid>>>> PENDING = new WeakHashMap<>();
    private static final int MAX_FLUSH_ROUNDS = 4;

    private FederationBindingRefresh() {
    }

    /** Reconciles the level's existing binding services when its tick ends. */
    public static synchronized void request(ServerLevel level) {
        PENDING.computeIfAbsent(level, ignored -> new ArrayList<>());
    }

    /** Like {@link #request(ServerLevel)}, first observing the Grids {@code members} returns at that time. */
    public static synchronized void request(ServerLevel level, Supplier<? extends Collection<IGrid>> members) {
        PENDING.computeIfAbsent(level, ignored -> new ArrayList<>()).add(members);
    }

    public static synchronized boolean pending(ServerLevel level) {
        return PENDING.containsKey(level);
    }

    /** Runs the level's pending reconciliation, and any it causes, now. */
    public static void flush(ServerLevel level) {
        for (var round = 0; round < MAX_FLUSH_ROUNDS; round++) {
            List<Supplier<? extends Collection<IGrid>>> members;
            synchronized (FederationBindingRefresh.class) {
                members = PENDING.remove(level);
            }
            if (members == null) {
                return;
            }
            reconcile(level, members);
        }
    }

    public static void flushAll() {
        List<ServerLevel> levels;
        synchronized (FederationBindingRefresh.class) {
            levels = List.copyOf(PENDING.keySet());
        }
        levels.forEach(FederationBindingRefresh::flush);
    }

    public static synchronized void closeLevel(ServerLevel level) {
        PENDING.remove(level);
    }

    private static void reconcile(ServerLevel level, List<Supplier<? extends Collection<IGrid>>> members) {
        Set<IGrid> grids = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var supplier : members) {
            for (var grid : supplier.get()) {
                if (grid != null && !grid.isEmpty()) {
                    grids.add(grid);
                }
            }
        }
        if (grids.isEmpty()) {
            StorageMountService.reconcileIfPresent(level);
            CraftingBindingService.reconcileIfPresent(level);
            EnergySharingService.reconcileIfPresent(level);
            return;
        }
        StorageMountService.get(level).observeFederationDomainMembers(grids);
        CraftingBindingService.get(level).observeFederationDomainMembers(grids);
        EnergySharingService.get(level).observeFederationDomainMembers(grids);
    }
}
