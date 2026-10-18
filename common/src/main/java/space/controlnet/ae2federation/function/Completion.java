package space.controlnet.ae2federation.function;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import space.controlnet.ae2federation.function.Lexer.Kind;
import space.controlnet.ae2federation.function.Lexer.Token;

/**
 * Completion at a cursor in a script that is usually half typed. The cursor's logical line is read from its tokens
 * (what is being typed, the call it is in, the type that place needs); the rest of the file is parsed with that line
 * replaced by {@code pass}, so a broken line never hides the variables and functions around it.
 */
public final class Completion {
    static final List<String> TYPES = List.of("int", "float", "str", "bool", "None", "list", "Item", "Fluid",
            "Resource", "Interface");
    static final List<String> STATEMENT_KEYWORDS = List.of("if", "elif", "else", "for", "while", "return", "break",
            "continue", "pass");
    static final List<String> VALUE_KEYWORDS = List.of("True", "False", "None", "not");

    private final Workspace workspace;
    private final List<FunctionDef> ownFunctions;
    private final Map<String, TypeRef> variables = new LinkedHashMap<>();

    private Completion(Workspace workspace, List<FunctionDef> ownFunctions) {
        this.workspace = workspace;
        this.ownFunctions = ownFunctions;
    }

    /** {@code line} and {@code column} count from 1; the cursor sits before the character at that column. */
    public static Completions complete(String source, int line, int column, Workspace workspace) {
        var before = source.substring(0, offset(source, line, column));
        var linePrefix = before.substring(before.lastIndexOf('\n') + 1);
        var none = new Completions(line, column, "", null, List.of());
        if (inComment(linePrefix)) {
            return none;
        }
        var tokens = Lexer.lexPartial(before);
        int start = tokens.size();
        while (start > 0 && tokens.get(start - 1).kind() != Kind.NEWLINE) {
            start--;
        }
        var context = new ArrayList<Token>();
        for (var token : tokens.subList(start, tokens.size())) {
            if (token.kind() != Kind.INDENT && token.kind() != Kind.DEDENT && token.kind() != Kind.COMMENT) {
                context.add(token);
            }
        }
        int logicalLine = context.isEmpty() ? line : context.get(0).line();

        var prefix = "";
        int prefixColumn = column;
        boolean inString = false;
        if (!context.isEmpty()) {
            var last = context.get(context.size() - 1);
            boolean endsAtCursor = last.line() == line && last.column() + last.text().length() == column;
            if (last.kind() == Kind.ERROR && Lexer.UNCLOSED_STRING.equals(last.value())) {
                var text = last.text();
                if (text.length() >= 3 && text.charAt(1) == text.charAt(0) && text.charAt(2) == text.charAt(0)
                        || text.charAt(0) != '"' && text.charAt(0) != '\'') {
                    return none;
                }
                prefix = text.substring(1);
                prefixColumn = last.column() + 1;
                inString = true;
                context.remove(context.size() - 1);
            } else if (last.kind() == Kind.NAME && endsAtCursor) {
                prefix = last.text();
                prefixColumn = last.column();
                context.remove(context.size() - 1);
            } else if (last.kind() == Kind.ERROR || endsAtCursor && last.kind() != Kind.OP) {
                return none;
            }
        }

        var parsed = ScriptParser.parse(neutralize(source, logicalLine, line));
        var completion = new Completion(workspace, parsed.program().functions());
        var function = completion.enclosing(parsed, logicalLine);
        if (function != null) {
            for (var param : function.params()) {
                completion.variables.put(param.name(), param.type());
            }
            completion.collect(function.body(), parsed, logicalLine);
        }
        var expected = completion.expected(context, function);
        var candidates = completion.candidates(context, prefix, inString, expected, linePrefix);
        return new Completions(line, prefixColumn, prefix, expected, candidates);
    }

