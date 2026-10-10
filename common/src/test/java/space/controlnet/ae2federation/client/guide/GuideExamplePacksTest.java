package space.controlnet.ae2federation.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
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
    void ungroupedPacksAllShowWhenTheirModsAreLoaded() {
        var other = new GuideExamplePacks.Pack("other", List.of("a"));
        assertEquals(List.of(PLAIN, other), GuideExamplePacks.active(List.of(PLAIN, other), Set.of("a")::contains));
    }
}
