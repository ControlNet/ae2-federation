package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HighlightSetTest {
    private static final String OVERWORLD = "minecraft:overworld";

    @Test
    void aSecondHighlightRunsBesideTheFirst() {
        var set = new HighlightSet<String>(10_000, 16);
        set.add("a", OVERWORLD, "first", 0);
        set.add("b", OVERWORLD, "second", 4_000);
        var active = set.active(9_000);
        assertEquals(2, active.size());
        assertEquals("first", active.get(0).value());
        assertEquals("second", active.get(1).value());
    }

    @Test
    void eachHighlightEndsOnItsOwnTime() {
        var set = new HighlightSet<String>(10_000, 16);
        set.add("a", OVERWORLD, "first", 0);
        set.add("b", OVERWORLD, "second", 4_000);
        var active = set.active(12_000);
        assertEquals(1, active.size());
        assertEquals("second", active.get(0).value());
        assertTrue(set.active(14_001).isEmpty());
    }

    @Test
    void highlightingTheSameBlocksAgainRestartsInsteadOfAddingACopy() {
        var set = new HighlightSet<String>(10_000, 16);
        set.add("a", OVERWORLD, "first", 0);
        set.add("a", OVERWORLD, "first again", 8_000);
        var active = set.active(12_000);
        assertEquals(1, active.size());
        assertEquals("first again", active.get(0).value());
        assertEquals(8_000, active.get(0).startedAt());
    }

    @Test
    void theOldestGivesWayAtCapacity() {
        var set = new HighlightSet<String>(10_000, 2);
        set.add("a", OVERWORLD, "first", 0);
        set.add("b", OVERWORLD, "second", 1);
        set.add("c", OVERWORLD, "third", 2);
        var active = set.active(3);
        assertEquals(2, active.size());
        assertEquals("second", active.get(0).value());
        assertEquals("third", active.get(1).value());
    }

    @Test
    void blinksBetweenFullAndDimStartingFullSoItShowsAtOnce() {
        var set = new HighlightSet<String>(10_000, 16);
        set.add("a", OVERWORLD, "first", 1_000);
        var entry = set.active(1_000).get(0);
        assertEquals(1f, entry.brightness(1_000));
        assertEquals(1f, entry.brightness(1_000 + HighlightSet.BLINK_ON_MILLIS - 1));
        assertEquals(0.2f, entry.brightness(1_000 + HighlightSet.BLINK_ON_MILLIS));
        assertEquals(0.2f, entry.brightness(1_000 + HighlightSet.BLINK_PERIOD_MILLIS - 1));
        assertEquals(1f, entry.brightness(1_000 + HighlightSet.BLINK_PERIOD_MILLIS));
    }

    @Test
    void clearEndsEveryHighlight() {
        var set = new HighlightSet<String>(10_000, 16);
        set.add("a", OVERWORLD, "first", 0);
        set.add("b", "minecraft:the_nether", "second", 0);
        set.clear();
        assertTrue(set.active(1).isEmpty());
    }
}
