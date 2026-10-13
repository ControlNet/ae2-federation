package space.controlnet.ae2federation.bundle;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import space.controlnet.ae2federation.router.FederationCableBlock;
import space.controlnet.ae2federation.router.FederationCableBlockEntity;

/**
 * The bundled cables of one level. The {@link BundleSet} here is the only record of which Federation Cables are bundled;
 * a cable's block entity reads its membership from it and saves nothing, so a bundle crossing unloaded chunks stays
 * whole. Every change refreshes the cells it touched: their capabilities, their neighbours' caches and their evidence.
 */
public final class BundledCables extends SavedData {
    private static final String DATA_NAME = "ae2federation_bundled_cables";
    private static final Factory<BundledCables> FACTORY = new Factory<>(BundledCables::new, BundledCables::load);

    private final BundleSet set = new BundleSet();

    public static BundledCables get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public enum Problem {
        NOT_ALL_CABLES,
        ALREADY_BUNDLED,
        UNLOADED
    }

    public Optional<BundleSet.Membership> at(BlockPos pos) {
        return set.at(pos);
    }

    public boolean linked(BlockPos a, BlockPos b) {
        return set.linked(a, b);
    }

    /** Why a box cannot be formed: the problem, how many of its positions have it, and how many positions it has. */
    public record Refusal(Problem problem, int count, int total) {
    }

    /** Why {@code segment} cannot be formed in {@code level}, or empty when every cell is a free Federation Cable. */
    public Optional<Refusal> formable(ServerLevel level, BundleSegment segment) {
        return check(level, segment);
    }

    /**
     * {@link #formable} on either side: a client checks its own view of the cables, the server its record. Positions
     * without a Federation Cable come first, as the more basic problem.
     */
    public static Optional<Refusal> check(Level level, BundleSegment segment) {
        var cells = segment.cells();
        int missing = 0;
        int bundled = 0;
        for (var pos : cells) {
            if (!level.isLoaded(pos)) return Optional.of(new Refusal(Problem.UNLOADED, 1, cells.size()));
            if (!(level.getBlockState(pos).getBlock() instanceof FederationCableBlock)) missing++;
            else if (BundleView.bundled(level, pos)) bundled++;
        }
        if (missing > 0) return Optional.of(new Refusal(Problem.NOT_ALL_CABLES, missing, cells.size()));
        if (bundled > 0) return Optional.of(new Refusal(Problem.ALREADY_BUNDLED, bundled, cells.size()));
        return Optional.empty();
    }

    /** Bundles the cables of {@code segment}, which {@link #formable} accepted. */
    public BundleSet.Bundle form(ServerLevel level, BundleSegment segment) {
        // The new bundle holds every cell whose links may change, including what it merged with.
        var bundle = set.form(segment);
        var touched = new HashSet<BlockPos>();
        bundle.segments().forEach(member -> touched.addAll(member.cells()));
        setDirty();
        refresh(level, touched);
        return bundle;
    }

    /** A bundled cable at {@code pos} was removed: its layer reverts and its bundle splits. */
    public void removed(ServerLevel level, BlockPos pos) {
        if (set.at(pos).isEmpty()) return;
        var removal = set.remove(pos);
        var touched = new HashSet<>(removal.touched());
        touched.addAll(removal.reverted());
        touched.add(pos);
        setDirty();
        refresh(level, touched);
    }

    /**
     * Invalidates the capabilities of every touched cell and of its neighbours, so caches on both sides of each face
     * resolve again, and has each loaded cable republish its evidence.
     */
    private static void refresh(ServerLevel level, Set<BlockPos> touched) {
        var invalidated = new HashSet<BlockPos>();
        for (var pos : touched) {
            if (invalidated.add(pos)) level.invalidateCapabilities(pos);
            for (var face : Direction.values()) {
                var neighbour = pos.relative(face);
                if (invalidated.add(neighbour) && level.isLoaded(neighbour)) level.invalidateCapabilities(neighbour);
            }
            if (level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof FederationCableBlock
                    && level.getBlockEntity(pos) instanceof FederationCableBlockEntity cable) {
                cable.bundleChanged();
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var list = new ListTag();
        for (var segment : set.segments()) {
            var entry = new CompoundTag();
            entry.putLong("min", segment.min().asLong());
            entry.putLong("max", segment.max().asLong());
            entry.putString("axis", segment.axis().getSerializedName());
            list.add(entry);
        }
        tag.put("segments", list);
        return tag;
    }

    private static BundledCables load(CompoundTag tag, HolderLookup.Provider registries) {
        var loaded = new BundledCables();
        for (var element : tag.getList("segments", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) element;
            var axis = Direction.Axis.byName(entry.getString("axis"));
            if (axis == null) continue;
            try {
                loaded.set.form(new BundleSegment(BlockPos.of(entry.getLong("min")), BlockPos.of(entry.getLong("max")),
                        axis));
            } catch (IllegalArgumentException ignored) {
                // A malformed or overlapping record is dropped rather than failing the whole save.
            }
        }
        return loaded;
    }
}