    private List<Candidate> candidates(List<Token> context, String prefix, boolean inString, TypeRef expected,
            String linePrefix) {
        if (inString) {
            return expected == null ? List.of() : workspace.catalog().values(expected, prefix);
        }
        int size = context.size();
        var last = size == 0 ? null : context.get(size - 1);
        if (size > 0 && keyword(context.get(0), "def")) {
            boolean typePlace = last.is(Kind.OP, ":") || last.is(Kind.OP, "->") || last.is(Kind.OP, "[");
            return size > 1 && typePlace ? types(prefix) : List.of();
        }
        if (last != null && last.is(Kind.OP, ".")) {
            boolean qualified = size >= 2 && context.get(size - 2).kind() == Kind.NAME
                    && !(size >= 3 && context.get(size - 3).is(Kind.OP, "."));
            return qualified ? members(context.get(size - 2).text(), prefix) : List.of();
        }
        if (size >= 2 && context.get(0).kind() == Kind.NAME && context.get(1).is(Kind.OP, ":")
                && (size == 2 || last.is(Kind.OP, "["))) {
            return types(prefix);
        }
        if (size >= 1 && size <= 2 && keyword(context.get(0), "for")) {
            return List.of();
        }
        if (size == 0 && linePrefix.length() - linePrefix.stripLeading().length() == 0) {
            return filter(List.of(new Candidate("def", Candidate.Kind.KEYWORD, "")), prefix);
        }
        return names(prefix, expected, size == 0);
    }

    private List<Candidate> names(String prefix, TypeRef expected, boolean statementStart) {
        var out = new ArrayList<Candidate>();
        var vars = new ArrayList<>(variables.entrySet());
        vars.sort(Comparator.comparing(e -> matches(expected, e.getValue()) ? 0 : 1));
        for (var entry : vars) {
            out.add(new Candidate(entry.getKey(), Candidate.Kind.VARIABLE,
                    entry.getValue() == null ? "" : entry.getValue().toString()));
        }
        ownFunctions.stream().sorted(Comparator.comparing(FunctionDef::name))
                .forEach(f -> out.add(new Candidate(f.name(), Candidate.Kind.FUNCTION, Signature.of(f).display())));
        workspace.files().keySet().stream().sorted()
                .forEach(file -> out.add(new Candidate(file, Candidate.Kind.FILE, "file")));
        workspace.catalog().namespaces().keySet().stream().sorted()
                .forEach(ns -> out.add(new Candidate(ns, Candidate.Kind.NAMESPACE, "namespace")));
        for (var keyword : statementStart ? STATEMENT_KEYWORDS : VALUE_KEYWORDS) {
            out.add(new Candidate(keyword, Candidate.Kind.KEYWORD, ""));
        }
        return filter(out, prefix);
    }

    private List<Candidate> members(String qualifier, String prefix) {
        var builtIns = workspace.catalog().namespaces().get(qualifier);
        List<Signature> signatures;
        if (builtIns != null) {
            signatures = builtIns;
        } else if (workspace.files().containsKey(qualifier)) {
            signatures = workspace.files().get(qualifier).functions().stream()
                    .filter(f -> !f.name().startsWith("_")).map(Signature::of).toList();
        } else {
            return List.of();
        }
        return filter(signatures.stream().sorted(Comparator.comparing(Signature::name))
                .map(s -> new Candidate(s.name(), Candidate.Kind.FUNCTION, s.display())).toList(), prefix);
    }

    private static List<Candidate> types(String prefix) {
        return filter(TYPES.stream().map(t -> new Candidate(t, Candidate.Kind.TYPE, "")).toList(), prefix);
    }

    /** Case-insensitive prefix match; the first candidate of a label wins. */
    private static List<Candidate> filter(List<Candidate> candidates, String prefix) {
        var lower = prefix.toLowerCase(Locale.ROOT);
        var seen = new LinkedHashSet<String>();
        return candidates.stream()
                .filter(c -> c.label().toLowerCase(Locale.ROOT).startsWith(lower))
                .filter(c -> seen.add(c.label()))
                .toList();
    }

    /** Whether a value of type {@code actual} fits where {@code expected} is needed, for ranking. */
    private static boolean matches(TypeRef expected, TypeRef actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return expected.equals(actual)
                || expected.name().equals("Resource") && (actual.name().equals("Item") || actual.name().equals("Fluid"))
                || expected.name().equals("float") && actual.name().equals("int");
    }

