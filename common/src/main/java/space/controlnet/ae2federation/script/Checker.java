package space.controlnet.ae2federation.script;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Static checks over one parsed file: names, types, calls, assignments and returns. Problems point at node IDs, so
 * the script view (through {@link ParseResult#positions()}) and the graph view can both show them. Where CPython has
 * a message for the same mistake, the problem uses it.
 */
public final class Checker {
    /** A mistake at a node. */
    public record Problem(String node, String message) {
    }

    /**
     * Every problem found; the type of every expression that has one, by node ID; this file's functions that act on
     * the world, directly or through what they call; the call nodes that do; each function's result type, written
     * or inferred (left out where it cannot be inferred); and, among the acting functions and calls, the long actions.
     */
    public record Result(List<Problem> problems, Map<String, TypeRef> types, Set<String> effectfulFunctions,
            Set<String> effectfulCalls, Map<String, TypeRef> results, Set<String> lastingFunctions,
            Set<String> lastingCalls) {
    }

    /** A return type worked out from the returns: {@code null} when there is none, with the problem saying why. */
    private record Inferred(TypeRef type, String problem) {
    }

    /** One function whose returns are being read; {@code waitsOn} are running functions it met unknown. */
    private record Frame(Program program, String name, Set<Frame> waitsOn) {
        @Override
        public boolean equals(Object other) {
            return this == other;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    /**
     * Return types inferred during one check, shared with the checkers it starts for other functions and files. A
     * function met while its own returns are still being read is unknown there, so {@code n * fact(n - 1)} leaves
     * {@code fact}'s other returns to decide; a result that rested on such an unknown is not kept.
     */
    private static final class Inference {
        final Map<Program, Map<String, Inferred>> done = new java.util.IdentityHashMap<>();
        final java.util.Deque<Frame> running = new java.util.ArrayDeque<>();
    }

    /** Longer constant texts are left to run time rather than built while checking. */
    private static final int MAX_CONSTANT_TEXT = 10_000;

    static final List<String> BUILT_INS = List.of("int", "float", "str", "bool", "list", "dict", "set", "range", "len",
            "abs", "min", "max", "sum", "round", "any", "all", "print");

    /** List methods over the element type {@code T}; parameter names are CPython's. */
    static final List<Signature> LIST_METHODS = List.of(
            method("append", "None", arg("object", "T")),
            method("extend", "None", arg("iterable", "Iterable[T]")),
            method("insert", "None", arg("index", "int"), arg("object", "T")),
            method("pop", "T", new Signature.Arg("index", Types.INT, "-1")),
            method("remove", "None", arg("value", "T")),
            method("index", "int", arg("value", "T")),
            method("count", "int", arg("value", "T")),
            method("reverse", "None"),
            method("sort", "None", new Signature.Arg("reverse", Types.BOOL, "False")),
            method("clear", "None"),
            method("copy", "list[T]"));

    /**
     * Dict methods over the key type {@code K} and value type {@code V}; CPython's names, positional only. {@code pop}'s
     * default has no value of its own: without it a missing key is an error. Stricter than Python: {@code setdefault}
     * needs its default, and {@code items()} is out with tuples.
     */
    static final List<Signature> DICT_METHODS = List.of(
            method("get", "Optional[V]", arg("key", "K"), new Signature.Arg("default", StandardLibrary.type("V"), "None")),
            method("pop", "V", arg("key", "K"), new Signature.Arg("default", StandardLibrary.type("V"), "...")),
            method("setdefault", "V", arg("key", "K"), arg("default", "V")),
            method("keys", "list[K]"),
            method("values", "list[V]"),
            method("update", "None", arg("other", "dict[K, V]")),
            method("clear", "None"),
            method("copy", "dict[K, V]"));

    /** Set methods over the item type {@code T}; CPython's names, positional only, each taking one other iterable. */
    static final List<Signature> SET_METHODS = List.of(
            method("add", "None", arg("elem", "T")),
            method("remove", "None", arg("elem", "T")),
            method("discard", "None", arg("elem", "T")),
            method("pop", "T"),
            method("union", "set[T]", arg("other", "Iterable[T]")),
            method("intersection", "set[T]", arg("other", "Iterable[T]")),
            method("difference", "set[T]", arg("other", "Iterable[T]")),
            method("symmetric_difference", "set[T]", arg("other", "Iterable[T]")),
            method("update", "None", arg("other", "Iterable[T]")),
            method("issubset", "bool", arg("other", "Iterable[T]")),
            method("issuperset", "bool", arg("other", "Iterable[T]")),
            method("isdisjoint", "bool", arg("other", "Iterable[T]")),
            method("clear", "None"),
            method("copy", "set[T]"));

    static final List<Signature> STR_METHODS = List.of(
            method("startswith", "bool", arg("prefix", "str")),
            method("endswith", "bool", arg("suffix", "str")),
            method("lower", "str"),
            method("upper", "str"),
            method("strip", "str"),
            method("split", "list[str]", new Signature.Arg("sep", Types.STR, "None")),
            method("join", "str", arg("iterable", "Iterable[str]")),
            method("replace", "str", arg("old", "str"), arg("new", "str")),
            method("find", "int", arg("sub", "str")),
            method("count", "int", arg("sub", "str")));

    static final List<Signature> RANGE_METHODS = List.of(
            method("index", "int", arg("value", "int")),
            method("count", "int", arg("value", "int")));

    /** A range's read-only attributes. */
    static final List<String> RANGE_FIELDS = List.of("start", "stop", "step");

    private final Program program;
    private final Workspace workspace;
    private final List<Problem> problems = new ArrayList<>();
    private final Map<String, TypeRef> types = new HashMap<>();
    /** Values known before the program runs, by node ID: Long, Double or String. */
    private final Map<String, Object> constants = new HashMap<>();
    private final Map<String, FunctionDef> functions = new LinkedHashMap<>();
    private final Map<String, ClassDef> classes = new LinkedHashMap<>();

    private final Inference inference;
    /** Each function's result type, written or inferred, as {@link Result#results()} gives them. */
    private final Map<String, TypeRef> results = new HashMap<>();
    /** While inferring: the type of each return, {@code null} where unknown. */
    private List<TypeRef> returned;

    private FunctionDef current;
    private TypeRef currentResult;
    private final Map<String, Local> locals = new HashMap<>();
    private final Set<String> assignedInFunction = new HashSet<>();

    /** A local variable and its type, fixed by its first assignment. */
    private record Local(TypeRef type) {
    }

    /** A record type's fields, with types as seen from this file. */
    private record Record(String name, Map<String, TypeRef> fields, boolean readOnly) {
    }

    private Checker(Program program, Workspace workspace, Inference inference) {
        this.program = program;
        this.workspace = workspace;
        this.inference = inference;
    }

    public static Result check(Program program, Workspace workspace) {
        var checker = new Checker(program, workspace, new Inference());
        checker.run();
        var effects = Effects.of(program, workspace);
        return new Result(List.copyOf(checker.problems), Map.copyOf(checker.types), effects.functions(),
                effects.calls(), Map.copyOf(checker.results), effects.lastingFunctions(), effects.lastingCalls());
    }

    /**
     * The long actions among the player functions {@code program} can call: its own by name, the other files' as
     * {@code file.name}. A function is one when it calls a long action, however indirectly.
     */
    public static Set<String> lastingFunctions(Program program, Workspace workspace) {
        return Effects.of(program, workspace).lastingKeys();
    }

    /**
     * Each function's result type in {@code program}, written or inferred, without the rest of the checks; a function
     * whose type cannot be inferred is left out.
     */
    public static Map<String, TypeRef> results(Program program, Workspace workspace) {
        var checker = new Checker(program, workspace, new Inference());
        checker.register();
        var out = new HashMap<String, TypeRef>();
        for (var function : checker.functions.values()) {
            var type = checker.resultOf(function);
            if (type != null) {
                out.put(function.name(), type);
            }
        }
        return out;
    }

    // ---- definitions ----

    private void run() {
        register();
        for (var record : classes.values()) {
            checkClass(record);
        }
        for (var definition : program.definitions()) {
            if (definition instanceof FunctionDef f && functions.get(f.name()) == f) {
                checkFunction(f);
            }
        }
    }

    /** Names this file's functions and classes; a name defined twice keeps its first definition. */
    private void register() {
        for (var definition : program.definitions()) {
            if (functions.containsKey(definition.name()) || classes.containsKey(definition.name())) {
                problem(definition.id(), "'" + definition.name() + "' is already defined in this file");
                continue;
            }
            switch (definition) {
                case FunctionDef f -> functions.put(f.name(), f);
                case ClassDef c -> {
                    if (Completion.TYPES.contains(c.name()) || c.name().equals("Optional")) {
                        problem(c.id(), "'" + c.name() + "' is a built-in type");
                    } else {
                        classes.put(c.name(), c);
                    }
                }
            }
        }
    }

    private void checkClass(ClassDef record) {
        var seen = new HashSet<String>();
        for (var field : record.fields()) {
            if (!seen.add(field.name())) {
                problem(field.id(), "field '" + field.name() + "' is already defined");
                continue;
            }
            var type = annotation(field.type(), field.id());
            if (field.defaultValue() != null) {
                var mutable = switch (field.defaultValue()) {
                    case Expr.ListLit l -> "list";
                    case Expr.DictLit d -> "dict";
                    case Expr.SetLit s -> "set";
                    default -> null;
                };
                if (mutable != null) {
                    problem(field.defaultValue().id(), "mutable default <class '" + mutable + "'> for field "
                            + field.name() + " is not allowed: use default_factory");
                    continue;
                }
                checkDefault(field.name(), type, field.defaultValue());
            }
        }
    }

    private void checkDefault(String name, TypeRef type, Expr value) {
        var actual = expr(value, type);
        boolean constant = value instanceof Expr.BoolLit || value instanceof Expr.NoneLit
                || constants.containsKey(value.id());
        if (!constant && actual != null) {
            // Stricter than Python, which evaluates any expression once when the def runs.
            problem(value.id(), "default of '" + name + "' must be a constant: a number, a string, True, False or None");
            return;
        }
        if (!Types.assignable(type, actual) && !Types.NONE.equals(actual)) {
            problem(value.id(), "default of '" + name + "' must be " + type + ", not " + actual);
        }
    }

    private void checkFunction(FunctionDef function) {
        if (function.result() == null) {
            var inferred = inferred(function);
            if (inferred.problem() != null) {
                problem(function.id(), inferred.problem());
            }
            body(function, inferred.type());
            if (inferred.type() != null) {
                results.put(function.name(), inferred.type());
            }
            // A run off the end is one of the inferred returns, not a missing one.
            Flow.check(function, null, assignedInFunction, this::problem);
            return;
        }
        var result = annotation(function.result(), function.id());
        body(function, result);
        if (result != null) {
            results.put(function.name(), result);
        }
        Flow.check(function, currentResult, assignedInFunction, this::problem);
    }

    /** Checks the parameters and the body, returns against {@code result}. */
    private void body(FunctionDef function, TypeRef result) {
        current = function;
        locals.clear();
        assignedInFunction.clear();
        for (var param : function.params()) {
            var type = annotation(param.type(), param.id());
            if (param.defaultValue() != null) {
                checkDefault(param.name(), type, param.defaultValue());
                if (param.defaultValue() instanceof Expr.NoneLit) {
                    type = Types.optional(type);
                }
            }
            locals.put(param.name(), new Local(type));
        }
        currentResult = result;
        collectAssigned(function.body());
        statements(function.body());
    }

    // ---- inferred results ----

    /** The type {@code function} of this file returns: its hint as written, or what its returns give. */
    private TypeRef resultOf(FunctionDef function) {
        return function.result() != null ? function.result() : inferred(function).type();
    }

    /** What {@code function} of {@code file} returns, as that file names its types. */
    private TypeRef resultOf(Program file, FunctionDef function) {
        return function.result() != null ? function.result()
                : new Checker(file, workspace, inference).inferred(function).type();
    }

    private Inferred inferred(FunctionDef function) {
        var known = inference.done.computeIfAbsent(program, p -> new HashMap<>());
        var found = known.get(function.name());
        if (found != null) {
            return found;
        }
        for (var frame : inference.running) {
            if (frame.program() == program && frame.name().equals(function.name())) {
                inference.running.peek().waitsOn().add(frame);
                return new Inferred(null, null);
            }
        }
        var frame = new Frame(program, function.name(), new HashSet<>());
        inference.running.push(frame);
        var checker = new Checker(program, workspace, inference);
        checker.register();
        var result = checker.infer(function);
        inference.running.pop();
        frame.waitsOn().remove(frame);
        if (frame.waitsOn().isEmpty()) {
            known.put(function.name(), result);
        } else if (!inference.running.isEmpty()) {
            inference.running.peek().waitsOn().addAll(frame.waitsOn());
        }
        return result;
    }

    /**
     * Reads {@code function}'s returns, as a type checker for Python would: one type for all of them, a bare
     * {@code return} or a run off the end adding None (so {@code Optional}); none at all is None.
     */
    private Inferred infer(FunctionDef function) {
        returned = new ArrayList<>();
        body(function, null);
        if (Flow.canEnd(function)) {
            returned.add(Types.NONE);
        }
        if (returned.isEmpty()) {
            return new Inferred(Types.NONE, null);
        }
        var display = function.name() + "()";
        TypeRef joined = null;
        for (var type : returned) {
            if (type == null) {
                continue;
            }
            var next = joined == null ? type : join(joined, type);
            if (next == null) {
                return new Inferred(null, display + " returns both " + joined + " and " + type
                        + "; write its return type");
            }
            joined = next;
        }
        if (joined == null) {
            // With a mistake in the returns, that mistake is the problem shown.
            return new Inferred(null, problems.isEmpty() ? display
                    + " has no return type to infer: its returns only call back into it; write its return type" : null);
        }
        if (joined.equals(Types.EMPTY_LIST)) {
            return new Inferred(null, display + " returns only an empty list; write its return type, as in -> list[int]");
        }
        return new Inferred(joined, null);
    }

    /** One type for two returns: an empty list takes the other list's type. */
    private static TypeRef join(TypeRef a, TypeRef b) {
        if (a.equals(Types.EMPTY_LIST) && Types.isList(Types.unwrap(b))) {
            return b;
        }
        if (b.equals(Types.EMPTY_LIST) && Types.isList(Types.unwrap(a))) {
            return a;
        }
        return Types.common(a, b);
    }

    /** Names assigned anywhere in the body are local to it, as in Python. */
    private void collectAssigned(List<Stmt> body) {
        for (var statement : body) {
            switch (statement) {
                case Stmt.Assign s -> {
                    if (s.target() instanceof Expr.Name n) {
                        assignedInFunction.add(n.name());
                    }
                }
                case Stmt.AugAssign s -> {
                    if (s.target() instanceof Expr.Name n) {
                        assignedInFunction.add(n.name());
                    }
                }
                case Stmt.For s -> {
                    assignedInFunction.add(s.variable());
                    collectAssigned(s.body());
                }
                case Stmt.While s -> collectAssigned(s.body());
                case Stmt.If s -> {
                    collectAssigned(s.body());
                    s.elifs().forEach(e -> collectAssigned(e.body()));
                    if (s.orElse() != null) {
                        collectAssigned(s.orElse().body());
                    }
                }
                default -> {
                }
            }
        }
    }

    /** A type hint as written, resolved; {@code null} (and a problem) if it names no type. */
    private TypeRef annotation(TypeRef written, String node) {
        var name = written.name();
        if (name.equals("list") || name.equals("Optional")) {
            if (written.args().size() != 1) {
                problem(node, name + " needs its element type, as in " + name + "[int]");
                return null;
            }
            var inner = annotation(written.args().get(0), node);
            return inner == null ? null : name.equals("list") ? Types.list(inner) : Types.optional(inner);
        }
        if (name.equals("set") || name.equals("dict")) {
            int arity = name.equals("set") ? 1 : 2;
            if (written.args().size() != arity) {
                problem(node, name.equals("set") ? "set needs its element type, as in set[int]"
                        : "dict needs its key and value types, as in dict[str, int]");
                return null;
            }
            var args = new ArrayList<TypeRef>();
            for (var arg : written.args()) {
                var inner = annotation(arg, node);
                if (inner == null) {
                    return null;
                }
                args.add(inner);
            }
            if (!hashable(args.get(0), node)) {
                return null;
            }
            return new TypeRef(name, args);
        }
        if (!written.args().isEmpty()) {
            problem(node, "'" + name + "' takes no type arguments");
            return null;
        }
        if (Completion.TYPES.contains(name) || classes.containsKey(name) || record(written).isPresent()) {
            return written;
        }
        problem(node, "unknown type '" + name + "'");
        return null;
    }

    // ---- statements ----

    private void statements(List<Stmt> body) {
        for (var statement : body) {
            statement(statement);
        }
    }

    private void statement(Stmt statement) {
        switch (statement) {
            case Stmt.Assign s -> assign(s);
            case Stmt.AugAssign s -> augAssign(s);
            case Stmt.CallStmt s -> expr(s.call(), null);
            case Stmt.If s -> {
                expr(s.condition(), null);
                statements(s.body());
                for (var elif : s.elifs()) {
                    expr(elif.condition(), null);
                    statements(elif.body());
                }
                if (s.orElse() != null) {
                    statements(s.orElse().body());
                }
            }
            case Stmt.While s -> {
                expr(s.condition(), null);
                statements(s.body());
            }
            case Stmt.For s -> {
                var iterable = expr(s.iterable(), null);
                var element = iterable == null ? null : elementOf(iterable, s.iterable().id());
                declare(s.variable(), element, s.id());
                statements(s.body());
            }
            case Stmt.Return s -> returns(s);
            case Stmt.Break s -> {
            }
            case Stmt.Continue s -> {
            }
            case Stmt.Pass s -> {
            }
        }
    }

    /** What a {@code for} loop over a value of this type yields; {@code null} and a problem if it is not iterable. */
    private TypeRef elementOf(TypeRef iterable, String node) {
        var t = Types.unwrap(iterable);
        if (Types.isIterable(t)) {
            return Types.iterated(t);
        }
        problem(node, "'" + Types.runtimeName(t) + "' object is not iterable");
        return null;
    }

    private void assign(Stmt.Assign s) {
        switch (s.target()) {
            case Expr.Name n -> {
                var annotated = s.type() == null ? null : annotation(s.type(), s.id());
                var local = locals.get(n.name());
                var expected = annotated != null ? annotated : local == null ? null : local.type();
                var value = expr(s.value(), expected);
                if (s.type() != null && annotated == null) {
                    declare(n.name(), null, s.id());
                    return;
                }
                if (annotated != null) {
                    if (local != null && local.type() != null && !local.type().equals(annotated)) {
                        problem(s.id(), n.name() + " is " + local.type() + "; it cannot be annotated " + annotated);
                        return;
                    }
                    // An annotation turns a str into a typed string; the value is checked when the program runs.
                    boolean converts = Types.isTypedString(Types.unwrap(annotated)) && Types.isStr(value);
                    if (!converts && !Types.assignable(annotated, value)) {
                        problem(s.id(), n.name() + " is " + annotated + "; it cannot hold " + value);
                    }
                    locals.put(n.name(), new Local(annotated));
                    return;
                }
                if (local == null && Types.EMPTY_LIST.equals(value)) {
                    problem(s.id(), "annotate the empty list, as in " + n.name() + ": list[int] = []");
                    declare(n.name(), null, s.id());
                    return;
                }
                if (local == null && (Types.EMPTY_DICT.equals(value) || Types.EMPTY_SET.equals(value))) {
                    problem(s.id(), Types.EMPTY_DICT.equals(value)
                            ? "annotate the empty dict, as in " + n.name() + ": dict[str, int] = {}"
                            : "annotate the empty set, as in " + n.name() + ": set[int] = set()");
                    declare(n.name(), null, s.id());
                    return;
                }
                declare(n.name(), value, s.id());
            }
            case Expr.Attribute a -> {
                var field = fieldForWrite(a);
                if (field == null) {
                    expr(s.value(), null);
                    return;
                }
                var value = expr(s.value(), field.type());
                if (!Types.assignable(field.type(), value)) {
                    problem(s.id(), "field '" + a.name() + "' of " + Types.runtimeName(field.owner()) + " is "
                            + field.type() + "; it cannot hold " + value);
                }
            }
            case Expr.Index i -> {
                var owner = expr(i.target(), null);
                if (Types.isDict(Types.unwrap(owner))) {
                    var dict = Types.unwrap(owner);
                    if (key(dict, i.index(), s.id()) && Types.value(dict) != null) {
                        // The place written to has the value type, as the graph's target port shows it.
                        types.put(i.id(), Types.value(dict));
                    }
                    var value = expr(s.value(), Types.value(dict));
                    if (Types.value(dict) != null && !Types.assignable(Types.value(dict), value)) {
                        problem(s.id(), "values of " + dict + " are " + Types.value(dict) + "; this is " + value);
                    }
                    return;
                }
                var element = itemForWrite(owner, i.id());
                expr(i.index(), Types.INT);
                if (element != null) {
                    checkIndex(i.index());
                    types.put(i.id(), element);
                }
                var value = expr(s.value(), element);
                if (element != null && !Types.assignable(element, value)) {
                    problem(s.id(), "items of " + types.get(i.target().id()) + " are " + element
                            + "; this is " + value);
                }
            }
            case Expr.Slice sl -> {
                var element = itemForWrite(expr(sl.target(), null), sl.id());
                sliceParts(sl);
                var value = expr(s.value(), element == null ? null : Types.list(element));
                if (element != null && !Types.assignable(Types.list(element), value)) {
                    problem(s.id(), "items of " + types.get(sl.target().id()) + " are " + element
                            + "; this is " + value);
                }
            }
            default -> expr(s.value(), null);
        }
    }

    /**
     * Gives a local its first type, or checks a later value against it. A local may not start as {@code None}
     * without an annotation (user decision, stricter than Python): its type would only be known later.
     */
    private void declare(String name, TypeRef value, String node) {
        var local = locals.get(name);
        if (local == null) {
            if (Types.NONE.equals(value)) {
                problem(node, name + " cannot start as None; give it a type, as in " + name
                        + ": Optional[int] = None");
                value = null;
            }
            locals.put(name, new Local(value));
            return;
        }
        if (local.type() != null && !Types.assignable(local.type(), value)) {
            problem(node, name + " is " + local.type() + "; it cannot hold " + value);
        }
    }

    private void augAssign(Stmt.AugAssign s) {
        var target = expr(s.target(), null);
        var value = expr(s.value(), null);
        if (target == null || value == null) {
            return;
        }
        var result = binary(s.op(), target, value, s.id());
        if (result != null && !Types.assignable(target, result)) {
            var name = s.target() instanceof Expr.Name n ? n.name() : ScriptPrinter.expr(s.target());
            problem(s.id(), name + " is " + target + "; it cannot hold " + result);
        }
    }

    private void returns(Stmt.Return s) {
        if (returned != null) {
            returned.add(s.value() == null ? Types.NONE : expr(s.value(), null));
            return;
        }
        var display = current.name() + "()";
        if (s.value() == null) {
            if (currentResult != null && !currentResult.equals(Types.NONE)) {
                problem(s.id(), display + " must return " + currentResult);
            }
            return;
        }
        var value = expr(s.value(), currentResult);
        if (Types.NONE.equals(currentResult)) {
            if (value != null && !value.equals(Types.NONE)) {
                problem(s.id(), display + " returns None; this return has a value");
            }
            return;
        }
        if (!Types.assignable(currentResult, value)) {
            problem(s.id(), display + " must return " + currentResult + ", not " + value);
        }
    }

    // ---- expressions ----

    /**
     * The type of {@code e}, recorded under its ID. {@code expected} is where the value goes, if known: a string
     * literal there takes its typed-string type, and a list literal its element type.
     */
    private TypeRef expr(Expr e, TypeRef expected) {
        var type = switch (e) {
            case Expr.IntLit l -> Types.INT;
            case Expr.FloatLit l -> Types.FLOAT;
            case Expr.BoolLit l -> Types.BOOL;
            case Expr.NoneLit l -> Types.NONE;
            case Expr.StrLit l -> {
                var target = Types.unwrap(expected);
                yield Types.isTypedString(target) ? target : Types.STR;
            }
            case Expr.ListLit l -> listLiteral(l, expected);
            case Expr.DictLit d -> dictLiteral(d, expected);
            case Expr.SetLit s -> setLiteral(s, expected);
            case Expr.Name n -> name(n);
            case Expr.Call c -> call(c);
            case Expr.Attribute a -> attribute(a);
            case Expr.Index i -> index(i);
            case Expr.Slice s -> slice(s);
            case Expr.Unary u -> unary(u);
            case Expr.Binary b -> {
                var left = expr(b.left(), null);
                var right = expr(b.right(), null);
                yield left == null || right == null ? null : binary(b.op(), left, right, b.id());
            }
        };
        if (type != null) {
            types.put(e.id(), type);
            fold(e);
            var target = Types.unwrap(expected);
            if (Types.isTypedString(target) && constants.get(e.id()) instanceof String value) {
                validate(e.id(), target, value);
            }
        }
        return type;
    }

    // ---- constants ----

    /** Records the value of a literal, or of an operator over constants, computed as Python computes it. */
    private void fold(Expr e) {
        switch (e) {
            case Expr.IntLit l -> constants.put(e.id(), l.value());
            case Expr.FloatLit l -> constants.put(e.id(), l.value());
            case Expr.StrLit l -> constants.put(e.id(), l.value());
            case Expr.Unary u when u.op() == UnaryOp.NEG -> {
                var operand = constants.get(u.operand().id());
                if (operand instanceof Long v) {
                    if (v == Long.MIN_VALUE) {
                        problem(e.id(), "integers are 64-bit; this result is too large");
                    } else {
                        constants.put(e.id(), -v);
                    }
                } else if (operand instanceof Double v) {
                    constants.put(e.id(), -v);
                }
            }
            case Expr.Binary b -> {
                var left = constants.get(b.left().id());
                var right = constants.get(b.right().id());
                if (left != null && right != null) {
                    var value = arithmetic(b.op(), left, right, b.id());
                    if (value != null) {
                        constants.put(e.id(), value);
                    }
                }
            }
            default -> {
            }
        }
    }

    private Object arithmetic(BinaryOp op, Object left, Object right, String node) {
        if (left instanceof String a && right instanceof String b) {
            return op == BinaryOp.ADD && a.length() + b.length() <= MAX_CONSTANT_TEXT ? a + b : null;
        }
        if (left instanceof Number && right instanceof Number && op.augmentable()) {
            try {
                return Ops.binary(op, left, right);
            } catch (RunError e) {
                // Division by zero and 64-bit overflow: the program would stop here.
                problem(node, e.getMessage());
                return null;
            }
        }
        return null;
    }

    /** Checks a constant typed string against the closed sets and the catalogue. */
    private void validate(String node, TypeRef type, String value) {
        var name = type.name();
        var fixed = StandardLibrary.ENUMERATIONS.get(name);
        if (fixed != null) {
            if (!fixed.contains(value)) {
                problem(node, "'" + value + "' is not a " + name + "; expected one of " + String.join(", ", fixed));
            }
            return;
        }
        switch (name) {
            case "Item", "Fluid", "Resource" -> {
                var kinds = workspace.catalog().kinds(value);
                if (kinds.isEmpty()) {
                    problem(node, "unknown " + kind(name) + " '" + value + "'");
                } else if (name.equals("Resource") && kinds.size() > 1) {
                    var sorted = kinds.stream().sorted(java.util.Comparator.comparingInt(Checker::kindOrder)
                            .thenComparing(k -> k)).map(k -> withArticle(kind(k))).toList();
                    problem(node, "'" + value + "' is " + (sorted.size() == 2 ? "both " + sorted.get(0) + " and "
                            + sorted.get(1) : "several kinds of resource (" + String.join(", ", sorted) + ")")
                            + "; annotate it as Item or Fluid");
                } else if (!name.equals("Resource") && !kinds.contains(name)) {
                    problem(node, "'" + value + "' is " + withArticle(kind(kinds.iterator().next())) + ", not "
                            + withArticle(kind(name)));
                }
            }
            case "Interface", "Network" -> {
                if (!workspace.catalog().exists(type, value)) {
                    problem(node, "no " + name.toLowerCase(java.util.Locale.ROOT) + " '" + value + "' in this domain");
                }
            }
            default -> {
            }
        }
    }

    private static int kindOrder(String kind) {
        return kind.equals("Item") ? 0 : kind.equals("Fluid") ? 1 : 2;
    }

    /** {@code item}, {@code fluid}, {@code resource}, or an addon's key type as it is named. */
    private static String kind(String name) {
        return switch (name) {
            case "Item", "Fluid", "Resource" -> name.toLowerCase(java.util.Locale.ROOT);
            default -> name;
        };
    }

    private static String withArticle(String word) {
        return ("aeiou".indexOf(word.charAt(0)) >= 0 ? "an " : "a ") + word;
    }

    private TypeRef listLiteral(Expr.ListLit l, TypeRef expected) {
        var target = Types.unwrap(expected);
        var element = Types.isList(target) ? Types.element(target) : null;
        if (l.items().isEmpty()) {
            return element != null ? target : Types.EMPTY_LIST;
        }
        TypeRef common = null;
        for (var item : l.items()) {
            var type = expr(item, element);
            if (type == null) {
                return null;
            }
            if (element != null) {
                if (!Types.assignable(element, type)) {
                    problem(item.id(), "list items must be " + element + ", not " + type);
                    return null;
                }
                continue;
            }
            if (common == null) {
                common = type;
                continue;
            }
            var joined = Types.common(common, type);
            if (joined == null) {
                problem(l.id(), "list items must share one type, here " + common + " and " + type);
                return null;
            }
            common = joined;
        }
        return Types.list(element != null ? element : common);
    }

    /** A problem for a key or set item type that cannot be hashed, in CPython's words; whether it can be. */
    private boolean hashable(TypeRef type, String node) {
        if (Types.hashable(type)) {
            return true;
        }
        problem(node, "unhashable type: '" + Types.runtimeName(type) + "'");
        return false;
    }

    /** The common type of literal parts, or {@code null} and a problem naming {@code what}. */
    private TypeRef shared(List<Expr> parts, TypeRef expected, String what, String node) {
        TypeRef common = null;
        for (var part : parts) {
            var type = expr(part, expected);
            if (type == null) {
                return null;
            }
            if (expected != null) {
                if (!Types.assignable(expected, type)) {
                    problem(part.id(), what + " must be " + expected + ", not " + type);
                    return null;
                }
                continue;
            }
            if (common == null) {
                common = type;
                continue;
            }
            var joined = Types.common(common, type);
            if (joined == null) {
                problem(node, what + " must share one type, here " + common + " and " + type);
                return null;
            }
            common = joined;
        }
        return expected != null ? expected : common;
    }

    private TypeRef dictLiteral(Expr.DictLit d, TypeRef expected) {
        var target = Types.unwrap(expected);
        boolean typed = Types.isDict(target) && !target.args().isEmpty();
        if (d.keys().isEmpty()) {
            return typed ? target : Types.EMPTY_DICT;
        }
        var key = shared(d.keys(), typed ? Types.key(target) : null, "dict keys", d.id());
        if (key == null || !hashable(key, d.keys().get(0).id())) {
            d.values().forEach(v -> expr(v, null));
            return null;
        }
        var value = shared(d.values(), typed ? Types.value(target) : null, "dict values", d.id());
        return value == null ? null : Types.dict(key, value);
    }

    private TypeRef setLiteral(Expr.SetLit s, TypeRef expected) {
        var target = Types.unwrap(expected);
        boolean typed = Types.isSet(target) && !target.args().isEmpty();
        var item = shared(s.items(), typed ? Types.element(target) : null, "set items", s.id());
        return item == null || !hashable(item, s.items().get(0).id()) ? null : Types.set(item);
    }

    /** Types a dict key against the dict's key type; whether it fits. */
    private boolean key(TypeRef dict, Expr key, String node) {
        var type = expr(key, Types.key(dict));
        if (type != null && Types.key(dict) != null && !Types.assignable(Types.key(dict), type)) {
            problem(node, "keys of " + dict + " are " + Types.key(dict) + "; this is " + type);
            return false;
        }
        return type != null;
    }

    private TypeRef name(Expr.Name n) {
        var local = locals.get(n.name());
        if (local != null) {
            return local.type();
        }
        if (assignedInFunction.contains(n.name())) {
            return null;
        }
        notAValue(n.id(), n.name());
        return null;
    }

    /** Reports a name that is not a variable: a function, class, namespace, file, or nothing. */
    private void notAValue(String node, String name) {
        if (functions.containsKey(name) || BUILT_INS.contains(name)) {
            problem(node, "functions are not values; call " + name + "(...)");
        } else if (classes.containsKey(name)) {
            problem(node, "classes are not values; construct one with " + name + "(...)");
        } else if (workspace.catalog().namespaces().containsKey(name)) {
            problem(node, "'" + name + "' is a namespace; call its functions as " + name + ".name(...)");
        } else if (workspace.files().containsKey(name)) {
            problem(node, "'" + name + "' is a file; use it as " + name + ".name(...)");
        } else {
            problem(node, "name '" + name + "' is not defined");
        }
    }

    private boolean isVariable(String name) {
        return locals.containsKey(name) || assignedInFunction.contains(name);
    }

    private TypeRef call(Expr.Call c) {
        if (c.receiver() == null) {
            var name = c.name();
            if (isVariable(name)) {
                var type = name(new Expr.Name(c.id(), name));
                arguments(c);
                if (type != null) {
                    problem(c.id(), "'" + Types.runtimeName(type) + "' object is not callable");
                }
                return null;
            }
            if (functions.containsKey(name)) {
                var function = functions.get(name);
                return bind(Signature.of(function, resultOf(function)), name + "()", c);
            }
            if (classes.containsKey(name)) {
                return bind(constructor(record(Types.simple(name)).orElseThrow()), name + "()", c);
            }
            if (BUILT_INS.contains(name)) {
                return builtIn(c);
            }
            arguments(c);
            problem(c.id(), "name '" + name + "' is not defined");
            return null;
        }
        if (c.receiver() instanceof Expr.Name r && !isVariable(r.name())) {
            var qualifier = r.name();
            var namespace = workspace.catalog().namespaces().get(qualifier);
            if (namespace != null) {
                var signature = namespace.stream().filter(s -> s.name().equals(c.name())).findFirst();
                if (signature.isEmpty()) {
                    arguments(c);
                    problem(c.id(), qualifier + " has no function '" + c.name() + "'");
                    return null;
                }
                return bind(signature.get(), qualifier + "." + c.name() + "()", c);
            }
            var file = workspace.files().get(qualifier);
            if (file != null) {
                return fileCall(qualifier, file, c);
            }
            arguments(c);
            notAValue(r.id(), qualifier);
            return null;
        }
        var receiver = expr(c.receiver(), null);
        if (receiver == null) {
            arguments(c);
            return null;
        }
        var t = Types.unwrap(receiver);
        List<Signature> methods;
        String owner;
        if (Types.isList(t)) {
            if (Types.element(t) == null) {
                arguments(c);
                return null;
            }
            methods = LIST_METHODS.stream().map(m -> substitute(m, Types.element(t))).toList();
            owner = "list";
        } else if (Types.isDict(t) || Types.isSet(t)) {
            return mappingMethod(c, t);
        } else if (Types.isStr(t)) {
            methods = STR_METHODS;
            owner = "str";
        } else if (Types.isRange(t)) {
            methods = RANGE_METHODS;
            owner = "range";
        } else {
            methods = List.of();
            owner = Types.runtimeName(t);
        }
        var method = methods.stream().filter(m -> m.name().equals(c.name())).findFirst();
        if (method.isEmpty()) {
            arguments(c);
            problem(c.id(), "'" + owner + "' object has no attribute '" + c.name() + "'");
            return null;
        }
        return bind(method.get(), owner + "." + c.name() + "()", c);
    }

    /**
     * A dict or set method. Their arguments are positional only, as in CPython. {@code get} and {@code pop} with a
     * default give the value type when the default is one, else {@code Optional}.
     */
    private TypeRef mappingMethod(Expr.Call c, TypeRef t) {
        boolean dict = Types.isDict(t);
        var owner = dict ? "dict" : "set";
        if (dict && c.name().equals("items")) {
            arguments(c);
            problem(c.id(), "dict.items() is not supported, as tuples are not; walk the keys with for key in d");
            return null;
        }
        var method = (dict ? DICT_METHODS : SET_METHODS).stream().filter(m -> m.name().equals(c.name())).findFirst();
        if (method.isEmpty()) {
            arguments(c);
            problem(c.id(), "'" + owner + "' object has no attribute '" + c.name() + "'");
            return null;
        }
        if (!c.keywords().isEmpty()) {
            arguments(c);
            problem(c.id(), owner + "." + c.name() + "() takes no keyword arguments");
            return null;
        }
        if (t.args().isEmpty()) {
            arguments(c);
            return null;
        }
        var variables = dict ? Map.of("K", Types.key(t), "V", Types.value(t)) : Map.of("T", Types.element(t));
        var signature = substitute(method.get(), variables);
        boolean withDefault = dict && (c.name().equals("get") || c.name().equals("pop")) && c.args().size() == 2;
        if (withDefault) {
            // The default may be None, which gives Optional, or a value, which gives the value type.
            var args = new ArrayList<>(signature.args());
            args.set(1, args.get(1).withType(Types.optional(Types.value(t))));
            signature = new Signature(signature.name(), args, signature.result(), signature.summary());
        }
        var result = bind(signature, owner + "." + c.name() + "()", c);
        if (withDefault) {
            var given = types.get(c.args().get(1).id());
            return given == null || Types.NONE.equals(given) || Types.isOptional(given)
                    ? Types.optional(Types.value(t)) : Types.value(t);
        }
        return result;
    }

    private TypeRef fileCall(String fileName, Program file, Expr.Call c) {
        var name = c.name();
        var function = file.functions().stream().filter(f -> f.name().equals(name)).findFirst();
        var record = file.classes().stream().filter(k -> k.name().equals(name)).findFirst();
        if (function.isEmpty() && record.isEmpty()) {
            arguments(c);
            problem(c.id(), fileName + " has no function '" + name + "'");
            return null;
        }
        if (name.startsWith("_")) {
            arguments(c);
            problem(c.id(), "'" + name + "' is private to " + fileName);
            return null;
        }
        var signature = function.isPresent() ? Signature.of(function.get(), resultOf(file, function.get()))
                : constructor(record(Types.simple(fileName + "." + name)).orElseThrow());
        return bind(qualify(signature, fileName, file), fileName + "." + name + "()", c);
    }

    /** Types the arguments of a call that cannot be checked further, so their own mistakes still show. */
    private void arguments(Expr.Call c) {
        c.args().forEach(a -> expr(a, null));
        c.keywords().forEach(k -> expr(k.value(), null));
    }

    /** Matches a call's arguments to a signature as Python does, checks their types, and gives the result type. */
    private TypeRef bind(Signature signature, String display, Expr.Call c) {
        var params = signature.args();
        var bound = new LinkedHashMap<String, Expr>();
        boolean failed = false;
        int positional = c.args().size();
        if (positional > params.size()) {
            long required = params.stream().filter(p -> p.defaultValue() == null).count();
            var takes = required == params.size()
                    ? params.size() + " positional argument" + (params.size() == 1 ? "" : "s")
                    : "from " + required + " to " + params.size() + " positional arguments";
            problem(c.id(), display + " takes " + takes + " but " + positional + " "
                    + (positional == 1 ? "was" : "were") + " given");
            failed = true;
        }
        for (int i = 0; i < Math.min(positional, params.size()); i++) {
            bound.put(params.get(i).name(), c.args().get(i));
        }
        for (var keyword : c.keywords()) {
            if (params.stream().noneMatch(p -> p.name().equals(keyword.name()))) {
                if (!failed) {
                    problem(c.id(), display + " got an unexpected keyword argument '" + keyword.name() + "'");
                }
                failed = true;
            } else if (bound.containsKey(keyword.name())) {
                if (!failed) {
                    problem(c.id(), display + " got multiple values for argument '" + keyword.name() + "'");
                }
                failed = true;
            } else {
                bound.put(keyword.name(), keyword.value());
            }
        }
        var missing = params.stream().filter(p -> p.defaultValue() == null && !bound.containsKey(p.name()))
                .map(p -> "'" + p.name() + "'").toList();
        if (!missing.isEmpty() && !failed) {
            problem(c.id(), display + " missing " + missing.size() + " required positional argument"
                    + (missing.size() == 1 ? "" : "s") + ": " + names(missing));
        }
        for (var param : params) {
            var arg = bound.get(param.name());
            if (arg == null) {
                continue;
            }
            var expected = "None".equals(param.defaultValue()) ? Types.optional(param.type()) : param.type();
            var actual = expr(arg, expected);
            if (param.range() != null && constants.get(arg.id()) instanceof Long value
                    && (value < param.range().min() || value > param.range().max())) {
                var range = param.range();
                problem(arg.id(), param.name() + " of " + display + " must be "
                        + (range.max() == Long.MAX_VALUE ? "at least " + range.min()
                                : "from " + range.min() + " to " + range.max()) + ", not " + value);
            }
            if (!Types.assignable(expected, actual)) {
                var message = "argument '" + param.name() + "' of " + display + " must be " + param.type()
                        + ", not " + actual;
                if (Types.isTypedString(param.type()) && Types.STR.equals(actual)) {
                    message += "; a str becomes a " + param.type() + " through an annotation, as in x: "
                            + param.type() + " = ...";
                }
                problem(arg.id(), message);
            }
        }
        for (int i = params.size(); i < positional; i++) {
            expr(c.args().get(i), null);
        }
        for (var keyword : c.keywords()) {
            if (bound.get(keyword.name()) != keyword.value()) {
                expr(keyword.value(), null);
            }
        }
        return signature.result();
    }

    /** {@code 'a'}, {@code 'a' and 'b'}, {@code 'a', 'b', and 'c'}, as CPython lists missing arguments. */
    private static String names(List<String> names) {
        if (names.size() == 1) {
            return names.get(0);
        }
        if (names.size() == 2) {
            return names.get(0) + " and " + names.get(1);
        }
        return String.join(", ", names.subList(0, names.size() - 1)) + ", and " + names.get(names.size() - 1);
    }

    // ---- built-in functions ----

    /**
     * The parameters of built-ins that take keyword arguments, as CPython names them; {@code ""} is positional only.
     * {@code min}/{@code max} take {@code default} apart from these.
     */
    /**
     * {@code print(*values, sep=" ")}: any values, written as {@code str} gives them. Stricter than Python: each
     * print is one line of the installation's log, so {@code end}, {@code file} and {@code flush} are refused.
     */
    private TypeRef print(Expr.Call c) {
        c.args().forEach(a -> expr(a, null));
        for (var keyword : c.keywords()) {
            var type = expr(keyword.value(), null);
            switch (keyword.name()) {
                case "sep" -> {
                    var t = Types.unwrap(type);
                    if (t != null && !Types.isStr(t) && !Types.NONE.equals(t)) {
                        problem(keyword.value().id(), "sep must be None or a string, not " + Types.runtimeName(t));
                    }
                }
                case "end", "file", "flush" -> problem(c.id(),
                        "print() " + keyword.name() + "= is not supported; each print is one log line");
                default -> problem(c.id(), "'" + keyword.name() + "' is an invalid keyword argument for print()");
            }
        }
        return Types.NONE;
    }

    static final Map<String, List<String>> BUILT_IN_KEYWORDS = Map.of(
            "round", List.of("number", "ndigits"), "str", List.of("object"), "sum", List.of("", "start"));

    private TypeRef builtIn(Expr.Call original) {
        var name = original.name();
        if (name.equals("print")) {
            return print(original);
        }
        Expr defaultValue = null;
        var c = original;
        if (name.equals("min") || name.equals("max")) {
            var others = new ArrayList<Expr.Keyword>();
            for (var keyword : original.keywords()) {
                if (keyword.name().equals("default")) {
                    defaultValue = keyword.value();
                } else {
                    others.add(keyword);
                }
            }
            c = new Expr.Call(original.id(), null, name, original.args(), others);
        }
        c = positional(c);
        if (c == null) {
            arguments(original);
            return null;
        }
        var args = c.args().stream().map(a -> expr(a, null)).toList();
        var defaultType = defaultValue == null ? null : expr(defaultValue, null);
        if (args.contains(null) || defaultValue != null && defaultType == null) {
            return null;
        }
        if (defaultValue != null) {
            if (args.size() != 1) {
                problem(c.id(), "Cannot specify a default for " + name + "() with multiple positional arguments");
                return null;
            }
            var result = extreme(c, args);
            if (result == null) {
                return null;
            }
            var common = Types.NONE.equals(defaultType) ? Types.optional(result) : Types.common(result, defaultType);
            if (common == null) {
                problem(defaultValue.id(), "default of " + name + "() must be " + result + ", not " + defaultType);
            }
            return common;
        }
        return switch (name) {
            case "int", "float" -> {
                if (!count(c, 0, 1)) {
                    yield null;
                }
                if (args.size() == 1 && !Types.isNumber(Types.unwrap(args.get(0))) && !Types.isStr(Types.unwrap(args.get(0)))) {
                    problem(c.id(), name + "() argument must be a string or a number, not '"
                            + Types.runtimeName(args.get(0)) + "'");
                }
                yield name.equals("int") ? Types.INT : Types.FLOAT;
            }
            case "str" -> count(c, 0, 1) ? Types.STR : null;
            case "list" -> {
                if (!count(c, 0, 1)) {
                    yield null;
                }
                if (args.isEmpty()) {
                    yield Types.EMPTY_LIST;
                }
                var t = Types.unwrap(args.get(0));
                if (!Types.isIterable(t)) {
                    problem(c.id(), "'" + Types.runtimeName(t) + "' object is not iterable");
                    yield null;
                }
                var item = Types.iterated(t);
                yield item == null ? Types.EMPTY_LIST : Types.list(item);
            }
            case "set" -> {
                if (!count(c, 0, 1)) {
                    yield null;
                }
                if (args.isEmpty()) {
                    yield Types.EMPTY_SET;
                }
                var t = Types.unwrap(args.get(0));
                if (!Types.isIterable(t)) {
                    problem(c.id(), "'" + Types.runtimeName(t) + "' object is not iterable");
                    yield null;
                }
                var item = Types.iterated(t);
                yield item == null ? Types.EMPTY_SET : hashable(item, c.id()) ? Types.set(item) : null;
            }
            case "dict" -> {
                if (!count(c, 0, 1)) {
                    yield null;
                }
                if (args.isEmpty()) {
                    yield Types.EMPTY_DICT;
                }
                // Stricter than Python, which also builds a dict from pairs: a copy of another dict.
                var t = Types.unwrap(args.get(0));
                if (!Types.isDict(t)) {
                    problem(c.id(), "dict() argument must be a dict, not '" + Types.runtimeName(t) + "'");
                    yield null;
                }
                yield t;
            }
            case "bool" -> count(c, 0, 1) ? Types.BOOL : null;
            case "range" -> {
                if (!count(c, 1, 3)) {
                    yield null;
                }
                for (var arg : args) {
                    var t = Types.unwrap(arg);
                    if (!t.equals(Types.INT) && !t.equals(Types.BOOL)) {
                        problem(c.id(), "'" + Types.runtimeName(t) + "' object cannot be interpreted as an integer");
                        yield null;
                    }
                }
                yield Types.RANGE;
            }
            case "len" -> {
                if (!count(c, 1, 1)) {
                    yield null;
                }
                var t = Types.unwrap(args.get(0));
                if (!Types.isIterable(t)) {
                    problem(c.id(), "object of type '" + Types.runtimeName(t) + "' has no len()");
                }
                yield Types.INT;
            }
            case "abs" -> {
                if (!count(c, 1, 1)) {
                    yield null;
                }
                var t = Types.unwrap(args.get(0));
                if (!Types.isNumber(t)) {
                    problem(c.id(), "bad operand type for abs(): '" + Types.runtimeName(t) + "'");
                    yield null;
                }
                yield t.equals(Types.FLOAT) ? Types.FLOAT : Types.INT;
            }
            case "min", "max" -> extreme(c, args);
            case "sum" -> {
                if (!count(c, 1, 2)) {
                    yield null;
                }
                var start = args.size() == 2 ? Types.unwrap(args.get(1)) : Types.INT;
                if (Types.isStr(start)) {
                    problem(c.id(), "sum() can't sum strings [use ''.join(seq) instead]");
                    yield null;
                }
                if (!Types.isNumber(start)) {
                    // Stricter than Python, which also sums lists from a list start.
                    problem(c.id(), "sum() start must be a number, not '" + Types.runtimeName(start) + "'");
                    yield null;
                }
                var t = Types.unwrap(args.get(0));
                if (!Types.isIterable(t)) {
                    problem(c.id(), "'" + Types.runtimeName(t) + "' object is not iterable");
                    yield null;
                }
                var element = Types.iterated(t);
                if (element != null && !Types.isNumber(element)) {
                    problem(c.id(), "unsupported operand type(s) for +: 'int' and '" + Types.runtimeName(element) + "'");
                    yield null;
                }
                yield Types.FLOAT.equals(element) || Types.FLOAT.equals(start) ? Types.FLOAT : Types.INT;
            }
            case "round" -> {
                if (!count(c, 1, 2)) {
                    yield null;
                }
                var t = Types.unwrap(args.get(0));
                if (!Types.isNumber(t)) {
                    problem(c.id(), "type " + Types.runtimeName(t) + " doesn't define __round__ method");
                    yield null;
                }
                var digits = args.size() == 2 ? args.get(1) : Types.NONE;
                if (!Types.NONE.equals(digits) && !Types.assignable(Types.INT, Types.unwrap(digits))) {
                    problem(c.id(), "'" + Types.runtimeName(digits) + "' object cannot be interpreted as an integer");
                    yield null;
                }
                yield Types.NONE.equals(digits) || !t.equals(Types.FLOAT) ? Types.INT : Types.FLOAT;
            }
            case "any", "all" -> {
                if (!count(c, 1, 1)) {
                    yield null;
                }
                var t = Types.unwrap(args.get(0));
                if (!Types.isIterable(t)) {
                    problem(c.id(), "'" + Types.runtimeName(t) + "' object is not iterable");
                }
                yield Types.BOOL;
            }
            default -> throw new IllegalStateException(name);
        };
    }

    /**
     * The call with its keyword arguments moved to their positions, matched as CPython matches them; {@code null}
     * after a problem.
     */
    private Expr.Call positional(Expr.Call c) {
        if (c.keywords().isEmpty()) {
            return c;
        }
        var name = c.name();
        var params = BUILT_IN_KEYWORDS.get(name);
        if (params == null) {
            var key = c.keywords().stream().filter(k -> k.name().equals("key")).findFirst();
            if ((name.equals("min") || name.equals("max")) && key.isPresent()) {
                // Stricter than Python: key= takes a function, and functions are not values here.
                problem(c.id(), name + "() key= is not supported; functions are not values");
            } else if (name.equals("min") || name.equals("max")) {
                problem(c.id(), "'" + c.keywords().get(0).name() + "' is an invalid keyword argument for " + name + "()");
            } else {
                problem(c.id(), name + "() takes no keyword arguments");
            }
            return null;
        }
        int given = c.args().size() + c.keywords().size();
        if (given > params.size()) {
            problem(c.id(), name + "() takes at most " + params.size() + " argument" + (params.size() == 1 ? "" : "s")
                    + " (" + given + " given)");
            return null;
        }
        var slots = new ArrayList<Expr>(c.args());
        while (slots.size() < params.size()) {
            slots.add(null);
        }
        for (var keyword : c.keywords()) {
            int at = params.indexOf(keyword.name());
            if (at < 0 || keyword.name().isEmpty()) {
                problem(c.id(), "'" + keyword.name() + "' is an invalid keyword argument for " + name + "()");
                return null;
            }
            if (at < c.args().size()) {
                problem(c.id(), "argument for " + name + "() given by name ('" + keyword.name() + "') and position ("
                        + (at + 1) + ")");
                return null;
            }
            slots.set(at, keyword.value());
        }
        int last = slots.size();
        while (last > 0 && slots.get(last - 1) == null) {
            last--;
        }
        for (int i = 0; i < last; i++) {
            if (slots.get(i) == null) {
                problem(c.id(), params.get(i).isEmpty()
                        ? name + "() takes at least " + (i + 1) + " positional argument (" + c.args().size() + " given)"
                        : name + "() missing required argument '" + params.get(i) + "' (pos " + (i + 1) + ")");
                return null;
            }
        }
        return new Expr.Call(c.id(), null, name, slots.subList(0, last), List.of());
    }

    /** {@code min}/{@code max}: one list, or two or more numbers or strings. */
    private TypeRef extreme(Expr.Call c, List<TypeRef> args) {
        if (args.isEmpty()) {
            problem(c.id(), c.name() + " expected at least 1 argument, got 0");
            return null;
        }
        List<TypeRef> values;
        if (args.size() == 1) {
            var t = Types.unwrap(args.get(0));
            if (!Types.isIterable(t)) {
                problem(c.id(), "'" + Types.runtimeName(t) + "' object is not iterable");
                return null;
            }
            if (Types.iterated(t) == null) {
                return null;
            }
            values = List.of(Types.iterated(t));
        } else {
            values = args.stream().map(Types::unwrap).toList();
        }
        var result = values.get(0);
        for (var value : values.subList(1, values.size())) {
            if (Types.isNumber(result) && Types.isNumber(value)) {
                result = Types.number(result, value);
            } else if (Types.isStr(result) && Types.isStr(value)) {
                result = Types.common(result, value);
            } else {
                problem(c.id(), "'" + (c.name().equals("max") ? ">" : "<") + "' not supported between instances of '"
                        + Types.runtimeName(value) + "' and '" + Types.runtimeName(result) + "'");
                return null;
            }
        }
        return Types.isNumber(result) && !result.equals(Types.FLOAT) ? Types.INT : result;
    }

    /** Whether a built-in got between {@code min} and {@code max} arguments; a problem if not. */
    private boolean count(Expr.Call c, int min, int max) {
        int n = c.args().size();
        if (n >= min && n <= max) {
            return true;
        }
        if (min == max) {
            problem(c.id(), c.name() + "() takes exactly " + (min == 1 ? "one argument" : min + " arguments") + " ("
                    + n + " given)");
        } else if (n > max) {
            problem(c.id(), c.name() + " expected at most " + max + " argument" + (max == 1 ? "" : "s") + ", got "
                    + n);
        } else {
            problem(c.id(), c.name() + " expected at least " + min + " argument" + (min == 1 ? "" : "s") + ", got "
                    + n);
        }
        return false;
    }

    // ---- fields, items and slices ----

    private TypeRef attribute(Expr.Attribute a) {
        if (a.target() instanceof Expr.Name n && !isVariable(n.name())) {
            notAValue(n.id(), n.name());
            return null;
        }
        var target = expr(a.target(), null);
        if (target == null) {
            return null;
        }
        var t = Types.unwrap(target);
        if (Types.isRange(t) && RANGE_FIELDS.contains(a.name())) {
            return Types.INT;
        }
        var record = record(t);
        if (record.isPresent() && record.get().fields().containsKey(a.name())) {
            return record.get().fields().get(a.name());
        }
        problem(a.id(), "'" + Types.runtimeName(t) + "' object has no attribute '" + a.name() + "'");
        return null;
    }

    private record FieldWrite(TypeRef owner, TypeRef type) {
    }

    /** The field an assignment writes; {@code null} (with a problem if needed) if it cannot be written. */
    private FieldWrite fieldForWrite(Expr.Attribute a) {
        var type = attribute(a);
        if (type == null) {
            return null;
        }
        var owner = Types.unwrap(types.get(a.target().id()));
        if (record(owner).orElseThrow().readOnly()) {
            problem(a.id(), "cannot assign to field '" + a.name() + "'");
            return null;
        }
        types.put(a.id(), type);
        return new FieldWrite(owner, type);
    }

    /** The element type of a list an item or slice assignment writes into; {@code null} if it cannot. */
    private TypeRef itemForWrite(TypeRef type, String node) {
        if (type == null) {
            return null;
        }
        var t = Types.unwrap(type);
        if (Types.isList(t)) {
            return Types.element(t);
        }
        problem(node, "'" + Types.runtimeName(t) + "' object does not support item assignment");
        return null;
    }

    private TypeRef index(Expr.Index i) {
        var target = expr(i.target(), null);
        var t = Types.unwrap(target);
        if (Types.isDict(t)) {
            return key(t, i.index(), i.id()) ? Types.value(t) : null;
        }
        expr(i.index(), Types.INT);
        if (target == null) {
            return null;
        }
        if (!Types.isIterable(t) || Types.isSet(t)) {
            problem(i.id(), "'" + Types.runtimeName(t) + "' object is not subscriptable");
            return null;
        }
        var index = types.get(i.index().id());
        if (index != null && !Types.assignable(Types.INT, index)) {
            problem(i.id(), (Types.isStr(t) ? "string indices must be integers, not "
                    : Types.runtimeName(t) + " indices must be integers or slices, not ")
                    + quotedForStrings(t, Types.runtimeName(index)));
            return null;
        }
        return Types.iterated(t);
    }

    /** Numbers with numbers, strings with strings, and lists item by item, as Python orders them. */
    private static boolean orderable(TypeRef left, TypeRef right) {
        left = Types.unwrap(left);
        right = Types.unwrap(right);
        if (Types.isList(left) && Types.isList(right)) {
            var a = Types.element(left);
            var b = Types.element(right);
            return a == null || b == null || orderable(a, b);
        }
        return Types.isNumber(left) && Types.isNumber(right) || Types.isStr(left) && Types.isStr(right);
    }

    /** CPython quotes the type in the string message only. */
    private static String quotedForStrings(TypeRef target, String name) {
        return Types.isStr(target) ? "'" + name + "'" : name;
    }

    private void checkIndex(Expr index) {
        var type = types.get(index.id());
        if (type != null && !Types.assignable(Types.INT, type)) {
            problem(index.id(), "list indices must be integers or slices, not " + Types.runtimeName(type));
        }
    }

    private TypeRef slice(Expr.Slice s) {
        var target = expr(s.target(), null);
        boolean parts = sliceParts(s);
        if (target == null || !parts) {
            return null;
        }
        var t = Types.unwrap(target);
        if (Types.isDict(t)) {
            problem(s.id(), "a dict cannot be sliced");
            return null;
        }
        if (!Types.isIterable(t) || Types.isSet(t)) {
            problem(s.id(), "'" + Types.runtimeName(t) + "' object is not subscriptable");
            return null;
        }
        return Types.isStr(t) ? Types.STR : t;
    }

    private boolean sliceParts(Expr.Slice s) {
        for (var part : java.util.Arrays.asList(s.lower(), s.upper(), s.step())) {
            if (part == null) {
                continue;
            }
            var type = expr(part, Types.optional(Types.INT));
            if (type != null && !Types.assignable(Types.optional(Types.INT), type)) {
                problem(s.id(), "slice indices must be integers or None");
                return false;
            }
        }
        return true;
    }

    // ---- operators ----

    private TypeRef unary(Expr.Unary u) {
        var operand = expr(u.operand(), null);
        if (u.op() == UnaryOp.NOT) {
            return Types.BOOL;
        }
        if (operand == null) {
            return null;
        }
        var t = Types.unwrap(operand);
        if (!Types.isNumber(t)) {
            problem(u.id(), "bad operand type for unary -: '" + Types.runtimeName(t) + "'");
            return null;
        }
        return t.equals(Types.FLOAT) ? Types.FLOAT : Types.INT;
    }

    private TypeRef binary(BinaryOp op, TypeRef leftType, TypeRef rightType, String node) {
        var left = Types.unwrap(leftType);
        var right = Types.unwrap(rightType);
        switch (op) {
            case AND, OR -> {
                // Stricter than Python (user decision): both sides are bool, so the result is a bool too.
                if (!Types.BOOL.equals(leftType) || !Types.BOOL.equals(rightType)) {
                    problem(node, "'" + op.symbol + "' needs bool on both sides, here " + leftType + " and "
                            + rightType + "; compare first, as in n > 0");
                }
                return Types.BOOL;
            }
            case EQ, NE, IS, IS_NOT -> {
                return Types.BOOL;
            }
            case LT, LE, GT, GE -> {
                if (orderable(left, right)) {
                    return Types.BOOL;
                }
                problem(node, "'" + op.symbol + "' not supported between instances of '" + Types.runtimeName(left)
                        + "' and '" + Types.runtimeName(right) + "'");
                return null;
            }
            case IN, NOT_IN -> {
                if (Types.isList(right) || Types.isRange(right) || Types.isDict(right) || Types.isSet(right)) {
                    return Types.BOOL;
                }
                if (Types.isStr(right)) {
                    if (!Types.isStr(left)) {
                        problem(node, "'in <string>' requires string as left operand, not " + Types.runtimeName(left));
                        return null;
                    }
                    return Types.BOOL;
                }
                problem(node, "argument of type '" + Types.runtimeName(right) + "' is not iterable");
                return null;
            }
            default -> {
            }
        }
        if (Types.isNumber(left) && Types.isNumber(right)) {
            return op == BinaryOp.DIV ? Types.FLOAT : Types.number(left, right);
        }
        if (op == BinaryOp.ADD && Types.isStr(left) && Types.isStr(right)) {
            return Types.STR;
        }
        if (op == BinaryOp.ADD && Types.isList(left) && Types.isList(right)) {
            var common = Types.common(left, right);
            if (common != null) {
                return common;
            }
        }
        if (op == BinaryOp.MUL && (Types.isStr(left) || Types.isList(left)) && right.equals(Types.INT)) {
            return Types.isStr(left) ? Types.STR : left;
        }
        if (op == BinaryOp.MUL && left.equals(Types.INT) && (Types.isStr(right) || Types.isList(right))) {
            return Types.isStr(right) ? Types.STR : right;
        }
        if (op == BinaryOp.MOD && Types.isStr(left)) {
            problem(node, "% on strings is not supported; build the text with + and str()");
            return null;
        }
        problem(node, "unsupported operand type(s) for " + op.symbol + ": '" + Types.runtimeName(left) + "' and '"
                + Types.runtimeName(right) + "'");
        return null;
    }

    // ---- records ----

    /** A record type by its name as this file sees it: built-in, this file's, or {@code file.Name}. */
    private Optional<Record> record(TypeRef type) {
        if (type == null || !type.args().isEmpty()) {
            return Optional.empty();
        }
        var name = type.name();
        var builtIn = StandardLibrary.RECORDS.get(name);
        if (builtIn != null) {
            var fields = new LinkedHashMap<String, TypeRef>();
            builtIn.forEach(a -> fields.put(a.name(), a.type()));
            return Optional.of(new Record(name, fields, true));
        }
        int dot = name.indexOf('.');
        ClassDef found;
        Program file = null;
        String fileName = null;
        if (dot < 0) {
            found = classes.get(name);
        } else {
            fileName = name.substring(0, dot);
            file = workspace.files().get(fileName);
            var simple = name.substring(dot + 1);
            found = file == null ? null
                    : file.classes().stream().filter(c -> c.name().equals(simple)).findFirst().orElse(null);
        }
        if (found == null) {
            return Optional.empty();
        }
        var fields = new LinkedHashMap<String, TypeRef>();
        for (var field : found.fields()) {
            var t = file == null ? field.type() : qualify(field.type(), fileName, file);
            fields.put(field.name(), field.defaultValue() instanceof Expr.NoneLit ? Types.optional(t) : t);
        }
        return Optional.of(new Record(name, fields, false));
    }

    /** A record's constructor: its fields in order, with their defaults. */
    private Signature constructor(Record record) {
        ClassDef definition = classes.get(record.name());
        if (definition == null) {
            int dot = record.name().indexOf('.');
            var simple = record.name().substring(dot + 1);
            definition = workspace.files().get(record.name().substring(0, dot)).classes().stream()
                    .filter(c -> c.name().equals(simple)).findFirst().orElseThrow();
        }
        var args = new ArrayList<Signature.Arg>();
        for (var field : definition.fields()) {
            var type = Types.unwrap(record.fields().get(field.name()));
            args.add(new Signature.Arg(field.name(), type,
                    field.defaultValue() == null ? null : ScriptPrinter.expr(field.defaultValue())));
        }
        return new Signature(definition.name(), args, Types.simple(record.name()), "");
    }

    /** Another file's types as this file names them: its classes get the file's name in front. */
    private static TypeRef qualify(TypeRef type, String fileName, Program file) {
        if (type == null) {
            return null;
        }
        var args = type.args().stream().map(a -> qualify(a, fileName, file)).toList();
        boolean own = !type.name().contains(".") && file.classes().stream().anyMatch(c -> c.name().equals(type.name()));
        return new TypeRef(own ? fileName + "." + type.name() : type.name(), args);
    }

    private static Signature qualify(Signature signature, String fileName, Program file) {
        return new Signature(signature.name(),
                signature.args().stream().map(a -> a.withType(qualify(a.type(), fileName, file))).toList(),
                qualify(signature.result(), fileName, file), signature.summary());
    }

    // ---- helpers ----

    private static Signature method(String name, String result, Signature.Arg... args) {
        return new Signature(name, List.of(args), StandardLibrary.type(result), "");
    }

    private static Signature.Arg arg(String name, String type) {
        return new Signature.Arg(name, StandardLibrary.type(type));
    }

    /**
     * The methods of a list, dict, set, str or range, with the receiver's types in place of the type variables when
     * they are known; empty for other types.
     */
    static List<Signature> methodsOf(TypeRef t) {
        if (Types.isStr(t)) {
            return STR_METHODS;
        }
        if (Types.isRange(t)) {
            return RANGE_METHODS;
        }
        var methods = Types.isList(t) ? LIST_METHODS : Types.isDict(t) ? DICT_METHODS : Types.isSet(t) ? SET_METHODS
                : List.<Signature>of();
        if (methods.isEmpty() || t.args().isEmpty()) {
            return methods;
        }
        var variables = Types.isDict(t) ? Map.of("K", Types.key(t), "V", Types.value(t)) : Map.of("T", t.args().get(0));
        return methods.stream().map(m -> substitute(m, variables)).toList();
    }

    /** A list method with the element type in place of {@code T}. */
    static Signature substitute(Signature method, TypeRef element) {
        return substitute(method, Map.of("T", element));
    }

    /** A method with the given types in place of its type variables ({@code T}, {@code K}, {@code V}). */
    static Signature substitute(Signature method, Map<String, TypeRef> variables) {
        return new Signature(method.name(),
                method.args().stream().map(a -> a.withType(substitute(a.type(), variables))).toList(),
                substitute(method.result(), variables), method.summary());
    }

    private static TypeRef substitute(TypeRef type, Map<String, TypeRef> variables) {
        if (type.args().isEmpty() && variables.containsKey(type.name())) {
            return variables.get(type.name());
        }
        return new TypeRef(type.name(), type.args().stream().map(a -> substitute(a, variables)).toList());
    }

    private void problem(String node, String message) {
        problems.add(new Problem(node, message));
    }
}
