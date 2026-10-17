package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class CableBeamFrameTest {
    private static List<Float> direct(int mask, int x, int y, int z, long tick, float partial, CableBeamStyle style,
            int detail, boolean glow) {
        var out = new ArrayList<Float>();
        CableBeamMesh.emitLayer(mask, x, y, z, tick, partial, style, detail, glow, (g, px, py, pz, r, gr, b, a) -> {
            out.add(px); out.add(py); out.add(pz); out.add(r); out.add(gr); out.add(b); out.add(a);
        });
        return out;
    }

    private static List<Float> cached(CableBeamFrame frame, int mask, int x, int y, int z, long tick, float partial,
            CableBeamStyle style, int detail, boolean glow) {
        var out = new ArrayList<Float>();
        float[] layer = frame.layer(mask, x, y, z, tick, partial, style, detail, glow, false);
        for (float value : layer) out.add(value);
        return out;
    }

    @Test
    void cablesThatLookAlikeShareOneBeamAndEachMatchesItsOwn() {
        var frame = new CableBeamFrame();
        var styles = List.of(CableBeamStyle.fromLoad(0), CableBeamStyle.fromLoad(.6), CableBeamStyle.fromLoad(1.25));
        int[][] cells = {{0, 64, 0}, {2, 64, 0}, {1, 64, 0}, {-3, 65, 8}, {7, 65, -2}, {4, 71, 9}};
        for (long tick = 0; tick < 3; tick++) {
            for (float partial : new float[] {0, .5f}) {
                for (var style : styles) {
                    for (int mask : new int[] {3, 5, 23, 63}) {
                        for (int detail = 0; detail < 3; detail++) {
                            for (boolean glow : new boolean[] {true, false}) {
                                for (var cell : cells) {
                                    assertEquals(direct(mask, cell[0], cell[1], cell[2], tick, partial, style, detail, glow),
                                            cached(frame, mask, cell[0], cell[1], cell[2], tick, partial, style, detail,
                                                    glow));
                                }
                            }
                        }
                    }
                }
            }
        }
        // Cells two blocks apart on every axis carry the same pulses, so the second is a lookup.
        var style = CableBeamStyle.fromLoad(.6);
        float[] first = frame.layer(3, 10, 64, 10, 9, .25f, style, 0, true, false);
        assertSame(first, frame.layer(3, 12, 66, -4, 9, .25f, style, 0, true, false));
        assertNotSame(first, frame.layer(3, 11, 64, 10, 9, .25f, style, 0, true, false), "the pulses are half a cycle on");
        assertNotSame(first, frame.layer(3, 10, 64, 10, 9, .5f, style, 0, true, false), "a new frame computes again");
    }
}
