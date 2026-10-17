package space.controlnet.ae2federation.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;

/** Each Federation block costs FE every tick, scaled by one server multiplier. */
final class OperatingPowerCostsTest {
    @AfterEach
    void restoreTheDefaultRule() {
        OperatingPowerRule.set(true, OperatingPowerRule.DEFAULT_MULTIPLIER);
    }

    @Test
    void theDefaultIsRequiredAtTheConfirmedCosts() {
        assertTrue(OperatingPowerRule.required());
        assertEquals(1.0, OperatingPowerRule.multiplier());
        assertEquals(0.5, OperatingPowerCosts.perTick(FederationDomainNodeKind.CABLE));
        assertEquals(2.0, OperatingPowerCosts.perTick(FederationDomainNodeKind.ROUTER));
        assertEquals(2.0, OperatingPowerCosts.perTick(FederationDomainNodeKind.SWITCH));
        assertEquals(2.0, OperatingPowerCosts.perTick(FederationDomainNodeKind.ENDPOINT));
        assertEquals(2.0, OperatingPowerCosts.perTick(FederationDomainNodeKind.PATTERN_PROVIDER));
        assertEquals(0.0, OperatingPowerCosts.perTick(FederationDomainNodeKind.P2P_TUNNEL), "AE2 charges the tunnel");
        assertEquals(0.0, OperatingPowerCosts.perTick(FederationDomainNodeKind.OTHER));
        assertEquals(0.0, OperatingPowerCosts.perTick(FederationDomainNodeKind.CONVERTER), "the converter is the supply");
    }

    @Test
    void aDomainPaysForEveryNodeAndBundleCell() {
        var nodes = new ArrayList<FederationDomainNodeEvidence>();
        // A 2x2x2 Router body, a 2x2 bundle run of two cells, a plain cable, a Switch and an Endpoint.
        add(nodes, FederationDomainNodeKind.ROUTER, 8, 1);
        add(nodes, FederationDomainNodeKind.CABLE, 2, 6);
        add(nodes, FederationDomainNodeKind.CABLE, 1, 1);
        add(nodes, FederationDomainNodeKind.SWITCH, 1, 1);
        add(nodes, FederationDomainNodeKind.ENDPOINT, 1, 1);
        var use = OperatingPowerCosts.use(nodes);

        assertEquals(16 + 1.5 + 2 + 2, use.total());
        assertEquals(Map.of(FederationDomainNodeKind.ROUTER, 16.0, FederationDomainNodeKind.CABLE, 1.5,
                FederationDomainNodeKind.SWITCH, 2.0, FederationDomainNodeKind.ENDPOINT, 2.0), use.byKind());
    }

    @Test
    void theMultiplierScalesEveryCostAndTurningTheRuleOffCostsNothing() {
        var nodes = new ArrayList<FederationDomainNodeEvidence>();
        add(nodes, FederationDomainNodeKind.CABLE, 4, 1);
        OperatingPowerRule.set(true, 3.0);
        assertEquals(6.0, OperatingPowerCosts.use(nodes).total());
        OperatingPowerRule.set(false, 3.0);
        assertFalse(OperatingPowerRule.required());
        assertEquals(0.0, OperatingPowerCosts.use(nodes).total(), "power that is not required costs nothing");
        assertThrows(IllegalArgumentException.class, () -> OperatingPowerRule.set(true, -1));
        assertThrows(IllegalArgumentException.class,
                () -> OperatingPowerRule.set(true, OperatingPowerRule.MAX_MULTIPLIER + 1));
    }

    private static void add(List<FederationDomainNodeEvidence> nodes, FederationDomainNodeKind kind, int count,
            int capacityFactor) {
        for (var index = 0; index < count; index++) {
            nodes.add(new FederationDomainNodeEvidence(new FederationDomainNodeId("test:dimension", nodes.size()), kind,
                    Map.of(), capacityFactor));
        }
    }
}
