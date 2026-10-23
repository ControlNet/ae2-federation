package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.util.FastColor;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.bundle.BundleBeam;

final class CableBeamFrameTest {
    /** A pass as the frame keeps it, from the mesh's own vertices: see {@link CableBeamFrame#BUNDLE_STRIDE}. */
    private static List<Float> packed(List<float[]> vertices) {
        var out = new ArrayList<Float>();
        for (float[] v : vertices) {
            float nx = v[7], ny = v[8], nz = v[9];
            int colour = FastColor.ARGB32.color(0, (int) (v[3] * 255), (int) (v[4] * 255), (int) (v[5] * 255));
            for (float value : new float[] {v[0], v[1], v[2], nx, ny, nz, nx * nx + ny * ny + nz * nz, v[6],
                    Float.intBitsToFloat(colour)}) {
                out.add(value);
            }
        }
        return out;
    }

    private static List<Float> list(float[] layer) {
        var out = new ArrayList<Float>();
        for (float value : layer) out.add(value);
        return out;
    }

    private static List<Float> bundle(BundleBeam beam, int x, int y, int z, long tick, float partial,
            CableBeamStyle style, boolean glow) {
        var vertices = new ArrayList<float[]>();
        BundleBeamMesh.emit(beam, x, y, z, tick, partial, style, 0, glow,
                (px, py, pz, r, g, b, a, nx, ny, nz) -> vertices.add(new float[] {px, py, pz, r, g, b, a, nx, ny, nz}));
        return packed(vertices);
    }

    @Test
    void bundlesThatLookAlikeShareOneBeamWithItsNormals() {
        var frame = new CableBeamFrame();
        var style = CableBeamStyle.fromLoad(.6);
        var beam = new BundleBeam(Direction.Axis.X, 3, 1);
        for (boolean glow : new boolean[] {true, false}) {
            assertEquals(bundle(beam, 4, 64, 2, 9, .25f, style, glow),
                    list(frame.bundle(beam, 4, 64, 2, 9, .25f, style, 0, glow)));
        }
        float[] first = frame.bundle(beam, 4, 64, 2, 9, .25f, style, 0, true);
        assertSame(first, frame.bundle(beam, 6, 66, 0, 9, .25f, style, 0, true));
        assertSame(first, frame.bundle(beam, 6, 65, 1, 9, .25f, style, 0, true), "a run along X reads x only");
        assertNotSame(first, frame.bundle(beam, 5, 64, 2, 9, .25f, style, 0, true), "a layer on, the lobes have moved");
        assertNotSame(first, frame.bundle(new BundleBeam(Direction.Axis.X, 3, 2), 4, 64, 2, 9, .25f, style, 0, true));
        float[] core = frame.bundle(beam, 4, 64, 2, 9, .25f, style, 0, false);
        assertSame(core, frame.bundle(beam, 5, 65, 3, 9, .25f, style, 0, false), "the core is an even tube");
    }

    @Test
    void ordinaryCellsThatLookAlikeShareOnePlasmaBeamWithItsNormals() {
        var frame = new CableBeamFrame();
        var style = CableBeamStyle.fromLoad(.6);
        for (int mask : new int[] {3, 19, 63}) {
            for (boolean glow : new boolean[] {true, false}) {
                var vertices = new ArrayList<float[]>();
                BundleBeamMesh.emitCell(mask, 4, 64, 2, 9, .25f, style, 0, glow,
                        (x, y, z, r, g, b, a, nx, ny, nz) -> vertices.add(new float[] {x, y, z, r, g, b, a, nx, ny, nz}));
                assertEquals(packed(vertices), list(frame.cell(mask, 4, 64, 2, 9, .25f, style, 0, glow)));
            }
        }
        float[] first = frame.cell(3, 4, 64, 2, 9, .25f, style, 0, true);
        assertSame(first, frame.cell(3, 6, 66, 0, 9, .25f, style, 0, true));
        assertNotSame(first, frame.cell(3, 5, 64, 2, 9, .25f, style, 0, true), "a block on, the lobes have moved");
    }

    @Test
    void aNewFrameComputesAgainInTheLastFramesArrays() {
        var frame = new CableBeamFrame();
        var style = CableBeamStyle.fromLoad(.6);
        var beam = new BundleBeam(Direction.Axis.X, 3, 1);
        float[] first = frame.bundle(beam, 4, 64, 2, 9, .25f, style, 0, true);
        var before = list(first);
        float[] next = frame.bundle(beam, 4, 64, 2, 9, .5f, style, 0, true);
        assertEquals(bundle(beam, 4, 64, 2, 9, .5f, style, true), list(next), "a new frame computes again");
        assertNotEquals(before, list(next), "the lobes have moved");
        assertSame(first, next, "into the array the beam had, rather than a new one every frame");
        // A beam no frame has drawn since is let go; one of another shape gets an array of its own.
        float[] other = frame.bundle(new BundleBeam(Direction.Axis.X, 3, 2), 4, 64, 2, 9, .75f, style, 0, true);
        assertNotSame(next, other);
        assertEquals(bundle(new BundleBeam(Direction.Axis.X, 3, 2), 4, 64, 2, 9, .75f, style, true), list(other));
    }
}
