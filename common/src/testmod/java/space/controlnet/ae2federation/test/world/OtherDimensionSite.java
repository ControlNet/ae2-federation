package space.controlnet.ae2federation.test.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import space.controlnet.ae2federation.test.FederationTestMod;

/**
 * TEST-ONLY scaffolding: a region of another dimension for one GameTest, as the GameTest framework places structures
 * only in the overworld. The region sits at the test's own x and z, so concurrent tests do not share one. It is kept
 * loaded and ticking by a ticket, walled in stone so lava and the like cannot flow in, and emptied when it opens and
 * when it closes, so its block entities do not outlive the test.
 */
public final class OtherDimensionSite implements AutoCloseable {
    private static final TicketController CONTROLLER =
            new TicketController(ResourceLocation.fromNamespaceAndPath(FederationTestMod.MOD_ID, "other_dimension_site"));
    /** Below the nether's bedrock roof, above its lava sea. */
    private static final int FLOOR_Y = 80;
    private static final Set<OtherDimensionSite> OPEN = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private final ServerLevel level;
    private final BlockPos origin;
    private final BlockPos size;
    private final List<ChunkPos> chunks = new ArrayList<>();
    private boolean closed;
    private boolean filled;

    private OtherDimensionSite(ServerLevel level, BlockPos origin, BlockPos size) {
        this.level = level;
        this.origin = origin;
        this.size = size;
    }

    public static void register(RegisterTicketControllersEvent event) {
        event.register(CONTROLLER);
    }

    /** Releases the tickets of sites a failed test left open. */
    public static void onServerStopped(ServerStoppedEvent event) {
        List.copyOf(OPEN).forEach(OtherDimensionSite::release);
    }

    /** A nether region {@code size} blocks large whose relative (0, 0, 0) is above the test's own origin. */
    public static OtherDimensionSite nether(GameTestHelper helper, BlockPos size) {
        return open(helper, Level.NETHER, size);
    }

    public static OtherDimensionSite open(GameTestHelper helper, ResourceKey<Level> dimension, BlockPos size) {
        var level = helper.getLevel().getServer().getLevel(dimension);
        helper.assertTrue(level != null, "The test server must have " + dimension.location());
        var testOrigin = helper.absolutePos(BlockPos.ZERO);
        var site = new OtherDimensionSite(level, new BlockPos(testOrigin.getX(), FLOOR_Y + 1, testOrigin.getZ()), size);
        site.load();
        OPEN.add(site);
        return site;
    }

    /**
     * Whether the region can be built on: once every chunk ticks block entities, which also needs its entities loaded,
     * the region is walled and emptied, and this stays true. Blocks placed earlier would sit in a chunk whose block
     * entities do not tick yet.
     */
    public boolean ready() {
        if (filled) return true;
        if (!chunks.stream().allMatch(this::ticking)) return false;
        fill();
        filled = true;
        return true;
    }

    /** How many of the region's chunks tick their block entities, for a test's waiting message. */
    public String tickDiagnostics() {
        return chunks.stream().filter(this::ticking).count() + "/" + chunks.size() + " chunks ticking";
    }

    private boolean ticking(ChunkPos chunk) {
        return level.getChunkSource().isPositionTicking(chunk.toLong()) && level.areEntitiesLoaded(chunk.toLong());
    }

    public ServerLevel level() {
        return level;
    }

    public BlockPos absolute(BlockPos relative) {
        return origin.offset(relative);
    }

    public void setBlock(BlockPos relative, BlockState state) {
        level.setBlock(absolute(relative), state, Block.UPDATE_ALL);
    }

    public BlockState getBlockState(BlockPos relative) {
        return level.getBlockState(absolute(relative));
    }

    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> T getBlockEntity(BlockPos relative) {
        return (T) level.getBlockEntity(absolute(relative));
    }

    /**
     * Unloads and reloads every block entity of the region in one tick, as {@link BlockEntityReload} does in the
     * overworld: each is saved, told its chunk unloaded, removed and loaded anew from the saved data.
     */
    public void reloadBlockEntities() {
        var registries = level.registryAccess();
        var saved = new java.util.LinkedHashMap<BlockPos, net.minecraft.nbt.CompoundTag>();
        for (var position : BlockPos.betweenClosed(origin, origin.offset(size).offset(-1, -1, -1))) {
            var entity = level.getBlockEntity(position);
            if (entity != null) saved.put(position.immutable(), entity.saveWithFullMetadata(registries));
        }
        for (var position : saved.keySet()) {
            level.getBlockEntity(position).onChunkUnloaded();
            level.removeBlockEntity(position);
        }
        saved.forEach((position, data) -> {
            var entity = BlockEntity.loadStatic(position, level.getBlockState(position), data, registries);
            if (entity == null) throw new IllegalStateException("A saved block entity must load again at " + position);
            level.setBlockEntity(entity);
        });
    }

    /** Empties the region, removing its block entities the way breaking their blocks does, and releases its tickets. */
    @Override
    public void close() {
        if (closed) return;
        for (var position : BlockPos.betweenClosed(origin, origin.offset(size).offset(-1, -1, -1))) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        release();
    }

    private void release() {
        if (closed) return;
        closed = true;
        OPEN.remove(this);
        for (var chunk : chunks) CONTROLLER.forceChunk(level, origin, chunk.x, chunk.z, false, true);
    }

    private void load() {
        var low = new ChunkPos(origin.offset(-1, 0, -1));
        var high = new ChunkPos(origin.offset(size));
        for (int x = low.x; x <= high.x; x++) {
            for (int z = low.z; z <= high.z; z++) {
                chunks.add(new ChunkPos(x, z));
                CONTROLLER.forceChunk(level, origin, x, z, true, true);
            }
        }
        // A chunk's entities load, and its block entities tick, only once every chunk within two of it is full; load
        // them all now, which also runs this level's chunk tasks while it waits.
        for (int x = low.x - 2; x <= high.x + 2; x++) {
            for (int z = low.z - 2; z <= high.z + 2; z++) {
                level.getChunk(x, z);
            }
        }
    }

    /** A stone shell one block thick around the region, with air inside: the shell's floor is the region's floor. */
    private void fill() {
        var shellLow = origin.offset(-1, -1, -1);
        var shellHigh = origin.offset(size);
        for (var position : BlockPos.betweenClosed(shellLow, shellHigh)) {
            boolean inside = position.getX() > shellLow.getX() && position.getX() < shellHigh.getX()
                    && position.getY() > shellLow.getY() && position.getY() < shellHigh.getY()
                    && position.getZ() > shellLow.getZ() && position.getZ() < shellHigh.getZ();
            // The shell goes in first and without neighbour updates, so nothing flows in while the inside is cleared.
            if (!inside) level.setBlock(position, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        for (var position : BlockPos.betweenClosed(origin, shellHigh.offset(-1, -1, -1))) {
            level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }
}
