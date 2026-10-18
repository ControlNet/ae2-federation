package space.controlnet.ae2federation.function;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A record: a player's class instance, shared by reference with writable fields, or a built-in record such as
 * {@code Stack} whose fields cannot be written. Equal to another record of the same type with equal fields.
 */
public final class RecordValue {
    final String type;
    final Map<String, Object> fields;
    final boolean readOnly;

    public RecordValue(String type, Map<String, Object> fields, boolean readOnly) {
        this.type = type;
        this.fields = new LinkedHashMap<>(fields);
        this.readOnly = readOnly;
    }

    public String type() {
        return type;
    }

    public Object field(String name) {
        return fields.get(name);
    }

    @Override
    public String toString() {
        return Ops.repr(this);
    }
}
