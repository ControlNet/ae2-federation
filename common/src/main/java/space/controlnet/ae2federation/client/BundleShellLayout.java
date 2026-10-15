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
 * face, so the two shells meet. As on an ordinary cable's connected core faces, no rail runs across the joint: where a
 * branch's wall carries on a face of the run, that face's rail opens, and the rails on either side turn into the
 * branch's.
 */
final class BundleShellLayout {
    /** Rails are 3 voxels wide, as on the core texture. */
    private static final int RAIL = 3;
    /** The core texture's frame starts 2 texels in; a straight rail's profile is read across row 6, glass at 6, 6. */
    private static final int FRAME = 2;
    private static final int STRAIGHT_ROW = 6;
    private static final int GLASS = 6;
    private static final float OPEN = Float.POSITIVE_INFINITY;

    private BundleShellLayout() {
    }

    /** One extent of a panel, and whether the shell's face ends at each end of it, so a rail runs there. */
    record Span(float low, float high, boolean lowClosed, boolean highClosed) {
    }

    /**
     * A stretch of a closed edge with no rail: the edge facing {@code edge}, from {@code from} to {@code to} along it.
     * Infinite bounds run on past the panel.
     */
    record Opening(Direction edge, float from, float to) {
    }

    /** One flat face facing {@code normal}, at {@code plane} along its axis, spanning {@code u} by {@code v}. */
    record Panel(Direction normal, float plane, Direction.Axis uAxis, Span u, Direction.Axis vAxis, Span v,
            List<Opening> openings) {
        Panel(Direction normal, float plane, Direction.Axis uAxis, Span u, Direction.Axis vAxis, Span v) {
            this(normal, plane, uAxis, u, vAxis, v, List.of());
        }
    }

