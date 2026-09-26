package space.controlnet.ae2federation.storage.subscription;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class IdentityListenerRegistry<S, L> {
    private final Map<S, List<Registration>> listeners = new IdentityHashMap<>();
    private final Map<S, Integer> cursors = new IdentityHashMap<>();
    private long nextId;
    private int registrations;
    private int removals;

    public Registration register(S source, L listener) {
        Objects.requireNonNull(source);
        Objects.requireNonNull(listener);
        nextId = Math.incrementExact(nextId);
        var registration = new Registration(nextId, source, listener);
        listeners.computeIfAbsent(source, ignored -> new ArrayList<>()).add(registration);
        registrations++;
        return registration;
    }

    public void visitAll(S source, Consumer<L> visitor) {
        var registered = listeners.get(source);
        if (registered == null) {
            return;
        }
        new ArrayList<>(registered).forEach(registration -> visitor.accept(registration.listener));
    }

    public void visitNext(S source, Consumer<L> visitor) {
        var registered = listeners.get(source);
        if (registered == null || registered.isEmpty()) {
            return;
        }
        var cursor = cursors.getOrDefault(source, 0);
        var index = Math.floorMod(cursor, registered.size());
        cursors.put(source, Math.incrementExact(index));
        visitor.accept(registered.get(index).listener);
    }

    public boolean hasListeners(S source) {
        var registered = listeners.get(source);
        return registered != null && !registered.isEmpty();
    }

    public int closeAll(S source) {
        var registered = listeners.get(source);
        if (registered == null) {
            return 0;
        }
        var closed = 0;
        for (var registration : new ArrayList<>(registered)) {
            if (registration.active()) {
                registration.close();
                closed++;
            }
        }
        return closed;
    }

    public int registrationCount() {
        return registrations;
    }

    public int removalCount() {
        return removals;
    }

    public int activeCount() {
        return listeners.values().stream().mapToInt(List::size).sum();
    }

    public int activeCount(S source) {
        var registered = listeners.get(source);
        return registered == null ? 0 : registered.size();
    }

    public final class Registration implements AutoCloseable {
        private final long id;
        private final S source;
        private L listener;

        private Registration(long id, S source, L listener) {
            this.id = id;
            this.source = source;
            this.listener = listener;
        }

        public long id() {
            return id;
        }

        public boolean active() {
            return listener != null;
        }

        @Override
        public void close() {
            if (listener == null) {
                return;
            }
            var registered = listeners.get(source);
            if (registered != null && registered.remove(this)) {
                removals++;
                if (registered.isEmpty()) {
                    listeners.remove(source);
                    cursors.remove(source);
                }
            }
            listener = null;
        }
    }
}
