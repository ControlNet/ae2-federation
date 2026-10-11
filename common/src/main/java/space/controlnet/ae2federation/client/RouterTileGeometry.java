package space.controlnet.ae2federation.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import space.controlnet.ae2federation.client.DeviceStructureGeometry.Quad;
import space.controlnet.ae2federation.client.DeviceStructureGeometry.Side;

/**
 * The faces of one member of a formed Router, composed from the artist's four connected-face tiles as the offline
 * prototype composes them ({@code tools/multiblock/router_tiles.py}). Each exposed face starts from
 * {@code router_base}; where a face edge lies on the body's outside, four rows of {@code router_side_hor} (top,
 * bottom) or four columns of {@code router_side_ver} (left, right) are laid over it, and where both edges of a corner
 * do, the corner's 4x4 square of {@code router_corner} covers that. A single block composed this way equals the
 * standalone texture.
 *
 * <p>Texels in the art's two backing greys sit one model unit below the face and every other texel is flush, as in
 * the standalone model; walls close each step, each showing its raised texel folded over the edge. Texture edges
 * follow Minecraft's face UVs, as the standalone model's do. The relief is read from the tiles' pixels, so it follows
 * the art. Touches no client class. Coordinates are model units and texture pixels until {@link Quad} divides by 16.
 */
public final class RouterTileGeometry implements DeviceStructureMember {
    public static final List<String> TILES = List.of("base", "corner", "side_hor", "side_ver");
    /** The two backing greys of the Router art, as 0xRRGGBB: these texels are recessed. */
    private static final Set<Integer> RECESSED = Set.of(0x878FA5, 0x787E97);
    private static final int DEPTH = 1;
    /** Each face's texture edges as the block side they lie on: left (u = 0), right (u = 16), top, bottom. */
    private static final Map<Side, Side[]> EDGES = new EnumMap<>(Side.class);
    static {
        EDGES.put(Side.NORTH, new Side[] {Side.EAST, Side.WEST, Side.UP, Side.DOWN});
        EDGES.put(Side.SOUTH, new Side[] {Side.WEST, Side.EAST, Side.UP, Side.DOWN});
        EDGES.put(Side.WEST, new Side[] {Side.NORTH, Side.SOUTH, Side.UP, Side.DOWN});
        EDGES.put(Side.EAST, new Side[] {Side.SOUTH, Side.NORTH, Side.UP, Side.DOWN});
        EDGES.put(Side.UP, new Side[] {Side.WEST, Side.EAST, Side.NORTH, Side.SOUTH});
        EDGES.put(Side.DOWN, new Side[] {Side.WEST, Side.EAST, Side.SOUTH, Side.NORTH});
    }
    private static final int LEFT = 0;
    private static final int RIGHT = 1;
    private static final int TOP = 2;
    private static final int BOTTOM = 3;

    private record Texel(String tile, boolean recessed) {
    }

    /** The tiles' ARGB pixels, row by row (index v * 16 + u). */
    private final Map<String, int[]> tiles;

    public RouterTileGeometry(Map<String, int[]> tiles) {
        for (var name : TILES) {
            var pixels = tiles.get(name);
            if (pixels == null || pixels.length != 256) {
                throw new IllegalArgumentException("The Router tile '" + name + "' must be 16x16");
            }
        }
        this.tiles = Map.copyOf(tiles);
    }

    @Override
    public List<Quad> build(Set<Side> connections) {
        var result = new ArrayList<Quad>();
        for (var side : Side.values()) {
            if (connections.contains(side)) continue;
            var edges = EDGES.get(side);
            var outside = new boolean[4];
            for (int i = 0; i < 4; i++) outside[i] = !connections.contains(edges[i]);
            var grid = compose(outside);
            var beyond = new ArrayList<IntFunction<Texel>>();
            for (int edge = 0; edge < 4; edge++) {
                if (outside[edge]) {
                    beyond.add(null);
                    continue;
                }
                // The connected member past this edge shares the other two boundary flags; its edges along this
                // axis are inside or irrelevant to the texels next to us.
                var other = outside.clone();
                other[edge] = false;
                other[edge ^ 1] = false;
                var past = compose(other);
                beyond.add(switch (edge) {
                    case LEFT -> i -> past[i][15];
                    case RIGHT -> i -> past[i][0];
                    case TOP -> i -> past[15][i];
                    default -> i -> past[0][i];
                });
            }
            faceQuads(side, grid, beyond, result);
        }
        return List.copyOf(result);
    }

