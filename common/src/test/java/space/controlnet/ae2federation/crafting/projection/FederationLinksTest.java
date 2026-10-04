package space.controlnet.ae2federation.crafting.projection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;

class FederationLinksTest {
    private static final NetworkId C = network(1);
    private static final NetworkId M = network(2);
    private static final NetworkId S = network(3);
    private static final NetworkId X = network(4);

    @Test
    void membersOfOneDomainAreLinked() {
        assertTrue(FederationLinks.of(List.of(Set.of(C, S))).linked(C, S));
    }

    @Test
    void domainsSharingAMemberLinkTheirOtherMembers() {
        var links = FederationLinks.of(List.of(Set.of(C, M), Set.of(M, S)));
        assertTrue(links.linked(C, S));
        assertTrue(links.linked(S, C));
    }

    @Test
    void separateDomainsAreNotLinked() {
        var links = FederationLinks.of(List.of(Set.of(C, M), Set.of(S, X)));
        assertFalse(links.linked(C, S));
    }

    @Test
    void aNetworkInNoDomainIsLinkedToNothing() {
        var links = FederationLinks.of(List.of(Set.of(C, M)));
        assertFalse(links.linked(C, S));
        assertFalse(links.linked(S, S));
    }

    private static NetworkId network(long value) {
        return new NetworkId(new UUID(0, value));
    }
}
