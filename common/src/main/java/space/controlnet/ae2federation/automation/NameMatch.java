package space.controlnet.ae2federation.automation;

import com.ibm.icu.text.Transliterator;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import space.controlnet.ae2federation.function.Candidate;

/**
 * How a typed text finds a resource by its shown name: from the name's start, as written (ignoring case) or by pinyin,
 * where each Chinese character is matched by its whole syllable or the syllable's start ("sh", "st" and "shitou"
 * all find 石头). The pinyin comes from ICU's Han-Latin transliterator, which Minecraft ships; it gives one reading per
 * character.
 */
public final class NameMatch {
    private static final Map<Integer, String> SYLLABLES = new ConcurrentHashMap<>();
    private static final Transliterator PINYIN = Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower");

    private NameMatch() {
    }

    /** Whether {@code typed} is the start of {@code name}, each Chinese character typed or started in pinyin. */
    public static boolean startsWith(String name, String typed) {
        if (typed.isEmpty()) return false;
        var units = name.toLowerCase(Locale.ROOT).codePoints().toArray();
        var text = typed.toLowerCase(Locale.ROOT).codePoints().toArray();
        return matches(units, 0, text, 0, new byte[units.length + 1][text.length + 1]);
    }

    /** Whether text[at..] is matched from unit {@code unit} on; {@code seen} keeps failed places (1) to stop early. */
    private static boolean matches(int[] units, int unit, int[] text, int at, byte[][] seen) {
        if (at == text.length) return true;
        if (unit == units.length || seen[unit][at] == 1) return false;
        if (units[unit] == text[at] && matches(units, unit + 1, text, at + 1, seen)) return true;
        var syllable = syllable(units[unit]);
        for (int length = 1; length <= syllable.length() && at + length <= text.length; length++) {
            if (syllable.charAt(length - 1) != text[at + length - 1]) break;
            if (at + length == text.length || matches(units, unit + 1, text, at + length, seen)) return true;
        }
        seen[unit][at] = 1;
        return false;
    }

    /** The pinyin of a Chinese character, without tones; empty for anything else. */
    private static String syllable(int codePoint) {
        if (Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.HAN) return "";
        return SYLLABLES.computeIfAbsent(codePoint, c -> {
            synchronized (PINYIN) {
                return PINYIN.transliterate(Character.toString(c)).replaceAll("[^a-z]", "");
            }
        });
    }

    /**
     * The resources {@code typed} finds, best first: those whose ID's path or shown name ({@link Candidate#detail})
     * starts with it, shorter names first; then those whose ID or name contains it. Each group is in ID order after.
     */
    public static List<Candidate> rank(List<Candidate> candidates, String typed) {
        var lower = typed.toLowerCase(Locale.ROOT);
        var starting = new ArrayList<Candidate>();
        var containing = new ArrayList<Candidate>();
        for (var candidate : candidates) {
            var id = candidate.label();
            var name = candidate.detail();
            if (id.substring(id.indexOf(':') + 1).startsWith(lower) || startsWith(name, typed)) {
                starting.add(candidate);
            } else if (id.contains(lower) || !lower.isEmpty() && name.toLowerCase(Locale.ROOT).contains(lower)) {
                containing.add(candidate);
            }
        }
        starting.sort(Comparator.comparingInt((Candidate candidate) -> candidate.detail().codePointCount(0,
                candidate.detail().length())).thenComparing(Candidate::label));
        containing.sort(Comparator.comparing(Candidate::label));
        starting.addAll(containing);
        return starting;
    }
}