    // ---- the type the cursor's place needs ----

    private TypeRef expected(List<Token> context, FunctionDef function) {
        int depth = 0;
        for (int i = context.size() - 1; i >= 0; i--) {
            var token = context.get(i);
            if (token.kind() != Kind.OP) {
                continue;
            }
            switch (token.text()) {
                case ")", "]", "}" -> depth++;
                case "(", "[", "{" -> {
                    if (depth > 0) {
                        depth--;
                        continue;
                    }
                    return token.text().equals("(") ? argumentType(context, i) : null;
                }
                default -> {
                }
            }
        }
        if (context.size() >= 2 && context.get(0).kind() == Kind.NAME && context.get(1).is(Kind.OP, ":")) {
            for (int i = 2; i < context.size(); i++) {
                if (context.get(i).is(Kind.OP, "=")) {
                    return typeOf(context.subList(2, i));
                }
            }
            return null;
        }
        if (context.size() >= 2 && context.get(0).kind() == Kind.NAME && context.get(1).is(Kind.OP, "=")) {
            return variables.get(context.get(0).text());
        }
        if (!context.isEmpty() && keyword(context.get(0), "return") && function != null) {
            return function.result();
        }
        return null;
    }

    /** The type of the argument being typed in the call whose {@code (} is at {@code open}. */
    private TypeRef argumentType(List<Token> context, int open) {
        if (open < 1 || context.get(open - 1).kind() != Kind.NAME) {
            return null;
        }
        String qualifier = open >= 3 && context.get(open - 2).is(Kind.OP, ".")
                && context.get(open - 3).kind() == Kind.NAME ? context.get(open - 3).text() : null;
        int index = 0;
        int depth = 0;
        for (var token : context.subList(open + 1, context.size())) {
            if (token.kind() != Kind.OP) {
                continue;
            }
            switch (token.text()) {
                case "(", "[", "{" -> depth++;
                case ")", "]", "}" -> depth--;
                case "," -> {
                    if (depth == 0) {
                        index++;
                    }
                }
                default -> {
                }
            }
        }
        int argument = index;
        return signature(qualifier, context.get(open - 1).text())
                .filter(s -> argument < s.args().size())
                .map(s -> s.args().get(argument).type())
                .orElse(null);
    }

    /** A type hint written as tokens ({@code list[Item]}); {@code null} if it is not one. */
    private static TypeRef typeOf(List<Token> tokens) {
        if (tokens.isEmpty() || tokens.get(0).kind() != Kind.NAME) {
            return null;
        }
        if (tokens.size() == 1) {
            return new TypeRef(tokens.get(0).text(), List.of());
        }
        if (tokens.get(1).is(Kind.OP, "[") && tokens.get(tokens.size() - 1).is(Kind.OP, "]")) {
            var inner = typeOf(tokens.subList(2, tokens.size() - 1));
            return inner == null ? null : new TypeRef(tokens.get(0).text(), List.of(inner));
        }
        return null;
    }

    // ---- what is in scope ----

    /** The last function starting at or before the line: the cursor is in its header or body. */
    private FunctionDef enclosing(ParseResult parsed, int line) {
        FunctionDef found = null;
        for (var function : parsed.program().functions()) {
            if (parsed.positions().get(function.id()).line() <= line) {
                found = function;
            }
        }
        return found;
    }

    /** Variables assigned in statements that start before the line, with the type they were given or hold. */
    private void collect(List<Stmt> body, ParseResult parsed, int line) {
        for (var statement : body) {
            if (parsed.positions().get(statement.id()).line() >= line) {
                return;
            }
            switch (statement) {
                case Stmt.Assign s -> declare(s.target(), s.type() != null ? s.type() : infer(s.value()));
                case Stmt.For s -> {
                    var iterable = infer(s.iterable());
                    declare(s.variable(), iterable != null && iterable.name().equals("list") && iterable.args().size() == 1
                            ? iterable.args().get(0) : null);
                    collect(s.body(), parsed, line);
                }
                case Stmt.If s -> {
                    collect(s.body(), parsed, line);
                    s.elifs().forEach(elif -> collect(elif.body(), parsed, line));
                    if (s.orElse() != null) {
                        collect(s.orElse().body(), parsed, line);
                    }
                }
                case Stmt.While s -> collect(s.body(), parsed, line);
                default -> {
                }
            }
        }
    }

