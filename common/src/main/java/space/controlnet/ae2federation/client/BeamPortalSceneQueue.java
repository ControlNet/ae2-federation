package space.controlnet.ae2federation.client;

import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/** Render-call-scoped portal inputs, independent of GPU state. */
final class BeamPortalSceneQueue {
    record Entry(Matrix4f pose, Direction facing, int side, long tick, float partialTick, CableBeamStyle style) {}

    private final ArrayList<Entry> entries = new ArrayList<>();
    private boolean active;

    void inScene(Runnable render) {
        entries.clear();
        active = true;
        try {
            render.run();
        } finally {
            active = false;
            entries.clear();
        }
    }

    void record(Matrix4f pose, Direction facing, int side, long tick, float partialTick, CableBeamStyle style) {
        if (active) entries.add(new Entry(new Matrix4f(pose), facing, side, tick, partialTick, style));
    }

    void draw(Consumer<Entry> draw) {
        try {
            entries.forEach(draw);
        } finally {
            entries.clear();
        }
    }
}
