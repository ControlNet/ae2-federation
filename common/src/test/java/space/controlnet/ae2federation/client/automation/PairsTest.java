package space.controlnet.ae2federation.client.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Brackets and quotes typed in the script editor come in pairs; the closing one is stepped over when typed. */
final class PairsTest {
    @Test
    void anOpeningBracketOrQuoteBringsItsPair() {
        assertEquals(Pairs.Typed.PAIR, Pairs.typed("ae2.craft", 9, '('));
        assertEquals(Pairs.Typed.PAIR, Pairs.typed("x = ", 4, '['));
        assertEquals(Pairs.Typed.PAIR, Pairs.typed("f()", 2, '"'));
        assertEquals(Pairs.Typed.PAIR, Pairs.typed("f(a, )", 5, '\''));
        assertEquals('"', Pairs.closing('"'));
        assertEquals(')', Pairs.closing('('));
    }

    @Test
    void noPairBeforeTextOrAfterAWordForQuotes() {
        assertEquals(Pairs.Typed.PLAIN, Pairs.typed("x = a", 4, '('));
        assertEquals(Pairs.Typed.PLAIN, Pairs.typed("# it", 4, '\''));
        assertEquals(Pairs.Typed.PLAIN, Pairs.typed("x", 1, 'a'));
    }

    @Test
    void theClosingOneTypedBeforeItselfStepsOver() {
        assertEquals(Pairs.Typed.STEP, Pairs.typed("f()", 2, ')'));
        assertEquals(Pairs.Typed.STEP, Pairs.typed("\"abc\"", 4, '"'));
        assertEquals(Pairs.Typed.PLAIN, Pairs.typed("f(", 2, ')'));
    }

    @Test
    void backspaceInsideAnEmptyPairDeletesBoth() {
        assertTrue(Pairs.emptyPairAt("f()", 2));
        assertTrue(Pairs.emptyPairAt("x = \"\"", 5));
        assertFalse(Pairs.emptyPairAt("f(a)", 3));
        assertFalse(Pairs.emptyPairAt("()", 0));
    }
}
