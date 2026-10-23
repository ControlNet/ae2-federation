package space.controlnet.ae2federation.script;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * What the graph editor offers to drag onto a function, in groups: the statements, the operators, the built-ins, the
 * literal values, the player's own functions, then each library namespace. An entry is Python text written into the
 * script once its card is wired: a statement on an exec output ({@link GraphCards#insertStatement}), a value on a port
 * ({@link GraphCards#put}); a call that acts, or gives nothing, goes on an exec output too.
 */
public final class Palette {
    /** The groups above the namespaces, in the order the Library shows them. */
    public static final String STATEMENTS = "statements";
    public static final String OPERATORS = "operators";
    public static final String BUILT_INS = "builtins";
    public static final String VALUES = "values";
    public static final String MINE = "mine";

    private Palette() {
    }

    /** A port on a dropped card: its label and the type of the value written there. */
    public record Port(String label, TypeRef type) {
    }

    /**
     * An entry: its row's {@code name}, its card's {@code title}, the {@code text} it writes and, for a statement,
     * the labels of its exec outputs. A statement or a loop that introduces a variable names it {@code variable} in
     * its text; {@link #text(String)} writes another name there, so a second one does not reuse the first.
     */
    public record Entry(String group, String name, String title, String text, String variable, boolean statement,
            boolean acts, boolean lasts, List<Port> ports, TypeRef result, List<String> outputs) {
        public Entry {
            ports = List.copyOf(ports);
            outputs = List.copyOf(outputs);
        }

        /** A statement's card, and an action's or a call that gives nothing, takes an exec wire. */
        public boolean execIn() {
            return statement || acts || result.equals(Types.NONE);
        }

        /** A value's card gives its value on a pin. */
        public boolean valueOut() {
            return !statement && !result.equals(Types.NONE);
        }

        /** The text with {@code name} for its variable; the text itself when it introduces none. */
        public String text(String name) {
            return variable == null ? text : text.replace(variable, name);
        }
    }

    /** A group of entries: a key above ({@link #STATEMENTS} ...) or a namespace's name. */
    public record Group(String key, boolean namespace, List<Entry> entries) {
        public Group {
            entries = List.copyOf(entries);
        }
    }

    /**
     * Every group for a function of {@code self}: the fixed ones, the player's functions of {@code self} and of the
     * other files of {@code workspace} (written {@code file.name}), and the library's namespaces, sorted. A group with
     * no entries is left out.
     */
    public static List<Group> groups(Program self, Workspace workspace) {
        return groups(mine(self, workspace), workspace.catalog().namespaces());
    }

    /** The groups when no program is open: the fixed ones and the namespaces of {@code library}. */
    public static List<Group> groups(Map<String, List<Signature>> library) {
        return groups(List.of(), library);
    }

    private static List<Group> groups(List<Entry> mine, Map<String, List<Signature>> library) {
        var groups = new ArrayList<Group>();
        groups.add(new Group(STATEMENTS, false, STATEMENT_ENTRIES));
        groups.add(new Group(OPERATORS, false, OPERATOR_ENTRIES));
        groups.add(new Group(BUILT_INS, false, BUILT_IN_ENTRIES));
        groups.add(new Group(VALUES, false, VALUE_ENTRIES));
        if (!mine.isEmpty()) groups.add(new Group(MINE, false, mine));
        library.keySet().stream().sorted().forEach(namespace -> groups.add(new Group(namespace, true,
                library.get(namespace).stream().map(function -> call(namespace, function)).toList())));
        return groups;
    }

    /** A library function's entry: a call with a plain value for each required argument ({@link GraphCards#callText}). */
    public static Entry call(String namespace, Signature function) {
        var ports = function.args().stream().filter(arg -> arg.defaultValue() == null)
                .map(arg -> new Port(arg.name(), arg.type())).toList();
        return new Entry(namespace, function.name(), namespace + "." + function.name(),
                GraphCards.callText(namespace, function), null, false, function.effect(),
                StandardLibrary.lasts(function), ports, function.result(), List.of());
    }

    private static List<Entry> mine(Program self, Workspace workspace) {
        var entries = new ArrayList<Entry>();
        var effects = Effects.of(self, workspace);
        for (var function : self.functions()) {
            entries.add(player(null, function, effects.functions().contains(function.name()),
                    effects.lastingFunctions().contains(function.name())));
        }
        workspace.files().keySet().stream().sorted().forEach(file -> {
            var program = workspace.files().get(file);
            var theirs = Effects.of(program, workspace);
            for (var function : program.functions()) {
                // Another file's helpers stay its own, as the checker keeps them.
                if (function.name().startsWith("_")) continue;
                entries.add(player(file, function, theirs.functions().contains(function.name()),
                        theirs.lastingFunctions().contains(function.name())));
            }
        });
        return entries;
    }

    private static Entry player(String file, FunctionDef function, boolean acts, boolean lasts) {
        var name = file == null ? function.name() : file + "." + function.name();
        var required = function.params().stream().filter(param -> param.defaultValue() == null).toList();
        var args = required.stream().map(param -> GraphCards.filler(param.type())).toList();
        var result = function.result() == null ? Types.NONE : function.result();
        return new Entry(MINE, name, name, name + "(" + String.join(", ", args) + ")", null, false, acts, lasts,
                required.stream().map(param -> new Port(param.name(), param.type())).toList(), result, List.of());
    }

    private static Entry statement(String name, String text, String variable, List<Port> ports, String... outputs) {
        return new Entry(STATEMENTS, name, name, text, variable, true, false, false, ports, Types.NONE,
                List.of(outputs));
    }

    private static final List<Entry> STATEMENT_ENTRIES = List.of(
            statement("value =", "value = None", "value", List.of(new Port("value", null)), ""),
            statement("if", "if False:\n    pass", null, List.of(new Port("cond", Types.BOOL)), "then", "else"),
            statement("for", "for item in []:\n    pass", "item", List.of(new Port("in", Types.EMPTY_LIST)),
                    "body", "next"),
            statement("while", "while False:\n    pass", null, List.of(new Port("cond", Types.BOOL)), "body",
                    "next"),
            statement("return", "return None", null, List.of(new Port("value", null))),
            statement("break", "break", null, List.of()),
            statement("continue", "continue", null, List.of()));

    private static Entry value(String group, String name, String text, TypeRef result, Port... ports) {
        return new Entry(group, name, name, text, null, false, false, false, List.of(ports), result, List.of());
    }

    private static Entry binary(String symbol, TypeRef operand, String filler, TypeRef result) {
        return value(OPERATORS, symbol, filler + " " + symbol + " " + filler, result, new Port("a", operand),
                new Port("b", operand));
    }

    private static final List<Entry> OPERATOR_ENTRIES = List.of(
            binary("+", Types.INT, "0", Types.INT),
            binary("-", Types.INT, "0", Types.INT),
            binary("*", Types.INT, "0", Types.INT),
            binary("/", Types.INT, "1", Types.FLOAT),
            binary("//", Types.INT, "1", Types.INT),
            binary("%", Types.INT, "1", Types.INT),
            binary("==", Types.INT, "0", Types.BOOL),
            binary("!=", Types.INT, "0", Types.BOOL),
            binary("<", Types.INT, "0", Types.BOOL),
            binary("<=", Types.INT, "0", Types.BOOL),
            binary(">", Types.INT, "0", Types.BOOL),
            binary(">=", Types.INT, "0", Types.BOOL),
            binary("and", Types.BOOL, "False", Types.BOOL),
            binary("or", Types.BOOL, "False", Types.BOOL),
            value(OPERATORS, "not", "not False", Types.BOOL, new Port("a", Types.BOOL)),
            value(OPERATORS, "in", "0 in []", Types.BOOL, new Port("a", Types.INT), new Port("b", Types.EMPTY_LIST)),
            value(OPERATORS, "not in", "0 not in []", Types.BOOL, new Port("a", Types.INT),
                    new Port("b", Types.EMPTY_LIST)));

    private static Entry builtIn(String name, String args, TypeRef result, Port... ports) {
        return value(BUILT_INS, name, name + "(" + args + ")", result, ports);
    }

    private static final List<Entry> BUILT_IN_ENTRIES = List.of(
            builtIn("int", "0", Types.INT, new Port("x", Types.INT)),
            builtIn("float", "0", Types.FLOAT, new Port("x", Types.INT)),
            builtIn("str", "0", Types.STR, new Port("x", Types.INT)),
            builtIn("bool", "0", Types.BOOL, new Port("x", Types.INT)),
            builtIn("list", "", Types.EMPTY_LIST),
            builtIn("dict", "", Types.EMPTY_DICT),
            builtIn("set", "", Types.EMPTY_SET),
            builtIn("range", "0", Types.RANGE, new Port("stop", Types.INT)),
            builtIn("len", "[]", Types.INT, new Port("x", Types.EMPTY_LIST)),
            builtIn("abs", "0", Types.INT, new Port("x", Types.INT)),
            builtIn("min", "0, 0", Types.INT, new Port("a", Types.INT), new Port("b", Types.INT)),
            builtIn("max", "0, 0", Types.INT, new Port("a", Types.INT), new Port("b", Types.INT)),
            builtIn("sum", "[]", Types.INT, new Port("x", Types.EMPTY_LIST)),
            builtIn("round", "0.0", Types.INT, new Port("x", Types.FLOAT)),
            builtIn("any", "[]", Types.BOOL, new Port("x", Types.EMPTY_LIST)),
            builtIn("all", "[]", Types.BOOL, new Port("x", Types.EMPTY_LIST)),
            builtIn("print", "\"\"", Types.NONE, new Port("x", Types.STR)));

    private static final List<Entry> VALUE_ENTRIES = List.of(
            value(VALUES, "[ ]", "[0]", Types.list(Types.INT), new Port("0", Types.INT)),
            value(VALUES, "{ : }", "{\"\": 0}", Types.dict(Types.STR, Types.INT), new Port("k0", Types.STR),
                    new Port("v0", Types.INT)),
            value(VALUES, "{ }", "{0}", Types.set(Types.INT), new Port("0", Types.INT)));
}
