package space.controlnet.ae2federation.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.util.FastColor;
import space.controlnet.ae2federation.bundle.BundleBeam;

/**
 * One frame's beams, shared by cables that look alike. A beam's pulses depend on a cable's position only through
 * whether each coordinate along its arms is odd or even ({@link BundleBeamMesh#parityAxes}), so within one frame every
 * cable with the same connections, load and parity draws the same vertices: they are computed once and replayed for the
 * rest. The next frame computes them again into the same arrays, so a steady scene allocates none.
 */
final class CableBeamFrame {
    /**
     * Floats per vertex: x, y, z within the block, the normal and its squared length, so the renderer can fade soft
     * layers, then alpha and the colour, as ARGB bits with alpha 0 (an exponent of at most 1: never NaN). The renderer
     * would otherwise take the last two from the mesh's floats again for every vertex of every cable.
     */
    static final int BUNDLE_STRIDE = 9;

    private record CellKey(int mask, int parity, int detail, boolean glow, CableBeamStyle style) {
    }

    private record BundleKey(BundleBeam beam, int parity, int detail, boolean glow, CableBeamStyle style) {
    }

    private Map<BundleKey, float[]> bundles = new HashMap<>();
    private Map<CellKey, float[]> cells = new HashMap<>();
    /** The last frame's arrays, until this frame takes them back; any left over are let go at the next frame. */
    private Map<BundleKey, float[]> lastBundles = new HashMap<>();
    private Map<CellKey, float[]> lastCells = new HashMap<>();
    private long tick = Long.MIN_VALUE;
    private float partialTick = Float.NaN;
    private float[] scratch = new float[BUNDLE_STRIDE * 1024];
    private int size;

    /**
     * The vertices of one pass of the bundle beam drawn by the cell at {@code x, y, z}, {@link #BUNDLE_STRIDE} floats
     * each; do not modify or keep past this frame.
     */
    float[] bundle(BundleBeam beam, int x, int y, int z, long tick, float partialTick, CableBeamStyle style,
            int detail, boolean glow) {
        advance(tick, partialTick);
        var key = new BundleKey(beam, parity(x, y, z) & BundleBeamMesh.parityAxes(beam, detail, glow), detail, glow,
                style);
        var layer = bundles.get(key);
        if (layer == null) {
            size = 0;
            BundleBeamMesh.emit(beam, x, y, z, tick, partialTick, style, detail, glow, this::record);
            layer = keep(lastBundles.remove(key));
            bundles.put(key, layer);
        }
        return layer;
    }

    /**
     * The vertices of one pass of the ordinary cell's plasma beam at {@code x, y, z}, {@link #BUNDLE_STRIDE} floats
     * each; do not modify or keep past this frame.
     */
    float[] cell(int mask, int x, int y, int z, long tick, float partialTick, CableBeamStyle style, int detail,
            boolean glow) {
        advance(tick, partialTick);
        var key = new CellKey(mask, parity(x, y, z) & BundleBeamMesh.cellParityAxes(mask, detail, glow), detail, glow,
                style);
        var layer = cells.get(key);
        if (layer == null) {
            size = 0;
            BundleBeamMesh.emitCell(mask, x, y, z, tick, partialTick, style, detail, glow, this::record);
            layer = keep(lastCells.remove(key));
            cells.put(key, layer);
        }
        return layer;
    }

    /** The vertices just computed, in {@code last} (the same beam's array from the last frame) when it fits. */
    private float[] keep(float[] last) {
        if (last == null || last.length != size) return Arrays.copyOf(scratch, size);
        System.arraycopy(scratch, 0, last, 0, size);
        return last;
    }

    private void advance(long tick, float partialTick) {
        if (tick != this.tick || Float.floatToIntBits(partialTick) != Float.floatToIntBits(this.partialTick)) {
            var spareBundles = lastBundles;
            lastBundles = bundles;
            bundles = spareBundles;
            bundles.clear();
            var spareCells = lastCells;
            lastCells = cells;
            cells = spareCells;
            cells.clear();
            this.tick = tick;
            this.partialTick = partialTick;
        }
    }

    private static int parity(int x, int y, int z) {
        return Math.floorMod(x, 2) | Math.floorMod(y, 2) << 1 | Math.floorMod(z, 2) << 2;
    }

    private void record(float x, float y, float z, float red, float green, float blue, float alpha, float nx, float ny,
            float nz) {
        if (size + BUNDLE_STRIDE > scratch.length) scratch = Arrays.copyOf(scratch, scratch.length * 2);
        scratch[size++] = x;
        scratch[size++] = y;
        scratch[size++] = z;
        scratch[size++] = nx;
        scratch[size++] = ny;
        scratch[size++] = nz;
        scratch[size++] = nx * nx + ny * ny + nz * nz;
        scratch[size++] = alpha;
        scratch[size++] = Float.intBitsToFloat(
                FastColor.ARGB32.color(0, (int) (red * 255), (int) (green * 255), (int) (blue * 255)));
    }
}
