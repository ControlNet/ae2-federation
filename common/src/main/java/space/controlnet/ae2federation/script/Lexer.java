package space.controlnet.ae2federation.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Splits a script into tokens the Python way: indentation becomes INDENT and DEDENT, a line break inside brackets is
 * no line break, and whole-line comments come out as COMMENT tokens after the next line's indentation, so they sit
 * right before the statement they precede. Errors are recorded and lexing goes on; a broken token becomes ERROR.
 */
final class Lexer {
    enum Kind { NAME, INT, FLOAT, STRING, OP, NEWLINE, INDENT, DEDENT, COMMENT, END_COMMENT, ERROR, EOF }

    /** {@code value}: the decoded string, a {@code Long} or {@code Double}, the comment text, or the error message. */
    record Token(Kind kind, String text, Object value, int line, int column) {
        boolean is(Kind kind, String text) {
            return this.kind == kind && this.text.equals(text);
        }
    }

    private static final List<String> OPERATORS = List.of(
            "**=", "//=", ">>=", "<<=", "...",
            "->", ":=", "==", "!=", "<=", ">=", "//", "**", "<<", ">>",
            "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "@=",
            "+", "-", "*", "/", "%", "<", ">", "=", "(", ")", "[", "]", "{", "}", ",", ":", ".", ";", "@", "&", "|",
            "^", "~");
    static final String UNCLOSED_STRING = "this string is never closed";
    private static final Set<String> STRING_PREFIXES = Set.of("f", "r", "b", "u", "rb", "br", "fr", "rf");

    private final String src;
    private final List<ScriptError> errors;
    private final List<Token> tokens = new ArrayList<>();
    private final List<Token> comments = new ArrayList<>();
    private final List<Integer> indents = new ArrayList<>(List.of(0));
    /** Open brackets, outermost first: line, column, the bracket character and its offset. */
    private final List<int[]> brackets = new ArrayList<>();
    private int pos;
    private int line = 1;
    private int lineStart;
    /** The indentation of the line the current logical line started on. */
    private int logicalIndent;
    /** Where the end-of-input tokens start; a partial lex leaves them out. */
    private int tail;
    /** The offset of the outermost bracket still open at the end, or -1. */
    private int unclosedAt = -1;
    /** Brackets opened at or after this offset may be ended early (see {@link #nextLineLeavesBrackets}). */
    private final int recoverFrom;

    private Lexer(String src, List<ScriptError> errors, int recoverFrom) {
        this.src = src;
        this.errors = errors;
        this.recoverFrom = recoverFrom;
    }

    /**
     * Lexes exactly as Python does. Only when a bracket is never closed (an error in Python too) is the script lexed
     * again with recovery from that bracket on, so the error costs that statement and not the rest of the file.
     */
    static List<Token> lex(String src, List<ScriptError> errors) {
        var lexer = new Lexer(src, new ArrayList<>(), Integer.MAX_VALUE);
        lexer.run();
        if (lexer.unclosedAt >= 0) {
            lexer = new Lexer(src, new ArrayList<>(), lexer.unclosedAt);
            lexer.run();
        }
        errors.addAll(lexer.errors);
        return lexer.tokens;
    }

    /**
     * The tokens of a script cut off at a cursor, without the line ends, dedents and end marker the end of input would
     * add: the last logical line stays open, as the cursor sees it.
     */
    static List<Token> lexPartial(String src) {
        var lexer = new Lexer(src, new ArrayList<>(), Integer.MAX_VALUE);
        lexer.run();
        return lexer.tokens.subList(0, lexer.tail);
    }

