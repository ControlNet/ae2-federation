package space.controlnet.ae2federation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.OptionalInt;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.AuthorityEpoch;

/** A Federation domain stops as a whole while any of its cables carries more than the cable capacity. */
final class CableCapacityLimitTest {
    private static final String DIMENSION = "test:dimension";

    @AfterEach
    void restoreTheDefaultLimit() {
        CableCapacityLimit.set(true, CableCapacityLimit.DEFAULT_CAPACITY);
    }

    @Test
    void theDefaultIsEnforcedWithEightUnitsPerCable() {
        assertEquals(OptionalInt.of(8), CableCapacityLimit.current());
        CableCapacityLimit.set(false, 9);
        assertEquals(OptionalInt.empty(), CableCapacityLimit.current(), "a limit that is not enforced limits nothing");
    }

    @Test
    void aDomainStopsWhileOneOfItsCablesCarriesMoreThanTheLimit() {
        var registry = registry();
        var networks = switchWith(registry, 9);
        var domain = registry.federationDomainOf(node(0)).orElseThrow().federationDomainId();

        assertTrue(registry.overCapacity(domain), "nine networks on one cable exceed eight");
        assertTrue(registry.shareFederationDomain(networks[0], networks[1]), "the domain itself stays as it was");
        assertFalse(registry.shareRunningFederationDomain(networks[0], networks[1]), "a stopped domain links nothing");

        // The ninth network leaves: eight units fit, and the domain runs again.
        registry.upsertNode(switchEvidence(1, java.util.Arrays.copyOf(networks, 8)));
        var after = registry.federationDomainOf(node(0)).orElseThrow().federationDomainId();
        assertFalse(registry.overCapacity(after));
        assertTrue(registry.shareRunningFederationDomain(networks[0], networks[1]));
    }

    @Test
    void aBundledCableCarriesItsMultipleOfTheLimit() {
        var registry = registry();
        switchWith(registry, 24, 3);
        assertFalse(registry.overCapacity(registry.federationDomainOf(node(0)).orElseThrow().federationDomainId()),
                "a 1x2 bundle carries three cables' worth");
        var over = registry();
        switchWith(over, 25, 3);
        assertTrue(over.overCapacity(over.federationDomainOf(node(0)).orElseThrow().federationDomainId()));
        var unlimited = registry();
        switchWith(unlimited, 200, FederationDomainNodeEvidence.UNLIMITED);
        assertFalse(unlimited.overCapacity(
                unlimited.federationDomainOf(node(0)).orElseThrow().federationDomainId()), "a 3x3 bundle has no limit");
    }

    @Test
    void aP2pTunnelPairCarriesOneCable() {
        var fits = registry();
        tunnelWith(fits, 8);
        assertFalse(fits.overCapacity(fits.federationDomainOf(node(0)).orElseThrow().federationDomainId()),
                "eight networks fit through a pair");
        var over = registry();
        tunnelWith(over, 9);
        assertTrue(over.overCapacity(over.federationDomainOf(node(0)).orElseThrow().federationDomainId()),
                "a pair carries one cable's eight, though the 1x2 bundle before it carries 24");
    }

    @Test
    void aDomainAtTheLimitRuns() {
        var registry = registry();
        var networks = switchWith(registry, 8);
        assertFalse(registry.overCapacity(registry.federationDomainOf(node(0)).orElseThrow().federationDomainId()));
        assertTrue(registry.shareRunningFederationDomain(networks[0], networks[7]));
    }

    @Test
    void aLimitThatIsNotEnforcedStopsNothing() {
        CableCapacityLimit.set(false, 1);
        var registry = registry();
        var networks = switchWith(registry, 5);
        assertFalse(registry.overCapacity(registry.federationDomainOf(node(0)).orElseThrow().federationDomainId()));
        assertTrue(registry.shareRunningFederationDomain(networks[0], networks[4]));
    }

    @Test
    void onlyAChangedVerdictAdvancesTheTopologyAndTheAuthority() {
        CableCapacityLimit.set(true, 6);
        var registry = registry();
        switchWith(registry, 5);
        var domain = registry.federationDomainOf(node(0)).orElseThrow().federationDomainId();
        long topology = registry.topologyRevision();
        long authority = AuthorityEpoch.current();

        CableCapacityLimit.set(true, 5);
        assertFalse(registry.overCapacity(domain));
        assertEquals(topology, registry.topologyRevision(), "a new limit the domain still fits changes nothing");
        assertEquals(authority, AuthorityEpoch.current());

        CableCapacityLimit.set(true, 4);
        assertTrue(registry.overCapacity(domain), "the limit alone stops the domain, with no evidence change");
        assertNotEquals(topology, registry.topologyRevision(), "the services learn of it through the topology revision");
        assertNotEquals(authority, AuthorityEpoch.current(), "and a held authorization through the authority epoch");
    }

