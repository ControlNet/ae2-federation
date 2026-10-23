package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

final class BeamPortalSceneQueueTest {
    private final BeamPortalSceneQueue queue = new BeamPortalSceneQueue();
    private final ArrayList<BeamPortalSceneQueue.Entry> drawn = new ArrayList<>();

    private void record(Matrix4f pose) {
        queue.record(pose, Direction.NORTH, 17, .25f, CableBeamStyle.fromLoad(.6));
    }

    @Test
    void copiesPoseAndDrawsInputsOnceInOrder() {
        queue.inScene(() -> {
            var pose = new Matrix4f().translation(1, 2, 3);
            record(pose);
            pose.translation(4, 5, 6);
            record(pose);
            assertTrue(drawn.isEmpty());
            queue.draw(drawn::add);
            queue.draw(drawn::add);
        });
        assertEquals(2, drawn.size());
        var first = drawn.getFirst();
        assertEquals(new Matrix4f().translation(1, 2, 3), first.pose());
        assertEquals(new Matrix4f().translation(4, 5, 6), drawn.getLast().pose());
        assertEquals(Direction.NORTH, first.facing());
        assertEquals(17, first.tick());
        assertEquals(.25f, first.partialTick());
        assertEquals(CableBeamStyle.fromLoad(.6), first.style());
    }

    @Test
    void clearsWhenFrameNeverDrawsAndIgnoresUnscopedRecords() {
        queue.inScene(() -> record(new Matrix4f()));
        record(new Matrix4f());
        queue.draw(drawn::add);
        queue.inScene(() -> queue.draw(drawn::add));
        assertTrue(drawn.isEmpty());
    }

    @Test
    void clearsOnRenderAndDrawFailure() {
        assertThrows(IllegalStateException.class, () -> queue.inScene(() -> {
            record(new Matrix4f());
            throw new IllegalStateException("Scene interrupted before translucent terrain");
        }));
        queue.draw(drawn::add);
        queue.inScene(() -> {
            record(new Matrix4f());
            assertThrows(IllegalStateException.class, () -> queue.draw(entry -> {
                throw new IllegalStateException("Portal draw interrupted");
            }));
            queue.draw(drawn::add);
        });
        assertTrue(drawn.isEmpty());
    }
}
