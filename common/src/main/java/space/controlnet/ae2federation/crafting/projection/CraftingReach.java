package space.controlnet.ae2federation.crafting.projection;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import space.controlnet.ae2federation.identity.NetworkId;

/**
 * Which networks' pattern providers each consumer can use, as storage re-export works: a consumer reaches every
 * network its own active crafting rules name, and through a reached network M also the networks M's rules name whose
 * rule re-exports ("M uses N" with re-export lets whoever uses M reach N). A consumer never reaches itself, so mutual
 * and circular rules end.
 */
final class CraftingReach {
    /** An active crafting rule: {@code consumer} uses {@code provider}'s crafting, passed on when {@code reexport}. */
    record Edge(NetworkId consumer, NetworkId provider, boolean reexport) {
    }

    private CraftingReach() {
    }

    static Map<NetworkId, Set<NetworkId>> compute(List<Edge> edges) {
        var outgoing = new HashMap<NetworkId, List<Edge>>();
        for (var edge : edges) {
            outgoing.computeIfAbsent(edge.consumer(), ignored -> new java.util.ArrayList<>()).add(edge);
        }
        var reach = new HashMap<NetworkId, Set<NetworkId>>();
        for (var consumer : outgoing.keySet()) {
            var reached = new LinkedHashSet<NetworkId>();
            var queue = new ArrayDeque<NetworkId>();
            for (var edge : outgoing.get(consumer)) {
                if (!edge.provider().equals(consumer) && reached.add(edge.provider())) queue.add(edge.provider());
            }
            while (!queue.isEmpty()) {
                var through = queue.removeFirst();
                for (var edge : outgoing.getOrDefault(through, List.of())) {
                    if (edge.reexport() && !edge.provider().equals(consumer) && reached.add(edge.provider())) {
                        queue.add(edge.provider());
                    }
                }
            }
            reach.put(consumer, Set.copyOf(reached));
        }
        return Map.copyOf(reach);
    }
}
