package space.controlnet.ae2federation.compat.neoecoae;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** A formed structure's parts are inside its bounds until it breaks; each change redraws the blocks it touched. */
final class FederationComputationLooksTest {
    private static final BlockPos CONTROLLER = new BlockPos(31, -59, 10);
    private static final BoundingBox FORMED = new BoundingBox(30, -60, 10, 34, -58, 11);
    private final List<BoundingBox> repainted = new ArrayList<>();

    @BeforeEach
    void listen() {
        FederationComputationLooks.clear();
        FederationComputationLooks.registerRepaint(repainted::add);
    }

    @AfterEach
    void reset() {
        FederationComputationLooks.clear();
        FederationComputationLooks.registerRepaint(bounds -> {
        });
    }

    @Test
    void aFormedStructureHoldsItsPartsUntilItBreaks() {
        FederationComputationLooks.place(CONTROLLER, FORMED);
        assertTrue(FederationComputationLooks.contains(new BlockPos(34, -58, 11)));
        assertFalse(FederationComputationLooks.contains(new BlockPos(35, -59, 10)));
        FederationComputationLooks.place(CONTROLLER, null);
        assertFalse(FederationComputationLooks.contains(new BlockPos(34, -58, 11)));
        assertEquals(List.of(FORMED, FORMED), repainted);
    }

    @Test
    void theSameBoundsAgainRedrawNothing() {
        FederationComputationLooks.place(CONTROLLER, FORMED);
        FederationComputationLooks.place(CONTROLLER, FORMED);
        FederationComputationLooks.place(CONTROLLER, null);
        FederationComputationLooks.place(CONTROLLER, null);
        assertEquals(List.of(FORMED, FORMED), repainted);
    }

    @Test
    void aLongerStructureRedrawsBothItsOldAndNewBounds() {
        var longer = new BoundingBox(30, -60, 10, 36, -58, 11);
        FederationComputationLooks.place(CONTROLLER, FORMED);
        FederationComputationLooks.place(CONTROLLER, longer);
        assertEquals(List.of(FORMED, FORMED, longer), repainted);
    }
}
