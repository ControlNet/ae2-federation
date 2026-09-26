package space.controlnet.ae2federation.storage.subscription;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

final class BoundedKeyCursor<K> {
    private final LinkedHashSet<K> known = new LinkedHashSet<>();
    private final int maximumSize;
    private int cursor;

    BoundedKeyCursor(int maximumSize) {
        if (maximumSize < 1) {
            throw new IllegalArgumentException("Maximum retained keys must be positive");
        }
        this.maximumSize = maximumSize;
    }

    boolean add(K key) {
        var retained = java.util.Objects.requireNonNull(key);
        if (known.contains(retained)) {
            return false;
        }
        if (known.size() == maximumSize) {
            throw new KeyRetentionOverflowException(maximumSize);
        }
        known.add(retained);
        return true;
    }

    List<K> addAll(Iterable<? extends K> keys) {
        var added = new ArrayList<K>();
        for (var key : keys) {
            if (add(key)) {
                added.add(key);
            }
        }
        return List.copyOf(added);
    }

    List<K> next(int budget) {
        if (budget < 1) {
            throw new IllegalArgumentException("Key budget must be positive");
        }
        if (known.isEmpty()) {
            return List.of();
        }
        var keys = List.copyOf(known);
        var count = Math.min(budget, keys.size());
        var result = new ArrayList<K>(count);
        for (var offset = 0; offset < count; offset++) {
            result.add(keys.get(Math.floorMod(cursor + offset, keys.size())));
        }
        cursor = Math.floorMod(cursor + count, keys.size());
        return List.copyOf(result);
    }

    int size() {
        return known.size();
    }

    boolean contains(K key) {
        return known.contains(key);
    }

    List<K> retained() {
        return List.copyOf(known);
    }
}
