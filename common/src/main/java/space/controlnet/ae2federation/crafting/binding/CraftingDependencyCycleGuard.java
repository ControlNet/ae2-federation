package space.controlnet.ae2federation.crafting.binding;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyKey;

public final class CraftingDependencyCycleGuard {
    private CraftingDependencyCycleGuard() {
    }

    public static Set<PolicyKey> cyclicKeys(Set<PolicyKey> relationships) {
        Map<NetworkId, Set<NetworkId>> dependencies = new HashMap<>();
        for (var relationship : relationships) {
            dependencies.computeIfAbsent(relationship.consumerNetworkId(), ignored -> new HashSet<>())
                    .add(relationship.providerNetworkId());
        }

        var cyclic = new HashSet<PolicyKey>();
        for (var relationship : relationships) {
            if (reachable(dependencies, relationship.providerNetworkId(), relationship.consumerNetworkId())) {
                cyclic.add(relationship);
            }
        }
        return Set.copyOf(cyclic);
    }

    private static boolean reachable(Map<NetworkId, Set<NetworkId>> dependencies, NetworkId start,
            NetworkId destination) {
        var frontier = new ArrayDeque<NetworkId>();
        var visited = new HashSet<NetworkId>();
        frontier.add(start);
        while (!frontier.isEmpty()) {
            var current = frontier.removeFirst();
            if (current.equals(destination)) {
                return true;
            }
            if (visited.add(current)) {
                frontier.addAll(dependencies.getOrDefault(current, Set.of()));
            }
        }
        return false;
    }
}
