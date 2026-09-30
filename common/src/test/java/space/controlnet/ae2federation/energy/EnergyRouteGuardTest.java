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

    @Test
    void largeDemandsStillVisitEachGridOnce() {
        var consumer = grid();
        var providers = new Object[40];
        for (var index = 0; index < providers.length; index++) {
            providers[index] = grid();
        }

        EnergyRouteGuard.demand(consumer, () -> {
            for (var provider : providers) {
                assertTrue(EnergyRouteGuard.visit(provider), "Each new provider may supply the demand");
            }
            for (var provider : providers) {
                assertFalse(EnergyRouteGuard.visit(provider), "No provider supplies one demand twice");
            }
            assertFalse(EnergyRouteGuard.visit(consumer), "The consumer stays visited past the scan limit");
            return 0.0;
        });

        assertEquals(1.0, EnergyRouteGuard.demand(consumer, () -> EnergyRouteGuard.visit(providers[39]) ? 1.0 : 0.0),
                "A later demand starts with nothing visited");
    }

    /** A stand-in Grid: the guard only compares identities. */
    private static Object grid() {
        return new Object();
    }
}
