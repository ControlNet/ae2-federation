package space.controlnet.ae2federation.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;

/**
 * The faces of one member of a formed Router or Switch, composed from the artist's four connected-face tiles of
 * that device as the offline prototype composes them ({@code tools/multiblock/face_tiles.py}). Each exposed face
 * starts from {@code <device>_base}; where a face edge lies on the body's outside, the frame rows of
 * {@code <device>_side_hor} (top, bottom) or frame columns of {@code <device>_side_ver} (left, right) are laid over
 * it, and where both edges of a corner do, the corner's square of {@code <device>_corner} covers that. The frame width
 * is the side tiles' opaque band. A single block composed this way equals the standalone texture.
 *
 * <p>Texels in the art's two backing greys sit one model unit below the face and every other texel is flush, as in
 * the standalone models (bar the Switch's two-unit notches on its frame edge); walls close each step, each showing its raised texel folded over the edge. Texture edges
 * follow Minecraft's face UVs, as the standalone models' do. The relief is read from the tiles' pixels, so it
 * follows the art.
 *
 * <p>A member's geometry depends only on which of its six neighbours belong to the same body, so it is built per
 * connection set. It touches no client class, so a unit test compares it with the prototype's output. Coordinates are
 * model units and texture pixels until {@link Quad} divides them by 16.
 */
public final class DeviceStructureGeometry {
    /** Python's side order; the order of output quads does not matter. */
    public enum Side {
        WEST(0, -1), EAST(0, 1), DOWN(1, -1), UP(1, 1), NORTH(2, -1), SOUTH(2, 1);

        final int axis;
        final int sign;

        Side(int axis, int sign) {
            this.axis = axis;
            this.sign = sign;
        }
    }

    public enum Device {
        ROUTER, SWITCH, QUANTUM_CUBE;

        /** The device's name in texture paths. */
        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * One quad: four corners in block units, counter-clockwise seen from its front, normalized texture UVs, and its
     * texture's path in the mod's namespace.
     */
    public record Quad(double[][] positions, double[][] uv, String kind, String texture) {
    }

    public static final List<String> TILES = List.of("base", "corner", "side_hor", "side_ver");
    /** The two backing greys of the Router and Switch art, as 0xRRGGBB: these texels are recessed. */
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
    static final int LEFT = 0;
    static final int RIGHT = 1;
    static final int TOP = 2;
    static final int BOTTOM = 3;

    private record Texel(String tile, boolean recessed) {
    }

    private final Device device;
    /** The tiles' ARGB pixels, row by row (index v * 16 + u). */
    private final Map<String, int[]> tiles;
    /** The frame's width in texels across the top and bottom edges, and across the left and right edges. */
    private final int rows;
    private final int columns;

    /** {@code tiles} maps each name of {@link #TILES} to its 16x16 ARGB pixels, row by row. */
    public DeviceStructureGeometry(Device device, Map<String, int[]> tiles) {
        for (var name : TILES) {
            var pixels = tiles.get(name);
            if (pixels == null || pixels.length != 256) {
                throw new IllegalArgumentException("The " + device.id() + " tile '" + name + "' must be 16x16");
            }
        }
        this.device = device;
        this.tiles = Map.copyOf(tiles);
        this.rows = band(this.tiles.get("side_hor"), true);
        this.columns = band(this.tiles.get("side_ver"), false);
    }

    /** How many rows (or columns) from the top (or left) edge the side tile covers completely. */
    static int band(int[] tile, boolean horizontal) {
        int count = 0;
        while (count < 8) {
            for (int i = 0; i < 16; i++) {
                int pixel = horizontal ? tile[count * 16 + i] : tile[i * 16 + count];
                if ((pixel >>> 24) == 0) return count;
            }
            count++;
        }
        return count;
    }

    /** The quads of a member whose neighbours on {@code connections} belong to the same body. */
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
        var grid = tileGrid(tiles, rows, columns, outside);
        var result = new Texel[16][16];
        for (int v = 0; v < 16; v++) {
            for (int u = 0; u < 16; u++) {
                int rgb = tiles.get(grid[v][u])[v * 16 + u] & 0xFFFFFF;
                result[v][u] = new Texel(grid[v][u], RECESSED.contains(rgb));
            }
        }
        return result;
    }

