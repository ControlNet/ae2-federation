package space.controlnet.ae2federation.function;

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

    /** Every problem found, and the type of every expression that has one, by node ID. */
    public record Result(List<Problem> problems, Map<String, TypeRef> types) {
    }

    static final List<String> BUILT_INS = List.of("int", "float", "str", "bool", "range", "len", "abs", "min", "max",
            "sum", "round", "any", "all");

    /** List methods over the element type {@code T}; parameter names are CPython's. */
    static final List<Signature> LIST_METHODS = List.of(
            method("append", "None", arg("object", "T")),
            method("extend", "None", arg("iterable", "list[T]")),
            method("insert", "None", arg("index", "int"), arg("object", "T")),
            method("pop", "T", new Signature.Arg("index", Types.INT, "-1")),
            method("remove", "None", arg("value", "T")),
            method("index", "int", arg("value", "T")),
            method("count", "int", arg("value", "T")),
            method("reverse", "None"),
            method("sort", "None", new Signature.Arg("reverse", Types.BOOL, "False")),
            method("clear", "None"),
            method("copy", "list[T]"));

    static final List<Signature> STR_METHODS = List.of(
            method("startswith", "bool", arg("prefix", "str")),
            method("endswith", "bool", arg("suffix", "str")),
            method("lower", "str"),
            method("upper", "str"),
            method("strip", "str"),
            method("split", "list[str]", new Signature.Arg("sep", Types.STR, "None")),
            method("join", "str", arg("iterable", "list[str]")),
            method("replace", "str", arg("old", "str"), arg("new", "str")),
            method("find", "int", arg("sub", "str")),
            method("count", "int", arg("sub", "str")));

    private final Program program;
    private final Workspace workspace;
    private final List<Problem> problems = new ArrayList<>();
    private final Map<String, TypeRef> types = new HashMap<>();
    private final Map<String, FunctionDef> functions = new LinkedHashMap<>();
    private final Map<String, ClassDef> classes = new LinkedHashMap<>();

    private FunctionDef current;
    private TypeRef currentResult;
    private final Map<String, Local> locals = new HashMap<>();
    private final Set<String> assignedInFunction = new HashSet<>();

    /** A local variable: its type, and whether it has only held {@code None} so far. */
    private static final class Local {
        TypeRef type;
        boolean onlyNone;

        Local(TypeRef type, boolean onlyNone) {
            this.type = type;
            this.onlyNone = onlyNone;
        }
    }

    /** A record type's fields, with types as seen from this file. */
    private record Record(String name, Map<String, TypeRef> fields, boolean readOnly) {
    }

    private Checker(Program program, Workspace workspace) {
        this.program = program;
        this.workspace = workspace;
    }

    public static Result check(Program program, Workspace workspace) {
        var checker = new Checker(program, workspace);
        checker.run();
        return new Result(List.copyOf(checker.problems), Map.copyOf(checker.types));
    }

    // ---- definitions ----

    private void run() {
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
        for (var record : classes.values()) {
            checkClass(record);
        }
        for (var definition : program.definitions()) {
            if (definition instanceof FunctionDef f && functions.get(f.name()) == f) {
                checkFunction(f);
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
                checkDefault(field.name(), type, field.defaultValue());
            }
        }
    }

    private void checkDefault(String name, TypeRef type, Expr value) {
        var actual = expr(value, type);
        if (!Types.assignable(type, actual) && !Types.NONE.equals(actual)) {
            problem(value.id(), "default of '" + name + "' must be " + type + ", not " + actual);
        }
    }

    private void checkFunction(FunctionDef function) {
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
            locals.put(param.name(), new Local(type, false));
        }
        currentResult = annotation(function.result(), function.id());
        collectAssigned(function.body());
        statements(function.body());
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
        if (Types.isList(t)) {
            return Types.element(t);
        }
        if (Types.isStr(t)) {
            return Types.STR;
        }
        problem(node, "'" + Types.runtimeName(t) + "' object is not iterable");
        return null;
    }

    private void assign(Stmt.Assign s) {
        switch (s.target()) {
            case Expr.Name n -> {
                var annotated = s.type() == null ? null : annotation(s.type(), s.id());
                var local = locals.get(n.name());
                var expected = annotated != null ? annotated : local == null || local.onlyNone ? null : local.type;
                var value = expr(s.value(), expected);
                if (s.type() != null && annotated == null) {
                    declare(n.name(), null, s.id());
                    return;
                }
                if (annotated != null) {
                    if (local != null && !local.onlyNone && local.type != null && !local.type.equals(annotated)) {
                        problem(s.id(), n.name() + " is " + local.type + "; it cannot be annotated " + annotated);
                        return;
                    }
                    // An annotation turns a str into a typed string; the value is checked when the program runs.
                    boolean converts = Types.isTypedString(Types.unwrap(annotated)) && Types.isStr(value);
                    if (!converts && !Types.assignable(annotated, value)) {
                        problem(s.id(), n.name() + " is " + annotated + "; it cannot hold " + value);
                    }
                    locals.put(n.name(), new Local(annotated, false));
                    return;
                }
                if (local == null && Types.EMPTY_LIST.equals(value)) {
                    problem(s.id(), "annotate the empty list, as in " + n.name() + ": list[int] = []");
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
                var element = itemForWrite(i.target(), i.id());
                expr(i.index(), Types.INT);
                checkIndex(i.index());
                var value = expr(s.value(), element);
                if (element != null && !Types.assignable(element, value)) {
                    problem(s.id(), "items of " + types.get(i.target().id()) + " are " + element
                            + "; this is " + value);
                }
            }
            case Expr.Slice sl -> {
                var element = itemForWrite(sl.target(), sl.id());
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
     * Gives a local its first type, or checks a later value against it. A local that has only held {@code None}
     * takes {@code Optional} of the type assigned next, so {@code best = None} then {@code best = x} works.
     */
    private void declare(String name, TypeRef value, String node) {
        var local = locals.get(name);
        if (local == null) {
            boolean none = Types.NONE.equals(value);
            locals.put(name, new Local(value, none));
            return;
        }
        if (local.onlyNone) {
            if (value != null && !Types.NONE.equals(value)) {
                local.type = Types.optional(value);
                local.onlyNone = false;
            }
            return;
        }
        if (local.type != null && !Types.assignable(local.type, value)) {
            problem(node, name + " is " + local.type + "; it cannot hold " + value);
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
        }
        return type;
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

    private TypeRef name(Expr.Name n) {
        var local = locals.get(n.name());
        if (local != null) {
            return local.onlyNone ? Types.NONE : local.type;
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
                return bind(Signature.of(functions.get(name)), name + "()", c);
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
        } else if (Types.isStr(t)) {
            methods = STR_METHODS;
            owner = "str";
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
        var signature = function.isPresent() ? Signature.of(function.get())
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

    private TypeRef builtIn(Expr.Call c) {
        var name = c.name();
        if (!c.keywords().isEmpty()) {
            arguments(c);
            problem(c.id(), name + "() takes no keyword arguments");
            return null;
        }
        var args = c.args().stream().map(a -> expr(a, null)).toList();
        if (args.contains(null)) {
            return null;
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
                yield Types.list(Types.INT);
            }
            case "len" -> {
                if (!count(c, 1, 1)) {
                    yield null;
                }
                var t = Types.unwrap(args.get(0));
                if (!Types.isList(t) && !Types.isStr(t)) {
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
                if (!count(c, 1, 1)) {
                    yield null;
                }
                var t = Types.unwrap(args.get(0));
                if (!Types.isList(t)) {
                    problem(c.id(), "'" + Types.runtimeName(t) + "' object is not iterable");
                    yield null;
                }
                var element = Types.element(t);
                if (element != null && !Types.isNumber(element)) {
                    problem(c.id(), "unsupported operand type(s) for +: 'int' and '" + Types.runtimeName(element) + "'");
                    yield null;
                }
                yield Types.FLOAT.equals(element) ? Types.FLOAT : Types.INT;
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
                yield args.size() == 1 || !t.equals(Types.FLOAT) ? Types.INT : Types.FLOAT;
            }
            case "any", "all" -> {
                if (!count(c, 1, 1)) {
                    yield null;
                }
                var t = Types.unwrap(args.get(0));
                if (!Types.isList(t) && !Types.isStr(t)) {
                    problem(c.id(), "'" + Types.runtimeName(t) + "' object is not iterable");
                }
                yield Types.BOOL;
            }
            default -> throw new IllegalStateException(name);
        };
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
            if (!Types.isList(t) && !Types.isStr(t)) {
                problem(c.id(), "'" + Types.runtimeName(t) + "' object is not iterable");
                return null;
            }
            if (Types.isStr(t)) {
                return Types.STR;
            }
            if (Types.element(t) == null) {
                return null;
            }
            values = List.of(Types.element(t));
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
    private TypeRef itemForWrite(Expr target, String node) {
        var type = expr(target, null);
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
        expr(i.index(), Types.INT);
        if (target == null) {
            return null;
        }
        var t = Types.unwrap(target);
        if (!Types.isList(t) && !Types.isStr(t)) {
            problem(i.id(), "'" + Types.runtimeName(t) + "' object is not subscriptable");
            return null;
        }
        var index = types.get(i.index().id());
        if (index != null && !Types.assignable(Types.INT, index)) {
            problem(i.id(), (Types.isList(t) ? "list indices must be integers or slices, not "
                    : "string indices must be integers, not ") + quotedForStrings(t, Types.runtimeName(index)));
            return null;
        }
        return Types.isList(t) ? Types.element(t) : Types.STR;
    }

    /** CPython quotes the type in the string message only. */
    private static String quotedForStrings(TypeRef target, String name) {
        return Types.isList(target) ? name : "'" + name + "'";
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
        if (!Types.isList(t) && !Types.isStr(t)) {
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
                var common = Types.common(leftType, rightType);
                if (common == null) {
                    problem(node, "'" + op.symbol + "' needs both sides of one type, here " + leftType + " and "
                            + rightType);
                }
                return common;
            }
            case EQ, NE, IS, IS_NOT -> {
                return Types.BOOL;
            }
            case LT, LE, GT, GE -> {
                if (Types.isNumber(left) && Types.isNumber(right) || Types.isStr(left) && Types.isStr(right)) {
                    return Types.BOOL;
                }
                problem(node, "'" + op.symbol + "' not supported between instances of '" + Types.runtimeName(left)
                        + "' and '" + Types.runtimeName(right) + "'");
                return null;
            }
            case IN, NOT_IN -> {
                if (Types.isList(right)) {
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
        var args = type.args().stream().map(a -> qualify(a, fileName, file)).toList();
        boolean own = !type.name().contains(".") && file.classes().stream().anyMatch(c -> c.name().equals(type.name()));
        return new TypeRef(own ? fileName + "." + type.name() : type.name(), args);
    }

    private static Signature qualify(Signature signature, String fileName, Program file) {
        return new Signature(signature.name(),
                signature.args().stream().map(a -> new Signature.Arg(a.name(), qualify(a.type(), fileName, file),
                        a.defaultValue())).toList(),
                qualify(signature.result(), fileName, file), signature.summary());
    }

    // ---- helpers ----

    private static Signature method(String name, String result, Signature.Arg... args) {
        return new Signature(name, List.of(args), StandardLibrary.type(result), "");
    }

    private static Signature.Arg arg(String name, String type) {
        return new Signature.Arg(name, StandardLibrary.type(type));
    }

    /** A list method with the element type in place of {@code T}. */
    private static Signature substitute(Signature method, TypeRef element) {
        return new Signature(method.name(),
                method.args().stream().map(a -> new Signature.Arg(a.name(), substitute(a.type(), element),
                        a.defaultValue())).toList(),
                substitute(method.result(), element), method.summary());
    }

    private static TypeRef substitute(TypeRef type, TypeRef element) {
        if (type.name().equals("T")) {
            return element;
        }
        return new TypeRef(type.name(), type.args().stream().map(a -> substitute(a, element)).toList());
    }

    private void problem(String node, String message) {
        problems.add(new Problem(node, message));
    }
}
