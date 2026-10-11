package space.controlnet.ae2federation.router;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Keeps each Router's and Switch's six connection properties equal to its validated body, as AE2's crafting blocks
 * keep their formed state: a block that loads, or whose neighbour joins or leaves its body, asks for a check, and the
 * level checks each asked-for body once when its tick ends. A formed member's properties name exactly its neighbours
 * inside the same cuboid; every block of an unformed candidate has none. Only blockstates change, sent to clients
 * without neighbour updates, so the check never triggers itself and the client redraws the chunk from the new state.
 *
 * <p>This is the visual and membership layer only. The faces keep their ports, so the Federation and ME behaviour of
 * touching blocks is unchanged.
 */
public final class DeviceStructureUpdater {
    private static final Map<ServerLevel, Set<BlockPos>> PENDING = new WeakHashMap<>();

    private DeviceStructureUpdater() {
    }

    public static synchronized void request(ServerLevel level, BlockPos position) {
        PENDING.computeIfAbsent(level, ignored -> new LinkedHashSet<>()).add(position.immutable());
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel level) {
            flush(level);
        }
    }

    /** Checks every body asked for in {@code level}, each once. */
    public static void flush(ServerLevel level) {
        Set<BlockPos> positions;
        synchronized (DeviceStructureUpdater.class) {
            positions = PENDING.remove(level);
        }
        if (positions == null) {
            return;
        }
        var settled = new HashSet<BlockPos>();
        for (var position : positions) {
            if (!settled.contains(position)) {
                update(level, position, settled);
            }
        }
    }

    /** Whether a change at {@code neighbor} can change the body of the block at {@code position}, as AE2 decides. */
    public static boolean affects(ServerLevel level, BlockPos position, BlockPos neighbor) {
        var state = level.getBlockState(position);
        int connections = RouterBlock.connections(state);
        // An unformed or single block always checks: the neighbour may have been the block that kept it unformed.
        if (connections == 0) return true;
        for (var side : Direction.values()) {
            if (position.relative(side).equals(neighbor)) {
                return (connections & 1 << side.get3DDataValue()) != 0 || level.getBlockState(neighbor).is(state.getBlock());
            }
        }
        return false;
    }

    private static void update(ServerLevel level, BlockPos start, Set<BlockPos> settled) {
        if (!level.isLoaded(start)) return;
        var block = level.getBlockState(start).getBlock();
        if (!(block instanceof RouterBlock)) return;
        var cursor = new BlockPos.MutableBlockPos();
        var result = DeviceStructure.evaluate(start, (x, y, z) -> {
            cursor.set(x, y, z);
            if (level.isOutsideBuildHeight(cursor)) return DeviceStructure.Cell.OTHER;
            if (!level.isLoaded(cursor)) return DeviceStructure.Cell.UNLOADED;
            return level.getBlockState(cursor).is(block) ? DeviceStructure.Cell.MEMBER : DeviceStructure.Cell.OTHER;
        }, DeviceStructure.MAX_EDGE);
        switch (result) {
            case DeviceStructure.Valid valid -> {
                for (var position : BlockPos.betweenClosed(valid.min(), valid.max())) {
                    int connections = 0;
                    for (var side : Direction.values()) {
                        if (valid.contains(position.relative(side))) connections |= 1 << side.get3DDataValue();
                    }
                    set(level, position, connections);
                    settled.add(position.immutable());
                }
            }
            case DeviceStructure.Invalid invalid -> unform(level, start, block, settled);
            case DeviceStructure.Unknown unknown -> {
                // The rest lies in unloaded chunks, which cannot have changed: keep the body as it was, except toward
                // loaded neighbours that are no longer part of it, so no open seam faces a removed member.
                var state = level.getBlockState(start);
                int connections = RouterBlock.connections(state);
                for (var side : Direction.values()) {
                    var neighbor = start.relative(side);
                    if (level.isLoaded(neighbor) && !level.getBlockState(neighbor).is(block)) {
                        connections &= ~(1 << side.get3DDataValue());
                    }
                }
                set(level, start, connections);
            }
        }
    }

    /** Clears the start's former body, following the connections that still join its members. */
    private static void unform(ServerLevel level, BlockPos start, Block block, Set<BlockPos> settled) {
        var queue = new ArrayDeque<BlockPos>();
        var seen = new HashSet<BlockPos>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty()) {
            var position = queue.removeFirst();
            var state = level.getBlockState(position);
            if (!state.is(block)) continue;
            int connections = RouterBlock.connections(state);
            set(level, position, 0);
            settled.add(position);
            for (var side : Direction.values()) {
                var neighbor = position.relative(side);
                if ((connections & 1 << side.get3DDataValue()) != 0 && level.isLoaded(neighbor) && seen.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
    }

    private static void set(ServerLevel level, BlockPos position, int connections) {
        var state = level.getBlockState(position);
        var updated = RouterBlock.withConnections(state, connections);
        if (updated != state) {
            level.setBlock(position, updated, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }

    public static synchronized void closeLevel(ServerLevel level) {
        PENDING.remove(level);
    }
}
