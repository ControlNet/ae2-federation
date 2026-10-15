package space.controlnet.ae2federation.client;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * One frame's beams, shared by cables that look alike. A beam's pulses depend on a cable's position only through
 * whether each coordinate is odd or even, so within one frame every cable with the same connections, load and parity
 * draws the same vertices: they are computed once and replayed for the rest.
 */
final class CableBeamFrame {
    /** Per vertex: x, y, z within the block, red, green, blue, alpha. */
    static final int STRIDE = 7;

    private record Key(int mask, int parity, int detail, boolean glow, CableBeamStyle style) {
    }

    private final Map<Key, float[]> layers = new HashMap<>();
    private long tick = Long.MIN_VALUE;
    private float partialTick = Float.NaN;
    private float[] scratch = new float[STRIDE * 1024];
    private int size;

    /** The vertices of one pass of the beam at {@code x, y, z}, {@link #STRIDE} floats each; do not modify. */
    float[] layer(int mask, int x, int y, int z, long tick, float partialTick, CableBeamStyle style, int detail,
            boolean glow) {
        if (tick != this.tick || Float.floatToIntBits(partialTick) != Float.floatToIntBits(this.partialTick)) {
            layers.clear();
            this.tick = tick;
            this.partialTick = partialTick;
        }
        int parity = Math.floorMod(x, 2) | Math.floorMod(y, 2) << 1 | Math.floorMod(z, 2) << 2;
        var key = new Key(mask, parity, detail, glow, style);
        var layer = layers.get(key);
        if (layer == null) {
            size = 0;
            CableBeamMesh.emitLayer(mask, x, y, z, tick, partialTick, style, detail, glow, this::record);
            layer = Arrays.copyOf(scratch, size);
            layers.put(key, layer);
        }
        return layer;
    }

    private void record(boolean glow, float x, float y, float z, float red, float green, float blue, float alpha) {
        if (size + STRIDE > scratch.length) scratch = Arrays.copyOf(scratch, scratch.length * 2);
        scratch[size++] = x;
        scratch[size++] = y;
        scratch[size++] = z;
        scratch[size++] = red;
        scratch[size++] = green;
        scratch[size++] = blue;
        scratch[size++] = alpha;
    }
}
