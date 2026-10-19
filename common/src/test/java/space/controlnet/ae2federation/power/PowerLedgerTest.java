package space.controlnet.ae2federation.power;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A domain's converters pool their buffers; the domain stops the tick they cannot pay, as an AE2 grid does. */
final class PowerLedgerTest {
    @Test
    void useDrainsTheConvertersInProportionToWhatTheyHold() {
        var stored = new double[] {300, 100};
        assertTrue(PowerLedger.drain(stored, 100));
        assertArrayEquals(new double[] {225, 75}, stored, 1e-9);
    }

    @Test
    void aBufferThatCannotPayIsEmptiedAndThatTickIsUnpaid() {
        var stored = new double[] {30, 10};
        assertFalse(PowerLedger.drain(stored, 50), "a stopped domain keeps drawing its use");
        assertArrayEquals(new double[] {0, 0}, stored);
        assertTrue(PowerLedger.drain(new double[] {50}, 50), "exactly enough pays");
    }

    @Test
    void aNewDomainThatCanPayRunsAtOnce() {
        var ledger = new PowerLedger();
        assertTrue(ledger.settle(new double[] {16000}, 20), "a domain seen for the first time does not wait");
    }

    @Test
    void aNewDomainThatCannotPayStopsAtOnce() {
        assertFalse(new PowerLedger().settle(new double[] {0}, 20));
    }

    @Test
    void aStoppedDomainRunsAgainOnlyAfterMoreThanThirtyTicksWithPower() {
        var ledger = new PowerLedger();
        assertTrue(ledger.settle(new double[] {10}, 5));
        assertFalse(ledger.settle(new double[] {0}, 5), "stops the tick the buffer cannot pay");
        // Power returns: the first paid tick restarts AE2's count at 0, and the grid runs once the count passes 30.
        for (var tick = 1; tick <= PowerLedger.RESTART_TICKS + 1; tick++) {
            assertFalse(ledger.settle(new double[] {5}, 5), "paid tick " + tick + " still waits");
        }
        assertTrue(ledger.settle(new double[] {5}, 5), "runs on paid tick " + (PowerLedger.RESTART_TICKS + 2));
    }

    @Test
    void anUnpaidTickWhileWaitingStartsTheWaitAgain() {
        var ledger = new PowerLedger();
        assertFalse(ledger.settle(new double[] {0}, 5));
        for (var tick = 0; tick < 20; tick++) ledger.settle(new double[] {5}, 5);
        assertFalse(ledger.settle(new double[] {0}, 5));
        for (var tick = 0; tick < PowerLedger.RESTART_TICKS + 1; tick++) {
            assertFalse(ledger.settle(new double[] {5}, 5));
        }
        assertTrue(ledger.settle(new double[] {5}, 5));
        assertEquals(true, ledger.running());
    }

    @Test
    void aDomainThatUsesNothingAlwaysRuns() {
        var ledger = new PowerLedger();
        assertTrue(ledger.settle(new double[0], 0), "nothing to pay is paid");
    }
}
