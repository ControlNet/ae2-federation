package space.controlnet.ae2federation.client.policy;

import java.util.Locale;
import java.util.function.BiPredicate;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.compat.jech.JechPinyin;

/**
 * How the domain screen's searches match a name: the lower-cased query as part of the text, ignoring case; with
 * JustEnoughCharacters installed, also by the text's pinyin, so "tie" finds 铁锭.
 */
public final class SearchText {
    private SearchText() {
    }

    /** Whether {@code text} matches {@code query}, already trimmed and lower-cased; an empty query matches all. */
    public static boolean contains(String text, String query) {
        return contains(text, query, Pinyin.MATCHER);
    }

    static boolean contains(String text, String query, @Nullable BiPredicate<String, String> pinyin) {
        if (query.isEmpty()) return true;
        var lower = text.toLowerCase(Locale.ROOT);
        return lower.contains(query) || pinyin != null && pinyin.test(lower, query);
    }

    /** Looked up on first use; the statistics page is also built on the server, which must not touch JECh early. */
    private static final class Pinyin {
        static final @Nullable BiPredicate<String, String> MATCHER = JechPinyin.find();
    }
}
