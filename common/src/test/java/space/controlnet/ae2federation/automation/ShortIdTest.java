package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Short random IDs such as {@code interface-k7m2qx}: six characters without look-alikes, unique where drawn. */
final class ShortIdTest {
    @Test
    void theAlphabetHasNoLookAlikes() {
        for (var c : List.of('0', 'o', '1', 'l', 'i')) {
            assertEquals(-1, ShortId.ALPHABET.indexOf(c), String.valueOf(c));
        }
        assertEquals(31, ShortId.ALPHABET.length());
        assertEquals(31, ShortId.ALPHABET.chars().distinct().count());
        assertTrue(ShortId.ALPHABET.chars().allMatch(c -> Character.isDigit(c) || Character.isLowerCase(c)));
    }

    @Test
    void anIdIsThePrefixAndSixCharactersOfTheAlphabet() {
        var random = new Random(7);
        for (int i = 0; i < 200; i++) {
            var id = ShortId.draw("interface", random, taken -> false);
            assertTrue(id.startsWith("interface-"), id);
            var tail = id.substring("interface-".length());
            assertEquals(6, tail.length(), id);
            assertTrue(tail.chars().allMatch(c -> ShortId.ALPHABET.indexOf(c) >= 0), id);
            assertTrue(ShortId.valid("interface", id), id);
        }
    }

    @Test
    void aTakenIdIsDrawnAgain() {
        var taken = new HashSet<String>();
        var first = ShortId.draw("interface", new Random(1), id -> false);
        taken.add(first);
        var again = ShortId.draw("interface", new Random(1), taken::contains);
        assertNotEquals(first, again, "the same seed draws the taken one first, then another");
        assertTrue(ShortId.valid("interface", again));
    }

    @Test
    void onlyWellFormedIdsAreValid() {
        assertTrue(ShortId.valid("interface", "interface-k7m2qx"));
        for (var bad : List.of("interface-k7m2q", "interface-k7m2qxx", "interface-k7m2q0", "interface-K7M2QX",
                "network-k7m2qx", "interfacek7m2qx", "interface-k7m2qi", "")) {
            assertFalse(ShortId.valid("interface", bad), bad);
        }
    }
}
