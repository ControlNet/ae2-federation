package space.controlnet.ae2federation.processing.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class PatternLaneMappingCodecTest {
    /** Four Pattern slots, two Lanes: slot 0 to Lane 0, slot 3 to both. */
    private static CompoundTag saved() {
        var mapping = new PatternLaneMapping(4, 2);
        mapping.replace(mapping.handle(0), Set.of(0));
        mapping.replace(mapping.handle(3), Set.of(0, 1));
        var tag = new CompoundTag();
        PatternLaneMappingCodec.write(mapping, tag);
        return tag;
    }

    @Test
    void aSaveRestoresTheSameShape() {
        var mapping = new PatternLaneMapping(4, 2);
        PatternLaneMappingCodec.read(mapping, saved());
        assertEquals(Set.of(0), mapping.lanesForSlot(0));
        assertEquals(Set.of(0, 1), mapping.lanesForSlot(3));
    }

    /** A Pattern Provider with more slots than the save, as when an addon that sets the slot count is added. */
    @Test
    void newSlotsStartUnmapped() {
        var mapping = new PatternLaneMapping(6, 2);
        PatternLaneMappingCodec.read(mapping, saved());
        assertEquals(Set.of(0, 1), mapping.lanesForSlot(3));
        assertEquals(Set.of(), mapping.lanesForSlot(4));
        assertEquals(Set.of(), mapping.lanesForSlot(5));
    }

    /** Fewer slots than the save: the slots that no longer exist lose their mapping, the rest keep it. */
    @Test
    void slotsThatNoLongerExistAreDropped() {
        var mapping = new PatternLaneMapping(2, 2);
        PatternLaneMappingCodec.read(mapping, saved());
        assertEquals(Set.of(0), mapping.lanesForSlot(0));
        assertEquals(Set.of(), mapping.lanesForSlot(1));
    }

    /** A Provider reloaded in place may already have more Lanes than the save; the extra Lanes start unmapped. */
    @Test
    void extraLanesStartUnmapped() {
        var mapping = new PatternLaneMapping(4, 3);
        PatternLaneMappingCodec.read(mapping, saved());
        assertEquals(Set.of(0, 1), mapping.lanesForSlot(3));
        assertEquals(Set.of(), mapping.slotsForLane(2));
    }

    @Test
    void aSaveNamingLanesTheProviderLacksIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PatternLaneMappingCodec.read(new PatternLaneMapping(4, 1), saved()));
    }
}