    /** 16x16 texels, indexed [v][u], for a face whose edges marked in {@code outside} lie on the body's outside. */
    private Texel[][] compose(boolean[] outside) {
        var grid = new String[16][16];
        for (var row : grid) java.util.Arrays.fill(row, "base");
        if (outside[TOP]) lay(grid, "side_hor", 0, 16, 0, 4);
        if (outside[BOTTOM]) lay(grid, "side_hor", 0, 16, 12, 16);
        if (outside[LEFT]) lay(grid, "side_ver", 0, 4, 0, 16);
        if (outside[RIGHT]) lay(grid, "side_ver", 12, 16, 0, 16);
        if (outside[LEFT] && outside[TOP]) lay(grid, "corner", 0, 4, 0, 4);
        if (outside[RIGHT] && outside[TOP]) lay(grid, "corner", 12, 16, 0, 4);
        if (outside[LEFT] && outside[BOTTOM]) lay(grid, "corner", 0, 4, 12, 16);
        if (outside[RIGHT] && outside[BOTTOM]) lay(grid, "corner", 12, 16, 12, 16);
        var result = new Texel[16][16];
        for (int v = 0; v < 16; v++) {
            for (int u = 0; u < 16; u++) {
                int rgb = tiles.get(grid[v][u])[v * 16 + u] & 0xFFFFFF;
                result[v][u] = new Texel(grid[v][u], RECESSED.contains(rgb));
            }
        }
        return result;
    }

    private void lay(String[][] grid, String tile, int u0, int u1, int v0, int v1) {
        var pixels = tiles.get(tile);
        for (int v = v0; v < v1; v++) {
            for (int u = u0; u < u1; u++) {
                if ((pixels[v * 16 + u] >>> 24) != 0) grid[v][u] = tile;
            }
        }
    }

    private static double[] toModel(Side side, double u, double v, double d) {
        return switch (side) {
            case NORTH -> new double[] {16 - u, 16 - v, d};
            case SOUTH -> new double[] {u, 16 - v, 16 - d};
            case WEST -> new double[] {d, 16 - v, u};
            case EAST -> new double[] {16 - d, 16 - v, 16 - u};
            case UP -> new double[] {u, 16 - d, v};
            case DOWN -> new double[] {u, d, 16 - v};
        };
    }

    private static double[] toTexture(Side side, double[] point) {
        double x = point[0], y = point[1], z = point[2];
        return switch (side) {
            case NORTH -> new double[] {16 - x, 16 - y};
            case SOUTH -> new double[] {x, 16 - y};
            case WEST -> new double[] {z, 16 - y};
            case EAST -> new double[] {16 - z, 16 - y};
            case UP -> new double[] {x, z};
            case DOWN -> new double[] {x, 16 - z};
        };
    }

    private static double depth(Side side, double[] point) {
        return side.sign < 0 ? point[side.axis] : 16 - point[side.axis];
    }

