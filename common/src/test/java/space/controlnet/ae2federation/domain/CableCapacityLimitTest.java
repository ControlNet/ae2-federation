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
    void theDefaultIsEnforcedWithFourUnitsPerCable() {
        assertEquals(OptionalInt.of(4), CableCapacityLimit.current());
        CableCapacityLimit.set(false, 9);
        assertEquals(OptionalInt.empty(), CableCapacityLimit.current(), "a limit that is not enforced limits nothing");
    }

    @Test
    void aDomainStopsWhileOneOfItsCablesCarriesMoreThanTheLimit() {
        var registry = registry();
        var networks = switchWith(registry, 5);
        var domain = registry.federationDomainOf(node(0)).orElseThrow().federationDomainId();

        assertTrue(registry.overCapacity(domain), "five networks on one cable exceed four");
        assertTrue(registry.shareFederationDomain(networks[0], networks[1]), "the domain itself stays as it was");
        assertFalse(registry.shareRunningFederationDomain(networks[0], networks[1]), "a stopped domain links nothing");

        // The fifth network leaves: four units fit, and the domain runs again.
        registry.upsertNode(switchEvidence(1, java.util.Arrays.copyOf(networks, 4)));
        var after = registry.federationDomainOf(node(0)).orElseThrow().federationDomainId();
        assertFalse(registry.overCapacity(after));
        assertTrue(registry.shareRunningFederationDomain(networks[0], networks[1]));
    }

    @Test
    void aDomainAtTheLimitRuns() {
        var registry = registry();
        var networks = switchWith(registry, 4);
        assertFalse(registry.overCapacity(registry.federationDomainOf(node(0)).orElseThrow().federationDomainId()));
        assertTrue(registry.shareRunningFederationDomain(networks[0], networks[3]));
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

    private static FederationDomainRegistry registry() {
        return new FederationDomainRegistry(FederationDomainRecomputeBudget.standard());
    }

    /** A cable linked to a Switch whose faces attach {@code count} distinct networks, all in one subdomain. */
    private static NetworkId[] switchWith(FederationDomainRegistry registry, int count) {
        var networks = new NetworkId[count];
        for (int index = 0; index < count; index++) networks[index] = new NetworkId(new UUID(0, index + 1));
        var cablePorts = new LinkedHashMap<String, FederationDomainPortEvidence>();
        cablePorts.put("east", new FederationDomainPortEvidence.Federation(new FederationDomainPortId(node(1), "west")));
        registry.upsertNode(new FederationDomainNodeEvidence(node(0), FederationDomainNodeKind.CABLE, cablePorts));
        registry.upsertNode(switchEvidence(1, networks));
        return networks;
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
