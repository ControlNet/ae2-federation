package space.controlnet.ae2federation.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import space.controlnet.ae2federation.client.policy.BlockMarks;

/**
 * Blinking outlines around blocks the player asked to find from the workspace, each for ten seconds; several can run at
 * once, so two networks highlighted one after the other both stay outlined. This is local presentation of positions the
 * menu already shows; it grants nothing and sends nothing to the server.
 *
 * <p>The outlines are drawn once the level is done and ignore depth, so no block, fluid or weather hides them: only
 * the hand and the GUI draw over them. Each edge is a thick coloured line on a wider dark halo, which keeps it
 * readable against a sky, a lit wall or a block of the same colour.
 */
public final class WorldHighlight {
    public static final long DURATION_MILLIS = 10_000;
    /** Line widths in pixels at 1920 wide; a wider window scales them up, as vanilla's own lines do. */
    private static final float LINE_WIDTH = 6;
    private static final float HALO_WIDTH = 11;
    /**
     * Turns depth testing off. Vanilla's {@code NO_DEPTH_TEST} only skips turning it on and so relies on it being off
     * already, but rain leaves it on at {@code AFTER_LEVEL}, which hid the outlines behind blocks whenever it rained.
     * Off is the state vanilla expects between render types, so clearing leaves it off.
     */
    private static final RenderStateShard.DepthTestStateShard NO_DEPTH_TEST =
            new RenderStateShard.DepthTestStateShard("always", 519) {
                @Override
                public void setupRenderState() {
                    super.setupRenderState();
                    RenderSystem.disableDepthTest();
                }
            };
    private static final RenderType LINE = lines("ae2federation_highlight_line", LINE_WIDTH);
    private static final RenderType HALO = lines("ae2federation_highlight_halo", HALO_WIDTH);
    private static final int MAX_HIGHLIGHTS = 16;
    private static final space.controlnet.ae2federation.client.policy.HighlightSet<List<Group>> HIGHLIGHTS =
            new space.controlnet.ae2federation.client.policy.HighlightSet<>(DURATION_MILLIS, MAX_HIGHLIGHTS);

    private WorldHighlight() {
    }

    /** Blocks outlined in one colour, for example one part of a network whose identity is in doubt. */
    public record Group(List<BlockMarks.Mark> blocks, int color) {
        public Group {
            blocks = List.copyOf(blocks);
        }
    }

    /** Outlines {@code blocks} beside the highlights still running; the same blocks again restart their time. */
    public static void show(String dimension, List<BlockMarks.Mark> blocks, int color) {
        show(dimension, List.of(new Group(blocks, color)));
    }

    /** Outlines several groups, each in its own colour, as one highlight beside those still running. */
    public static void show(String dimension, List<Group> groups) {
        var shown = groups.stream().filter(group -> !group.blocks().isEmpty()).toList();
        if (shown.isEmpty()) return;
        HIGHLIGHTS.add(List.of(dimension, shown), dimension, shown, System.currentTimeMillis());
    }

    /** Ends every running highlight. */
    public static void clear() {
        HIGHLIGHTS.clear();
    }

    /** Blocks outlined right now, for the workspace's own feedback and tests. */
    public static int activeBlocks() {
        return HIGHLIGHTS.active(System.currentTimeMillis()).stream().flatMap(entry -> entry.value().stream())
                .mapToInt(group -> group.blocks().size()).sum();
    }

    /** Colour groups outlined right now, over all running highlights. */
    public static int activeGroups() {
        return HIGHLIGHTS.active(System.currentTimeMillis()).stream().mapToInt(entry -> entry.value().size()).sum();
    }

    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        long now = System.currentTimeMillis();
        var dimension = level.dimension().location().toString();
        // Each highlight blinks from its own start, so one just asked for is lit at once.
        var groups = HIGHLIGHTS.active(now).stream()
                .filter(entry -> entry.dimension().equals(dimension) && entry.lit(now))
                .flatMap(entry -> entry.value().stream()).toList();
        if (groups.isEmpty()) return;
        var camera = event.getCamera().getPosition();
        // The level's own model-view matrix is gone by now; the event still carries the camera's rotation.
        var pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        pose.translate(-camera.x, -camera.y, -camera.z);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var halo = buffers.getBuffer(HALO);
        for (var group : groups) {
            for (var block : group.blocks()) LevelRenderer.renderLineBox(pose, halo, box(block), 0.04f, 0.03f, 0.06f, 0.85f);
        }
        buffers.endBatch(HALO);
        var line = buffers.getBuffer(LINE);
        for (var group : groups) {
            float red = (group.color() >> 16 & 0xff) / 255f;
            float green = (group.color() >> 8 & 0xff) / 255f;
            float blue = (group.color() & 0xff) / 255f;
            for (var block : group.blocks()) LevelRenderer.renderLineBox(pose, line, box(block), red, green, blue, 1f);
        }
        buffers.endBatch(LINE);
    }

    private static AABB box(BlockMarks.Mark block) {
        return new AABB(block.x(), block.y(), block.z(), block.x() + 1, block.y() + 1, block.z() + 1).inflate(0.02);
    }

    /** Vanilla's line type, {@code width} pixels wide, drawn over everything: no depth test and no depth written. */
    private static RenderType lines(String name, float width) {
        var lineWidth = new RenderStateShard.LineStateShard(OptionalDouble.of(width)) {
            @Override
            public void setupRenderState() {
                super.setupRenderState();
                RenderSystem.lineWidth(Math.max(width, Minecraft.getInstance().getWindow().getWidth() / 1920f * width));
            }
        };
        return RenderType.create(name, DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1536,
                RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                        .setLineState(lineWidth)
                        .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                        .setDepthTestState(NO_DEPTH_TEST)
                        .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                        .setOutputState(RenderStateShard.MAIN_TARGET)
                        .setCullState(RenderStateShard.NO_CULL)
                        .createCompositeState(false));
    }
}
