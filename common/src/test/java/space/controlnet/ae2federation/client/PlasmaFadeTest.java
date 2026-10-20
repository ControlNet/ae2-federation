package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import net.minecraft.core.Direction;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.bundle.BundleBeam;
import space.controlnet.ae2federation.router.CableVisualConnections;

/** The plasma beam's per-vertex fade: the world's short path must draw what the eye-space path draws. */
final class PlasmaFadeTest {
    private static int mask(Direction... sides) {
        int mask = 0;
        for (var side : sides) mask |= 1 << List.of(CableVisualConnections.DIRECTIONS).indexOf(side);
        return mask;
    }

    private static final List<BundleBeam> BEAMS = List.of(
            new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST), 1, 2),
            new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST), 1, 3),
            new BundleBeam(Direction.Axis.X, mask(Direction.EAST, Direction.WEST, Direction.UP), 2, 2),
            new BundleBeam(Direction.Axis.Z, mask(Direction.NORTH, Direction.EAST), 3, 3));

    /** As the renderer packs it: the alpha byte of the vertex colour. */
    private static int alphaByte(float alpha) {
        return (int) (alpha * 255);
    }

    @Test
    void theMovedPathFadesAsTheEyeSpacePathDoes() {
        var random = new Random(20261020L);
        var frame = new CableBeamFrame();
        var eye = new Vector3f();
        var facing = new Vector3f();
        int vertices = 0;
        int differ = 0;
        for (int trial = 0; trial < 300; trial++) {
            var beam = BEAMS.get(trial % BEAMS.size());
            var style = CableBeamStyle.fromLoad(random.nextDouble() * 1.3);
            float[] glow = trial % 2 == 0
                    ? frame.bundle(beam, trial, 64, -trial, 1000 + trial, .3f, style, trial % 2, true)
                    : frame.cell(mask(Direction.EAST, Direction.UP), trial, 64, 3, 1000 + trial, .3f, style, 0, true);
            // The world: a block entity's pose moves it to the camera; the model-view only turns.
            var pose = new Matrix4f().translation((float) random.nextGaussian() * 20, (float) random.nextGaussian() * 6,
                    (float) random.nextGaussian() * 20);
            var modelView = new Matrix4f().rotationXYZ((float) (random.nextDouble() * 3 - 1.5),
                    (float) (random.nextDouble() * Math.TAU), 0);
            assertTrue(PlasmaFade.movesOnly(pose));
            assertTrue(PlasmaFade.turnsOnly(modelView));
            boolean orthographic = trial % 5 == 0;
            var view = new Matrix4f(modelView).mul(pose);
            var turn = view.normal(new Matrix3f());
            for (int i = 0; i < glow.length; i += CableBeamFrame.BUNDLE_STRIDE) {
                float alpha = glow[i + 6];
                float expected = PlasmaFade.inEye(view, turn, orthographic, glow[i], glow[i + 1], glow[i + 2],
                        glow[i + 7], glow[i + 8], glow[i + 9], alpha, eye, facing);
                float x = glow[i] + pose.m30(), y = glow[i + 1] + pose.m31(), z = glow[i + 2] + pose.m32();
                var check = new Vector3f();
                pose.transformPosition(glow[i], glow[i + 1], glow[i + 2], check);
                assertEquals(check, new Vector3f(x, y, z), "the moved position is the pose's");
                float actual = orthographic
                        ? PlasmaFade.movedOrthographic(modelView.m02(), modelView.m12(), modelView.m22(), glow[i + 7],
                                glow[i + 8], glow[i + 9], alpha)
                        : PlasmaFade.moved(x, y, z, glow[i + 7], glow[i + 8], glow[i + 9], alpha);
                assertEquals(expected, actual, 2e-5f);
                int a = alphaByte(expected), b = alphaByte(actual);
                assertTrue(Math.abs(a - b) <= 1, a + " " + b);
                if (a != b) differ++;
                vertices++;
            }
        }
        // Float rounding may tip a value across a step of the alpha byte, very rarely.
        assertTrue(vertices > 500_000, String.valueOf(vertices));
        assertTrue(differ * 10_000L < vertices, differ + " of " + vertices + " alpha bytes differ");
    }

    @Test
    void onlyAMovingPoseAndATurningViewTakeTheShortPath() {
        assertTrue(PlasmaFade.movesOnly(new Matrix4f().translation(3, -2, 7)));
        assertFalse(PlasmaFade.movesOnly(new Matrix4f().translation(3, -2, 7).scale(2)), "a guidebook's zoom");
        assertFalse(PlasmaFade.movesOnly(new Matrix4f().rotationY(.5f)));
        assertTrue(PlasmaFade.turnsOnly(new Matrix4f().rotationXYZ(.4f, 2.1f, 0)));
        assertTrue(PlasmaFade.turnsOnly(new Matrix4f()));
        assertFalse(PlasmaFade.turnsOnly(new Matrix4f().rotationY(.3f).scale(1.5f)));
        assertFalse(PlasmaFade.turnsOnly(new Matrix4f().rotationY(.3f).translate(0, 1, 0)));
        assertFalse(PlasmaFade.turnsOnly(new Matrix4f().m10(.2f)));
    }
}
