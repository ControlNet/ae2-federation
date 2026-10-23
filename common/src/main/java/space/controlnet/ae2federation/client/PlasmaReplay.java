package space.controlnet.ae2federation.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Writes one pass of a plasma beam the frame has computed ({@link CableBeamFrame}) into a buffer: the glow fades by
 * view ({@link PlasmaFade}), the core keeps its alpha. Every cable that draws runs this for every vertex, every frame.
 */
final class PlasmaReplay {
    private static final int STRIDE = CableBeamFrame.BUNDLE_STRIDE;

    private PlasmaReplay() {
    }

    /**
     * In the world: the pose only moves the beam by {@code ox, oy, oz} to the camera, and the model-view only turns,
     * an orthographic one looking along {@code ex, ey, ez}.
     */
    static void moved(float[] layer, boolean fade, float ox, float oy, float oz, boolean orthographic, float ex,
            float ey, float ez, Vector3f normal, VertexConsumer out) {
        float nx = normal.x(), ny = normal.y(), nz = normal.z();
        for (int i = 0; i < layer.length; i += STRIDE) {
            float x = layer[i] + ox, y = layer[i + 1] + oy, z = layer[i + 2] + oz;
            float alpha = layer[i + 7];
            if (fade) {
                alpha = orthographic
                        ? PlasmaFade.movedOrthographic(ex, ey, ez, layer[i + 3], layer[i + 4], layer[i + 5],
                                layer[i + 6], alpha)
                        : PlasmaFade.moved(x, y, z, layer[i + 3], layer[i + 4], layer[i + 5], layer[i + 6], alpha);
            }
            vertex(out, x, y, z, alpha, layer[i + 8], nx, ny, nz);
        }
    }

    /**
     * Under any other pose, as a guidebook scene's scaled and turned one: {@code view} is the model-view times the
     * pose, {@code turn} its normal matrix ({@link PlasmaFade#inEye}).
     */
    static void inEye(float[] layer, boolean fade, Matrix4f pose, Matrix4f view, Matrix3f turn, boolean orthographic,
            Vector3f normal, VertexConsumer out) {
        var position = new Vector3f();
        var eye = new Vector3f();
        var facing = new Vector3f();
        float nx = normal.x(), ny = normal.y(), nz = normal.z();
        for (int i = 0; i < layer.length; i += STRIDE) {
            float alpha = !fade ? layer[i + 7] : PlasmaFade.inEye(view, turn, orthographic, layer[i], layer[i + 1],
                    layer[i + 2], layer[i + 3], layer[i + 4], layer[i + 5], layer[i + 7], eye, facing);
            pose.transformPosition(layer[i], layer[i + 1], layer[i + 2], position);
            vertex(out, position.x(), position.y(), position.z(), alpha, layer[i + 8], nx, ny, nz);
        }
    }

    private static void vertex(VertexConsumer out, float x, float y, float z, float alpha, float colour, float nx,
            float ny, float nz) {
        out.addVertex(x, y, z, (int) (alpha * 255) << 24 | Float.floatToRawIntBits(colour), .5f, .5f,
                OverlayTexture.NO_OVERLAY, LightTexture.FULL_BRIGHT, nx, ny, nz);
    }
}
