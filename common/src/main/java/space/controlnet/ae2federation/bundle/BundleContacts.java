package space.controlnet.ae2federation.bundle;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import java.util.function.Predicate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import space.controlnet.ae2federation.router.CableLinkedBlock;
import space.controlnet.ae2federation.router.RouterBlock;

/**
 * Which faces of a Federation Cable may link. A bundle never links to an ordinary cable, and two bundles link only
 * where {@link BundleSet#linked} joins them. To a Router or Switch, a bundle links only through a whole contact: its
 * whole end face, or one layer of a side across that side's full width, lying against one formed device body or
 * {@link BundleBody}.
 * Anything less counts as not connected.
 */
public final class BundleContacts {
    private BundleContacts() {
    }

    /** A contact with a device: how many of its cells a device covers, and whether one body covers them all. */
    public record Contact(List<BlockPos> cells, Direction face, int covered, boolean whole, BlockPos firstDevice) {
        /** The device positions against the contact. */
        public List<BlockPos> devices() {
            return cells.stream().map(cell -> cell.relative(face)).toList();
        }
    }

    /**
     * What a bundle touches: whole end faces, runs of whole side layers, every whole contact, and the contacts a device
     * covers only in part.
     */
    public record Summary(int ends, int sides, List<Contact> whole, List<Contact> partial) {
    }

    /** Whether the Federation Cable at {@code pos} may link through {@code face}. */
    public static boolean open(ServerLevel level, BlockPos pos, Direction face) {
        var bundles = BundledCables.get(level);
        var here = bundles.at(pos);
        var neighbour = pos.relative(face);
        var there = bundles.at(neighbour);
        if (here.isEmpty()) return there.isEmpty();
        if (there.isPresent()) return bundles.linked(pos, neighbour);
        return contact(level, here.get().segment(), pos, face).whole();
    }

    /**
     * The contact that {@code face} of {@code pos} belongs to: the whole end face along the axis, or the row across the
     * side at {@code pos}'s layer.
     */
    public static Contact contact(BlockGetter level, BundleSegment segment, BlockPos pos, Direction face) {
        var cells = face.getAxis() == segment.axis() ? segment.endLayer(face).cells() : row(segment, pos, face);
        int covered = 0;
        BlockPos firstDevice = null;
        net.minecraft.world.level.block.Block body = null;
        for (var cell : cells) {
            var device = cell.relative(face);
            if (level instanceof Level loaded && !loaded.isLoaded(device)) continue;
            var state = level.getBlockState(device);
            if (!formed(state) || body != null && state.getBlock() != body
                    || !(state.getBlock() instanceof CableLinkedBlock linked && linked.linksCable(state, face.getOpposite()))) {
                continue;
            }
            if (body == null) {
                body = state.getBlock();
                firstDevice = device;
            }
            covered++;
        }
        boolean whole = covered == cells.size() && oneBody(level, cells, face);
        return new Contact(cells, face, covered, whole, firstDevice);
    }

    /** The device contacts of {@code bundle}: whole end faces, whole side runs, and contacts a device covers in part. */
    public static Summary summary(ServerLevel level, BundleSet.Bundle bundle) {
        var bundles = BundledCables.get(level);
        return summary(level, bundle, pos -> bundles.at(pos).isPresent());
    }

    /** {@link #summary(ServerLevel, BundleSet.Bundle)}, with {@code bundled} telling which cells are bundled. */
    public static Summary summary(Level level, BundleSet.Bundle bundle, Predicate<BlockPos> bundled) {
        int ends = 0;
        int sides = 0;
        var whole = new ArrayList<Contact>();
        var partial = new ArrayList<Contact>();
        for (var segment : bundle.segments()) {
            for (var face : Direction.values()) {
                if (face.getAxis() == segment.axis()) {
                    var contact = contact(level, segment, segment.endLayer(face).min(), face);
                    if (outsideBundles(bundled, contact.cells(), face)) {
                        if (contact.whole()) {
                            ends++;
                            whole.add(contact);
                        } else if (contact.covered() > 0) {
                            partial.add(contact);
                        }
                    }
                    continue;
                }
                boolean previousWhole = false;
                var axis = segment.axis();
                for (int layer = segment.min().get(axis); layer <= segment.max().get(axis); layer++) {
                    var pos = BundleSegment.with(face.getAxisDirection() == Direction.AxisDirection.POSITIVE
                            ? segment.max() : segment.min(), axis, layer);
                    var contact = contact(level, segment, pos, face);
                    boolean outside = outsideBundles(bundled, contact.cells(), face);
                    boolean isWhole = outside && contact.whole();
                    if (isWhole && !previousWhole) sides++;
                    if (isWhole) whole.add(contact);
                    else if (outside && contact.covered() > 0) partial.add(contact);
                    previousWhole = isWhole;
                }
            }
        }
        return new Summary(ends, sides, whole, partial);
    }

    /** The cells of {@code segment}'s side toward {@code face} at {@code pos}'s layer, across the side's width. */
    private static List<BlockPos> row(BundleSegment segment, BlockPos pos, Direction face) {
        var width = BundleSegment.third(segment.axis(), face.getAxis());
        var start = BundleSegment.with(BundleSegment.with(segment.min(), segment.axis(), pos.get(segment.axis())),
                face.getAxis(), pos.get(face.getAxis()));
        var cells = new ArrayList<BlockPos>();
        for (int at = segment.min().get(width); at <= segment.max().get(width); at++) {
            cells.add(BundleSegment.with(start, width, at));
        }
        return cells;
    }

    /** Whether the devices beside {@code cells} are joined into one body along every edge between them. */
    private static boolean oneBody(BlockGetter level, List<BlockPos> cells, Direction face) {
        for (var cell : cells) {
            int connections = connections(level.getBlockState(cell.relative(face)));
            for (var step : Direction.values()) {
                if (step.getAxis() == face.getAxis() || !cells.contains(cell.relative(step))) continue;
                if ((connections & 1 << step.get3DDataValue()) == 0) return false;
            }
        }
        return true;
    }

    /** Whether {@code state} belongs to a formed body: a Router-like device's, or a {@link BundleBody}'s. */
    private static boolean formed(BlockState state) {
        return state.getBlock() instanceof BundleBody body ? body.bodyFormed(state) : RouterBlock.formed(state);
    }

    private static int connections(BlockState state) {
        return state.getBlock() instanceof BundleBody body ? body.bodyConnections(state)
                : RouterBlock.connections(state);
    }

    private static boolean outsideBundles(Predicate<BlockPos> bundled, List<BlockPos> cells, Direction face) {
        for (var cell : cells) {
            if (bundled.test(cell.relative(face))) return false;
        }
        return true;
    }
}
