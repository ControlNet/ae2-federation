package space.controlnet.ae2federation.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * A Python dict: mutable, shared by reference, in insertion order. Keys are held by {@link Ops#hashKey}, so
 * {@code 1}, {@code 1.0} and {@code True} are one key, and the first key written stays, as in Python.
 */
public final class DictValue {
    /** One entry: the key as first written and its value. */
    static final class Entry {
        final Object key;
        Object value;

        Entry(Object key, Object value) {
            this.key = key;
            this.value = value;
        }
    }

    final LinkedHashMap<Object, Entry> entries = new LinkedHashMap<>();
    /** Counts changes to the keys, so a {@code for} loop over the dict notices them. */
    int version;

    public DictValue() {
    }

    /** The value of {@code key}, or {@code null} for a key that is not there (a value may also be None). */
    Entry entry(Object key) {
        return entries.get(Ops.hashKey(key));
    }

    public void put(Object key, Object value) {
        var hash = Ops.hashKey(key);
        var entry = entries.get(hash);
        if (entry != null) {
            entry.value = value;
            return;
        }
        Ops.growMapping(entries.size() + 1L);
        entries.put(hash, new Entry(key, value));
        version++;
    }

    Entry remove(Object key) {
        var entry = entries.remove(Ops.hashKey(key));
        if (entry != null) {
            version++;
        }
        return entry;
    }

    void clear() {
        if (!entries.isEmpty()) {
            version++;
        }
        entries.clear();
    }

    public List<Object> keys() {
        var keys = new ArrayList<>();
        entries.values().forEach(entry -> keys.add(entry.key));
        return keys;
    }

    public List<Object> values() {
        var values = new ArrayList<>();
        entries.values().forEach(entry -> values.add(entry.value));
        return values;
    }

    public int size() {
        return entries.size();
    }

    @Override
    public String toString() {
        return Ops.repr(this);
    }
}
