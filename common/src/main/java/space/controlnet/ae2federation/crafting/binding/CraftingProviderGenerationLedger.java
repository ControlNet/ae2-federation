package space.controlnet.ae2federation.crafting.binding;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import space.controlnet.ae2federation.identity.NetworkId;

final class CraftingProviderGenerationLedger<S, P> {
    private final Map<NetworkId, CraftingProviderSnapshot<S, P>> current = new HashMap<>();
    private final Map<NetworkId, Long> generations = new HashMap<>();

    CraftingProviderSnapshot<S, P> update(NetworkId origin, S service, List<P> providers) {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(service);
        var snapshotProviders = List.copyOf(providers);
        var unique = Collections.newSetFromMap(new IdentityHashMap<P, Boolean>());
        if (snapshotProviders.stream().anyMatch(provider -> !unique.add(Objects.requireNonNull(provider)))) {
            throw new IllegalArgumentException("A native crafting provider cannot be published twice");
        }
        var active = current.get(origin);
        if (active != null && active.service() == service && sameIdentity(active.providers(), snapshotProviders)) {
            return active;
        }
        var generation = new ProviderGeneration(Math.incrementExact(generations.getOrDefault(origin, 0L)));
        var replacement = new CraftingProviderSnapshot<>(origin, generation, service, snapshotProviders);
        current.put(origin, replacement);
        generations.put(origin, generation.value());
        return replacement;
    }

    boolean isCurrent(CraftingProviderSnapshot<S, P> snapshot) {
        return current.get(snapshot.origin()) == snapshot;
    }

    void invalidate(NetworkId origin) {
        current.remove(origin);
        generations.put(origin, Math.incrementExact(generations.getOrDefault(origin, 0L)));
    }

    void clear() {
        current.clear();
        generations.clear();
    }

    private static <P> boolean sameIdentity(List<P> first, List<P> second) {
        if (first.size() != second.size()) {
            return false;
        }
        var unmatched = Collections.newSetFromMap(new IdentityHashMap<P, Boolean>());
        unmatched.addAll(first);
        for (var candidate : second) {
            if (!unmatched.remove(candidate)) {
                return false;
            }
        }
        return unmatched.isEmpty();
    }
}
