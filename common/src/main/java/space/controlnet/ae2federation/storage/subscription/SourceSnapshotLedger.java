package space.controlnet.ae2federation.storage.subscription;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class SourceSnapshotLedger<K> implements AutoCloseable {
    private final long generation;
    private final int pendingLimit;
    private final Consumer<SourceQuantityUpdate<K>> updateConsumer;
    private final Map<K, Long> amounts = new HashMap<>();
    private final ArrayDeque<PendingEvent<K>> pending = new ArrayDeque<>();
    private long eventVersion;
    private long snapshotVersion;
    private boolean snapshotting;
    private boolean initialized;
    private boolean closed;

    public SourceSnapshotLedger(long generation, int pendingLimit, Consumer<SourceQuantityUpdate<K>> updateConsumer) {
        if (generation < 1 || pendingLimit < 1) {
            throw new IllegalArgumentException("Generation and pending limit must be positive");
        }
        this.generation = generation;
        this.pendingLimit = pendingLimit;
        this.updateConsumer = Objects.requireNonNull(updateConsumer);
    }

    public boolean beginSnapshot(long eventGeneration) {
        if (!accepts(eventGeneration)) {
            return false;
        }
        snapshotting = true;
        pending.clear();
        return true;
    }

    public boolean completeSnapshot(long eventGeneration, Map<K, Long> snapshot, boolean publishChanges) {
        Objects.requireNonNull(snapshot);
        if (!accepts(eventGeneration) || !snapshotting) {
            return false;
        }
        var replacement = normalized(snapshot);
        snapshotVersion = Math.incrementExact(snapshotVersion);
        if (publishChanges) {
            var affected = new LinkedHashSet<K>();
            affected.addAll(amounts.keySet());
            affected.addAll(replacement.keySet());
            for (var key : affected) {
                publishIfChanged(key, replacement.getOrDefault(key, 0L));
            }
        } else {
            amounts.clear();
            amounts.putAll(replacement);
        }
        snapshotting = false;
        initialized = true;
        while (!pending.isEmpty() && !closed) {
            var event = pending.removeFirst();
            apply(event.key(), event.value(), event.semantics());
        }
        return !closed;
    }

    public boolean acceptAbsolute(long eventGeneration, K key, long absoluteAmount) {
        return accept(eventGeneration, key, absoluteAmount, EventSemantics.ABSOLUTE);
    }

    public boolean acceptDelta(long eventGeneration, K key, long delta) {
        return accept(eventGeneration, key, delta, EventSemantics.DELTA);
    }

    public long amount(K key) {
        return amounts.getOrDefault(Objects.requireNonNull(key), 0L);
    }

    public long snapshotVersion() {
        return snapshotVersion;
    }

    public long eventVersion() {
        return eventVersion;
    }

    public boolean initialized() {
        return initialized;
    }

    public boolean closed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
        snapshotting = false;
        pending.clear();
    }

    private boolean accept(long eventGeneration, K key, long value, EventSemantics semantics) {
        Objects.requireNonNull(key);
        if (!accepts(eventGeneration)) {
            return false;
        }
        if (semantics == EventSemantics.ABSOLUTE && value < 0) {
            throw new IllegalArgumentException("Absolute source amount cannot be negative");
        }
        if (snapshotting) {
            if (pending.size() == pendingLimit) {
                close();
                throw new SnapshotQueueOverflowException();
            }
            pending.addLast(new PendingEvent<>(key, value, semantics));
            return true;
        }
        if (!initialized) {
            return false;
        }
        apply(key, value, semantics);
        return true;
    }

    private void apply(K key, long value, EventSemantics semantics) {
        var absolute = semantics == EventSemantics.ABSOLUTE ? value : Math.addExact(amount(key), value);
        if (absolute < 0) {
            throw new IllegalArgumentException("Delta would make the source amount negative");
        }
        publishIfChanged(key, absolute);
    }

    private void publishIfChanged(K key, long absolute) {
        var previous = amount(key);
        if (previous == absolute) {
            return;
        }
        if (absolute == 0) {
            amounts.remove(key);
        } else {
            amounts.put(key, absolute);
        }
        eventVersion = Math.incrementExact(eventVersion);
        updateConsumer.accept(new SourceQuantityUpdate<>(key, previous, absolute, eventVersion, snapshotVersion));
    }

    private boolean accepts(long eventGeneration) {
        return !closed && eventGeneration == generation;
    }

    private static <K> Map<K, Long> normalized(Map<K, Long> snapshot) {
        var result = new HashMap<K, Long>();
        snapshot.forEach((key, value) -> {
            Objects.requireNonNull(key);
            Objects.requireNonNull(value);
            if (value < 0) {
                throw new IllegalArgumentException("Snapshot amount cannot be negative");
            }
            if (value > 0) {
                result.put(key, value);
            }
        });
        return result;
    }

    private enum EventSemantics {
        ABSOLUTE,
        DELTA
    }

    private record PendingEvent<K>(K key, long value, EventSemantics semantics) {
    }
}
