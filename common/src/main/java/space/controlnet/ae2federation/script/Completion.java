package space.controlnet.ae2federation.script;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import space.controlnet.ae2federation.script.Lexer.Kind;
import space.controlnet.ae2federation.script.Lexer.Token;

/**
 * Completion at a cursor in a script that is usually half typed. The cursor's logical line is read from its tokens
 * (what is being typed, the call it is in, the type that place needs); the rest of the file is parsed with that line
 * replaced by {@code pass}, so a broken line never hides the variables and functions around it.
 */
public final class Completion {
    static final List<String> TYPES = java.util.stream.Stream.of(List.of("int", "float", "str", "bool", "None", "list"),
            StandardLibrary.TYPED_STRINGS, StandardLibrary.OPAQUE, List.copyOf(StandardLibrary.RECORDS.keySet()))
            .flatMap(List::stream).toList();
    static final List<String> STATEMENT_KEYWORDS = List.of("if", "elif", "else", "for", "while", "return", "break",
            "continue", "pass");
    static final List<String> VALUE_KEYWORDS = List.of("True", "False", "None", "not");

    private final Workspace workspace;
    private final Program program;
    private final List<FunctionDef> ownFunctions;
    private final List<ClassDef> ownClasses;
    private final Map<String, TypeRef> variables = new LinkedHashMap<>();
    /** Result types of the functions without a hint, by file ({@code null} for this one), inferred when first asked. */
    private final Map<String, Map<String, TypeRef>> results = new java.util.HashMap<>();

    private Completion(Workspace workspace, Program program) {
        this.workspace = workspace;
        this.program = program;
        this.ownFunctions = program.functions();
        this.ownClasses = program.classes();
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
        var completion = new Completion(workspace, parsed.program());
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
            var receiver = receiver(context);
            if (receiver.size() == 1 && receiver.get(0).kind() == Kind.NAME
                    && !variables.containsKey(receiver.get(0).text())) {
                return members(receiver.get(0).text(), prefix);
            }
            return membersOf(receiverType(receiver), prefix);
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
                .forEach(f -> out.add(Candidate.of(signature(null, f), Candidate.Kind.FUNCTION)));
        ownClasses.stream().sorted(Comparator.comparing(ClassDef::name))
                .forEach(c -> out.add(Candidate.of(Signature.of(c), Candidate.Kind.TYPE)));
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
        var out = new ArrayList<Candidate>();
        if (builtIns != null) {
            builtIns.forEach(s -> out.add(Candidate.of(s, Candidate.Kind.FUNCTION)));
        } else if (workspace.files().containsKey(qualifier)) {
            var file = workspace.files().get(qualifier);
            file.functions().stream().filter(f -> !f.name().startsWith("_")).map(f -> signature(qualifier, f))
                    .forEach(s -> out.add(Candidate.of(s, Candidate.Kind.FUNCTION)));
            file.classes().stream().filter(c -> !c.name().startsWith("_"))
                    .forEach(c -> out.add(Candidate.of(Signature.of(c), Candidate.Kind.TYPE)));
        } else {
            return List.of();
        }
        out.sort(Comparator.comparing(Candidate::label));
        return filter(out, prefix);
    }

    /** Methods and fields of a value of this type: a list, a string, a range, or a record. */
    private List<Candidate> membersOf(TypeRef type, String prefix) {
        if (type == null) {
            return List.of();
        }
        var t = Types.unwrap(type);
        var out = new ArrayList<Candidate>();
        List<Signature> methods = List.of();
        if (Types.isList(t)) {
            var element = Types.element(t);
            methods = Checker.LIST_METHODS.stream().map(m -> element == null ? m : Checker.substitute(m, element))
                    .toList();
        } else if (Types.isStr(t)) {
            methods = Checker.STR_METHODS;
        } else if (Types.isRange(t)) {
            methods = Checker.RANGE_METHODS;
            Checker.RANGE_FIELDS.forEach(f -> out.add(new Candidate(f, Candidate.Kind.FIELD, "int")));
        } else {
            fields(t.name()).forEach((name, fieldType) ->
                    out.add(new Candidate(name, Candidate.Kind.FIELD, fieldType.toString())));
        }
        methods.forEach(m -> out.add(Candidate.of(m, Candidate.Kind.METHOD)));
        out.sort(Comparator.comparing(Candidate::label));
        return filter(out, prefix);
    }

    /** A record's fields and their types, in declared order: a built-in record or a player class. */
    private Map<String, TypeRef> fields(String typeName) {
        var fields = new LinkedHashMap<String, TypeRef>();
        var builtIn = StandardLibrary.RECORDS.get(typeName);
        if (builtIn != null) {
            builtIn.forEach(a -> fields.put(a.name(), a.type()));
        } else {
            record(typeName).ifPresent(c -> c.fields().forEach(f -> fields.put(f.name(), f.type())));
        }
        return fields;
    }

