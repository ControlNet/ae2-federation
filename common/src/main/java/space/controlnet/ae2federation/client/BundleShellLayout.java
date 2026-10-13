package space.controlnet.ae2federation.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import space.controlnet.ae2federation.bundle.BundleLook;
import space.controlnet.ae2federation.router.CableShapes;

/**
 * The faces of one bundled cell's part of its bundle's shell, in voxels. The shell is one box the size of the
 * cross-section, 2 voxels inside its outer boundary as an ordinary cable's core is, so a cell draws only the faces on
 * that boundary; faces between two cells of a bundle are never drawn. Each face is a panel of the artist's core frame
 * stretched over the whole shell: a 3-voxel rail where the shell's face ends, and glass between. A face that runs on
 * into the next cell has no rail there.
 *
 * <p>Where a branch's end face lies against a side, that side opens. The cells at the opening keep only the strips of
 * the side outside the branch's shell, and draw the branch's walls across the 2 voxels between their shell and their
 * face, so the two shells meet.
 */
final class BundleShellLayout {
    /** Rails are 3 voxels wide, as on the core texture; the middle samples 2 texels whose rows and columns are uniform. */
    private static final float RAIL = 3;
    private static final float LOW_RAIL_TEXEL = 2;
    private static final float HIGH_RAIL_TEXEL = 14;
    private static final float MIDDLE_FROM_TEXEL = 5;
    private static final float MIDDLE_TO_TEXEL = 7;

    private BundleShellLayout() {
    }

    /** One extent of a panel, and whether the shell's face ends at each end of it, so a rail runs there. */
    record Span(float low, float high, boolean lowClosed, boolean highClosed) {
    }

    /** A rectangle of a panel and the texels it samples along one axis. */
    record Slice(float from, float to, float texelFrom, float texelTo) {
    }

    /** One flat face facing {@code normal}, at {@code plane} along its axis, spanning {@code u} by {@code v}. */
    record Panel(Direction normal, float plane, Direction.Axis uAxis, Span u, Direction.Axis vAxis, Span v) {
    }

    static List<Panel> panels(BundleLook look) {
        var panels = new ArrayList<Panel>();
        var axis = look.axis();
        for (var side : Direction.values()) {
            var normal = side.getAxis();
            boolean positive = side.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            float plane = (float) (positive ? CableShapes.shellHigh(look, normal) : CableShapes.shellLow(look, normal));
            if (normal == axis) {
                // An end that neither runs on nor joins a branch closes, with a dense arm beyond it or not.
                if (look.open(side)) continue;
                var across = others(normal);
                panels.add(new Panel(side, plane, across[0], span(look, across[0]), across[1], span(look, across[1])));
                continue;
            }
            if (!look.edge(side)) continue;
            var width = third(axis, normal);
            var along = span(look, axis);
            var wide = span(look, width);
            if (!look.join(side)) {
                panels.add(new Panel(side, plane, axis, along, width, wide));
                continue;
            }
            float holeLow = look.joinsBefore(side) ? 0 : 2;
            float holeHigh = look.joinsAfter(side) ? 16 : 14;
            if (holeLow > along.low()) {
                panels.add(new Panel(side, plane, axis, new Span(along.low(), holeLow, along.lowClosed(), false),
                        width, wide));
            }
            if (along.high() > holeHigh) {
                panels.add(new Panel(side, plane, axis, new Span(holeHigh, along.high(), false, along.highClosed()),
                        width, wide));
            }
            // The branch's shell, from this cell's face in to this shell: its walls across the bundle's width...
            var depth = positive ? new Span(plane, 16, false, false) : new Span(0, plane, false, false);
            var hole = new Span(holeLow, holeHigh, !look.joinsBefore(side), !look.joinsAfter(side));
            for (var wall : directions(width)) {
                if (!look.edge(wall)) continue;
                float at = (float) (wall.getAxisDirection() == Direction.AxisDirection.POSITIVE
                        ? CableShapes.shellHigh(look, width) : CableShapes.shellLow(look, width));
                panels.add(new Panel(wall, at, normal, depth, axis, hole));
            }
            // ...and its walls where it starts and ends along this bundle.
            for (var wall : directions(axis)) {
                boolean runsOn = wall.getAxisDirection() == Direction.AxisDirection.NEGATIVE
                        ? look.joinsBefore(side) : look.joinsAfter(side);
                if (runsOn) continue;
                float at = wall.getAxisDirection() == Direction.AxisDirection.NEGATIVE ? holeLow : holeHigh;
                panels.add(new Panel(wall, at, normal, depth, width, wide));
            }
        }
        return List.copyOf(panels);
    }

    /**
     * How a span samples the core texture: a rail at each closed end, mapped texel for voxel from the frame's edge, and
     * the uniform middle stretched between. A span too short for two whole rails splits its length between them.
     */
    static List<Slice> slices(Span span) {
        var slices = new ArrayList<Slice>(3);
        int closed = (span.lowClosed() ? 1 : 0) + (span.highClosed() ? 1 : 0);
        float length = span.high() - span.low();
        float rail = closed == 0 ? 0 : Math.min(RAIL, length / closed);
        float middleLow = span.lowClosed() ? span.low() + rail : span.low();
        float middleHigh = span.highClosed() ? span.high() - rail : span.high();
        if (span.lowClosed() && rail > 0) {
            slices.add(new Slice(span.low(), middleLow, LOW_RAIL_TEXEL, LOW_RAIL_TEXEL + rail));
        }
        if (middleHigh > middleLow) slices.add(new Slice(middleLow, middleHigh, MIDDLE_FROM_TEXEL, MIDDLE_TO_TEXEL));
        if (span.highClosed() && rail > 0) {
            slices.add(new Slice(middleHigh, span.high(), HIGH_RAIL_TEXEL - rail, HIGH_RAIL_TEXEL));
        }
        return slices;
    }

    private static Span span(BundleLook look, Direction.Axis axis) {
        var low = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        var high = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        // Across the bundle a face's rail runs along every outer edge, past an opening too, where a branch's own rails
        // meet it; along the bundle it runs only where the shell ends.
        boolean along = axis == look.axis();
        return new Span((float) CableShapes.shellLow(look, axis), (float) CableShapes.shellHigh(look, axis),
                along ? !look.open(low) : look.edge(low), along ? !look.open(high) : look.edge(high));
    }

    private static Direction.Axis[] others(Direction.Axis axis) {
        return switch (axis) {
            case X -> new Direction.Axis[] {Direction.Axis.Y, Direction.Axis.Z};
            case Y -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z};
            case Z -> new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Y};
        };
    }

    private static Direction.Axis third(Direction.Axis first, Direction.Axis second) {
        for (var axis : Direction.Axis.values()) {
            if (axis != first && axis != second) return axis;
        }
        throw new IllegalArgumentException("Same axis twice: " + first);
    }

    private static Direction[] directions(Direction.Axis axis) {
        return new Direction[] {Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE),
                Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE)};
    }
}
