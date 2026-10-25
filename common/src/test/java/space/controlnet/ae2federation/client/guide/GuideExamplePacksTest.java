package space.controlnet.ae2federation.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GuideExamplePacksTest {
    private static final GuideExamplePacks.Pack PLAIN = new GuideExamplePacks.Pack("plain", List.of("a"));
    private static final GuideExamplePacks.Pack FIRST = new GuideExamplePacks.Pack("first", List.of("x", "a"), "variant");
    private static final GuideExamplePacks.Pack SECOND = new GuideExamplePacks.Pack("second", List.of("x", "b"), "variant");
    private static final GuideExamplePacks.Pack THIRD = new GuideExamplePacks.Pack("third", List.of("x", "c"), "variant");
    private static final List<GuideExamplePacks.Pack> PACKS = List.of(PLAIN, FIRST, SECOND, THIRD);

    @Test
    void aGroupShowsOnlyItsFirstPackWhoseModsAreLoaded() {
        assertEquals(List.of(PLAIN, FIRST), GuideExamplePacks.active(PACKS, Set.of("x", "a", "b", "c")::contains));
        assertEquals(List.of(SECOND), GuideExamplePacks.active(PACKS, Set.of("x", "b", "c")::contains));
        assertEquals(List.of(THIRD), GuideExamplePacks.active(PACKS, Set.of("x", "c")::contains));
    }

    @Test
    void aGroupWithNoLoadedPackShowsNothing() {
        assertEquals(List.of(PLAIN), GuideExamplePacks.active(PACKS, Set.of("a", "b", "c")::contains));
    }

    @Test
    void aVersionedRequirementNeedsThatVersionOrALaterOne() {
        var loaded = GuideExamplePacks.requirements(
                id -> Optional.ofNullable(Map.of("old", "1.3.9", "new", "1.4.0", "newer", "1.10.0").get(id)));
        assertTrue(loaded.test("old"));
        assertFalse(loaded.test("old@1.4.0"));
        assertTrue(loaded.test("new@1.4.0"));
        assertTrue(loaded.test("newer@1.4.0"));
        assertFalse(loaded.test("absent"));
        assertFalse(loaded.test("absent@1.0.0"));
    }

    @Test
    void ungroupedPacksAllShowWhenTheirModsAreLoaded() {
        var other = new GuideExamplePacks.Pack("other", List.of("a"));
        assertEquals(List.of(PLAIN, other), GuideExamplePacks.active(List.of(PLAIN, other), Set.of("a")::contains));
    }
}
