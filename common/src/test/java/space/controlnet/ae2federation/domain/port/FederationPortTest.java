package space.controlnet.ae2federation.domain.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;

class FederationPortTest {
    private static final BlockPos HOST = new BlockPos(4, 64, -2);

    @Test
    void aBlockPortHasNoPart() {
        assertEquals(new FederationPort(HOST, Direction.NORTH, ""), new FederationPort(HOST, Direction.NORTH));
    }

    @Test
    void portsOfTwoPartsOfOneBlockDiffer() {
        assertNotEquals(new FederationPort(HOST, Direction.NORTH, "north"), new FederationPort(HOST, Direction.NORTH));
        assertNotEquals(new FederationPort(HOST, Direction.NORTH, "north"),
                new FederationPort(HOST, Direction.NORTH, "south"));
    }

    @Test
    void aPartPortConnectsToTheBlockItFaces() {
        var part = new FederationPort(HOST, Direction.EAST, "east");
        var cable = new FederationPort(HOST.east(), Direction.WEST);

        assertTrue(part.connectsTo(cable) && cable.connectsTo(part));
    }
}
