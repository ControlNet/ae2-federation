package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.bundle.BundleBeam;

final class CableBeamFrameTest {
    @Test
    void bundlesThatLookAlikeShareOneBeamWithItsNormals() {
        var frame = new CableBeamFrame();
        var style = CableBeamStyle.fromLoad(.6);
        var beam = new BundleBeam(Direction.Axis.X, 3, 1);
        for (boolean glow : new boolean[] {true, false}) {
            var direct = new ArrayList<Float>();
            BundleBeamMesh.emit(beam, 4, 64, 2, 9, .25f, style, 0, glow, (x, y, z, r, g, b, a, nx, ny, nz) -> {
                for (float value : new float[] {x, y, z, r, g, b, a, nx, ny, nz}) direct.add(value);
            });
            var cached = new ArrayList<Float>();
            for (float value : frame.bundle(beam, 4, 64, 2, 9, .25f, style, 0, glow)) cached.add(value);
            assertEquals(direct, cached);
        }
        float[] first = frame.bundle(beam, 4, 64, 2, 9, .25f, style, 0, true);
        assertSame(first, frame.bundle(beam, 6, 66, 0, 9, .25f, style, 0, true));
        assertNotSame(first, frame.bundle(beam, 5, 64, 2, 9, .25f, style, 0, true), "a layer on, the lobes have moved");
        assertNotSame(first, frame.bundle(new BundleBeam(Direction.Axis.X, 3, 2), 4, 64, 2, 9, .25f, style, 0, true));
    }

    @Test
    void ordinaryCellsThatLookAlikeShareOnePlasmaBeamWithItsNormals() {
        var frame = new CableBeamFrame();
        var style = CableBeamStyle.fromLoad(.6);
        for (int mask : new int[] {3, 19, 63}) {
            for (boolean glow : new boolean[] {true, false}) {
                var direct = new ArrayList<Float>();
                BundleBeamMesh.emitCell(mask, 4, 64, 2, 9, .25f, style, 0, glow, (x, y, z, r, g, b, a, nx, ny, nz) -> {
                    for (float value : new float[] {x, y, z, r, g, b, a, nx, ny, nz}) direct.add(value);
                });
                var cached = new ArrayList<Float>();
                for (float value : frame.cell(mask, 4, 64, 2, 9, .25f, style, 0, glow)) cached.add(value);
                assertEquals(direct, cached);
            }
        }
        float[] first = frame.cell(3, 4, 64, 2, 9, .25f, style, 0, true);
        assertSame(first, frame.cell(3, 6, 66, 0, 9, .25f, style, 0, true));
        assertNotSame(first, frame.cell(3, 5, 64, 2, 9, .25f, style, 0, true), "a block on, the lobes have moved");
        assertNotSame(first, frame.cell(3, 4, 64, 2, 9, .5f, style, 0, true), "a new frame computes again");
    }
}