    /** A rectangle of a panel, in voxels, that samples one texel of the core texture throughout. */
    record Tile(int u0, int v0, int u1, int v1, int texelU, int texelV) {
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
                // A branch whose last layer is this one carries the end on in its wall: no rail along that edge.
                var openings = new ArrayList<Opening>();
                for (var edge : Direction.values()) {
                    if (edge.getAxis() == axis || !look.join(edge)) continue;
                    if (!(positive ? look.joinsAfter(edge) : look.joinsBefore(edge))) {
                        openings.add(new Opening(edge, -OPEN, OPEN));
                    }
                }
                panels.add(new Panel(side, plane, across[0], span(look, across[0]), across[1], span(look, across[1]),
                        List.copyOf(openings)));
                continue;
            }
            if (!look.edge(side)) continue;
            var width = third(axis, normal);
            var along = span(look, axis);
            var wide = span(look, width);
            var openings = joints(look, width);
            if (!look.join(side)) {
                panels.add(new Panel(side, plane, axis, along, width, wide, openings));
                continue;
            }
            float holeLow = look.joinsBefore(side) ? 0 : 2;
            float holeHigh = look.joinsAfter(side) ? 16 : 14;
            if (holeLow > along.low()) {
                panels.add(new Panel(side, plane, axis, new Span(along.low(), holeLow, along.lowClosed(), false),
                        width, wide, openings));
            }
            if (along.high() > holeHigh) {
                panels.add(new Panel(side, plane, axis, new Span(holeHigh, along.high(), false, along.highClosed()),
                        width, wide, openings));
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
     * How a panel samples the core texture, as rectangles of one texel each. A voxel within 3 of a closed edge is rail,
     * its texel the column that far into the frame; where two closed edges meet it takes the frame's corner. Past the
     * end of a rail, where an opening starts, the distance is measured to that end too, so the rail turns the corner
     * into the branch's wall instead of stopping. Everything else is glass.
     */
    static List<Tile> tiles(Panel panel) {
        int u0 = Math.round(panel.u().low());
        int u1 = Math.round(panel.u().high());
        int v0 = Math.round(panel.v().low());
        int v1 = Math.round(panel.v().high());
        var edges = edges(panel);
        int[][] texels = new int[u1 - u0][v1 - v0];
        for (int a = u0; a < u1; a++) {
            for (int b = v0; b < v1; b++) texels[a - u0][b - v0] = texel(edges, a, b);
        }
        return merge(texels, u0, v0);
    }

    /** One closed edge: across {@code uEdge ? u : v} at {@code at}, facing low or high, with rail on {@code rails}. */
    private record Edge(boolean uEdge, int at, boolean low, List<float[]> rails) {
    }

    private static List<Edge> edges(Panel panel) {
        var edges = new ArrayList<Edge>();
        addEdge(edges, panel, true, true);
        addEdge(edges, panel, true, false);
        addEdge(edges, panel, false, true);
        addEdge(edges, panel, false, false);
        return edges;
    }

    private static void addEdge(List<Edge> edges, Panel panel, boolean uEdge, boolean low) {
        var span = uEdge ? panel.u() : panel.v();
        if (!(low ? span.lowClosed() : span.highClosed())) return;
        var facing = Direction.fromAxisAndDirection(uEdge ? panel.uAxis() : panel.vAxis(),
                low ? Direction.AxisDirection.NEGATIVE : Direction.AxisDirection.POSITIVE);
        var rails = new ArrayList<float[]>();
        rails.add(new float[] {-OPEN, OPEN});
        for (var opening : panel.openings()) {
            if (opening.edge() != facing) continue;
            var cut = new ArrayList<float[]>();
            for (var rail : rails) {
                if (opening.from() > rail[0]) cut.add(new float[] {rail[0], Math.min(rail[1], opening.from())});
                if (opening.to() < rail[1]) cut.add(new float[] {Math.max(rail[0], opening.to()), rail[1]});
            }
            rails = cut;
        }
        edges.add(new Edge(uEdge, Math.round(low ? span.low() : span.high()), low, rails));
    }

    /** The texel of voxel {@code a, b}, packed as {@code u * 16 + v}. */
    private static int texel(List<Edge> edges, int a, int b) {
        int best = RAIL;
        int cornerU = -1;
        int cornerV = -1;
        for (var edge : edges) {
            int across = edge.uEdge() ? a : b;
            int along = edge.uEdge() ? b : a;
            int perp = edge.low() ? across - edge.at() : edge.at() - (across + 1);
            if (perp < 0 || perp >= RAIL) continue;
            for (var rail : edge.rails()) {
                // How far past the rail's end the voxel lies along the edge; -1 beside the rail itself.
                int beyond = along >= rail[1] ? along - (int) rail[1] : along + 1 <= rail[0] ? (int) rail[0] - along - 1
                        : -1;
                int distance = Math.max(perp, beyond);
                if (distance >= RAIL) continue;
                best = Math.min(best, distance);
                if (beyond < 0) {
                    if (edge.uEdge()) cornerU = perp;
                    else cornerV = perp;
                }
            }
        }
        if (cornerU >= 0 && cornerV >= 0) return (FRAME + cornerU) * 16 + FRAME + cornerV;
        if (best < RAIL) return (FRAME + best) * 16 + STRAIGHT_ROW;
        return GLASS * 16 + GLASS;
    }

    /** Greedy rectangles of equal texels: runs along u, stacked along v while they repeat. */
    private static List<Tile> merge(int[][] texels, int u0, int v0) {
        var tiles = new ArrayList<Tile>();
        int width = texels.length;
        int height = width == 0 ? 0 : texels[0].length;
        boolean[][] done = new boolean[width][height];
        for (int b = 0; b < height; b++) {
            for (int a = 0; a < width; a++) {
                if (done[a][b]) continue;
                int texel = texels[a][b];
                int end = a + 1;
                while (end < width && !done[end][b] && texels[end][b] == texel) end++;
                int top = b + 1;
                while (top < height && runs(texels, done, a, end, top, texel)) top++;
                for (int x = a; x < end; x++) {
                    for (int y = b; y < top; y++) done[x][y] = true;
                }
                tiles.add(new Tile(u0 + a, v0 + b, u0 + end, v0 + top, texel / 16, texel % 16));
            }
        }
        return tiles;
    }

    private static boolean runs(int[][] texels, boolean[][] done, int from, int to, int row, int texel) {
        for (int a = from; a < to; a++) {
            if (done[a][row] || texels[a][row] != texel) return false;
        }
        return true;
    }

    /**
     * The edges of a side panel that a branch carries on: where a branch joins the side beyond {@code width}'s edge,
     * its wall continues this panel, so the rail opens over the branch's length here.
     */
    private static List<Opening> joints(BundleLook look, Direction.Axis width) {
        var openings = new ArrayList<Opening>();
        for (var edge : directions(width)) {
            if (!look.join(edge)) continue;
            openings.add(new Opening(edge, look.joinsBefore(edge) ? -OPEN : 2, look.joinsAfter(edge) ? OPEN : 14));
        }
        return List.copyOf(openings);
    }

    private static Span span(BundleLook look, Direction.Axis axis) {
        var low = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
        var high = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
        // Across the bundle a face's rail runs along every outer edge, unless a branch carries the face on (see
        // joints); along the bundle it runs only where the shell ends.
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