    private void declare(String name, TypeRef type) {
        if (!variables.containsKey(name) || variables.get(name) == null) {
            variables.put(name, type);
        }
    }

    /** The type an expression evaluates to as far as completion needs it; {@code null} when unknown. */
    private TypeRef infer(Expr e) {
        return switch (e) {
            case Expr.IntLit l -> simple("int");
            case Expr.FloatLit l -> simple("float");
            case Expr.StrLit l -> simple("str");
            case Expr.BoolLit l -> simple("bool");
            case Expr.NoneLit l -> simple("None");
            case Expr.ListLit l -> {
                var element = l.items().isEmpty() ? null : infer(l.items().get(0));
                yield element == null ? simple("list") : new TypeRef("list", List.of(element));
            }
            case Expr.Name n -> variables.get(n.name());
            case Expr.Call c -> signature(c.qualifier(), c.name()).map(Signature::result).orElse(null);
            case Expr.Unary u -> u.op() == UnaryOp.NOT ? simple("bool") : infer(u.operand());
            case Expr.Binary b -> {
                if (b.op().precedence <= BinaryOp.COMPARISON) {
                    yield simple("bool");
                }
                if (b.op() == BinaryOp.DIV) {
                    yield simple("float");
                }
                var left = infer(b.left());
                var right = infer(b.right());
                if (left == null || right == null) {
                    yield null;
                }
                yield left.name().equals("float") || right.name().equals("float") ? simple("float") : left;
            }
        };
    }

    private Optional<Signature> signature(String qualifier, String name) {
        if (qualifier == null) {
            return ownFunctions.stream().filter(f -> f.name().equals(name)).findFirst().map(Signature::of);
        }
        var builtIns = workspace.catalog().namespaces().get(qualifier);
        if (builtIns != null) {
            return builtIns.stream().filter(s -> s.name().equals(name)).findFirst();
        }
        var file = workspace.files().get(qualifier);
        return file == null ? Optional.empty()
                : file.functions().stream().filter(f -> f.name().equals(name)).findFirst().map(Signature::of);
    }

    // ---- text helpers ----

    private static TypeRef simple(String name) {
        return new TypeRef(name, List.of());
    }

    private static boolean keyword(Token token, String keyword) {
        return token.is(Kind.NAME, keyword);
    }

    /** The source with lines {@code from..to} (the cursor's logical line) replaced by one {@code pass}. */
    private static String neutralize(String source, int from, int to) {
        var lines = source.split("\n", -1);
        var out = new StringBuilder();
        for (int i = 0; i < lines.length; i++) {
            int number = i + 1;
            if (number == from) {
                var text = lines[i];
                out.append(text, 0, text.length() - text.stripLeading().length()).append("pass");
            } else if (number < from || number > to) {
                out.append(lines[i]);
            }
            if (i < lines.length - 1) {
                out.append('\n');
            }
        }
        return out.toString();
    }

    /** Whether a {@code #} outside a string starts a comment before the cursor on this line. */
    private static boolean inComment(String linePrefix) {
        char quote = 0;
        for (int i = 0; i < linePrefix.length(); i++) {
            char c = linePrefix.charAt(i);
            if (quote != 0) {
                if (c == '\\') {
                    i++;
                } else if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '#') {
                return true;
            }
        }
        return false;
    }

    private static int offset(String source, int line, int column) {
        int offset = 0;
        for (int i = 1; i < line; i++) {
            int next = source.indexOf('\n', offset);
            if (next < 0) {
                return source.length();
            }
            offset = next + 1;
        }
        int end = source.indexOf('\n', offset);
        return Math.min(offset + column - 1, end < 0 ? source.length() : end);
    }
}
