package space.controlnet.ae2federation.client.automation;

/**
 * The script editor's paired characters: typing an opening bracket or a quote adds its closing one after the caret,
 * typing the closing one just before itself steps over it, and Backspace between an empty pair deletes both. A pair
 * is only added before a space, the line's end or closing punctuation, and a quote not right after a word (as in
 * "it's").
 */
final class Pairs {
    enum Typed { PAIR, STEP, PLAIN }

    private static final String OPENING = "([{\"'";
    private static final String CLOSING = ")]}\"'";
    /** What may follow the caret for a pair to be added. */
    private static final String BEFORE = ")]},:";

    private Pairs() {
    }

    /** What typing {@code c} at {@code column} of {@code line} does. */
    static Typed typed(String line, int column, char c) {
        char next = column < line.length() ? line.charAt(column) : 0;
        if (CLOSING.indexOf(c) >= 0 && next == c) return Typed.STEP;
        if (OPENING.indexOf(c) < 0) return Typed.PLAIN;
        if (next != 0 && !Character.isWhitespace(next) && BEFORE.indexOf(next) < 0) return Typed.PLAIN;
        if ((c == '"' || c == '\'') && column > 0) {
            char before = line.charAt(column - 1);
            if (Character.isLetterOrDigit(before) || before == '_') return Typed.PLAIN;
        }
        return Typed.PAIR;
    }

    /** The closing character of an opening one. */
    static char closing(char opening) {
        return CLOSING.charAt(OPENING.indexOf(opening));
    }

    /** Whether the caret at {@code column} sits between an opening character and its closing one. */
    static boolean emptyPairAt(String line, int column) {
        if (column <= 0 || column >= line.length()) return false;
        int at = OPENING.indexOf(line.charAt(column - 1));
        return at >= 0 && line.charAt(column) == CLOSING.charAt(at);
    }
}
