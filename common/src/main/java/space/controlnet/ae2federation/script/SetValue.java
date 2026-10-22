package space.controlnet.ae2federation.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * A Python set: mutable and shared by reference. Items are held by {@link Ops#hashKey}, as dict keys are. Stricter
 * than Python, whose set order is arbitrary: items stay in the order they were added.
 */
public final class SetValue {
    /** Each item as first added, by its hash key. */
    final LinkedHashMap<Object, Object> items = new LinkedHashMap<>();
    /** Counts changes, so a {@code for} loop over the set notices them. */
    int version;

    public SetValue() {
    }

    boolean contains(Object item) {
        return items.containsKey(Ops.hashKey(item));
    }

    void add(Object item) {
        var hash = Ops.hashKey(item);
        if (items.containsKey(hash)) {
            return;
        }
        Ops.growMapping(items.size() + 1L);
        items.put(hash, item);
        version++;
    }

    boolean remove(Object item) {
        var hash = Ops.hashKey(item);
        if (!items.containsKey(hash)) {
            return false;
        }
        // An item may be None, so the removed value cannot say whether there was one.
        items.remove(hash);
        version++;
        return true;
    }

    void clear() {
        if (!items.isEmpty()) {
            version++;
        }
        items.clear();
    }

    public List<Object> items() {
        return new ArrayList<>(items.values());
    }

    public int size() {
        return items.size();
    }

    @Override
    public String toString() {
        return Ops.repr(this);
    }
}