    /**
     * The tokens of the expression before the final {@code .}: names, literals, calls and items joined by dots,
     * as in {@code ae2.storage()[0]}; empty when there is none.
     */
    private static List<Token> receiver(List<Token> context) {
        int i = context.size() - 2;
        int start = -1;
        while (i >= 0) {
            var token = context.get(i);
            if (token.is(Kind.OP, ")") || token.is(Kind.OP, "]")) {
                int depth = 0;
                for (; i >= 0; i--) {
                    var t = context.get(i);
                    if (t.is(Kind.OP, ")") || t.is(Kind.OP, "]")) {
                        depth++;
                    } else if (t.is(Kind.OP, "(") || t.is(Kind.OP, "[")) {
                        depth--;
                    }
                    if (depth == 0) {
                        break;
                    }
                }
                if (i < 0) {
                    return List.of();
                }
                // Applied to what stands before it (a call or an item), or a group or list of its own.
                var before = i == 0 ? null : context.get(i - 1);
                boolean applied = before != null && (before.kind() == Kind.STRING || before.is(Kind.OP, ")")
                        || before.is(Kind.OP, "]") || before.kind() == Kind.NAME && !KEYWORDS.contains(before.text()));
                if (!applied) {
                    start = i;
                    break;
                }
                i--;
            } else if (token.kind() == Kind.NAME && !KEYWORDS.contains(token.text()) || token.kind() == Kind.STRING) {
                if (i > 0 && context.get(i - 1).is(Kind.OP, ".")) {
                    i -= 2;
                    continue;
                }
                start = i;
                break;
            } else {
                return List.of();
            }
        }
        return start < 0 ? List.of() : context.subList(start, context.size() - 1);
    }

    /** Python's keywords: never a value a member is read from. */
    private static final java.util.Set<String> KEYWORDS = java.util.Set.of("if", "elif", "else", "for", "while",
            "return", "break", "continue", "pass", "def", "class", "and", "or", "not", "in", "is");

    private TypeRef receiverType(List<Token> receiver) {
        if (receiver.isEmpty()) {
            return null;
        }
        var text = new StringBuilder();
        receiver.forEach(t -> text.append(t.text()).append(' '));
        var parsed = ScriptParser.parse("def completion_receiver() -> None:\n    completion_value = " + text + "\n");
        if (!parsed.ok()) {
            return null;
        }
        return infer(((Stmt.Assign) parsed.program().functions().get(0).body().get(0)).value());
    }

