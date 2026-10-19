package space.controlnet.ae2federation.client.automation;

import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.language.ILanguageDefinition;
import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.language.StyleManager;
import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.language.TokenType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import net.minecraft.network.chat.Style;
import space.controlnet.ae2federation.function.StandardLibrary;

/**
 * The script editor's colours, line by line, as the design board 「自动化页签」 draws them: keywords, the names a
 * {@code def} or {@code class} defines and the functions a program calls, types, the library's namespaces, strings,
 * numbers and comments. The pattern matches every character, because the editor draws only what it matches.
 */
public final class PythonLanguage implements ILanguageDefinition {
    private static final List<String> KEYWORDS = List.of("def", "class", "return", "if", "elif", "else", "for",
            "while", "in", "not", "and", "or", "is", "pass", "break", "continue", "lambda", "global", "nonlocal",
            "del", "import", "from", "as", "with", "try", "except", "finally", "raise", "assert", "yield", "async",
            "await", "match", "case");
    private static final List<String> CONSTANTS = List.of("True", "False");

    /** The kinds, in the order they are tried, with their colours. */
    private static final Map<String, Integer> COLORS = new LinkedHashMap<>();
    private static final Map<String, String> PATTERNS = new LinkedHashMap<>();

    static {
        var types = new ArrayList<>(List.of("int", "float", "str", "bool", "None", "list", "Optional"));
        types.addAll(StandardLibrary.TYPED_STRINGS);
        types.addAll(StandardLibrary.OPAQUE);
        types.addAll(StandardLibrary.RECORDS.keySet());
        kind("comment", 0x6f6a7a, "#.*");
        kind("string", 0xf7b13a, "(?:[rRbBfFuU]{1,2})?(?:\"(?:[^\"\\\\]|\\\\.)*\"?|'(?:[^'\\\\]|\\\\.)*'?)");
        kind("number", 0xffb86c, "\\b\\d[\\d_]*(?:\\.\\d[\\d_]*)?(?:[eE][+-]?\\d+)?\\b|\\.\\d[\\d_]*");
        kind("definition", 0x9cd3ff, "(?<=\\bdef |\\bclass )[\\p{L}_][\\p{L}\\p{N}_]*");
        kind("keyword", 0xb37bea, words(KEYWORDS));
        kind("constant", 0xffb86c, words(CONSTANTS));
        kind("type", 0x6ff0e0, words(types));
        kind("namespace", 0xe0855a, words(StandardLibrary.NAMESPACES.keySet().stream().toList()) + "(?=\\s*\\.)");
        kind("call", 0x9cd3ff, "(?<![.\\p{L}\\p{N}_])[\\p{L}_][\\p{L}\\p{N}_]*(?=\\s*\\()");
        kind("name", 0xd6d0e0, "[\\p{L}_][\\p{L}\\p{N}_]*");
        kind("operator", 0xd6d0e0, "->|[^\\s\\p{L}\\p{N}_]");
        kind("space", 0xd6d0e0, "\\s+");
    }

    /** Built after the kinds above: static fields are set in the order they are written. */
    public static final PythonLanguage INSTANCE = new PythonLanguage();

    private static void kind(String name, int color, String pattern) {
        COLORS.put(name, color);
        PATTERNS.put(name, pattern);
    }

    private static String words(List<String> words) {
        return "\\b(?:" + String.join("|", words) + ")\\b";
    }

    private final Pattern pattern = Pattern.compile(PATTERNS.entrySet().stream()
            .map(entry -> "(?<" + entry.getKey() + ">" + entry.getValue() + ")").collect(Collectors.joining("|")));
    private final List<TokenType> types = COLORS.keySet().stream().map(TokenType::new).toList();
    private final StyleManager styles = new StyleManager();

    private PythonLanguage() {
        styles.getStyleMap().clear();
        COLORS.forEach((name, color) -> styles.getStyleMap().put(name, Style.EMPTY.withColor(color)));
        styles.setDefaultStyle(Style.EMPTY.withColor(COLORS.get("name")));
    }

    /** The colours to give the editor with this language. */
    public StyleManager styles() {
        return styles;
    }

    @Override
    public String getName() {
        return "python";
    }

    @Override
    public Pattern getTokenPattern() {
        return pattern;
    }

    @Override
    public TokenType getTokenType(Matcher matcher) {
        for (var type : types) {
            if (type.test(matcher)) {
                return type;
            }
        }
        throw new IllegalStateException("no kind matched " + matcher.group());
    }

    @Override
    public boolean shouldIncreaseIndentation(String trimmedLine) {
        return trimmedLine.endsWith(":") && !trimmedLine.startsWith("#");
    }
}
