package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import net.minecraft.core.Direction;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

final class CableCollarTest {
    @Test
    void eachCollarLandsAtTheEndItIsAskedFor() {
        for (var toward : Direction.values()) {
            var end = FederationCableBuilder.collarEnd(toward);
            // The centre of the artist's ring, in the model's north-south frame, turned about the block centre.
            var centre = end.rotation().getRotation().getMatrix()
                    .transform(new Vector4f(0, 0, (end.high() ? 15.5f : .5f) / 16 - .5f, 1));
            float[] p = {centre.x() + .5f, centre.y() + .5f, centre.z() + .5f};
            for (var axis : Direction.Axis.values()) {
                float expected = axis != toward.getAxis() ? .5f
                        : toward.getAxisDirection() == Direction.AxisDirection.POSITIVE ? 15.5f / 16 : .5f / 16;
                assertEquals(expected, p[axis.ordinal()], 1e-6, toward + " " + axis);
            }
        }
    }
}
