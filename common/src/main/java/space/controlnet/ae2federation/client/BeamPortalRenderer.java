package space.controlnet.ae2federation.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/** Fullbright vanilla beacon shading, with the depth disc submitted before additive light. */
public final class BeamPortalRenderer {
    private static final BeamPortalSceneQueue SCENE = new BeamPortalSceneQueue();
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    // Entity translucent emissive still calls minecraft_mix_light and dims optical carriers.
    // Beacon's shader uses unlit vertex color, with world fog and normal scene depth testing.
    private static final RenderType DISC = RenderType.create("ae2federation_portal_disc",
            DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 1536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_BEACON_BEAM_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(WHITE, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    private static final RenderType GLOW = RenderType.create("ae2federation_portal_glow",
            DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 1536, false, false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_BEACON_BEAM_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(WHITE, false, false))
                    .setTransparencyState(new RenderStateShard.TransparencyStateShard("optical_additive", () -> {
                        RenderSystem.enableBlend();
                        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
                    }, () -> {
                        RenderSystem.disableBlend();
                        RenderSystem.defaultBlendFunc();
                    }))
                    .setCullState(RenderStateShard.NO_CULL)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private BeamPortalRenderer() {}

    public static void inScene(Runnable render) {
        SCENE.inScene(render);
    }

    static void recordScene(PoseStack stack, Direction facing, long tick, float partialTick, CableBeamStyle style) {
        SCENE.record(stack.last().pose(), facing, tick, partialTick, style);
    }

    public static void drawScene(MultiBufferSource.BufferSource buffers) {
        SCENE.draw(entry -> {
            var stack = new PoseStack();
            stack.last().pose().set(entry.pose());
            draw(stack, buffers, entry.facing(), 0, entry.tick(), entry.partialTick(), entry.style());
        });
    }

    static void draw(PoseStack stack, MultiBufferSource buffers, Direction facing, int detail, long tick,
            float partialTick, CableBeamStyle style) {
        pass(stack, buffers, facing, detail, tick, partialTick, style, false);
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(DISC);
        pass(stack, buffers, facing, detail, tick, partialTick, style, true);
        if (buffers instanceof MultiBufferSource.BufferSource source) source.endBatch(GLOW);
    }

    private static void pass(PoseStack stack, MultiBufferSource buffers, Direction facing, int detail, long tick,
            float partialTick, CableBeamStyle style, boolean glow) {
        var out = buffers.getBuffer(glow ? GLOW : DISC);
        var pose = stack.last();
        BeamPortalMesh.Sink sink = (x, y, z, r, g, b, alpha) -> out.addVertex(pose.pose(), x, y, z)
                .setColor(r, g, b, alpha).setUv(0, 0).setUv2(240, 240).setNormal(0, 1, 0);
        BeamPortalMesh.emit(true, 2, detail, tick, partialTick, style, glow,
                (x, y, z, r, g, b, alpha) -> BeamPortalMesh.oriented(facing, sink, x, y, z, r, g, b, alpha));
    }
}
