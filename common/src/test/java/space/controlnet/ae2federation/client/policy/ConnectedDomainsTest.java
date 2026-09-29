package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConnectedDomainsTest {
    /** Domains by name, each with its member networks. */
    private static ConnectedDomains.Reach<String, String> reach(Map<String, List<String>> domains, String current,
            List<String> start) {
        return ConnectedDomains.reach(start, current,
                network -> domains.entrySet().stream().filter(entry -> entry.getValue().contains(network))
                        .map(Map.Entry::getKey).sorted().toList(),
                domain -> domains.getOrDefault(domain, List.of()));
    }

    @Test
    void aChainOfDomainsIsFollowedToItsEnd() {
        // A shares b with B, B shares c with C, C shares d with D: all are connected to A.
        var domains = Map.of("A", List.of("a", "b"), "B", List.of("b", "c"), "C", List.of("c", "d"), "D", List.of("d", "e"));
        var reach = reach(domains, "A", List.of("a", "b"));
        assertEquals(List.of("B", "C", "D"), reach.domains());
        assertEquals(List.of("c", "d", "e"), reach.networks());
    }

    @Test
    void nearerDomainsComeFirst() {
        // B and E both share a network with A; C is only reached through B.
        var domains = Map.of("A", List.of("a", "b"), "B", List.of("b", "c"), "C", List.of("c", "x"),
                "E", List.of("a", "y"));
        var reach = reach(domains, "A", List.of("a", "b"));
        assertEquals(List.of("E", "B", "C"), reach.domains());
        assertEquals(List.of("y", "c", "x"), reach.networks());
    }

    @Test
    void aCycleIsVisitedOnce() {
        var domains = Map.of("A", List.of("a", "b"), "B", List.of("b", "c"), "C", List.of("c", "a"));
        var reach = reach(domains, "A", List.of("a", "b"));
        assertEquals(List.of("C", "B"), reach.domains());
        assertEquals(List.of("c"), reach.networks());
    }

    @Test
    void unconnectedDomainsStayOut() {
        var domains = Map.of("A", List.of("a", "b"), "B", List.of("b", "c"), "Z", List.of("z1", "z2"));
        var reach = reach(domains, "A", List.of("a", "b"));
        assertEquals(List.of("B"), reach.domains());
        assertEquals(List.of("c"), reach.networks());
    }

    @Test
    void aDomainWithoutOthersReachesNothing() {
        var reach = reach(Map.of("A", List.of("a", "b")), "A", List.of("a", "b"));
        assertEquals(List.of(), reach.domains());
        assertEquals(List.of(), reach.networks());
    }
}
