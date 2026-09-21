package space.controlnet.ae2federation.energy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import space.controlnet.ae2federation.identity.NetworkId;

final class EnergyProviderGenerationLedger<S, P> {
    private final Map<NetworkId, EnergyProviderSnapshot<S, P>> current = new HashMap<>();
    private final Map<NetworkId, Long> generations = new HashMap<>();

    EnergyProviderSnapshot<S, P> update(NetworkId origin, S service, List<P> sources) {
        Objects.requireNonNull(origin);
        Objects.requireNonNull(service);
        var snapshotSources = List.copyOf(sources);
        var unique = new HashSet<P>();
        if (snapshotSources.stream().anyMatch(source -> !unique.add(Objects.requireNonNull(source)))) {
            throw new IllegalArgumentException("A native energy source cannot be published twice");
        }
        var active = current.get(origin);
        if (active != null && active.service() == service && sameIdentity(active.sources(), snapshotSources)) {
            return active;
        }
        var generation = new EnergyProviderGeneration(Math.incrementExact(generations.getOrDefault(origin, 0L)));
        var replacement = new EnergyProviderSnapshot<>(origin, generation, service, snapshotSources);
        current.put(origin, replacement);
        generations.put(origin, generation.value());
        return replacement;
    }

    boolean isCurrent(EnergyProviderSnapshot<S, P> snapshot) {
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
        return first.equals(second);
    }
}
