package space.controlnet.ae2federation.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterRenderBuffersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import space.controlnet.ae2federation.bundle.BundleBeam;
import space.controlnet.ae2federation.router.FederationCableBlockEntity;
import space.controlnet.ae2federation.router.RouterRegistration;

/** Default M coherent optical carrier, soft moving packets, depth-tested light shoulders and in-shell fog. */
public final class CableFlowRenderer implements BlockEntityRenderer<FederationCableBlockEntity> {
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    // Entity translucent emissive still calls minecraft_mix_light and dims optical carriers.
    // Beacon's shader uses unlit vertex color, with world fog and normal scene depth testing.
    private static final RenderType CORE = RenderType.create("ae2federation_optical_core",
            DefaultVertexFormat.BLOCK, VertexFormat.Mode.QUADS, 1536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_BEACON_BEAM_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(WHITE, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    // The shell is translucent terrain, drawn after block entities. Without depth, any shell face
                    // behind the carrier (another arm, a neighbouring cable) would paint its frame over it.
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .createCompositeState(false));
    private static final RenderType GLOW = RenderType.create("ae2federation_optical_glow",
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

    /** World cables that look alike in a frame share one computed beam; only the render thread draws. */
    private static final CableBeamFrame FRAME = new CableBeamFrame();
    /** Room for a few hundred near cables before a buffer grows; it keeps what it grew to. */
    private static final int BATCH_BYTES = 1 << 20;

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(RouterRegistration.FEDERATION_CABLE_BLOCK_ENTITY.get(),
                context -> new CableFlowRenderer());
    }

    /**
     * Each pass gets a buffer of its own, glow first. In the shared buffer every cable's switch from glow to carrier
     * and back ended a batch: two draw calls per cable per frame. Now all cables' beams go out in two.
     */
    public static void registerBuffers(RegisterRenderBuffersEvent event) {
        event.registerRenderBuffer(GLOW, new ByteBufferBuilder(BATCH_BYTES));
        event.registerRenderBuffer(CORE, new ByteBufferBuilder(BATCH_BYTES));
    }

    /**
     * Draws the frame's beams as soon as the block entities are done, before translucent terrain: the carrier's depth
     * must be in place before the shells are drawn, in every graphics mode.
     */
    public static void flush(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) return;
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        buffers.endBatch(GLOW);
        buffers.endBatch(CORE);
    }