    @Test
    void theCountJudgedIsTheCableDemandEvenWhenNoLimitIsEnforced() {
        CableCapacityLimit.set(false, 8);
        var registry = registry();
        var networks = switchWith(registry, 5);
        var domain = registry.federationDomainOf(node(0)).orElseThrow().federationDomainId();
        assertEquals(CableCapacityCounter.count(registry.nodeEvidence(domain)), registry.capacityDemand(domain));
        assertEquals(5, registry.capacityDemand(domain).get(node(0)).units());

        // One network leaves: the next read counts again.
        registry.upsertNode(switchEvidence(1, java.util.Arrays.copyOf(networks, 4)));
        var after = registry.federationDomainOf(node(0)).orElseThrow().federationDomainId();
        assertEquals(4, registry.capacityDemand(after).get(node(0)).units());
    }

    @Test
    void evidenceIsPendingUntilTheNextRead() {
        var registry = registry();
        assertFalse(registry.pending());
        var networks = switchWith(registry, 3);
        assertTrue(registry.pending(), "a published node waits for the next read");
        registry.federationDomainOf(node(0));
        assertFalse(registry.pending(), "the read recomputed and judged it");
        registry.upsertNode(switchEvidence(1, java.util.Arrays.copyOf(networks, 2)));
        assertTrue(registry.pending());
    }

    private static FederationDomainRegistry registry() {
        return new FederationDomainRegistry();
    }

    /** A cable linked to a Switch whose faces attach {@code count} distinct networks, all in one subdomain. */
    private static NetworkId[] switchWith(FederationDomainRegistry registry, int count) {
        return switchWith(registry, count, 1);
    }

    private static NetworkId[] switchWith(FederationDomainRegistry registry, int count, int capacityFactor) {
        var networks = new NetworkId[count];
        for (int index = 0; index < count; index++) networks[index] = new NetworkId(new UUID(0, index + 1));
        var cablePorts = new LinkedHashMap<String, FederationDomainPortEvidence>();
        cablePorts.put("east", new FederationDomainPortEvidence.Federation(new FederationDomainPortId(node(1), "west")));
        registry.upsertNode(new FederationDomainNodeEvidence(node(0), FederationDomainNodeKind.CABLE, cablePorts,
                capacityFactor));
        registry.upsertNode(switchEvidence(1, networks));
        return networks;
    }

    /**
     * A 1x2 bundle (three cables' worth) linked through a Federation P2P tunnel to a Switch whose faces attach
     * {@code count} networks: the tunnel joins one region.
     */
    private static void tunnelWith(FederationDomainRegistry registry, int count) {
        var networks = new NetworkId[count];
        for (int index = 0; index < count; index++) networks[index] = new NetworkId(new UUID(0, index + 1));
        var cablePorts = new LinkedHashMap<String, FederationDomainPortEvidence>();
        cablePorts.put("east", new FederationDomainPortEvidence.Federation(new FederationDomainPortId(node(2), "cable")));
        registry.upsertNode(new FederationDomainNodeEvidence(node(0), FederationDomainNodeKind.CABLE, cablePorts, 3));
        var tunnelPorts = new LinkedHashMap<String, FederationDomainPortEvidence>();
        tunnelPorts.put("cable", new FederationDomainPortEvidence.Federation(new FederationDomainPortId(node(0), "east")));
        tunnelPorts.put("switch", new FederationDomainPortEvidence.Federation(new FederationDomainPortId(node(1), "west")));
        registry.upsertNode(new FederationDomainNodeEvidence(node(2), FederationDomainNodeKind.P2P_TUNNEL, tunnelPorts));
        var switchPorts = new LinkedHashMap<String, FederationDomainPortEvidence>();
        switchPorts.put("west", new FederationDomainPortEvidence.Federation(new FederationDomainPortId(node(2), "switch")));
        for (int index = 0; index < count; index++) {
            var face = "native" + index;
            switchPorts.put(face, new FederationDomainPortEvidence.Native(
                    FederationDomainSourceId.nativePort(new FederationDomainPortId(node(1), face)), networks[index]));
        }
        registry.upsertNode(new FederationDomainNodeEvidence(node(1), FederationDomainNodeKind.SWITCH, switchPorts));
    }

    private static FederationDomainNodeEvidence switchEvidence(long position, NetworkId... networks) {
        var ports = new LinkedHashMap<String, FederationDomainPortEvidence>();
        ports.put("west", new FederationDomainPortEvidence.Federation(new FederationDomainPortId(node(0), "east")));
        for (int index = 0; index < networks.length; index++) {
            var face = "native" + index;
            ports.put(face, new FederationDomainPortEvidence.Native(
                    FederationDomainSourceId.nativePort(new FederationDomainPortId(node(position), face)), networks[index]));
        }
        return new FederationDomainNodeEvidence(node(position), FederationDomainNodeKind.SWITCH, ports);
    }

    private static FederationDomainNodeId node(long position) {
        return new FederationDomainNodeId(DIMENSION, position);
    }
}
