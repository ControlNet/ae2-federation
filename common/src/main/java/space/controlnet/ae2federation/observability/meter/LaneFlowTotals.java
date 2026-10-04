package space.controlnet.ae2federation.observability.meter;

import java.util.Collections;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * What some Provider lanes moved over the flow window: the number of deliveries, and the amount of each resource type
 * apart, by key type id, because an item count and a fluid volume do not add up.
 */
public final class LaneFlowTotals {
    private long events;
    private final SortedMap<String, Long> amounts = new TreeMap<>();

    public void add(String keyType, PairFlowWindow.Summary summary) {
        if (!summary.active()) return;
        events = PairFlowWindow.saturatedAdd(events, summary.events());
        amounts.merge(keyType, summary.amount(), PairFlowWindow::saturatedAdd);
    }

    public long events() {
        return events;
    }

    public SortedMap<String, Long> amounts() {
        return Collections.unmodifiableSortedMap(amounts);
    }

    public boolean active() {
        return events > 0;
    }
}