    @Override
    public void render(FederationCableBlockEntity cable, float partialTick, PoseStack stack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var level = cable.getLevel();
        if (level == null) return;
        var pos = cable.getBlockPos();
        double distance = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition()
                .distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5);
        // The beam shows the cable's capacity count against its limit, as the server counted it; not traffic.
        // The count changes only with the domain, so the alarm follows it directly and needs no hysteresis.
        double target = cable.capacityLoad();
        // An unpowered domain's beams alarm at the width of no load.
        var style = cable.unpowered() ? CableBeamStyle.fromLoad(0, true)
                : CableBeamStyle.fromLoad(cable.beamLoad(target, Util.getNanos()), target > 1);
        int mask = cable.flowMask(), detail = CableBeamMesh.detail(distance);
        long tick = level.getGameTime();
        // A 2x2 bundle draws one beam down its middle, over the box its corner cell names.
        var bundle = cable.bundleBeam();
        if (bundle != null) {
            if (bundle.length() > 0) renderBundle(cable, bundle, tick, partialTick, style, detail, stack, buffers);
            return;
        }
        emitCell(stack, buffers, mask, pos.getX(), pos.getY(), pos.getZ(), tick, partialTick, style, detail, false);
        endSceneBatch(level, buffers);
    }

    /**
     * One cell's beam, glow then carrier, into the world's batched buffers: {@code x, y, z} place its pulses, the pose
     * places and stretches its geometry.
     */
    static void emitCell(PoseStack stack, MultiBufferSource buffers, int mask, int x, int y, int z, long tick,
            float partialTick, CableBeamStyle style, int detail, boolean volume) {
        var pose = stack.last();
        var matrix = pose.pose();
        var normal = pose.transformNormal(0, 1, 0, new Vector3f());
        var position = new Vector3f();
        // Glow first: the carrier then writes depth over it without fighting the coplanar shoulders.
        for (int pass = 0; pass < 2; pass++) {
            var out = buffers.getBuffer(pass == 0 ? GLOW : CORE);
            float[] layer = FRAME.layer(mask, x, y, z, tick, partialTick, style, detail, pass == 0, volume);
            // The whole vertex in one call, which a block-format BufferBuilder writes straight to memory; the same
            // values the chained calls in draw() would write.
            for (int i = 0; i < layer.length; i += CableBeamFrame.STRIDE) {
                matrix.transformPosition(layer[i], layer[i + 1], layer[i + 2], position);
                int color = FastColor.ARGB32.color((int) (layer[i + 6] * 255), (int) (layer[i + 3] * 255),
                        (int) (layer[i + 4] * 255), (int) (layer[i + 5] * 255));
                out.addVertex(position.x(), position.y(), position.z(), color, .5f, .5f, OverlayTexture.NO_OVERLAY,
                        LightTexture.FULL_BRIGHT, normal.x(), normal.y(), normal.z());
            }
        }
    }

    /**
     * The 2x2 beam, in blocks from the corner cell. Its sheaths and halo fade toward their outlines as they turn from
     * the eye: the cached mesh carries normals, and the fade is taken here per vertex, since it depends on the view.
     */
    private static void renderBundle(FederationCableBlockEntity cable, BundleBeam bundle, long tick, float partialTick,
            CableBeamStyle style, int detail, PoseStack stack, MultiBufferSource buffers) {
        var pos = cable.getBlockPos();
        var pose = stack.last();
        var matrix = pose.pose();
        var normal = pose.transformNormal(0, 1, 0, new Vector3f());
        // From the block to the eye's space: there the eye sits at the origin, or looks down -Z in an orthographic
        // view, as a guidebook scene may use.
        var view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(matrix);
        var turn = view.normal(new Matrix3f());
        boolean orthographic = RenderSystem.getProjectionMatrix().m23() == 0;
        var position = new Vector3f();
        var eye = new Vector3f();
        var facing = new Vector3f();
        for (int pass = 0; pass < 2; pass++) {
            var out = buffers.getBuffer(pass == 0 ? GLOW : CORE);
            float[] layer = FRAME.bundle(bundle, pos.getX(), pos.getY(), pos.getZ(), tick, partialTick, style, detail,
                    pass == 0);
            for (int i = 0; i < layer.length; i += CableBeamFrame.BUNDLE_STRIDE) {
                float alpha = layer[i + 6];
                if (pass == 0) {
                    view.transformPosition(layer[i], layer[i + 1], layer[i + 2], eye);
                    turn.transform(layer[i + 7], layer[i + 8], layer[i + 9], facing).normalize();
                    if (orthographic) eye.set(0, 0, 1);
                    else eye.negate().normalize();
                    alpha = Math.min(1, alpha * BundleBeamMesh.fade(facing.x(), facing.y(), facing.z(), eye.x(),
                            eye.y(), eye.z()));
                }
                matrix.transformPosition(layer[i], layer[i + 1], layer[i + 2], position);
                int color = FastColor.ARGB32.color((int) (alpha * 255), (int) (layer[i + 3] * 255),
                        (int) (layer[i + 4] * 255), (int) (layer[i + 5] * 255));
                out.addVertex(position.x(), position.y(), position.z(), color, .5f, .5f, OverlayTexture.NO_OVERLAY,
                        LightTexture.FULL_BRIGHT, normal.x(), normal.y(), normal.z());
            }
        }
        endSceneBatch(cable.getLevel(), buffers);
    }

    /**
     * A guidebook scene ends these buffers only after its terrain, once its camera is gone, so the beam came out with
     * no depth against the scene, over every ring and rail. Drawn now, before the scene's terrain, it sits in its shell
     * as in the world.
     */
    static void endSceneBatch(Level level, MultiBufferSource buffers) {
        if (!(level instanceof ClientLevel) && buffers instanceof MultiBufferSource.BufferSource source) {
            source.endBatch(GLOW);
            source.endBatch(CORE);
        }
    }

    /** A 2x2 bundle's middle beam reaches past its corner cell, over the box it is stretched to. */
    @Override
    public AABB getRenderBoundingBox(FederationCableBlockEntity cable) {
        var box = new AABB(cable.getBlockPos());
        var bundle = cable.bundleBeam();
        if (bundle == null || bundle.length() == 0) return box;
        return box.expandTowards(bundle.extent(Direction.Axis.X) - 1, bundle.extent(Direction.Axis.Y) - 1,
                bundle.extent(Direction.Axis.Z) - 1);
    }

    /** The visual-input seam shared by world cables and native renderer previews; no gameplay effects. */
    public static void draw(PoseStack stack, MultiBufferSource buffers, int mask, int x, int y, int z,
            long tick, float partialTick, CableBeamStyle style, int detail) {
        var pose = stack.last();
        // In the world each pass has its own buffer (registerBuffers). Elsewhere, as in previews, switching RenderTypes
        // can finish the prior shared buffer, so each pass is complete before the next starts.
        // Glow first: the carrier then writes depth over it without fighting the coplanar shoulders.
        for (int pass = 0; pass < 2; pass++) {
            var out = buffers.getBuffer(pass == 0 ? GLOW : CORE);
            CableBeamMesh.emitLayer(mask, x, y, z, tick, partialTick, style, detail, pass == 0,
                (additive, px, py, pz, red, green, blue, alpha) -> {
                    out.addVertex(pose, px, py, pz).setColor(red, green, blue, alpha)
                            .setUv(.5f, .5f)
                            .setLight(LightTexture.FULL_BRIGHT).setNormal(pose, 0, 1, 0);
                });
        }
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
