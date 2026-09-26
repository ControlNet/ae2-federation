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

    static Set<Integer> read(PatternLaneMapping mapping, CompoundTag tag) {
        if (tag.getInt("schema") != SCHEMA_VERSION || tag.getInt("patternSlots") != mapping.patternSlots()
                || tag.getInt("laneCount") != mapping.laneCount()) {
            throw new IllegalArgumentException("Pattern Lane mapping dimensions or schema do not match");
        }
        var assignments = tag.getList("assignments", Tag.TAG_COMPOUND);
        if (assignments.size() != mapping.patternSlots()) {
            throw new IllegalArgumentException("Pattern Lane mapping persistence is incomplete");
        }
        var loaded = new ArrayList<Set<Integer>>(Collections.nCopies(mapping.patternSlots(), null));
        for (var raw : assignments) {
            var entry = (CompoundTag) raw;
            var slot = entry.getInt("slot");
            if (slot < 0 || slot >= mapping.patternSlots() || loaded.get(slot) != null) {
                throw new IllegalArgumentException("Invalid or duplicate persisted Pattern slot mapping");
            }
            var lanes = new TreeSet<Integer>();
            for (var lane : entry.getIntArray("lanes")) {
                lanes.add(lane);
            }
            loaded.set(slot, lanes);
        }
        if (loaded.contains(null)) {
            throw new IllegalArgumentException("Missing persisted Pattern slot mapping");
        }
        return mapping.restore(loaded, tag.getLongArray("generations"));
    }
}
