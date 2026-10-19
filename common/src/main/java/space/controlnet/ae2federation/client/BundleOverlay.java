package space.controlnet.ae2federation.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Boxes drawn in the world while a player forms, extends or inspects a bundle, in the manner of Create's super glue
 * selection: solid edges a fraction of a block thick, and faces filled with a faint colour and diagonal stripes. They
 * are depth tested, so walls hide them as they hide the cables.
 */
public final class BundleOverlay {
    private static final ResourceLocation HATCH =
            ResourceLocation.fromNamespaceAndPath("ae2federation", "textures/misc/bundle_selection_hatch.png");
    /** Boxes stand this far outside the blocks they enclose, so their faces do not fight the cables' own. */
    private static final double INFLATE = 1 / 32d;
    private static final RenderType EDGES = RenderType.create("ae2federation_bundle_edges",
            DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 4096, RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.POSITION_COLOR_SHADER)
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));
    private static final RenderType FACES = RenderType.create("ae2federation_bundle_faces",
            DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS, 4096, RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(
                            net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader))
                    .setTextureState(new RenderStateShard.TextureStateShard(HATCH, false, false))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .createCompositeState(false));

    private BundleOverlay() {
    }

    /**
     * One box over whole blocks: {@code edge} is the edge thickness in blocks, {@code hatched} fills the faces, and
     * {@code alpha} fades the whole box.
     */
    public record Box(AABB blocks, int color, float edge, boolean hatched, float alpha) {
    }

    public static void render(RenderLevelStageEvent event, List<Box> boxes) {
        if (boxes.isEmpty()) return;
        var camera = event.getCamera().getPosition();
        var pose = new PoseStack();
        pose.mulPose(event.getModelViewMatrix());
        pose.translate(-camera.x, -camera.y, -camera.z);
        var buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        var faces = buffers.getBuffer(FACES);
        for (var box : boxes) {
            if (box.hatched()) faces(pose, faces, box.blocks().inflate(INFLATE), box.color(), box.alpha());
        }
        buffers.endBatch(FACES);
        var edges = buffers.getBuffer(EDGES);
        for (var box : boxes) edges(pose, edges, box.blocks().inflate(INFLATE), box.edge(), box.color(), box.alpha());
        buffers.endBatch(EDGES);
    }

    /** The six faces, the hatch repeating once per block. */
    private static void faces(PoseStack pose, VertexConsumer out, AABB box, int color, float alpha) {
        float r = red(color), g = green(color), b = blue(color);
        var matrix = pose.last().pose();
        double x0 = box.minX, y0 = box.minY, z0 = box.minZ, x1 = box.maxX, y1 = box.maxY, z1 = box.maxZ;
        float dx = (float) (x1 - x0), dy = (float) (y1 - y0), dz = (float) (z1 - z0);
        double[][][] quads = {
            {{x0, y0, z0}, {x0, y1, z0}, {x0, y1, z1}, {x0, y0, z1}},
            {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}},
            {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}},
            {{x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}},
            {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}},
            {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}},
        };
        float[][] spans = {{dy, dz}, {dy, dz}, {dx, dz}, {dx, dz}, {dx, dy}, {dx, dy}};
        float[][] uv = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int face = 0; face < quads.length; face++) {
            for (int corner = 0; corner < 4; corner++) {
                var p = quads[face][corner];
                out.addVertex(matrix, (float) p[0], (float) p[1], (float) p[2])
                        .setUv(uv[corner][0] * spans[face][0], uv[corner][1] * spans[face][1])
                        .setColor(r, g, b, alpha);
            }
        }
    }

    /** The twelve edges, each a square bar {@code thickness} wide centred on the edge. */
    private static void edges(PoseStack pose, VertexConsumer out, AABB box, float thickness, int color, float alpha) {
        double h = thickness / 2;
        double[] xs = {box.minX, box.maxX};
        double[] ys = {box.minY, box.maxY};
        double[] zs = {box.minZ, box.maxZ};
        for (double y : ys) {
            for (double z : zs) bar(pose, out, new AABB(box.minX - h, y - h, z - h, box.maxX + h, y + h, z + h), color, alpha);
        }
        for (double x : xs) {
            for (double z : zs) bar(pose, out, new AABB(x - h, box.minY - h, z - h, x + h, box.maxY + h, z + h), color, alpha);
        }
        for (double x : xs) {
            for (double y : ys) bar(pose, out, new AABB(x - h, y - h, box.minZ - h, x + h, y + h, box.maxZ + h), color, alpha);
        }
    }

    private static void bar(PoseStack pose, VertexConsumer out, AABB bar, int color, float alpha) {
        var m = pose.last().pose();
        float r = red(color), g = green(color), b = blue(color);
        float x0 = (float) bar.minX, y0 = (float) bar.minY, z0 = (float) bar.minZ;
        float x1 = (float) bar.maxX, y1 = (float) bar.maxY, z1 = (float) bar.maxZ;
        // Flat colour on every face, as Create draws its selection, so the bar reads in the colour it names.
        quad(out, m, r, g, b, alpha, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        quad(out, m, r, g, b, alpha, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0);
        quad(out, m, r, g, b, alpha, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        quad(out, m, r, g, b, alpha, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1);
        quad(out, m, r, g, b, alpha, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1);
        quad(out, m, r, g, b, alpha, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
    }

    private static void quad(VertexConsumer out, org.joml.Matrix4f m, float r, float g, float b, float a, float... p) {
        for (int i = 0; i < 12; i += 3) out.addVertex(m, p[i], p[i + 1], p[i + 2]).setColor(r, g, b, a);
    }

    private static float red(int color) {
        return (color >> 16 & 0xff) / 255f;
    }

    private static float green(int color) {
        return (color >> 8 & 0xff) / 255f;
    }

    private static float blue(int color) {
        return (color & 0xff) / 255f;
    }
}
