package space.controlnet.ae2federation.client;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * How much of a plasma beam's soft layers each vertex keeps as it turns from the eye ({@link BundleBeamMesh#fade}),
 * taken per vertex every frame.
 *
 * <p>In general the vertex and its normal are carried into the eye's space ({@link #inEye}). In the world the pose of a
 * block entity only moves it to the camera, and the model-view only turns: a turn keeps dot products and lengths, so
 * the same fade comes from the camera-relative position and the mesh's own normal ({@link #moved},
 * {@link #movedOrthographic}), without two transforms and two square roots per vertex.
 */
final class PlasmaFade {
    private static final float ORTHONORMAL = 1e-4f;

    private PlasmaFade() {
    }

    /**
     * The alpha of a vertex at {@code x, y, z} in the block with normal {@code nx, ny, nz}, seen through {@code view}
     * (model-view times pose) with {@code turn} its normal matrix; {@code eye} and {@code facing} are scratch.
     */
    static float inEye(Matrix4f view, Matrix3f turn, boolean orthographic, float x, float y, float z, float nx,
            float ny, float nz, float alpha, Vector3f eye, Vector3f facing) {
        view.transformPosition(x, y, z, eye);
        turn.transform(nx, ny, nz, facing).normalize();
        // In the eye's space the eye sits at the origin, or looks down -Z in an orthographic view.
        if (orthographic) eye.set(0, 0, 1);
        else eye.negate().normalize();
        return Math.min(1, alpha * BundleBeamMesh.fade(facing.x(), facing.y(), facing.z(), eye.x(), eye.y(), eye.z()));
    }

    /**
     * {@link #inEye} for a vertex already at {@code x, y, z} from the camera, when the pose only moves and the
     * model-view only turns, in a perspective view.
     */
    static float moved(float x, float y, float z, float nx, float ny, float nz, float alpha) {
        float facing = nx * x + ny * y + nz * z;
        return Math.min(1, alpha * 2 * facing * facing / ((nx * nx + ny * ny + nz * nz) * (x * x + y * y + z * z)));
    }

    /**
     * {@link #inEye} in an orthographic view, when the pose only moves and the model-view only turns: the eye looks
     * along the model-view's third row, {@code ex, ey, ez}.
     */
    static float movedOrthographic(float ex, float ey, float ez, float nx, float ny, float nz, float alpha) {
        float facing = (nx * ex + ny * ey + nz * ez) / (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        return Math.min(1, alpha * 2 * facing * facing);
    }

    /** Whether {@code pose} only moves: its 3x3 part is the identity and it has no projective part. */
    static boolean movesOnly(Matrix4f pose) {
        return pose.m00() == 1 && pose.m11() == 1 && pose.m22() == 1 && pose.m01() == 0 && pose.m02() == 0
                && pose.m10() == 0 && pose.m12() == 0 && pose.m20() == 0 && pose.m21() == 0 && pose.m03() == 0
                && pose.m13() == 0 && pose.m23() == 0 && pose.m33() == 1;
    }

    /** Whether {@code view} only turns: no translation or projective part, and an orthonormal 3x3 part. */
    static boolean turnsOnly(Matrix4f view) {
        if (view.m30() != 0 || view.m31() != 0 || view.m32() != 0 || view.m03() != 0 || view.m13() != 0
                || view.m23() != 0 || view.m33() != 1) return false;
        return unit(view.m00(), view.m01(), view.m02()) && unit(view.m10(), view.m11(), view.m12())
                && unit(view.m20(), view.m21(), view.m22())
                && square(view.m00(), view.m01(), view.m02(), view.m10(), view.m11(), view.m12())
                && square(view.m00(), view.m01(), view.m02(), view.m20(), view.m21(), view.m22())
                && square(view.m10(), view.m11(), view.m12(), view.m20(), view.m21(), view.m22());
    }

    private static boolean unit(float x, float y, float z) {
        return Math.abs(x * x + y * y + z * z - 1) < ORTHONORMAL;
    }

    private static boolean square(float ax, float ay, float az, float bx, float by, float bz) {
        return Math.abs(ax * bx + ay * by + az * bz) < ORTHONORMAL;
    }
}
