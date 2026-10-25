package space.controlnet.ae2federation.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.AuthorityEpoch;

/** A domain whose Beam Converters cannot pay stops as a whole, as one over cable capacity does. */
final class DomainUnpoweredTest {
    private static final String DIMENSION = "test:dimension";
    private static final NetworkId A = new NetworkId(new UUID(0, 1));
    private static final NetworkId B = new NetworkId(new UUID(0, 2));

    @AfterEach
    void restoreTheDefaultLimit() {
        CableCapacityLimit.set(true, CableCapacityLimit.DEFAULT_CAPACITY);
    }

    @Test
    void anUnpoweredDomainStopsAndRunsAgainWhenPowered() {
        var registry = registry();
        var domain = registry.federationDomainOf(node(0)).orElseThrow().federationDomainId();
        assertTrue(registry.shareRunningFederationDomain(A, B));

        registry.setUnpowered(domain, true);
        assertTrue(registry.unpowered(domain));
        assertTrue(registry.stopped(domain));
        assertFalse(registry.overCapacity(domain), "the two causes stay apart");
        assertTrue(registry.shareFederationDomain(A, B), "the domain itself stays as it was");
        assertFalse(registry.shareRunningFederationDomain(A, B), "a stopped domain links nothing");
        assertFalse(registry.shareOverCapacityFederationDomain(A, B));

        registry.setUnpowered(domain, false);
        assertFalse(registry.stopped(domain));
        assertTrue(registry.shareRunningFederationDomain(A, B));
    }

    @Test
    void onlyAChangedVerdictAdvancesTheTopologyAndTheAuthority() {
        var registry = registry();
        var domain = registry.federationDomainOf(node(0)).orElseThrow().federationDomainId();
        long topology = registry.topologyRevision();
        long authority = AuthorityEpoch.current();

        registry.setUnpowered(domain, false);
        assertEquals(topology, registry.topologyRevision(), "a powered domain kept powered changes nothing");
        assertEquals(authority, AuthorityEpoch.current());

        registry.setUnpowered(domain, true);
        assertNotEquals(topology, registry.topologyRevision(), "the services learn of it through the topology revision");
        assertNotEquals(authority, AuthorityEpoch.current(), "and a held authorization through the authority epoch");
    }

    @Test
    void overCapacityIsTheCauseShownWhenBothApply() {
        CableCapacityLimit.set(true, 1);
        var registry = registry();
        var domain = registry.federationDomainOf(node(0)).orElseThrow().federationDomainId();
        registry.setUnpowered(domain, true);
        assertTrue(registry.overCapacity(domain));
        assertTrue(registry.shareOverCapacityFederationDomain(A, B));
    }

    @Test
    void aDomainThatIsGoneLetsGoOfItsVerdict() {
        var registry = registry();
        var domain = registry.federationDomainOf(node(0)).orElseThrow().federationDomainId();
        registry.setUnpowered(domain, true);
        registry.removeNode(node(0));
        registry.removeNode(node(1));
        assertTrue(registry.federationDomain(domain).isEmpty());
        assertFalse(registry.unpowered(domain));
        assertTrue(registry.unpoweredDomains().isEmpty());
    }

    @Test
    void aDomainTheRegistryDoesNotKnowCannotBeMarked() {
        var registry = registry();
        var unknown = FederationDomainId.physical(999);
        registry.setUnpowered(unknown, true);
        assertFalse(registry.unpowered(unknown));
    }

    /** A cable linked to a Switch whose two faces attach networks A and B. */
    private static FederationDomainRegistry registry() {
        var registry = new FederationDomainRegistry();
        var cablePorts = new LinkedHashMap<String, FederationDomainPortEvidence>();
        cablePorts.put("east", new FederationDomainPortEvidence.Federation(new FederationDomainPortId(node(1), "west")));
        registry.upsertNode(new FederationDomainNodeEvidence(node(0), FederationDomainNodeKind.CABLE, cablePorts));
        var ports = new LinkedHashMap<String, FederationDomainPortEvidence>();
        ports.put("west", new FederationDomainPortEvidence.Federation(new FederationDomainPortId(node(0), "east")));
        var networks = new NetworkId[] {A, B};
        for (int index = 0; index < networks.length; index++) {
            var face = "native" + index;
            ports.put(face, new FederationDomainPortEvidence.Native(
                    FederationDomainSourceId.nativePort(new FederationDomainPortId(node(1), face)), networks[index]));
        }
        registry.upsertNode(new FederationDomainNodeEvidence(node(1), FederationDomainNodeKind.SWITCH, ports));
        return registry;
    }

    private static FederationDomainNodeId node(long position) {
        return new FederationDomainNodeId(DIMENSION, position);
    }
}
