package space.controlnet.ae2federation.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import space.controlnet.ae2federation.client.DeviceStructureGeometry.Quad;
import space.controlnet.ae2federation.client.DeviceStructureGeometry.Side;

/**
 * One face of connected tiles, composed as {@link DeviceStructureGeometry} composes a Router's, drawn flat a hair
 * outside a block's face: the formed look of a part whose block is covered by another block's model, such as a
 * communication interface on a formed computation system. It has no relief, since nothing may sink into the model
 * beneath. Like the Router's, it touches no client class.
 */
public final class FacePlateGeometry {
    /** How far outside the block face the plate lies, in model units: the JSON plate's distance. */
    static final double OFFSET = 0.04;

    private final Map<String, int[]> tiles;
    private final String texturePrefix;
    private final int rows;
    private final int columns;

    /**
     * {@code tiles} maps each name of {@link DeviceStructureGeometry#TILES} to its 16x16 ARGB pixels, row by row; a
     * quad's texture is {@code texturePrefix} followed by its tile's name.
     */
    public FacePlateGeometry(Map<String, int[]> tiles, String texturePrefix) {
        for (var name : DeviceStructureGeometry.TILES) {
            var pixels = tiles.get(name);
            if (pixels == null || pixels.length != 256) {
                throw new IllegalArgumentException("The tile '" + name + "' must be 16x16");
            }
        }
        this.tiles = Map.copyOf(tiles);
        this.texturePrefix = texturePrefix;
        this.rows = DeviceStructureGeometry.band(this.tiles.get("side_hor"), true);
        this.columns = DeviceStructureGeometry.band(this.tiles.get("side_ver"), false);
    }

    /** The quads of the plate on {@code face} of a block whose neighbours on {@code connections} join its body. */
    public List<Quad> build(Side face, Set<Side> connections) {
        var edges = DeviceStructureGeometry.edges(face);
        var outside = new boolean[4];
        for (int i = 0; i < 4; i++) outside[i] = !connections.contains(edges[i]);
        var grid = DeviceStructureGeometry.tileGrid(tiles, rows, columns, outside);
        var result = new ArrayList<Quad>();
        // Greedy row-major merge of texels with the same tile.
        var done = new boolean[16][16];
        for (int v = 0; v < 16; v++) {
            for (int u = 0; u < 16; u++) {
                if (done[v][u]) continue;
                var tile = grid[v][u];
                int u1 = u;
                while (u1 < 16 && !done[v][u1] && grid[v][u1].equals(tile)) u1++;
                int v1 = v + 1;
                while (v1 < 16 && rowMatches(grid, done, v1, u, u1, tile)) v1++;
                for (int j = v; j < v1; j++) for (int i = u; i < u1; i++) done[j][i] = true;
                result.add(quad(face, u, v, u1, v1, tile));
            }
        }
        return List.copyOf(result);
    }

    private Quad quad(Side face, int u0, int v0, int u1, int v1, String tile) {
        var lo = new double[] {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        var hi = new double[] {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (int u : new int[] {u0, u1}) {
            for (int v : new int[] {v0, v1}) {
                var c = DeviceStructureGeometry.toModel(face, u, v, -OFFSET);
                for (int a = 0; a < 3; a++) {
                    lo[a] = Math.min(lo[a], c[a]);
                    hi[a] = Math.max(hi[a], c[a]);
                }
            }
        }
        var points = DeviceStructureGeometry.facePoints(lo, hi, face);
        var p = new double[4][3];
        var t = new double[4][2];
        for (int i = 0; i < 4; i++) {
            var uv = DeviceStructureGeometry.toTexture(face, points[i]);
            for (int a = 0; a < 3; a++) p[i][a] = points[i][a] / 16;
            for (int a = 0; a < 2; a++) t[i][a] = uv[a] / 16;
        }
        return new Quad(p, t, "plate", texturePrefix + tile);
    }

    private static boolean rowMatches(String[][] grid, boolean[][] done, int v, int u0, int u1, String tile) {
        for (int i = u0; i < u1; i++) {
            if (done[v][i] || !grid[v][i].equals(tile)) return false;
        }
        return true;
    }
}