    private void run() {
        boolean atLineStart = true;
        while (pos < src.length()) {
            if (atLineStart && brackets.isEmpty()) {
                if (!indentation()) {
                    continue;
                }
                atLineStart = false;
            }
            char c = src.charAt(pos);
            if (c == '\n' || c == '\r') {
                if (!brackets.isEmpty() && brackets.get(0)[3] >= recoverFrom && nextLineLeavesBrackets()) {
                    unclosed();
                }
                if (brackets.isEmpty()) {
                    add(Kind.NEWLINE, "\n", null, pos);
                    atLineStart = true;
                }
                newline();
            } else if (c == ' ' || c == '\t') {
                pos++;
            } else if (c == '#') {
                int start = pos;
                String text = restOfLine();
                if (!brackets.isEmpty()) {
                    error(start, "comments inside brackets are not supported");
                } else {
                    add(Kind.END_COMMENT, "#" + text, text, start);
                }
            } else if (c == '\\') {
                error(pos, "line continuation with '\\' is not supported; break the line inside brackets instead");
                pos++;
                if (pos < src.length() && (src.charAt(pos) == '\n' || src.charAt(pos) == '\r')) {
                    newline();
                }
            } else if (Character.isLetter(c) || c == '_') {
                name();
            } else if (Character.isDigit(c) || c == '.' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1))) {
                number();
            } else if (c == '"' || c == '\'') {
                string(pos);
            } else {
                operator();
            }
        }
        tail = tokens.size();
        if (!brackets.isEmpty()) {
            unclosedAt = brackets.get(0)[3];
            unclosed();
        }
        if (!tokens.isEmpty() && tokens.get(tokens.size() - 1).kind() != Kind.NEWLINE
                && tokens.get(tokens.size() - 1).kind() != Kind.COMMENT) {
            add(Kind.NEWLINE, "\n", null, pos);
        }
        while (indents.size() > 1) {
            indents.remove(indents.size() - 1);
            add(Kind.DEDENT, "", null, pos);
        }
        tokens.addAll(comments);
        add(Kind.EOF, "", null, pos);
    }

    /** At a line start: false for a blank or comment-only line (consumed), else emits the indentation tokens. */
    private boolean indentation() {
        int p = pos;
        int width = 0;
        boolean tab = false;
        while (p < src.length() && (src.charAt(p) == ' ' || src.charAt(p) == '\t')) {
            if (src.charAt(p) == '\t') {
                tab = true;
                width = (width / 8 + 1) * 8;
            } else {
                width++;
            }
            p++;
        }
        if (p >= src.length() || src.charAt(p) == '\n' || src.charAt(p) == '\r') {
            pos = p;
            if (pos < src.length()) {
                newline();
            }
            return false;
        }
        if (src.charAt(p) == '#') {
            pos = p;
            int start = pos;
            String text = restOfLine();
            comments.add(new Token(Kind.COMMENT, "#" + text, text, line, start - lineStart + 1));
            if (pos < src.length()) {
                newline();
            }
            return false;
        }
        if (tab) {
            error(pos, "indent with spaces, not tabs");
        }
        pos = p;
        logicalIndent = width;
        int top = indents.get(indents.size() - 1);
        if (width > top) {
            indents.add(width);
            add(Kind.INDENT, "", null, pos);
        } else {
            while (width < indents.get(indents.size() - 1)) {
                indents.remove(indents.size() - 1);
                add(Kind.DEDENT, "", null, pos);
            }
            if (width != indents.get(indents.size() - 1)) {
                error(pos, "this indentation does not match any outer block");
            }
        }
        tokens.addAll(comments);
        comments.clear();
        return true;
    }

    private void name() {
        int start = pos;
        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_')) {
            pos++;
        }
        String text = src.substring(start, pos);
        if (pos < src.length() && (src.charAt(pos) == '"' || src.charAt(pos) == '\'')
                && STRING_PREFIXES.contains(text.toLowerCase())) {
            int before = errors.size();
            error(start, "string prefixes such as f\"...\" are not supported");
            string(start);
            tokens.set(tokens.size() - 1, errorToken(start, errors.get(before).message()));
            return;
        }
        add(Kind.NAME, text, null, start);
    }

    private void number() {
        int start = pos;
        if (src.startsWith("0x", pos) || src.startsWith("0X", pos) || src.startsWith("0o", pos)
                || src.startsWith("0O", pos) || src.startsWith("0b", pos) || src.startsWith("0B", pos)) {
            pos += 2;
            skipWord();
            fail(start, "hexadecimal, octal and binary numbers are not supported");
            return;
        }
        boolean isFloat = false;
        digits();
        if (pos < src.length() && src.charAt(pos) == '.') {
            isFloat = true;
            pos++;
            digits();
        }
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            isFloat = true;
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) {
                pos++;
            }
            digits();
        }
        if (pos < src.length() && src.charAt(pos) == '_') {
            skipWord();
            fail(start, "underscores in numbers are not supported");
            return;
        }
        if (pos < src.length() && (src.charAt(pos) == 'j' || src.charAt(pos) == 'J')) {
            pos++;
            fail(start, "complex numbers are not supported");
            return;
        }
        String text = src.substring(start, pos);
        if (isFloat) {
            add(Kind.FLOAT, text, Double.parseDouble(text), start);
        } else if (text.length() > 1 && text.charAt(0) == '0' && !text.chars().allMatch(ch -> ch == '0')) {
            fail(start, "leading zeros in numbers are not allowed");
        } else {
            try {
                add(Kind.INT, text, Long.parseLong(text), start);
            } catch (NumberFormatException e) {
                fail(start, "integers are 64-bit; this one is too large");
            }
        }
    }

    private void digits() {
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) {
            pos++;
        }
    }

    private void skipWord() {
        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_'
                || src.charAt(pos) == '.')) {
            pos++;
        }
    }

    /** A string starting at the quote under {@code pos}; {@code start} is where its token begins. */
    private void string(int start) {
        char quote = src.charAt(pos);
        boolean triple = src.startsWith(String.valueOf(quote).repeat(3), pos);
        int startLine = line;
        int startColumn = start - lineStart + 1;
        pos += triple ? 3 : 1;
        var value = new StringBuilder();
        String error = null;
        while (true) {
            if (pos >= src.length() || !triple && (src.charAt(pos) == '\n' || src.charAt(pos) == '\r')) {
                error = UNCLOSED_STRING;
                break;
            }
            char c = src.charAt(pos);
            if (triple ? src.startsWith(String.valueOf(quote).repeat(3), pos) : c == quote) {
                pos += triple ? 3 : 1;
                break;
            }
            if (c == '\\' && pos + 1 < src.length()) {
                char next = src.charAt(pos + 1);
                switch (next) {
                    case '\\', '\'', '"' -> value.append(next);
                    case 'n' -> value.append('\n');
                    case 't' -> value.append('\t');
                    case 'r' -> value.append('\r');
                    default -> {
                        if (error == null) {
                            error = "escape \\" + next + " is not supported (use \\\\ \\' \\\" \\n \\t or \\r)";
                        }
                    }
                }
                pos += 2;
                continue;
            }
            if (c == '\n') {
                line++;
                lineStart = pos + 1;
            }
            value.append(c);
            pos++;
        }
        String text = src.substring(start, pos);
        if (error != null) {
            errors.add(new ScriptError(startLine, startColumn, error));
            tokens.add(new Token(Kind.ERROR, text, error, startLine, startColumn));
        } else {
            tokens.add(new Token(Kind.STRING, text, value.toString(), startLine, startColumn));
        }
    }

    private void operator() {
        for (var op : OPERATORS) {
            if (src.startsWith(op, pos)) {
                switch (op) {
                    case "(", "[", "{" -> brackets.add(new int[] {line, pos - lineStart + 1, op.charAt(0), pos});
                    case ")", "]", "}" -> {
                        if (!brackets.isEmpty()) {
                            brackets.remove(brackets.size() - 1);
                        }
                    }
                    default -> {
                    }
                }
                add(Kind.OP, op, null, pos);
                pos += op.length();
                return;
            }
        }
        int start = pos;
        pos++;
        fail(start, "unexpected character '" + src.charAt(start) + "'");
    }

    /**
     * Recovery only, for a bracket Python reports as never closed: true at a line break when the next line with code
     * is indented no further than the line that opened the bracket and does not start by closing one. Python reads on
     * to the end of the file and reports the bracket; ending it here as well lets everything after it parse.
     */
    private boolean nextLineLeavesBrackets() {
        int p = pos;
        while (p < src.length()) {
            p = src.charAt(p) == '\r' && p + 1 < src.length() && src.charAt(p + 1) == '\n' ? p + 2 : p + 1;
            int width = 0;
            while (p < src.length() && (src.charAt(p) == ' ' || src.charAt(p) == '\t')) {
                width = src.charAt(p) == '\t' ? (width / 8 + 1) * 8 : width + 1;
                p++;
            }
            if (p >= src.length()) {
                return false;
            }
            char c = src.charAt(p);
            if (c == '\n' || c == '\r') {
                continue;
            }
            if (c == '#') {
                while (p < src.length() && src.charAt(p) != '\n' && src.charAt(p) != '\r') {
                    p++;
                }
                if (p >= src.length()) {
                    return false;
                }
                continue;
            }
            return width <= logicalIndent && c != ')' && c != ']' && c != '}';
        }
        return false;
    }

    private void unclosed() {
        var open = brackets.get(0);
        errors.add(new ScriptError(open[0], open[1], "'" + (char) open[2] + "' was never closed"));
        brackets.clear();
    }

    private String restOfLine() {
        int start = pos + 1;
        while (pos < src.length() && src.charAt(pos) != '\n' && src.charAt(pos) != '\r') {
            pos++;
        }
        return src.substring(Math.min(start, pos), pos);
    }

    private void newline() {
        if (src.charAt(pos) == '\r' && pos + 1 < src.length() && src.charAt(pos + 1) == '\n') {
            pos++;
        }
        pos++;
        line++;
        lineStart = pos;
    }

    private void add(Kind kind, String text, Object value, int at) {
        tokens.add(new Token(kind, text, value, line, at - lineStart + 1));
    }

    private void error(int at, String message) {
        errors.add(new ScriptError(line, at - lineStart + 1, message));
    }

    /** Records the error and leaves an ERROR token covering {@code start..pos}. */
    private void fail(int start, String message) {
        error(start, message);
        tokens.add(errorToken(start, message));
    }

    private Token errorToken(int start, String message) {
        return new Token(Kind.ERROR, src.substring(start, pos), message, line, start - lineStart + 1);
    }
}
