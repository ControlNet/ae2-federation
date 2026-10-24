package space.controlnet.ae2federation.power;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.beam.BeamLinkRule;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;
import space.controlnet.ae2federation.domain.FederationDomainPortEvidence;
import space.controlnet.ae2federation.domain.FederationDomainPortId;
import space.controlnet.ae2federation.quantum.QuantumLinkService;

/** Each Federation block costs FE every tick, scaled by one server multiplier. */
final class OperatingPowerCostsTest {
    // This release ships without the Beam Converter and leaves the rule off; the costs are tested with it on.
    @BeforeEach
    void requirePower() {
        OperatingPowerRule.set(true, OperatingPowerRule.DEFAULT_MULTIPLIER);
    }

    @AfterEach
    void restoreTheDefaultRule() {
        OperatingPowerRule.set(false, OperatingPowerRule.DEFAULT_MULTIPLIER);
    }

    @Test
    void theDefaultMultiplierGivesTheConfirmedCosts() {
        assertEquals(1.0, OperatingPowerRule.multiplier());
        assertEquals(0.5, OperatingPowerCosts.perTick(FederationDomainNodeKind.CABLE));
        assertEquals(2.0, OperatingPowerCosts.perTick(FederationDomainNodeKind.ROUTER));
        assertEquals(2.0, OperatingPowerCosts.perTick(FederationDomainNodeKind.SWITCH));
        assertEquals(2.0, OperatingPowerCosts.perTick(FederationDomainNodeKind.ENDPOINT));
        assertEquals(2.0, OperatingPowerCosts.perTick(FederationDomainNodeKind.PATTERN_PROVIDER));
        assertEquals(0.0, OperatingPowerCosts.perTick(FederationDomainNodeKind.P2P_TUNNEL), "AE2 charges the tunnel");
        assertEquals(0.0, OperatingPowerCosts.perTick(FederationDomainNodeKind.OTHER));
        assertEquals(0.0, OperatingPowerCosts.perTick(FederationDomainNodeKind.CONVERTER), "the converter is the supply");
        assertEquals(0.0, OperatingPowerCosts.perTick(FederationDomainNodeKind.BEAM_FORMER), "the beam pays instead");
        assertEquals(44.0, OperatingPowerCosts.perTick(FederationDomainNodeKind.QUANTUM_CUBE), "AE2's bridge, 22 AE/t");
    }

    @Test
    void aFormedQuantumCubePaysPerBlockLinkedOrNot() {
        // A formed 3x3x3 cube: every member has ports toward the rest of its body; one loose block has none.
        var nodes = new ArrayList<FederationDomainNodeEvidence>();
        for (int i = 0; i < 27; i++) nodes.add(quantumCube(new BlockPos(i, 64, 0), Map.of("east",
                new FederationDomainPortEvidence.Federation(new FederationDomainPortId(
                        new FederationDomainNodeId("test:dimension", new BlockPos(i + 1, 64, 0).asLong()), "west")))));
        nodes.add(quantumCube(new BlockPos(0, 80, 0), Map.of()));
        var use = OperatingPowerCosts.use(nodes);

        assertEquals(27 * 44.0, use.total(), 1e-9, "594 AE/t, in FE; the unformed block is free");
        assertEquals(Map.of(OperatingPowerCosts.Group.QUANTUM_CUBES, 27 * 44.0), use.byGroup());
        assertFalse(OperatingPowerCosts.quantumLinked(nodes.get(0)), "ports in its body only: not linked");
        var partner = new FederationDomainNodeId("test:nether", BlockPos.ZERO.asLong());
        assertTrue(OperatingPowerCosts.quantumLinked(quantumCube(BlockPos.ZERO, Map.of(QuantumLinkService.PORT + partner,
                new FederationDomainPortEvidence.Federation(new FederationDomainPortId(partner, "x"))))));
    }

    private static FederationDomainNodeEvidence quantumCube(BlockPos position,
            Map<String, FederationDomainPortEvidence> ports) {
        return new FederationDomainNodeEvidence(new FederationDomainNodeId("test:dimension", position.asLong()),
                FederationDomainNodeKind.QUANTUM_CUBE, ports, 1);
    }

