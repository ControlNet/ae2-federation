package space.controlnet.ae2federation.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class CableFlowGeometryTest {
    @Test
    void everyMaskKeepsFlowInsideGlassAndOpensOnlyConnectedBoundaries() {
        for (int mask = 0; mask < 64; mask++) {
            int boundaryMask = 0;
            for (var ribbon : CableFlowGeometry.ribbons(mask)) {
                for (var vertex : ribbon.vertices()) {
                    float[] position = {vertex.x(), vertex.y(), vertex.z()};
                    for (int axis = 0; axis < 3; axis++) {
                        assertTrue(position[axis] >= 0 && position[axis] <= 1);
                        if (position[axis] == 1) boundaryMask |= 1 << (axis * 2);
                        if (position[axis] == 0) boundaryMask |= 2 << (axis * 2);
                    }
                    assertTrue(insideGlass(mask, position), "Outside enclosure for mask " + mask);
                }
            }
            assertEquals(mask, boundaryMask, "Boundary mismatch for mask " + mask);
        }
    }

    @Test
    void elbowsTurnWithoutOverlappingCentralAxisStrips() {
        for (int first = 0; first < 6; first++) {
            for (int second = first + 1; second < 6; second++) {
                if (first / 2 == second / 2) continue;
                int mask = (1 << first) | (1 << second);
                assertTrue(CableFlowGeometry.ribbons(mask).stream()
                        .anyMatch(r -> r.layer() == CableFlowGeometry.Layer.BEND), "Missing bend " + mask);
                assertTrue(CableFlowGeometry.ribbons(mask).stream()
                        .noneMatch(r -> r.layer() == CableFlowGeometry.Layer.HUB));
                // Flow approaching the curve must blend into its continuous dim base.
                assertTrue(CableFlowGeometry.ribbons(mask).stream()
                        .filter(r -> r.layer() == CableFlowGeometry.Layer.FLOW)
                        .flatMap(r -> r.vertices().stream()).anyMatch(v -> v.alpha() == 0));
            }
        }
    }

    @Test
    void branchingNodesOwnEachCentralPlaneOnce() {
        for (int mask = 0; mask < 64; mask++) {
            if (Integer.bitCount(mask) < 3) continue;
            var hubs = CableFlowGeometry.ribbons(mask).stream()
                    .filter(r -> r.layer() == CableFlowGeometry.Layer.HUB).toList();
            assertEquals(3, hubs.size(), "Hub plane count for " + mask);
            assertEquals(3, hubs.stream().map(CableFlowGeometry.Ribbon::axis).distinct().count());
            for (var ribbon : CableFlowGeometry.ribbons(mask)) {
                if (ribbon.layer() != CableFlowGeometry.Layer.FLOW) continue;
                for (var vertex : ribbon.vertices()) {
                    if (vertex.alpha() == 0) {
                        float[] point = {vertex.x(), vertex.y(), vertex.z()};
                        assertEquals(0.125f, Math.abs(point[ribbon.axis()] - 0.5f), 0.00001);
                    }
                }
            }
        }
    }

    private static boolean insideGlass(int mask, float[] point) {
        for (int arm = -1; arm < 6; arm++) {
            if (arm >= 0 && (mask & (1 << arm)) == 0) continue;
            boolean inside = true;
            for (int axis = 0; axis < 3; axis++) {
                float lo = 5f / 16, hi = 11f / 16;
                if (arm >= 0 && arm / 2 == axis) {
                    if ((arm & 1) == 0) hi = 1;
                    else lo = 0;
                }
                inside &= point[axis] >= lo - 0.00001 && point[axis] <= hi + 0.00001;
            }
            if (inside) return true;
        }
        return false;
    }

    @Test
    void adjacentBlocksShareTexturePhaseIncludingNegativeAndLargeCoordinates() {
        for (int coordinate : new int[] {-30000000, -5, -4, -1, 0, 3, 4, 29999999}) {
            float end = CableFlowGeometry.phase(coordinate, 12031, 0.25f) + 0.25f;
            float next = CableFlowGeometry.phase(coordinate + 1, 12031, 0.25f);
            assertEquals(Math.rint(end - next), end - next, 0.00001);
        }
    }

    @Test
    void boundedClockWrapDoesNotJumpTheRepeatingTexture() {
        float before = CableFlowGeometry.phase(-3, 4095, 1);
        float after = CableFlowGeometry.phase(-3, 4096, 0);
        assertEquals(Math.rint(after - before), after - before, 0.00001);
    }
}
