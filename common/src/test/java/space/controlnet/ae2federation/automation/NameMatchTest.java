package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.function.Candidate;

/** A resource's shown name matches what is typed from its start: as written, or by the pinyin of its characters. */
final class NameMatchTest {
    @Test
    void pinyinMatchesWholeOrStartedSyllables() {
        assertTrue(NameMatch.startsWith("石头", "sh"));
        assertTrue(NameMatch.startsWith("石头", "shitou"));
        assertTrue(NameMatch.startsWith("石头", "st"));
        assertTrue(NameMatch.startsWith("石头", "sht"));
        assertTrue(NameMatch.startsWith("石头", "shito"));
        assertFalse(NameMatch.startsWith("石头", "tou"));
        assertFalse(NameMatch.startsWith("石头", "ss"));
        assertFalse(NameMatch.startsWith("石头", "shitoux"));
    }

    @Test
    void writtenTextMatchesAsItIs() {
        assertTrue(NameMatch.startsWith("石头", "石"));
        assertTrue(NameMatch.startsWith("石头", "石t"));
        assertTrue(NameMatch.startsWith("Iron Ingot", "iron i"));
        assertTrue(NameMatch.startsWith("铁锭", "tied"));
        assertFalse(NameMatch.startsWith("Iron Ingot", "ingot"));
        assertFalse(NameMatch.startsWith("石头", ""));
    }

    @Test
    void startsBeforeContainsAndShorterNamesFirst() {
        var found = List.of(new Candidate("minecraft:shulker_box", Candidate.Kind.VALUE, "潜影盒"),
                new Candidate("minecraft:stone", Candidate.Kind.VALUE, "石头"),
                new Candidate("minecraft:mushroom_stew", Candidate.Kind.VALUE, "蘑菇煲"),
                new Candidate("minecraft:stone_bricks", Candidate.Kind.VALUE, "石砖块"),
                new Candidate("minecraft:book", Candidate.Kind.VALUE, "书"));
        assertEquals(List.of("minecraft:book", "minecraft:stone", "minecraft:shulker_box", "minecraft:stone_bricks",
                "minecraft:mushroom_stew"), NameMatch.rank(found, "sh").stream().map(Candidate::label).toList());
    }
}
