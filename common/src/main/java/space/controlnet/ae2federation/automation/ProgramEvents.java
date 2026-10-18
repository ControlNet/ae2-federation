package space.controlnet.ae2federation.automation;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.events.GridBootingStatusChange;
import appeng.api.networking.events.GridControllerChange;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.api.networking.events.GridPowerStatusChange;
import appeng.api.networking.events.GridSpatialEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;

/**
 * The curated events an installation can start on (user, 2026-10-18), and what happened this tick. Network events
 * come from AE2's public Grid events and count when a network of the device's domain posts them; resource events are
 * read every interval and count when the value changed; block events come from NeoForge's and count at the block an
 * interface points at. No mixins.
 */
public final class ProgramEvents {
    public enum Kind {
        /** Of any network of the domain; no arguments. */
        NETWORK,
        /** Of one resource in the domain; its argument is the resource. */
        RESOURCE,
        /** At a block in front of an interface; its arguments are the interface and the reach (1 to 4, default 1). */
        BLOCK
    }

    public record Spec(String name, Kind kind, String description) {
    }

    public static final Map<String, Spec> EVENTS;

    static {
        var events = new LinkedHashMap<String, Spec>();
        for (var spec : new Spec[] {
                new Spec("stock_changed", Kind.RESOURCE, "The amount of a resource in the domain changed."),
                new Spec("crafting_changed", Kind.RESOURCE, "How much of a resource is being crafted changed."),
                new Spec("craftable_changed", Kind.RESOURCE, "Whether a pattern makes the resource changed."),
                new Spec("network_boot", Kind.NETWORK, "A network of the domain started or finished booting."),
                new Spec("power_changed", Kind.NETWORK, "A network of the domain lost or regained power."),
                new Spec("controller_changed", Kind.NETWORK, "A network's controllers changed."),
                new Spec("cpus_changed", Kind.NETWORK, "A network's crafting CPUs changed."),
                new Spec("spatial_io", Kind.NETWORK, "A Spatial IO Port of the domain was used."),
                new Spec("block_changed", Kind.BLOCK, "The block was placed, broken or changed."),
                new Spec("crop_grew", Kind.BLOCK, "The crop grew."),
                new Spec("right_clicked", Kind.BLOCK, "A player right-clicked the block.")}) {
            events.put(spec.name(), spec);
        }
        EVENTS = Collections.unmodifiableMap(events);
    }

    /** A block event at a place. */
    record Happening(String dimension, long position, String name) {
    }

    private static final Map<IGrid, Set<String>> GRID_EVENTS = new HashMap<>();
    private static final Set<Happening> BLOCK_EVENTS = new HashSet<>();
    /** Whether an installation waits for a network or a block event; nothing is recorded otherwise. */
    private static boolean gridListeners;
    private static boolean blockListeners;

    private ProgramEvents() {
    }

    /** Once, at mod start: AE2's Grid event subscriptions are global and permanent. */
    public static void register() {
        GridHelper.addEventHandler(GridBootingStatusChange.class, (grid, event) -> grid(grid, "network_boot"));
        GridHelper.addEventHandler(GridPowerStatusChange.class, (grid, event) -> grid(grid, "power_changed"));
        GridHelper.addEventHandler(GridControllerChange.class, (grid, event) -> grid(grid, "controller_changed"));
        GridHelper.addEventHandler(GridCraftingCpuChange.class, (grid, event) -> grid(grid, "cpus_changed"));
        GridHelper.addEventHandler(GridSpatialEvent.class, (grid, event) -> grid(grid, "spatial_io"));
        NeoForge.EVENT_BUS.addListener((BlockEvent.NeighborNotifyEvent event) ->
                block(event.getLevel(), event.getPos(), "block_changed"));
        NeoForge.EVENT_BUS.addListener((CropGrowEvent.Post event) -> block(event.getLevel(), event.getPos(), "crop_grew"));
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) ->
                block(event.getLevel(), event.getPos(), "right_clicked"));
    }

    private static void grid(IGrid grid, String name) {
        if (!gridListeners) {
            return;
        }
        GRID_EVENTS.computeIfAbsent(grid, ignored -> new HashSet<>()).add(name);
    }

    private static void block(LevelAccessor accessor, BlockPos position, String name) {
        if (blockListeners && accessor instanceof ServerLevel level) {
            BLOCK_EVENTS.add(new Happening(FederationDomainRegistryAccess.dimension(level), position.asLong(), name));
        }
    }

    /** Whether any of the grids posted the event since the last {@link #clear}. */
    static boolean posted(Iterable<IGrid> grids, String name) {
        for (var grid : grids) {
            if (GRID_EVENTS.getOrDefault(grid, Set.of()).contains(name)) {
                return true;
            }
        }
        return false;
    }

    static boolean anyGridEvent() {
        return !GRID_EVENTS.isEmpty();
    }

    static boolean happened(String dimension, BlockPos position, String name) {
        return BLOCK_EVENTS.contains(new Happening(dimension, position.asLong(), name));
    }

    static boolean anyBlockEvent() {
        return !BLOCK_EVENTS.isEmpty();
    }

    /** Which kinds of event the installations wait for, from the next event on. */
    static void listen(boolean grids, boolean blocks) {
        gridListeners = grids;
        blockListeners = blocks;
    }

    /** Forgets this tick's events, once the scheduler has looked at them. */
    static void clear() {
        GRID_EVENTS.clear();
        BLOCK_EVENTS.clear();
    }
}
