package space.controlnet.ae2federation.script;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the graph view writes on the cards of a function ({@link Layout#cards}): each card's title, whether it acts
 * and whether that is a long action,
 * its input rows with their labels and types, its exec outputs with their labels and, for a value, its result type;
 * and the function's variables for the strip above the canvas. A port's type is the checker's type of the value on
 * it, so a port, its wire and the chip on it share a colour.
 */
public final class GraphCards {
    private GraphCards() {
    }

    /** An input row: the port of {@code node} it shows, its label, what it holds and that value's type. */
    public record Row(String node, String port, String label, Canvas.Input input, TypeRef type) {
    }

    /** An exec output and its label; the plain {@code next} of a simple statement has none. */
    public record Output(String name, String label) {
    }

    /**
     * A card as drawn; {@code lasts} when what it calls is a long action, a library one or a player function that
     * reaches one; {@code result} is a value's type, {@code null} for a statement.
     */
    public record Card(Layout.Card card, String title, boolean acts, boolean lasts, List<Row> rows,
            List<Output> outputs, TypeRef result) {
        public Card {
            rows = List.copyOf(rows);
            outputs = List.copyOf(outputs);
        }

        public String id() {
            return card.id();
        }

        public boolean statement() {
            return card.statement();
        }
    }

    /** A variable of the function: a parameter, an assigned name or a loop variable. */
    public record Variable(String name, TypeRef type) {
    }

    /**
     * The cards of {@code canvas}, a function of {@code self}; calls into other files find their parameters in
     * {@code workspace}.
     */
    public static List<Card> of(Canvas canvas, Program self, Workspace workspace) {
        var linked = new HashSet<String>();
        canvas.links().forEach(link -> linked.add(link.from() + "/" + link.output()));
        var lasting = Effects.of(self, workspace).lastingCalls();
        var cards = new ArrayList<Card>();
        for (var card : Layout.cards(canvas)) {
            var node = card.node();
            var rows = new ArrayList<Row>();
            for (var row : card.rows()) {
                var owner = row.node().equals(node.id()) ? node : card.call();
                rows.add(new Row(row.node(), row.port(), label(owner, row.port(), canvas, self, workspace),
                        row.input(), type(row.input(), canvas.types())));
            }
            var outputs = new ArrayList<Output>();
            for (var output : card.outputs()) {
                boolean compound = node.kind() == Canvas.Kind.IF || node.kind() == Canvas.Kind.WHILE
                        || node.kind() == Canvas.Kind.FOR;
                // After a loop or an if the next statement is shown only when there is one, as the boards draw it.
                if (compound && output.equals("next") && !linked.contains(node.id() + "/next")) continue;
                outputs.add(new Output(output, outputLabel(output, compound)));
            }
            boolean acts = node.effect() || card.call() != null && card.call().effect();
            boolean lasts = lasting.contains(node.id()) || card.call() != null && lasting.contains(card.call().id());
            cards.add(new Card(card, title(card), acts, acts && lasts, rows, outputs,
                    card.statement() ? null : canvas.types().get(node.id())));
        }
        return cards;
    }

    /** The parameters first, then every other variable in the order the canvas first shows it. */
    public static List<Variable> variables(Canvas canvas) {
        var variables = new LinkedHashMap<String, TypeRef>();
        var entry = canvas.nodes().get(0);
        entry.params().forEach(param -> variables.put(param.name(), param.type()));
        for (var node : canvas.nodes()) {
            // A target is typed by its annotation or by the value assigned, as the checker types it.
            if (node.kind() == Canvas.Kind.ASSIGN && node.inputs().get("target") instanceof Canvas.Input.Chip chip
                    && variables.get(chip.name().name()) == null) {
                variables.put(chip.name().name(), node.type() != null ? node.type()
                        : type(node.inputs().get("value"), canvas.types()));
            }
            if (node.kind() == Canvas.Kind.FOR) variables.putIfAbsent(node.name(), null);
            for (var input : node.inputs().values()) {
                if (input instanceof Canvas.Input.Chip chip) {
                    var type = canvas.types().get(chip.name().id());
                    if (variables.get(chip.name().name()) == null) variables.put(chip.name().name(), type);
                }
            }
        }
        var list = new ArrayList<Variable>();
        variables.forEach((name, type) -> list.add(new Variable(name, type)));
        return list;
    }

    /**
     * {@code text} with {@code typed} written on a port of the graph {@code canvas}, a function of {@code text}: a
     * literal, a variable or any value, edited as {@link Edits#put} does and printed in place of that function, the
     * rest of the text kept. An item or fluid ID typed without quotes on a resource port is taken as its string.
     * Refused with a message when {@code typed} is no value or the edit gives no program.
     */
    public static String put(String text, Canvas canvas, String node, String port, TypeRef portType, String typed) {
        var value = typed.strip();
        if (resource(portType) && RESOURCE_ID.matcher(value).matches()) value = "\"" + value + "\"";
        var ids = new Ids("n", ScriptParser.parse(text).positions().keySet());
        var edited = Edits.put(canvas, node, port, ScriptParser.expression(value, ids));
        return Library.splice(text, edited.toFunction());
    }

    /**
     * A call of a library function dragged from the Library, as written once its draft card is wired: each required
     * argument takes a plain value of its type to be edited on the card, {@code None} where the type has none.
     */
    public static String callText(String namespace, Signature function) {
        var args = function.args().stream().filter(arg -> arg.defaultValue() == null)
                .map(arg -> filler(arg.type())).toList();
        return namespace + "." + function.name() + "(" + String.join(", ", args) + ")";
    }

    /**
     * {@code text} with {@code statement}, one statement such as a dropped card's ({@link Palette.Entry#text}), on the
     * exec output {@code output} of {@code from}; a call that acts is a statement too.
     */
    public static String insertStatement(String text, Canvas canvas, String from, String output, String statement) {
        var ids = new Ids("n", ScriptParser.parse(text).positions().keySet());
        var edited = Edits.insert(canvas, from, output, ScriptParser.statement(statement, ids));
        return Library.splice(text, edited.toFunction());
    }

    /**
     * {@code text} with the value card {@code value} wired to another port ({@link Edits#move}); the port it leaves
     * takes a plain value of its type, as a dropped library call's arguments do.
     */
    public static String move(String text, Canvas canvas, String value, String node, String port) {
        var ids = new Ids("n", ScriptParser.parse(text).positions().keySet());
        var filler = ScriptParser.expression(filler(canvas.types().get(value)), ids);
        return Library.splice(text, Edits.move(canvas, value, node, port, filler).toFunction());
    }

    /**
     * A name for a new variable of {@code canvas}, a function of {@code program}: {@code base}, or with {@code _2},
     * {@code _3}, ... when a variable, a function, a built-in, a namespace or a file of the workspace has it already.
     */
    public static String freshName(Canvas canvas, String base, Program program, Workspace workspace) {
        var taken = new HashSet<>(Canvas.locals(canvas.toFunction()));
        program.definitions().forEach(definition -> taken.add(definition.name()));
        taken.addAll(Checker.BUILT_INS);
        taken.addAll(workspace.catalog().namespaces().keySet());
        taken.addAll(workspace.files().keySet());
        if (!taken.contains(base)) {
            return base;
        }
        for (int i = 2; ; i++) {
            if (!taken.contains(base + "_" + i)) {
                return base + "_" + i;
            }
        }
    }

    /** {@code text} with an {@code elif False:} added to the if card {@code node}, after its last arm. */
    public static String addArm(String text, Canvas canvas, String node) {
        var ids = new Ids("n", ScriptParser.parse(text).positions().keySet());
        return Library.splice(text, Edits.addArm(canvas, node, ScriptParser.expression("False", ids)).toFunction());
    }

    /** {@code text} without the last {@code elif} of the if card {@code node}, and the statements under it. */
    public static String removeLastArm(String text, Canvas canvas, String node) {
        int arms = (int) canvas.node(node).inputs().keySet().stream().filter(k -> k.startsWith("condition")).count();
        if (arms < 2) throw new IllegalArgumentException("this if has no elif");
        return Library.splice(text, Edits.removeArm(canvas, node, "arm" + (arms - 1)).toFunction());
    }

    /** {@code text} with the variable {@code from} of {@code canvas} named {@code to} wherever the function uses it. */
    public static String renameVariable(String text, Canvas canvas, String from, String to) {
        return Library.splice(text, Edits.renameVariable(canvas, from, to.strip()).toFunction());
    }

    /** {@code text} with the statement card {@code statement} moved to the exec output {@code output} of {@code from}. */
    public static String relocate(String text, Canvas canvas, String statement, String from, String output) {
        return Library.splice(text, Edits.relocate(canvas, statement, from, output).toFunction());
    }

    /**
     * {@code text} without the card {@code card}: a statement with what is inside it ({@link Edits#remove}), or a
     * value, whose port takes a plain value of its type in its place.
     */
    public static String remove(String text, Canvas canvas, String card) {
        var node = canvas.nodes().stream().filter(n -> n.id().equals(card)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no card " + card));
        if (Layout.cards(canvas).stream().anyMatch(c -> c.id().equals(card) && c.statement())) {
            return Library.splice(text, Edits.remove(canvas, card).toFunction());
        }
        for (var holder : canvas.nodes()) {
            for (var input : holder.inputs().entrySet()) {
                if (input.getValue() instanceof Canvas.Input.Wire wire && wire.node().equals(node.id())) {
                    return put(text, canvas, holder.id(), input.getKey(), null,
                            filler(canvas.types().get(node.id())));
                }
            }
        }
        throw new IllegalArgumentException(card + " feeds no port");
    }

    /** A plain value of {@code type}, written where a value is needed and none is given yet. */
    static String filler(TypeRef type) {
        if (type == null) return "None";
        return switch (type.name()) {
            case "int" -> "0";
            case "float" -> "0.0";
            case "bool" -> "False";
            case "str", "Item", "Fluid", "Resource" -> "\"\"";
            case "list" -> "[]";
            default -> "None";
        };
    }

    /** What a resource port's field shows to be edited: an item's ID without its quotes, anything else as written. */
    public static String editText(Canvas.Input input, TypeRef type) {
        return switch (input) {
            case Canvas.Input.Literal literal when resource(type) && literal.value() instanceof Expr.StrLit string ->
                    string.value();
            case Canvas.Input.Literal literal -> ScriptPrinter.expr(literal.value());
            case Canvas.Input.Chip chip -> chip.name().name();
            default -> "";
        };
    }

    private static final java.util.regex.Pattern RESOURCE_ID = java.util.regex.Pattern.compile(
            "[a-z0-9_.-]+:[a-z0-9_./-]+");

    private static boolean resource(TypeRef type) {
        return type != null && (type.name().equals("Item") || type.name().equals("Fluid")
                || type.name().equals("Resource"));
    }

    /** The type of the value on a port: a literal's, a variable's or the wired node's; {@code null} for none. */
    static TypeRef type(Canvas.Input input, Map<String, TypeRef> types) {
        return switch (input) {
            case Canvas.Input.Literal literal -> types.get(literal.value().id());
            case Canvas.Input.Chip chip -> types.get(chip.name().id());
            case Canvas.Input.Wire wire -> types.get(wire.node());
            case Canvas.Input.Empty empty -> null;
        };
    }

    private static String title(Layout.Card card) {
        var node = card.node();
        return switch (node.kind()) {
            case ENTRY -> node.name();
            case ASSIGN -> {
                var target = node.inputs().get("target");
                if (!(target instanceof Canvas.Input.Chip chip)) yield "=";
                var name = chip.name().name() + (node.type() == null ? "" : ": " + node.type());
                yield card.call() == null ? name + " =" : name + " = " + callName(card.call());
            }
            case AUG_ASSIGN -> node.inputs().get("target") instanceof Canvas.Input.Chip chip
                    ? chip.name().name() + " " + node.name() + "=" : node.name() + "=";
            case CALL_STATEMENT -> callName(card.call());
            case IF -> "if";
            case WHILE -> "while";
            case FOR -> "for " + node.name();
            case RETURN -> "return";
            case BREAK -> "break";
            case CONTINUE -> "continue";
            case PASS -> "pass";
            case CALL -> callName(node);
            case BINARY, UNARY -> node.name();
            case ATTRIBUTE -> "." + node.name();
            case INDEX -> "[ ]";
            case SLICE -> "[ : ]";
            case LIST -> "list";
            case DICT -> "dict";
            case SET -> "set";
        };
    }

    /** {@code ae2.craft}, {@code iron_tools.low}, {@code low} or a method's {@code .append}. */
    private static String callName(Canvas.Node call) {
        if (call.qualifier() != null) return call.qualifier() + "." + call.name();
        return call.inputs().containsKey("receiver") ? "." + call.name() : call.name();
    }

    private static String outputLabel(String output, boolean compound) {
        if (output.equals("arm0")) return "then";
        if (output.startsWith("arm")) return "elif";
        return switch (output) {
            case "else" -> "else";
            case "body" -> "body";
            default -> compound ? "next" : "";
        };
    }

    private static String label(Canvas.Node owner, String port, Canvas canvas, Program self, Workspace workspace) {
        return switch (owner.kind()) {
            case CALL -> {
                if (port.equals("receiver")) yield "object";
                if (port.startsWith("kw:")) yield port.substring(3);
                int index = Integer.parseInt(port.substring(3));
                var args = parameters(owner, canvas, self, workspace);
                yield index < args.size() ? args.get(index) : "";
            }
            case IF -> port.equals("condition") ? "cond" : "elif";
            case WHILE -> "cond";
            case FOR -> "in";
            case BINARY -> port.equals("left") ? "a" : "b";
            case UNARY -> "a";
            case LIST, SET -> port.substring(4);
            case DICT -> port.startsWith("key") ? "k" + port.substring(3) : "v" + port.substring(5);
            default -> port;
        };
    }

    /** The parameter names of a call: a library function's, a player function's or a built-in type's method's. */
    private static List<String> parameters(Canvas.Node call, Canvas canvas, Program self, Workspace workspace) {
        var qualifier = call.qualifier();
        List<Signature> library = qualifier == null ? null : workspace.catalog().namespaces().get(qualifier);
        if (library != null) return names(library, call.name());
        if (call.inputs().get("receiver") instanceof Canvas.Input receiver) {
            var type = type(receiver, canvas.types());
            var methods = type == null ? List.<Signature>of() : Checker.methodsOf(Types.unwrap(type));
            return names(methods, call.name());
        }
        var file = qualifier == null ? self : workspace.files().get(qualifier);
        if (file == null) return List.of();
        for (var function : file.functions()) {
            if (function.name().equals(call.name())) {
                return function.params().stream().map(Param::name).toList();
            }
        }
        return List.of();
    }

    private static List<String> names(List<Signature> signatures, String name) {
        for (var signature : signatures) {
            if (signature.name().equals(name)) return signature.args().stream().map(Signature.Arg::name).toList();
        }
        return List.of();
    }
}
