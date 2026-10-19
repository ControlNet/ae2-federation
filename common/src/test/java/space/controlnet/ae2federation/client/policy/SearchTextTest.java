package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import org.junit.jupiter.api.Test;

class SearchTextTest {
    /** TEST-ONLY stand-in for JustEnoughCharacters: knows one name's pinyin and records what it was asked. */
    private static final class Pinyin implements BiPredicate<String, String> {
        final List<String> asked = new ArrayList<>();

        @Override
        public boolean test(String text, String query) {
            asked.add(text + "|" + query);
            return text.equals("铁锭") && "tieding".startsWith(query);
        }
    }

    @Test
    void emptyQueryMatchesWithoutAskingPinyin() {
        var pinyin = new Pinyin();
        assertTrue(SearchText.contains("铁锭", "", pinyin));
        assertEquals(List.of(), pinyin.asked);
    }

    @Test
    void plainTextMatchesIgnoringCaseWithoutAskingPinyin() {
        var pinyin = new Pinyin();
        assertTrue(SearchText.contains("Iron Ingot", "iron", pinyin));
        assertTrue(SearchText.contains("铁锭", "铁", pinyin));
        assertEquals(List.of(), pinyin.asked);
    }

    @Test
    void pinyinIsAskedOnlyWhenPlainTextMisses() {
        var pinyin = new Pinyin();
        assertTrue(SearchText.contains("铁锭", "tie", pinyin));
        assertFalse(SearchText.contains("金锭", "tie", pinyin));
        assertEquals(List.of("铁锭|tie", "金锭|tie"), pinyin.asked);
    }

    @Test
    void withoutPinyinOnlyPlainTextMatches() {
        assertTrue(SearchText.contains("Main Base", "main", null));
        assertFalse(SearchText.contains("铁锭", "tie", null));
    }

    @Test
    void withoutJustEnoughCharactersTheDefaultIsPlainText() {
        assertTrue(SearchText.contains("Main Base", "base"));
        assertFalse(SearchText.contains("铁锭", "tie"));
    }
}