    /** The model-space box spanning texels [u0, u1) x [v0, v1) of {@code side}, from depth d0 to d1. */
    private static double[][] box(Side side, int u0, int v0, int u1, int v1, int d0, int d1) {
        var lo = new double[] {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        var hi = new double[] {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int u : new int[] {u0, u1}) {
            for (int v : new int[] {v0, v1}) {
                for (int d : new int[] {d0, d1}) {
                    var c = toModel(side, u, v, d);
                    for (int a = 0; a < 3; a++) {
                        lo[a] = Math.min(lo[a], c[a]);
                        hi[a] = Math.max(hi[a], c[a]);
                    }
                }
            }
        }
        return new double[][] {lo, hi};
    }

    private static Quad quad(double[][] points, double[][] uv, String tile, String kind) {
        var p = new double[4][3];
        var t = new double[4][2];
        for (int i = 0; i < 4; i++) {
            for (int a = 0; a < 3; a++) p[i][a] = points[i][a] / 16;
            for (int a = 0; a < 2; a++) t[i][a] = uv[i][a] / 16;
        }
        return new Quad(p, t, kind, "block/router/router_" + tile);
    }

    private static void faceQuads(Side side, Texel[][] grid, List<IntFunction<Texel>> beyond, List<Quad> out) {
        // Greedy row-major merge of texels with the same tile and depth.
        var done = new boolean[16][16];
        for (int v = 0; v < 16; v++) {
            for (int u = 0; u < 16; u++) {
                if (done[v][u]) continue;
                var key = grid[v][u];
                int u1 = u;
                while (u1 < 16 && !done[v][u1] && grid[v][u1].equals(key)) u1++;
                int v1 = v + 1;
                while (v1 < 16 && rowMatches(grid, done, v1, u, u1, key)) v1++;
                for (int j = v; j < v1; j++) for (int i = u; i < u1; i++) done[j][i] = true;
                int d = key.recessed() ? DEPTH : 0;
                var b = box(side, u, v, u1, v1, d, d);
                var points = DeviceStructureGeometry.facePoints(b[0], b[1], side);
                var uv = new double[4][];
                for (int i = 0; i < 4; i++) uv[i] = toTexture(side, points[i]);
                out.add(quad(points, uv, key.tile(), key.recessed() ? "tile-recess" : "tile-surface"));
            }
        }
        var edges = EDGES.get(side);
        // Each raised texel walls in every recessed neighbour; runs along one texel row or column from one tile merge.
        int[][] steps = {{-1, 0, LEFT}, {1, 0, RIGHT}, {0, -1, TOP}, {0, 1, BOTTOM}};
        for (var step : steps) {
            int du = step[0], dv = step[1], edge = step[2];
            var wallSide = edges[edge];
            for (int line = 0; line < 16; line++) {
                int run = 0;
                while (run < 16) {
                    var tile = walled(grid, beyond, du, dv, edge, du != 0 ? line : run, du != 0 ? run : line);
                    int end = run + 1;
                    while (tile != null && end < 16 && tile.equals(walled(grid, beyond, du, dv, edge,
                            du != 0 ? line : end, du != 0 ? end : line))) {
                        end++;
                    }
                    if (tile != null) {
                        int u0 = du != 0 ? line : run, v0 = du != 0 ? run : line;
                        int u1 = du != 0 ? line + 1 : end, v1 = du != 0 ? end : line + 1;
                        var b = box(side, u0, v0, u1, v1, 0, DEPTH);
                        var points = DeviceStructureGeometry.facePoints(b[0], b[1], wallSide);
                        var uv = new double[4][];
                        for (int i = 0; i < 4; i++) {
                            var t = toTexture(side, points[i]);
                            double depth = depth(side, points[i]);
                            // Fold the raised texels over their edge: depth runs back across the texel.
                            if (du != 0) {
                                t[0] = line + (du > 0 ? 1 - depth : depth);
                            } else {
                                t[1] = line + (dv > 0 ? 1 - depth : depth);
                            }
                            uv[i] = t;
                        }
                        out.add(quad(points, uv, tile, "tile-wall"));
                    }
                    run = end;
                }
            }
        }
        for (int edge = 0; edge < 4; edge++) {
            if (beyond.get(edge) != null) continue;
            for (int i = 0; i < 16; i++) {
                var texel = switch (edge) {
                    case LEFT -> grid[i][0];
                    case RIGHT -> grid[i][15];
                    case TOP -> grid[0][i];
                    default -> grid[15][i];
                };
                if (texel.recessed()) {
                    throw new IllegalStateException("The Router art recesses a texel on the outer edge of a face");
                }
            }
        }
    }

    private static boolean rowMatches(Texel[][] grid, boolean[][] done, int v, int u0, int u1, Texel key) {
        for (int i = u0; i < u1; i++) {
            if (done[v][i] || !grid[v][i].equals(key)) return false;
        }
        return true;
    }

    /** The tile of the raised texel at (u, v) when its neighbour toward (du, dv) is recessed, else null. */
    private static String walled(Texel[][] grid, List<IntFunction<Texel>> beyond, int du, int dv, int edge, int u,
            int v) {
        var texel = grid[v][u];
        if (texel.recessed()) return null;
        int nu = u + du, nv = v + dv;
        boolean lower;
        if (nu >= 0 && nu < 16 && nv >= 0 && nv < 16) {
            lower = grid[nv][nu].recessed();
        } else {
            var past = beyond.get(edge);
            lower = past != null && past.apply(du == 0 ? u : v).recessed();
        }
        return lower ? texel.tile() : null;
    }
}
