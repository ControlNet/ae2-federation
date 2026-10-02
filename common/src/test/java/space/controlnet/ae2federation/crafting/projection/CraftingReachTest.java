package space.controlnet.ae2federation.crafting.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;

class CraftingReachTest {
    private static final NetworkId A = network(1);
    private static final NetworkId B = network(2);
    private static final NetworkId C = network(3);
    private static final NetworkId D = network(4);

    @Test
    void aRuleReachesItsProvider() {
        assertEquals(Map.of(A, Set.of(B)), CraftingReach.compute(List.of(new CraftingReach.Edge(A, B, false))));
    }

    @Test
    void aChainNeedsTheUpstreamRuleToReexport() {
        // A uses B, B uses C: A reaches C only when "B uses C" re-exports.
        assertEquals(Map.of(A, Set.of(B), B, Set.of(C)), CraftingReach.compute(List.of(
                new CraftingReach.Edge(A, B, false), new CraftingReach.Edge(B, C, false))));
        assertEquals(Map.of(A, Set.of(B, C), B, Set.of(C)), CraftingReach.compute(List.of(
                new CraftingReach.Edge(A, B, false), new CraftingReach.Edge(B, C, true))));
        // The consumer's own hop re-exporting changes nothing for it.
        assertEquals(Map.of(A, Set.of(B), B, Set.of(C)), CraftingReach.compute(List.of(
                new CraftingReach.Edge(A, B, true), new CraftingReach.Edge(B, C, false))));
    }

    @Test
    void everyFurtherHopMustReexportToo() {
        var edges = List.of(new CraftingReach.Edge(A, B, false), new CraftingReach.Edge(B, C, true),
                new CraftingReach.Edge(C, D, false));
        assertEquals(Set.of(B, C), CraftingReach.compute(edges).get(A));
        var open = List.of(new CraftingReach.Edge(A, B, false), new CraftingReach.Edge(B, C, true),
                new CraftingReach.Edge(C, D, true));
        assertEquals(Set.of(B, C, D), CraftingReach.compute(open).get(A));
    }

    @Test
    void mutualAndCircularRulesNeverReachTheConsumerItself() {
        var mutual = CraftingReach.compute(List.of(new CraftingReach.Edge(A, B, true), new CraftingReach.Edge(B, A, true)));
        assertEquals(Map.of(A, Set.of(B), B, Set.of(A)), mutual);
        var ring = CraftingReach.compute(List.of(new CraftingReach.Edge(A, B, true), new CraftingReach.Edge(B, C, true),
                new CraftingReach.Edge(C, A, true)));
        assertEquals(Set.of(B, C), ring.get(A));
        assertEquals(Set.of(C, A), ring.get(B));
    }

    private static NetworkId network(long value) {
        return new NetworkId(new UUID(0, value));
    }
}