    /**
     * Which tile each texel of a face shows, indexed [v][u], for a face whose edges marked in {@code outside} (by
     * {@link #LEFT} to {@link #BOTTOM}) lie on the body's outside; {@code rows} and {@code columns} are the frame's
     * width, from {@link #band}.
     */
    static String[][] tileGrid(Map<String, int[]> tiles, int rows, int columns, boolean[] outside) {
        var grid = new String[16][16];
        for (var row : grid) java.util.Arrays.fill(row, "base");
        if (outside[TOP]) lay(tiles, grid, "side_hor", 0, 16, 0, rows);
        if (outside[BOTTOM]) lay(tiles, grid, "side_hor", 0, 16, 16 - rows, 16);
        if (outside[LEFT]) lay(tiles, grid, "side_ver", 0, columns, 0, 16);
        if (outside[RIGHT]) lay(tiles, grid, "side_ver", 16 - columns, 16, 0, 16);
        if (outside[LEFT] && outside[TOP]) lay(tiles, grid, "corner", 0, columns, 0, rows);
        if (outside[RIGHT] && outside[TOP]) lay(tiles, grid, "corner", 16 - columns, 16, 0, rows);
        if (outside[LEFT] && outside[BOTTOM]) lay(tiles, grid, "corner", 0, columns, 16 - rows, 16);
        if (outside[RIGHT] && outside[BOTTOM]) lay(tiles, grid, "corner", 16 - columns, 16, 16 - rows, 16);
        return grid;
    }

    /** A face's texture edges as the block sides they lie on: left, right, top, bottom. */
    static Side[] edges(Side side) {
        return EDGES.get(side).clone();
    }

    private static void lay(Map<String, int[]> tiles, String[][] grid, String tile, int u0, int u1, int v0, int v1) {
        var pixels = tiles.get(tile);
        for (int v = v0; v < v1; v++) {
            for (int u = u0; u < u1; u++) {
                if ((pixels[v * 16 + u] >>> 24) != 0) grid[v][u] = tile;
            }
        }
    }

    static double[] toModel(Side side, double u, double v, double d) {
        return switch (side) {
            case NORTH -> new double[] {16 - u, 16 - v, d};
            case SOUTH -> new double[] {u, 16 - v, 16 - d};
            case WEST -> new double[] {d, 16 - v, u};
            case EAST -> new double[] {16 - d, 16 - v, 16 - u};
            case UP -> new double[] {u, 16 - d, v};
            case DOWN -> new double[] {u, d, 16 - v};
        };
    }

    static double[] toTexture(Side side, double[] point) {
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

    private Quad quad(double[][] points, double[][] uv, String tile, String kind) {
        var p = new double[4][3];
        var t = new double[4][2];
        for (int i = 0; i < 4; i++) {
            for (int a = 0; a < 3; a++) p[i][a] = points[i][a] / 16;
            for (int a = 0; a < 2; a++) t[i][a] = uv[i][a] / 16;
        }
        return new Quad(p, t, kind, "block/" + device.id() + "/" + device.id() + "_" + tile);
    }

    private void faceQuads(Side side, Texel[][] grid, List<IntFunction<Texel>> beyond, List<Quad> out) {
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
                var points = facePoints(b[0], b[1], side);
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
                        var points = facePoints(b[0], b[1], wallSide);
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
                    throw new IllegalStateException("The " + device.id() + " art recesses a texel on the outer edge of a face");
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

    /** A quad's front normal (unnormalized), from its counter-clockwise corners. */
    public static double[] normal(double[][] p) {
        double ax = p[1][0] - p[0][0], ay = p[1][1] - p[0][1], az = p[1][2] - p[0][2];
        double bx = p[2][0] - p[0][0], by = p[2][1] - p[0][1], bz = p[2][2] - p[0][2];
        return new double[] {ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx};
    }

    /** The corners of a box's face, in Minecraft's vertex order: counter-clockwise from the top left, seen outside. */
    static double[][] facePoints(double[] lo, double[] hi, Side side) {
        double x = lo[0], y = lo[1], z = lo[2], X = hi[0], Y = hi[1], Z = hi[2];
        return switch (side) {
            case NORTH -> new double[][] {{X, Y, z}, {X, y, z}, {x, y, z}, {x, Y, z}};
            case SOUTH -> new double[][] {{x, Y, Z}, {x, y, Z}, {X, y, Z}, {X, Y, Z}};
            case WEST -> new double[][] {{x, Y, z}, {x, y, z}, {x, y, Z}, {x, Y, Z}};
            case EAST -> new double[][] {{X, Y, Z}, {X, y, Z}, {X, y, z}, {X, Y, z}};
            case UP -> new double[][] {{x, Y, z}, {x, Y, Z}, {X, Y, Z}, {X, Y, z}};
            case DOWN -> new double[][] {{x, y, Z}, {x, y, z}, {X, y, z}, {X, y, Z}};
        };
    }
}
