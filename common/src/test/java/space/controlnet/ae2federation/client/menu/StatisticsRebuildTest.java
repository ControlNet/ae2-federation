package space.controlnet.ae2federation.client.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StatisticsRebuildTest {
    @Test
    void aChangedRequestRebuildsAtMostOnceEveryFiveReads() {
        // Built at second 5 for a page refreshed every second; the binding is read once a tick.
        assertFalse(FederationDomainPolicyMenuHolder.statisticsDue(true, 1, 5, 5, 1),
                "A request changed one tick after a build waits");
        assertFalse(FederationDomainPolicyMenuHolder.statisticsDue(true, 4, 5, 5, 1));
        assertTrue(FederationDomainPolicyMenuHolder.statisticsDue(true, 5, 5, 5, 1),
                "Five reads later the latest request is built");
    }

    @Test
    void anUnchangedRequestRebuildsOnItsPeriod() {
        assertFalse(FederationDomainPolicyMenuHolder.statisticsDue(false, 100, 9, 5, 5));
        assertTrue(FederationDomainPolicyMenuHolder.statisticsDue(false, 100, 10, 5, 5));
    }

    @Test
    void aClockThatWentBackRebuilds() {
        assertTrue(FederationDomainPolicyMenuHolder.statisticsDue(false, 0, 4, 5, 1));
    }
}
