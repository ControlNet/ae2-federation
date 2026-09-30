package space.controlnet.ae2federation.energy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class EnergyRouteGuardTest {
    @Test
    void visitsEachGridOncePerDemand() {
        var consumer = grid();
        var provider = grid();

        var result = EnergyRouteGuard.demand(consumer, () -> {
            assertFalse(EnergyRouteGuard.visit(consumer), "The consumer is visited when its demand starts");
            assertTrue(EnergyRouteGuard.visit(provider), "An unvisited provider may supply the demand");
            assertFalse(EnergyRouteGuard.visit(provider), "A provider supplies one demand only once");
            return 4.0;
        });

        assertEquals(4.0, result);
    }

    @Test
    void nestedDemandJoinsTheRunningOne() {
        var first = grid();
        var second = grid();
        var third = grid();

        EnergyRouteGuard.demand(first, () -> {
            assertTrue(EnergyRouteGuard.visit(second));
            // The provider's own Federation source asks on its behalf: the walk must not come back to the first Grid.
            return EnergyRouteGuard.demand(second, () -> {
                assertFalse(EnergyRouteGuard.visit(first));
                assertTrue(EnergyRouteGuard.visit(third));
                return 0.0;
            });
        });
    }

    @Test
    void retainsNoStateBetweenDemands() {
        var consumer = grid();
        var provider = grid();

        EnergyRouteGuard.demand(consumer, () -> EnergyRouteGuard.visit(provider) ? 1.0 : 0.0);

        assertTrue(EnergyRouteGuard.visit(provider), "Outside a demand nothing is visited");
        assertEquals(1.0, EnergyRouteGuard.demand(consumer, () -> EnergyRouteGuard.visit(provider) ? 1.0 : 0.0),
                "A later demand may use the provider again");
    }

    /** A stand-in Grid: the guard only compares identities. */
    private static Object grid() {
        return new Object();
    }
}
