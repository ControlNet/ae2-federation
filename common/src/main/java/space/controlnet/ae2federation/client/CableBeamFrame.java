package space.controlnet.ae2federation.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import space.controlnet.ae2federation.bundle.BundleBeam;

/**
 * One frame's beams, shared by cables that look alike. A beam's pulses depend on a cable's position only through
 * whether each coordinate is odd or even, so within one frame every cable with the same connections, load and parity
 * draws the same vertices: they are computed once and replayed for the rest.
 */
final class CableBeamFrame {
    /**
     * Per vertex: x, y, z within the block, red, green, blue, alpha, then the normal, so the renderer can fade soft
     * layers.
     */
    static final int BUNDLE_STRIDE = 10;

    private record CellKey(int mask, int parity, int detail, boolean glow, CableBeamStyle style) {
    }

    private record BundleKey(BundleBeam beam, int parity, int detail, boolean glow, CableBeamStyle style) {
    }

    private final Map<BundleKey, float[]> bundles = new HashMap<>();
    private final Map<CellKey, float[]> cells = new HashMap<>();
    private long tick = Long.MIN_VALUE;
    private float partialTick = Float.NaN;
    private float[] scratch = new float[BUNDLE_STRIDE * 1024];
    private int size;

    /**
     * The vertices of one pass of the bundle beam drawn by the cell at {@code x, y, z}, {@link #BUNDLE_STRIDE} floats
     * each; do not modify.
     */
    float[] bundle(BundleBeam beam, int x, int y, int z, long tick, float partialTick, CableBeamStyle style,
            int detail, boolean glow) {
        advance(tick, partialTick);
        var key = new BundleKey(beam, parity(x, y, z), detail, glow, style);
        var layer = bundles.get(key);
        if (layer == null) {
            size = 0;
            BundleBeamMesh.emit(beam, x, y, z, tick, partialTick, style, detail, glow, this::record);
            layer = Arrays.copyOf(scratch, size);
            bundles.put(key, layer);
        }
        return layer;
    }

    /**
     * The vertices of one pass of the ordinary cell's plasma beam at {@code x, y, z}, {@link #BUNDLE_STRIDE} floats
     * each; do not modify.
     */
    float[] cell(int mask, int x, int y, int z, long tick, float partialTick, CableBeamStyle style, int detail,
            boolean glow) {
        advance(tick, partialTick);
        var key = new CellKey(mask, parity(x, y, z), detail, glow, style);
        var layer = cells.get(key);
        if (layer == null) {
            size = 0;
            BundleBeamMesh.emitCell(mask, x, y, z, tick, partialTick, style, detail, glow, this::record);
            layer = Arrays.copyOf(scratch, size);
            cells.put(key, layer);
        }
        return layer;
    }

    private void advance(long tick, float partialTick) {
        if (tick != this.tick || Float.floatToIntBits(partialTick) != Float.floatToIntBits(this.partialTick)) {
            bundles.clear();
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
        scratch[size++] = red;
        scratch[size++] = green;
        scratch[size++] = blue;
        scratch[size++] = alpha;
        scratch[size++] = nx;
        scratch[size++] = ny;
        scratch[size++] = nz;
    }
}
