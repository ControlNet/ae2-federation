package space.controlnet.ae2federation.processing.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

public final class PatternLaneMapping {
    private final int patternSlots;
    private int laneCount;
    private final List<Set<Integer>> lanesBySlot;
    private final long[] generations;

    public PatternLaneMapping(int patternSlots, int laneCount) {
        if (patternSlots < 1 || laneCount < 0) {
            throw new IllegalArgumentException("Pattern slot and Lane counts must be positive");
        }
        this.patternSlots = patternSlots;
        this.laneCount = laneCount;
        lanesBySlot = new ArrayList<>(patternSlots);
        for (int slot = 0; slot < patternSlots; slot++) {
            lanesBySlot.add(Set.of());
        }
        generations = new long[patternSlots];
    }

    /** Appends one Lane index; existing assignments and slot generations are unchanged. */
    public int addLane() {
        return laneCount++;
    }

    public PatternSlotHandle handle(int slot) {
        checkSlot(slot);
        return new PatternSlotHandle(slot, generations[slot]);
    }

    public Optional<Set<Integer>> replace(PatternSlotHandle handle, Set<Integer> laneIndexes) {
        checkSlot(handle.slot());
        var replacement = immutableIndexes(laneIndexes);
        if (handle.generation() != generations[handle.slot()]) {
            return Optional.empty();
        }
        var previous = lanesBySlot.get(handle.slot());
        var changed = new TreeSet<>(previous);
        changed.addAll(replacement);
        if (!previous.equals(replacement)) {
            lanesBySlot.set(handle.slot(), replacement);
            generations[handle.slot()] = Math.incrementExact(generations[handle.slot()]);
        }
        return Optional.of(Collections.unmodifiableSet(changed));
    }

    public Set<Integer> lanesForSlot(int slot) {
        checkSlot(slot);
        return lanesBySlot.get(slot);
    }

    public Set<Integer> slotsForLane(int laneIndex) {
        checkLane(laneIndex);
        var slots = new TreeSet<Integer>();
        for (int slot = 0; slot < patternSlots; slot++) {
            if (lanesBySlot.get(slot).contains(laneIndex)) {
                slots.add(slot);
            }
        }
        return Collections.unmodifiableSet(slots);
    }

    public boolean isAssigned(int laneIndex, int slot) {
        checkLane(laneIndex);
        checkSlot(slot);
        return lanesBySlot.get(slot).contains(laneIndex);
    }

    Set<Integer> restore(List<Set<Integer>> loaded, long[] loadedGenerations) {
        if (loaded.size() != patternSlots || loadedGenerations.length != patternSlots) {
            throw new IllegalArgumentException("Pattern Lane mapping persistence is incomplete");
        }
        var changed = new TreeSet<Integer>();
        for (int slot = 0; slot < patternSlots; slot++) {
            var assignment = immutableIndexes(loaded.get(slot));
            changed.addAll(lanesBySlot.get(slot));
            changed.addAll(assignment);
            lanesBySlot.set(slot, assignment);
            generations[slot] = loadedGenerations[slot];
        }
        return Collections.unmodifiableSet(changed);
    }

    int patternSlots() {
        return patternSlots;
    }

    int laneCount() {
        return laneCount;
    }

    long[] generations() {
        return generations.clone();
    }

    private Set<Integer> immutableIndexes(Set<Integer> laneIndexes) {
        var ordered = new TreeSet<Integer>();
        for (var laneIndex : laneIndexes) {
            checkLane(laneIndex);
            ordered.add(laneIndex);
        }
        return Collections.unmodifiableSet(ordered);
    }

    private void checkSlot(int slot) {
        if (slot < 0 || slot >= patternSlots) {
            throw new IndexOutOfBoundsException("Pattern slot " + slot + " is outside 0.." + (patternSlots - 1));
        }
    }

    private void checkLane(int laneIndex) {
        if (laneIndex < 0 || laneIndex >= laneCount) {
            throw new IllegalArgumentException("Lane " + laneIndex + " is outside 0.." + (laneCount - 1));
        }
    }
}
