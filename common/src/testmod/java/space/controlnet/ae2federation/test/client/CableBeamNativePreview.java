package space.controlnet.ae2federation.test.client;

import space.controlnet.ae2federation.client.CableFlowRenderer;
import space.controlnet.ae2federation.client.CableBeamStyle;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderStateShard;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import space.controlnet.ae2federation.router.CableCoreFaces;
import space.controlnet.ae2federation.router.CableVisualConnections;

/** Native GPU previews of the production draw method and shell builder. Testmod only, no synthetic assets. */
public final class CableBeamNativePreview {
    public static final String OUTPUT = "ae2federation.beamPreview.out";
    private static boolean written;
    private static final RenderType SHELL = RenderType.create("ae2federation_preview_shell",
            DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 1536, false, true,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_TRANSLUCENT_EMISSIVE_SHADER)
                    .setTextureState(new RenderStateShard.TextureStateShard(TextureAtlas.LOCATION_BLOCKS, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.CULL)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(false));

    private CableBeamNativePreview() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(CableBeamNativePreview::rendered);
    }

    private static void rendered(ScreenEvent.Render.Post event) {
        var mc = Minecraft.getInstance();
        if (written || mc.getOverlay() != null || !(event.getScreen() instanceof TitleScreen)) return;
        written = true;
        Path directory = Path.of(System.getProperty(OUTPUT));
        try {
            Files.createDirectories(directory);
            int[] loads = {0, 25, 60, 90, 100, 125};
            for (int load : loads) {
                for (int tick : new int[] {0, 16}) {
                    try (var image = render(mc, load / 100.0, 3, tick, true)) {
                        if (load == 60 && tick == 16) verifyBrightCarrier(image);
                        image.writeToFile(directory.resolve("load-" + load + "-tick-" + tick + ".png"));
                    }
                }
            }
            try (var image = render(mc, .6, 3, 16, true, true)) {
                image.writeToFile(directory.resolve("medium-same-view.png"));
            }
            try (var image = render(mc, .6, 3, 16, true, false, true)) {
                image.writeToFile(directory.resolve("opaque-occlusion.png"));
            }
            for (int load : new int[] {60, 125}) {
                var frames = directory.resolve("animation-" + load);
                Files.createDirectories(frames);
                for (int tick = 0; tick < 64; tick++) {
                    try (var image = render(mc, load / 100.0, 3, tick, true)) {
                        image.writeToFile(frames.resolve(String.format("%03d.png", tick)));
                    }
                }
            }
            for (int mask : new int[] {0, 5, 19, 63}) {
                try (var image = render(mc, .6, mask, 16, true)) {
                    image.writeToFile(directory.resolve("mask-" + mask + ".png"));
                }
            }
            try (var image = render(mc, .6, 3, 16, false)) {
                image.writeToFile(directory.resolve("shell-only.png"));
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        LogUtils.getLogger().info("AE2F_BEAM_PREVIEW_OK path={} loads=6 topologies=5", directory);
        mc.stop();
    }

    private static void verifyBrightCarrier(NativeImage image) {
        int bright = 0;
        for (int y = 0; y < image.getHeight(); y++) for (int x = 0; x < image.getWidth(); x++) {
            int pixel = image.getPixelRGBA(x, y);
            int red = pixel & 255, green = (pixel >>> 8) & 255, blue = (pixel >>> 16) & 255;
            if (blue > 220 && green > 190 && blue - red > 35) bright++;
        }
        if (bright < 1000) throw new IllegalStateException("Optical carrier lost its unlit cyan intensity: " + bright);
        LogUtils.getLogger().info("AE2F_BEAM_BRIGHTNESS_OK pixels={}", bright);
    }

    @SuppressWarnings("unchecked")
    private static java.util.List<net.minecraft.client.renderer.block.model.BakedQuad> shellQuads(
            java.lang.reflect.Method build, Object builder, int connections) throws ReflectiveOperationException {
        return (java.util.List<net.minecraft.client.renderer.block.model.BakedQuad>) build.invoke(builder, connections);
    }

    private record Placement(int x, int y, int z, int mask) {}

    private static NativeImage render(Minecraft mc, double load, int mask, int tick, boolean beam) {
        return render(mc, load, mask, tick, beam, false);
    }

    private static NativeImage render(Minecraft mc, double load, int mask, int tick, boolean beam, boolean oldView) {
        return render(mc, load, mask, tick, beam, oldView, false);
    }

    private static NativeImage render(Minecraft mc, double load, int mask, int tick, boolean beam,
            boolean oldView, boolean occluded) {
        var target = new TextureTarget(1200, 420, true, Minecraft.ON_OSX);
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        var buffers = mc.renderBuffers().bufferSource();
        try {
            target.setClearColor(.055f, .065f, .08f, 1);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            float zoom = mask == 3 ? (oldView ? 1 : .78f) : 1.6f;
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(-3.5f * zoom, 3.5f * zoom,
                    -1.225f * zoom, 1.225f * zoom, -20, 20),
                    VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.enableDepthTest();
            var pose = new PoseStack();
            pose.mulPose(new Quaternionf().rotateX(oldView ? .16f : .28f).rotateY(oldView ? -.16f : -.35f));
            pose.translate(-.5, -.5, -.5);
            var placements = new ArrayList<Placement>();
            if (mask == 3 || mask == 19 || mask == 63) {
                for (int x = -2; x <= 2; x++) placements.add(new Placement(x, 0, 0, x == 0 ? mask : 3));
                if ((mask & 16) != 0) placements.add(new Placement(0, 0, 1, 32));
                if ((mask & 32) != 0) placements.add(new Placement(0, 0, -1, 16));
                if ((mask & 4) != 0) placements.add(new Placement(0, 1, 0, 8));
                if ((mask & 8) != 0) placements.add(new Placement(0, -1, 0, 4));
            } else {
                placements.add(new Placement(0, 0, 0, mask));
                if (mask == 5) {
                    placements.add(new Placement(1, 0, 0, 2));
                    placements.add(new Placement(0, 1, 0, 8));
                }
            }
            if (occluded) {
                // Explicit test fixture: opaque plane in front of the carrier, writing scene depth.
                var wall = buffers.getBuffer(RenderType.beaconBeam(
                        ResourceLocation.withDefaultNamespace("textures/misc/white.png"), false));
                for (float[] vertex : new float[][] {{.15f,.1f,1}, {.85f,.1f,1}, {.85f,.9f,1}, {.15f,.9f,1}}) {
                    wall.addVertex(pose.last(), vertex[0], vertex[1], vertex[2]).setColor(.18f,.2f,.23f,1)
                            .setUv(.5f,.5f).setLight(LightTexture.FULL_BRIGHT).setNormal(pose.last(),0,0,1);
                }
                buffers.endBatch();
            }
            if (beam) {
                for (var p : placements) {
                    pose.pushPose(); pose.translate(p.x, p.y, p.z);
                    CableFlowRenderer.draw(pose, buffers, p.mask, p.x, p.y, p.z, tick, 0,
                            CableBeamStyle.fromLoad(load), 0);
                    pose.popPose();
                }
                buffers.endBatch();
            }
            if (beam && !oldView && !occluded && load == .6 && mask == 3 && tick == 16) {
                try (var diagnostic = new NativeImage(1200, 420, false)) {
                    RenderSystem.bindTexture(target.getColorTextureId());
                    diagnostic.downloadTexture(0, true); diagnostic.flipY();
                    diagnostic.writeToFile(Path.of(System.getProperty(OUTPUT)).resolve("beam-only.png"));
                } catch (IOException exception) { throw new UncheckedIOException(exception); }
            }
            var atlas = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
            var cores = new EnumMap<CableCoreFaces.Variant, TextureAtlasSprite>(CableCoreFaces.Variant.class);
            for (var variant : CableCoreFaces.Variant.values()) cores.put(variant, atlas.apply(
                    ResourceLocation.fromNamespaceAndPath("ae2federation", "part/cable/dense/" + variant.texture())));
            var builderType = Class.forName("space.controlnet.ae2federation.client.FederationCableBuilder");
            var constructor = builderType.getDeclaredConstructor(java.util.Map.class, TextureAtlasSprite.class);
            constructor.setAccessible(true);
            var builder = constructor.newInstance(cores, atlas.apply(
                    ResourceLocation.fromNamespaceAndPath("ae2federation", "part/cable/dense/line")));
            var build = builderType.getDeclaredMethod("build", int.class);
            build.setAccessible(true);
            // Fully lit native asset preview, with the same transparent shell textures and quads as the world.
            var shell = buffers.getBuffer(SHELL);
            for (var p : placements) {
                int connections = 0;
                for (int bit = 0; bit < 6; bit++) if ((p.mask & (1 << bit)) != 0) {
                    connections |= CableVisualConnections.CABLE << (bit * 2);
                }
                pose.pushPose(); pose.translate(p.x, p.y, p.z);
                for (var quad : shellQuads(build, builder, connections)) shell.putBulkData(pose.last(), quad, 1, 1, 1, 1,
                        LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            }
            buffers.endBatch();
            var image = new NativeImage(1200, 420, false);
            RenderSystem.bindTexture(target.getColorTextureId());
            image.downloadTexture(0, true); image.flipY();
            return image;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot build native cable shell", exception);
        } finally {
            RenderSystem.setProjectionMatrix(projection, sorting);
            modelView.popMatrix(); RenderSystem.applyModelViewMatrix();
            target.destroyBuffers(); mc.getMainRenderTarget().bindWrite(true);
        }
    }
}