    @Test
    void aLinkedBeamFormerPairPaysOnceByItsSquareAndLength() {
        // Two 2x2 formers' anchors ten blocks apart along x: nine blocks between the fronts.
        var west = beamFormer(new BlockPos(0, 64, 0), 6);
        var east = beamFormer(new BlockPos(10, 64, 0), 6);
        var nodes = List.of(link(west, east), link(east, west), beamFormer(new BlockPos(0, 65, 0), 6));
        var use = OperatingPowerCosts.use(nodes);

        var expected = 4 * Math.pow(9, 1.05) * 2;
        assertEquals(expected, use.total(), 1e-9, "M*N x L^1.05 AE, in FE, once for the pair");
        assertEquals(Map.of(OperatingPowerCosts.Group.BEAMS, expected), use.byGroup());
        assertEquals(expected, OperatingPowerCosts.beamLink(CableCrossSection.TWO_BY_TWO, 9), 1e-9);
    }

    @Test
    void aShortBeamPaysForTwoBlocksAndA3x3ForNine() {
        assertEquals(Math.pow(2, 1.05) * 2, OperatingPowerCosts.beamLink(CableCrossSection.ONE_BY_ONE, 0), 1e-9,
                "fronts touching cost as two blocks apart");
        assertEquals(Math.pow(2, 1.05) * 2, OperatingPowerCosts.beamLink(CableCrossSection.ONE_BY_ONE, 1), 1e-9);
        assertEquals(9 * Math.pow(100, 1.05) * 2, OperatingPowerCosts.beamLink(CableCrossSection.THREE_BY_THREE, 100),
                1e-9);
        var below = beamFormer(new BlockPos(0, 60, 0), FederationDomainNodeEvidence.UNLIMITED);
        var above = beamFormer(new BlockPos(0, 64, 0), FederationDomainNodeEvidence.UNLIMITED);
        OperatingPowerRule.set(true, 2.0);
        assertEquals(2 * 9 * Math.pow(3, 1.05) * 2,
                OperatingPowerCosts.use(List.of(link(below, above), link(above, below))).total(), 1e-9,
                "a 3x3 pair, three blocks between, at multiplier 2");
    }

    private static FederationDomainNodeEvidence beamFormer(BlockPos anchor, int capacityFactor) {
        return new FederationDomainNodeEvidence(new FederationDomainNodeId("test:dimension", anchor.asLong()),
                FederationDomainNodeKind.BEAM_FORMER, Map.of(), capacityFactor);
    }

    private static FederationDomainNodeEvidence link(FederationDomainNodeEvidence anchor,
            FederationDomainNodeEvidence partner) {
        return new FederationDomainNodeEvidence(anchor.nodeId(), anchor.kind(), Map.of(
                BeamLinkRule.PORT + partner.nodeId(), new FederationDomainPortEvidence.Federation(
                        new FederationDomainPortId(partner.nodeId(), BeamLinkRule.PORT + anchor.nodeId()))),
                anchor.capacityFactor());
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
        // The statistics page's groups: bundle cells apart from plain cable, Endpoints and Providers together.
        assertEquals(Map.of(OperatingPowerCosts.Group.ROUTERS, 16.0, OperatingPowerCosts.Group.BUNDLES, 1.0,
                OperatingPowerCosts.Group.CABLES, 0.5, OperatingPowerCosts.Group.SWITCHES, 2.0,
                OperatingPowerCosts.Group.DEVICES, 2.0), use.byGroup());
    }

    @Test
    void anUnlimitedBundleCountsAsABundleAndAProviderAsADevice() {
        var nodes = new ArrayList<FederationDomainNodeEvidence>();
        add(nodes, FederationDomainNodeKind.CABLE, 2, FederationDomainNodeEvidence.UNLIMITED);
        add(nodes, FederationDomainNodeKind.PATTERN_PROVIDER, 1, 1);
        assertEquals(Map.of(OperatingPowerCosts.Group.BUNDLES, 1.0, OperatingPowerCosts.Group.DEVICES, 2.0),
                OperatingPowerCosts.use(nodes).byGroup());
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
