package space.controlnet.ae2federation.processing.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

final class PatternLaneMappingCodec {
    private static final int SCHEMA_VERSION = 1;

    private PatternLaneMappingCodec() {
    }

    static void write(PatternLaneMapping mapping, CompoundTag tag) {
        tag.putInt("schema", SCHEMA_VERSION);
        tag.putInt("patternSlots", mapping.patternSlots());
        tag.putInt("laneCount", mapping.laneCount());
        tag.putLongArray("generations", mapping.generations());
        var assignments = new ListTag();
        for (int slot = 0; slot < mapping.patternSlots(); slot++) {
            var entry = new CompoundTag();
            entry.putInt("slot", slot);
            entry.putIntArray("lanes", List.copyOf(mapping.lanesForSlot(slot)));
            assignments.add(entry);
        }
        tag.put("assignments", assignments);
    }

    /**
     * Restores a saved mapping into {@code mapping}. The saved Pattern slot count may differ from the current one, as
     * when an addon changes how many slots AE2's Pattern Provider has: slots both share keep their Lanes, saved slots
     * past the end are dropped, and new slots start unmapped. The provider may also already have more Lanes than the
     * save, as when it is reloaded in place; those Lanes start unmapped.
     */
    static Set<Integer> read(PatternLaneMapping mapping, CompoundTag tag) {
        var savedSlots = tag.getInt("patternSlots");
        if (tag.getInt("schema") != SCHEMA_VERSION || savedSlots < 0 || tag.getInt("laneCount") > mapping.laneCount()) {
            throw new IllegalArgumentException("Pattern Lane mapping dimensions or schema do not match");
        }
        var assignments = tag.getList("assignments", Tag.TAG_COMPOUND);
        var savedGenerations = tag.getLongArray("generations");
        if (assignments.size() != savedSlots || savedGenerations.length != savedSlots) {
            throw new IllegalArgumentException("Pattern Lane mapping persistence is incomplete");
        }
        var saved = new ArrayList<Set<Integer>>(Collections.nCopies(savedSlots, null));
        for (var raw : assignments) {
            var entry = (CompoundTag) raw;
            var slot = entry.getInt("slot");
            if (slot < 0 || slot >= savedSlots || saved.get(slot) != null) {
                throw new IllegalArgumentException("Invalid or duplicate persisted Pattern slot mapping");
            }
            var lanes = new TreeSet<Integer>();
            for (var lane : entry.getIntArray("lanes")) {
                lanes.add(lane);
            }
            saved.set(slot, lanes);
        }
        var loaded = new ArrayList<Set<Integer>>();
        var generations = mapping.generations();
        for (int slot = 0; slot < mapping.patternSlots(); slot++) {
            if (slot < savedSlots) {
                loaded.add(saved.get(slot));
                generations[slot] = savedGenerations[slot];
            } else {
                loaded.add(Set.of());
            }
        }
        return mapping.restore(loaded, generations);
    }
}
