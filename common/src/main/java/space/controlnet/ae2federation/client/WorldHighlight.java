package space.controlnet.ae2federation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import space.controlnet.ae2federation.client.policy.BlockMarks;

/**
 * Pulsing outlines around blocks the player asked to find from the workspace, for ten seconds. This is local
 * presentation of positions the menu already shows; it grants nothing and sends nothing to the server.
 */
public final class WorldHighlight {
    public static final long DURATION_MILLIS = 10_000;
    private static volatile Highlight current;

    private WorldHighlight() {
    }

    private record Highlight(String dimension, List<BlockMarks.Mark> blocks, int color, long expiresAt) {
    }

    /** Replaces any running highlight; only one set of blocks is outlined at a time. */
    public static void show(String dimension, List<BlockMarks.Mark> blocks, int color) {
        current = blocks.isEmpty() ? null
                : new Highlight(dimension, List.copyOf(blocks), color, System.currentTimeMillis() + DURATION_MILLIS);
    }

    /** Blocks outlined right now, for the workspace's own feedback and tests. */
    public static int activeBlocks() {
        var highlight = current;
        return highlight == null || highlight.expiresAt() < System.currentTimeMillis() ? 0 : highlight.blocks().size();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        var highlight = current;
        var level = Minecraft.getInstance().level;
        if (highlight == null || level == null) return;
        long now = System.currentTimeMillis();
        if (highlight.expiresAt() < now) {
            current = null;
            return;
        }
        if (!level.dimension().location().toString().equals(highlight.dimension())) return;
        float pulse = 0.55f + 0.45f * (float) Math.sin(now / 160.0);
        float red = (highlight.color() >> 16 & 0xff) / 255f;
        float green = (highlight.color() >> 8 & 0xff) / 255f;
        float blue = (highlight.color() & 0xff) / 255f;
        var camera = event.getCamera().getPosition();
        var pose = new PoseStack();
        pose.translate(-camera.x, -camera.y, -camera.z);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var lines = buffers.getBuffer(RenderType.lines());
        for (var block : highlight.blocks()) {
            var box = new AABB(block.x(), block.y(), block.z(), block.x() + 1, block.y() + 1, block.z() + 1).inflate(0.02);
            LevelRenderer.renderLineBox(pose, lines, box, red, green, blue, pulse);
        }
        buffers.endBatch(RenderType.lines());
    }
}