    private List<Candidate> types(String prefix) {
        var out = new ArrayList<Candidate>();
        TYPES.forEach(t -> out.add(new Candidate(t, Candidate.Kind.TYPE, "")));
        ownClasses.forEach(c -> out.add(Candidate.of(Signature.of(c), Candidate.Kind.TYPE)));
        return filter(out, prefix);
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
            return signature(null, function).result();
        }
        return null;
    }

    /** The type of the argument being typed in the call whose {@code (} is at {@code open}. */
    private TypeRef argumentType(List<Token> context, int open) {
        if (open < 1 || context.get(open - 1).kind() != Kind.NAME) {
            return null;
        }
        String qualifier = null;
        if (open >= 2 && context.get(open - 2).is(Kind.OP, ".")) {
            boolean plain = open >= 3 && context.get(open - 3).kind() == Kind.NAME
                    && !(open >= 4 && context.get(open - 4).is(Kind.OP, "."));
            if (!plain) {
                return null;
            }
            qualifier = context.get(open - 3).text();
        }
        int index = 0;
        int depth = 0;
        int argumentStart = open + 1;
        for (int i = open + 1; i < context.size(); i++) {
            var token = context.get(i);
            if (token.kind() != Kind.OP) {
                continue;
            }
            switch (token.text()) {
                case "(", "[", "{" -> depth++;
                case ")", "]", "}" -> depth--;
                case "," -> {
                    if (depth == 0) {
                        index++;
                        argumentStart = i + 1;
                    }
                }
                default -> {
                }
            }
        }
        var signature = signature(qualifier, context.get(open - 1).text());
        if (argumentStart + 1 < context.size() && context.get(argumentStart).kind() == Kind.NAME
                && context.get(argumentStart + 1).is(Kind.OP, "=")) {
            var keyword = context.get(argumentStart).text();
            return signature.flatMap(s -> s.args().stream().filter(a -> a.name().equals(keyword)).findFirst())
                    .map(Signature.Arg::type).orElse(null);
        }
        int argument = index;
        return signature.filter(s -> argument < s.args().size())
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

    /**
     * The last function starting at or before the line: the cursor is in its header or body. {@code null} when the
     * last definition there is a class, or there is none.
     */
    private FunctionDef enclosing(ParseResult parsed, int line) {
        Definition found = null;
        for (var definition : parsed.program().definitions()) {
            if (parsed.positions().get(definition.id()).line() <= line) {
                found = definition;
            }
        }
        return found instanceof FunctionDef function ? function : null;
    }

    /** Variables assigned in statements that start before the line, with the type they were given or hold. */
    private void collect(List<Stmt> body, ParseResult parsed, int line) {
        for (var statement : body) {
            if (parsed.positions().get(statement.id()).line() >= line) {
                return;
            }
            switch (statement) {
                case Stmt.Assign s -> {
                    if (s.target() instanceof Expr.Name name) {
                        declare(name.name(), s.type() != null ? s.type() : infer(s.value()));
                    }
                }
                case Stmt.For s -> {
                    var iterable = infer(s.iterable());
                    declare(s.variable(), iterable != null && Types.isIterable(Types.unwrap(iterable))
                            ? Types.iterated(Types.unwrap(iterable)) : null);
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
            case Expr.Call c -> {
                if (c.receiver() == null) {
                    var own = signature(null, c.name());
                    yield own.isPresent() ? own.get().result() : builtIn(c);
                }
                if (c.receiver() instanceof Expr.Name n && !variables.containsKey(n.name())) {
                    yield signature(n.name(), c.name()).map(Signature::result).orElse(null);
                }
                yield method(infer(c.receiver()), c.name());
            }
            case Expr.Attribute a -> {
                var type = infer(a.target());
                if (type == null) {
                    yield null;
                }
                type = Types.unwrap(type);
                yield Types.isRange(type) && Checker.RANGE_FIELDS.contains(a.name()) ? Types.INT
                        : fields(type.name()).get(a.name());
            }
            case Expr.Index i -> {
                var target = infer(i.target());
                target = target == null ? null : Types.unwrap(target);
                yield target != null && Types.isIterable(target) ? Types.iterated(target) : null;
            }
            case Expr.Slice s -> infer(s.target());
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

    /** What a built-in function gives, as far as completion needs it. */
    private TypeRef builtIn(Expr.Call c) {
        var first = c.args().isEmpty() ? null : infer(c.args().get(0));
        var iterable = first != null && Types.isIterable(Types.unwrap(first)) ? Types.unwrap(first) : null;
        return switch (c.name()) {
            case "int", "len" -> Types.INT;
            case "float" -> Types.FLOAT;
            case "str" -> Types.STR;
            case "bool", "any", "all" -> Types.BOOL;
            case "range" -> Types.RANGE;
            case "list" -> iterable == null || Types.iterated(iterable) == null ? Types.EMPTY_LIST
                    : Types.list(Types.iterated(iterable));
            case "min", "max", "sum" -> c.args().size() == 1 && iterable != null ? Types.iterated(iterable) : first;
            case "abs" -> first;
            case "round" -> c.args().size() == 1 ? Types.INT : first;
            case "print" -> Types.NONE;
            default -> null;
        };
    }

    /** What a list, str or range method gives. */
    private static TypeRef method(TypeRef receiver, String name) {
        if (receiver == null) {
            return null;
        }
        var t = Types.unwrap(receiver);
        List<Signature> methods = Types.isList(t) ? Checker.LIST_METHODS : Types.isStr(t) ? Checker.STR_METHODS
                : Types.isRange(t) ? Checker.RANGE_METHODS : List.of();
        var element = Types.isList(t) ? Types.element(t) : null;
        return methods.stream().filter(m -> m.name().equals(name)).findFirst()
                .map(m -> element == null ? m : Checker.substitute(m, element)).map(Signature::result).orElse(null);
    }

    /** A record type by name: one of this file's classes, or {@code file.Name} for another file's. */
    private Optional<ClassDef> record(String name) {
        int dot = name.indexOf('.');
        var classes = dot < 0 ? ownClasses
                : Optional.ofNullable(workspace.files().get(name.substring(0, dot))).map(Program::classes).orElse(List.of());
        var simple = name.substring(dot + 1);
        return classes.stream().filter(c -> c.name().equals(simple)).findFirst();
    }

    private Optional<Signature> signature(String qualifier, String name) {
        if (qualifier == null) {
            return ownFunctions.stream().filter(f -> f.name().equals(name)).findFirst().map(f -> signature(null, f))
                    .or(() -> record(name).map(Signature::of));
        }
        if (variables.containsKey(qualifier)) {
            // A local shadows a namespace or file of the same name, as in Python; its methods come with B3.
            return Optional.empty();
        }
        var builtIns = workspace.catalog().namespaces().get(qualifier);
        if (builtIns != null) {
            return builtIns.stream().filter(s -> s.name().equals(name)).findFirst();
        }
        var file = workspace.files().get(qualifier);
        if (file == null) {
            return Optional.empty();
        }
        return file.functions().stream().filter(f -> f.name().equals(name)).findFirst().map(f -> signature(qualifier, f))
                .or(() -> record(qualifier + "." + name).map(Signature::of).map(s -> new Signature(s.name(), s.args(),
                        new TypeRef(qualifier + "." + name, List.of()), s.summary())));
    }

    /** {@code function} of {@code file} ({@code null} for this one), with its result type written or inferred. */
    private Signature signature(String file, FunctionDef function) {
        var result = function.result() != null ? function.result() : results.computeIfAbsent(file, name ->
                Checker.results(name == null ? program : workspace.files().get(name), workspace)).get(function.name());
        return Signature.of(function, result);
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
