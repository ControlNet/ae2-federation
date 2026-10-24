package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** The artist's rings, shared out between the cells of their two- or three-block frame. */
final class LargeCableRingsTest {
    @Test
    void everyBoxIsDrawnByExactlyOneCellWhicheverWayTheRingTurns() {
        for (var model : List.of(LargeCableRingModels.STRAIGHT, LargeCableRingModels.CORNER,
                LargeCableRingModels.HUGE_STRAIGHT, LargeCableRingModels.HUGE_CORNER)) {
            int cells = model.cells();
            for (var modelAxis : List.of(Direction.Axis.Y, Direction.Axis.Z)) {
                for (var worldAxis : Direction.Axis.values()) {
                    var rotation = LargeCableRings.rotation(modelAxis, worldAxis);
                    var drawn = new HashMap<LargeCableRingModels.Element, Integer>();
                    for (int x = 0; x < cells; x++) {
                        for (int y = 0; y < cells; y++) {
                            for (int z = 0; z < cells; z++) {
                                for (var box : LargeCableRings.boxes(model, rotation, new int[] {x, y, z})) {
                                    drawn.merge(box.element(), 1, Integer::sum);
                                }
                            }
                        }
                    }
                    assertEquals(model.elements().size(), drawn.size(), modelAxis + " onto " + worldAxis);
                    drawn.values().forEach(count -> assertEquals(1, count));
                }
            }
        }
    }

    @Test
    void the3x3RingsCentreOnTheMiddleCell() {
        // The straight ring stands inside the middle layer; the corner rings straddle the middle layer's boundaries.
        float[] low = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        float[] high = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        for (var element : LargeCableRingModels.HUGE_STRAIGHT.elements()) {
            if (element.turn() != null) continue;
            int[] from = {element.x0(), element.y0(), element.z0()};
            int[] to = {element.x1(), element.y1(), element.z1()};
            for (int i = 0; i < 3; i++) {
                low[i] = Math.min(low[i], from[i]);
                high[i] = Math.max(high[i], to[i]);
            }
        }
        for (int i = 0; i < 3; i++) assertEquals(48, low[i] + high[i], "axis " + i);
        for (var element : LargeCableRingModels.HUGE_STRAIGHT.elements()) {
            assertEquals(1, LargeCableRings.home(element, 2)[2], "in the middle layer along the run");
        }
        // The corner rings: one about y = 16 and one about y = 32, the middle layer's boundaries.
        int[] below = {Integer.MAX_VALUE, Integer.MIN_VALUE};
        int[] above = {Integer.MAX_VALUE, Integer.MIN_VALUE};
        for (var element : LargeCableRingModels.HUGE_CORNER.elements()) {
            var ring = element.y0() + element.y1() < 48 ? below : above;
            ring[0] = Math.min(ring[0], element.y0());
            ring[1] = Math.max(ring[1], element.y1());
        }
        assertEquals(32, below[0] + below[1]);
        assertEquals(64, above[0] + above[1]);
    }
}
