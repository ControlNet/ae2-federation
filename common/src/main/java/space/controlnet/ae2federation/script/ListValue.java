package space.controlnet.ae2federation.script;

import java.util.ArrayList;
import java.util.List;

/** A Python list: mutable and shared by reference, so two names can hold the same list. */
public final class ListValue {
    final List<Object> items;

    public ListValue(List<Object> items) {
        this.items = new ArrayList<>(items);
    }

    public List<Object> items() {
        return items;
    }

    @Override
    public String toString() {
        return Ops.shown(this);
    }
}
