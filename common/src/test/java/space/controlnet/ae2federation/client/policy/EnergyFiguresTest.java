package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class EnergyFiguresTest {
    @Test
    void aNetworkOfItsOwnReadsItsOwnCells() {
        var figures = EnergyFigures.of(500, 1000, 500L, 1000L, 1);
        assertFalse(figures.shared());
        assertEquals(500, figures.stored());
        assertEquals(1000, figures.max());
        assertEquals(50, figures.percent());
    }

    @Test
    void aNetworkWithoutCellsInAPoolReadsThePool() {
        // Its own buffer is nearly empty, but it draws on a 76 % full pool.
        var figures = EnergyFigures.of(0, 800, 1_520_000L, 2_000_800L, 2);
        assertTrue(figures.shared());
        assertEquals(1_520_000, figures.stored());
        assertEquals(2_000_800, figures.max());
        assertEquals(76, figures.percent());
    }

    @Test
    void everyMemberOfOnePoolReadsTheSamePercentage() {
        var lender = EnergyFigures.of(1_520_000, 2_000_000, 1_520_000L, 2_000_800L, 2);
        var borrower = EnergyFigures.of(0, 800, 1_520_000L, 2_000_800L, 2);
        assertEquals(lender.percent(), borrower.percent());
        assertEquals(lender.fraction(), borrower.fraction());
    }

    @Test
    void anEmptyPoolIsStillShared() {
        var figures = EnergyFigures.of(0, 800, 0L, 2_000_800L, 2);
        assertTrue(figures.shared());
        assertEquals(0, figures.percent());
    }

    @Test
    void aCreativeCellOnItsOwnIsNotAPool() {
        // A creative cell reports Long.MAX_VALUE / 10000 stored and held; alone it is still the network's own.
        long creative = Long.MAX_VALUE / 10000;
        var figures = EnergyFigures.of(creative, creative + 800, creative, creative + 800, 1);
        assertFalse(figures.shared());
        assertEquals(100, figures.percent());
    }

    @Test
    void aPoolWithACreativeCellReadsFull() {
        long creative = Long.MAX_VALUE / 10000;
        var figures = EnergyFigures.of(0, 800, creative, creative + 800, 2);
        assertTrue(figures.shared());
        assertEquals(100, figures.percent());
    }

    @Test
    void factsFromBeforeThePoolFiguresReadTheOwnCells() {
        var figures = EnergyFigures.of(250, 1000, null, null, 0);
        assertFalse(figures.shared());
        assertEquals(25, figures.percent());
    }

    @Test
    void noCapacityIsUnknown() {
        var figures = EnergyFigures.of(0, 0, 0L, 0L, 1);
        assertEquals(-1f, figures.fraction());
        assertEquals(0, figures.percent());
    }
}
