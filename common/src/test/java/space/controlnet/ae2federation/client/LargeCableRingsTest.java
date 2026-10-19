package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.List;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

/** The artist's rings, shared out between the eight cells of their two-block frame. */
final class LargeCableRingsTest {
    @Test
    void everyBoxIsDrawnByExactlyOneCellWhicheverWayTheRingTurns() {
        for (var model : List.of(LargeCableRingModels.STRAIGHT, LargeCableRingModels.CORNER)) {
            for (var modelAxis : List.of(Direction.Axis.Y, Direction.Axis.Z)) {
                for (var worldAxis : Direction.Axis.values()) {
                    var rotation = LargeCableRings.rotation(modelAxis, worldAxis);
                    var drawn = new HashMap<LargeCableRingModels.Element, Integer>();
                    for (int octant = 0; octant < 8; octant++) {
                        for (var box : LargeCableRings.boxes(model, rotation, octant)) {
                            drawn.merge(box.element(), 1, Integer::sum);
                        }
                    }
                    assertEquals(model.size(), drawn.size(), modelAxis + " onto " + worldAxis);
                    drawn.values().forEach(count -> assertEquals(1, count));
                }
            }
        }
    }
}
