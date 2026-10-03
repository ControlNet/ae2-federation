package space.controlnet.ae2federation.client.policy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Outlines the player asked for in the world, each running for its own time. A new one joins those still running;
 * asking again for the same blocks restarts that one instead of adding a copy. Free of Minecraft types so it stays
 * unit-testable.
 */
public final class HighlightSet<T> {
    /**
     * An outline blinks, which a player spots more easily: full brightness this long, then dim for the rest of the
     * period. Dim, not dark, so the blocks stay findable between flashes.
     */
    public static final long BLINK_ON_MILLIS = 500;
    public static final long BLINK_PERIOD_MILLIS = 800;
    public static final float DIM_BRIGHTNESS = 0.2f;

    private final long durationMillis;
    private final int capacity;
    private final LinkedHashMap<Object, Entry<T>> entries = new LinkedHashMap<>();

    public HighlightSet(long durationMillis, int capacity) {
        if (durationMillis < 1 || capacity < 1) throw new IllegalArgumentException("Duration and capacity must be positive");
        this.durationMillis = durationMillis;
        this.capacity = capacity;
    }

    public record Entry<T>(String dimension, T value, long startedAt, long expiresAt) {
        /**
         * The outline's brightness at {@code now}, 1 or {@link HighlightSet#DIM_BRIGHTNESS}; it starts at full, so it
         * shows the moment it is asked for.
         */
        public float brightness(long now) {
            return Math.floorMod(now - startedAt, BLINK_PERIOD_MILLIS) < BLINK_ON_MILLIS ? 1f : DIM_BRIGHTNESS;
        }
    }

    /** Starts the highlight {@code key}, or restarts it; past capacity the oldest one ends. */
    public synchronized void add(Object key, String dimension, T value, long now) {
        entries.remove(key);
        entries.put(key, new Entry<>(dimension, value, now, now + durationMillis));
        var oldest = entries.keySet().iterator();
        while (entries.size() > capacity) {
            oldest.next();
            oldest.remove();
        }
    }

    /** The highlights still running at {@code now}, oldest first; ended ones are dropped. */
    public synchronized List<Entry<T>> active(long now) {
        entries.values().removeIf(entry -> entry.expiresAt() < now);
        return new ArrayList<>(entries.values());
    }

    public synchronized void clear() {
        entries.clear();
    }
}
