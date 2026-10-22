package space.controlnet.ae2federation.script;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The graph view of one function, projected from the program: the program stays the source, and every edit changes
 * the program. Nothing is lost on the way, so {@link #toFunction()} gives the function back.
 *
 * <p>Statements are nodes on the exec chain, joined by {@link Link}s. A value that is a literal or a variable sits on
 * the port that takes it (a {@link Input.Literal} or a {@link Input.Chip}); any other value is a node of its own,
 * wired to the one port it feeds. A node's ID is its program node's ID. Elif arms are numbered: the inputs
 * {@code condition1, condition2, ...} and the exec outputs {@code arm1, arm2, ...}; {@code arm0} is the {@code if}'s
 * own body.
 */
public record Canvas(List<Node> nodes, List<Link> links, Map<String, TypeRef> types) {
    public Canvas {
        nodes = List.copyOf(nodes);
        links = List.copyOf(links);
        types = Map.copyOf(types);
    }

    public enum Kind {
        ENTRY, ASSIGN, AUG_ASSIGN, CALL_STATEMENT, IF, WHILE, FOR, RETURN, BREAK, CONTINUE, PASS,
        CALL, BINARY, UNARY, ATTRIBUTE, INDEX, SLICE, LIST, DICT, SET
    }

    /** What a port takes. */
    public sealed interface Input {
        /** A literal written on the port; a negative number counts as one. */
        record Literal(Expr value) implements Input {
        }

        /** A variable read on the port. */
        record Chip(Expr.Name name) implements Input {
        }

        /** The output of another node. */
        record Wire(String node) implements Input {
        }

        /** Nothing: an optional part left out, such as a slice bound or a bare {@code return}. */
        enum Empty implements Input {
            INSTANCE
        }
    }

    /** A parameter of the entry node, with its default ({@link Input.Empty} when it has none). */
    public record Param(String id, String name, TypeRef type, Input defaultValue) {
    }

    /**
     * One node. {@code name} is what the node shows: the function's, the call's, the field's or the loop variable's
     * name, or the operator. {@code namespace} is the namespace or file of a call such as {@code ae2.amount}; it is
     * part of the call's name, not a value, so it has no port. {@code type} is an assignment's annotation or the function's result.
     * {@code armNotes} holds the comments of each {@code elif}, then of the {@code else}.
     */
    public record Node(String id, Kind kind, String name, Expr.Name namespace, TypeRef type, Map<String, Input> inputs,
            List<Param> params, String docstring, Notes notes, List<Notes> armNotes, boolean effect) {
        public Node {
            inputs = Collections.unmodifiableMap(new LinkedHashMap<>(inputs));
            params = List.copyOf(params);
            armNotes = List.copyOf(armNotes);
        }

        /** The namespace or file a call is qualified with, as written; {@code null} for none. */
        public String qualifier() {
            return namespace == null ? null : namespace.name();
        }
    }

    /** An exec wire: control leaves {@code from} by its {@code output} and goes to {@code to}. */
    public record Link(String from, String output, String to) {
    }

    public Node node(String id) {
        return nodes.stream().filter(n -> n.id().equals(id)).findFirst().orElseThrow();
    }

    // ---- program to canvas ----

    public static Canvas of(FunctionDef function, Checker.Result result) {
        return of(function, result.types(), result.effectfulCalls());
    }

    /** The canvas of {@code function}, with the types and the actions found by checking it. */
    static Canvas of(FunctionDef function, Map<String, TypeRef> types, Set<String> effectfulCalls) {
        var builder = new Builder(locals(function), effectfulCalls);
        var params = new ArrayList<Param>();
        for (var param : function.params()) {
            params.add(new Param(param.id(), param.name(), param.type(),
                    param.defaultValue() == null ? Input.Empty.INSTANCE : builder.input(param.defaultValue())));
        }
        builder.nodes.add(0, new Node(function.id(), Kind.ENTRY, function.name(), null, function.result(), Map.of(),
                params, function.docstring(), function.notes(), List.of(), false));
        builder.link(function.id(), "next", builder.block(function.body()));
        // Links follow the nodes they leave, in the nodes' order; a node's own outputs keep their arm order.
        var order = new HashMap<String, Integer>();
        builder.nodes.forEach(n -> order.put(n.id(), order.size()));
        var links = new ArrayList<>(builder.links);
        links.sort(Comparator.comparing(l -> order.get(l.from())));
        return new Canvas(builder.nodes, links, types);
    }

    /** The calls marked as actions. */
    Set<String> effectfulCalls() {
        var calls = new HashSet<String>();
        nodes.stream().filter(Node::effect).forEach(n -> calls.add(n.id()));
        return calls;
    }

    /** The function's variables: its parameters and every name it assigns or loops over, as Python scopes them. */
    static Set<String> locals(FunctionDef function) {
        var names = new HashSet<String>();
        function.params().forEach(p -> names.add(p.name()));
        addLocals(function.body(), names);
        return names;
    }

    private static void addLocals(List<Stmt> body, Set<String> names) {
        for (var statement : body) {
            switch (statement) {
                case Stmt.Assign s when s.target() instanceof Expr.Name n -> names.add(n.name());
                case Stmt.AugAssign s when s.target() instanceof Expr.Name n -> names.add(n.name());
                case Stmt.If s -> {
                    addLocals(s.body(), names);
                    s.elifs().forEach(e -> addLocals(e.body(), names));
                    if (s.orElse() != null) {
                        addLocals(s.orElse().body(), names);
                    }
                }
                case Stmt.While s -> addLocals(s.body(), names);
                case Stmt.For s -> {
                    names.add(s.variable());
                    addLocals(s.body(), names);
                }
                default -> {
                }
            }
        }
    }

    /** Projects statements and values into nodes and links; {@link Edits} uses it for the parts it adds. */
    static final class Builder {
        final Set<String> locals;
        final Set<String> effectfulCalls;
        final List<Node> nodes = new ArrayList<>();
        final List<Link> links = new ArrayList<>();

        Builder(Set<String> locals, Set<String> effectfulCalls) {
            this.locals = locals;
            this.effectfulCalls = effectfulCalls;
        }

        void link(String from, String output, String to) {
            if (to != null) {
                links.add(new Link(from, output, to));
            }
        }

        /** Adds a body's statements joined by {@code next}; the first one's ID, {@code null} for none. */
        String block(List<Stmt> body) {
            String first = null;
            String previous = null;
            for (var statement : body) {
                var id = statement(statement);
                if (previous == null) {
                    first = id;
                } else {
                    link(previous, "next", id);
                }
                previous = id;
            }
            return first;
        }

        private Node statementNode(Stmt s, Kind kind, String name, TypeRef type, Map<String, Input> inputs,
                List<Notes> armNotes) {
            return new Node(s.id(), kind, name, null, type, inputs, List.of(), null, s.notes(), armNotes, false);
        }

        String statement(Stmt statement) {
            int at = nodes.size();
            nodes.add(null);
            var inputs = new LinkedHashMap<String, Input>();
            Node node;
            switch (statement) {
                case Stmt.Assign s -> {
                    inputs.put("target", input(s.target()));
                    inputs.put("value", input(s.value()));
                    node = statementNode(s, Kind.ASSIGN, null, s.type(), inputs, List.of());
                }
                case Stmt.AugAssign s -> {
                    inputs.put("target", input(s.target()));
                    inputs.put("value", input(s.value()));
                    node = statementNode(s, Kind.AUG_ASSIGN, s.op().symbol, null, inputs, List.of());
                }
                case Stmt.CallStmt s -> {
                    inputs.put("call", input(s.call()));
                    node = statementNode(s, Kind.CALL_STATEMENT, null, null, inputs, List.of());
                }
                case Stmt.If s -> {
                    inputs.put("condition", input(s.condition()));
                    for (int i = 0; i < s.elifs().size(); i++) {
                        inputs.put("condition" + (i + 1), input(s.elifs().get(i).condition()));
                    }
                    var armNotes = new ArrayList<Notes>();
                    s.elifs().forEach(e -> armNotes.add(e.notes()));
                    if (s.orElse() != null) {
                        armNotes.add(s.orElse().notes());
                    }
                    node = statementNode(s, Kind.IF, null, null, inputs, armNotes);
                    nodes.set(at, node);
                    link(s.id(), "arm0", block(s.body()));
                    for (int i = 0; i < s.elifs().size(); i++) {
                        link(s.id(), "arm" + (i + 1), block(s.elifs().get(i).body()));
                    }
                    if (s.orElse() != null) {
                        link(s.id(), "else", block(s.orElse().body()));
                    }
                    return s.id();
                }
                case Stmt.While s -> {
                    inputs.put("condition", input(s.condition()));
                    node = statementNode(s, Kind.WHILE, null, null, inputs, List.of());
                    nodes.set(at, node);
                    link(s.id(), "body", block(s.body()));
                    return s.id();
                }
                case Stmt.For s -> {
                    inputs.put("iterable", input(s.iterable()));
                    node = statementNode(s, Kind.FOR, s.variable(), null, inputs, List.of());
                    nodes.set(at, node);
                    link(s.id(), "body", block(s.body()));
                    return s.id();
                }
                case Stmt.Return s -> {
                    inputs.put("value", s.value() == null ? Input.Empty.INSTANCE : input(s.value()));
                    node = statementNode(s, Kind.RETURN, null, null, inputs, List.of());
                }
                case Stmt.Break s -> node = statementNode(s, Kind.BREAK, null, null, inputs, List.of());
                case Stmt.Continue s -> node = statementNode(s, Kind.CONTINUE, null, null, inputs, List.of());
                case Stmt.Pass s -> node = statementNode(s, Kind.PASS, null, null, inputs, List.of());
            }
            nodes.set(at, node);
            return statement.id();
        }

        /** What a port holding {@code e} takes; a value that is neither a literal nor a variable becomes a node. */
        Input input(Expr e) {
            if (e == null) {
                return Input.Empty.INSTANCE;
            }
            if (isLiteral(e)) {
                return new Input.Literal(e);
            }
            if (e instanceof Expr.Name n) {
                return new Input.Chip(n);
            }
            return new Input.Wire(expression(e));
        }

        private static boolean isLiteral(Expr e) {
            return switch (e) {
                case Expr.IntLit l -> true;
                case Expr.FloatLit l -> true;
                case Expr.StrLit l -> true;
                case Expr.BoolLit l -> true;
                case Expr.NoneLit l -> true;
                case Expr.Unary u -> u.op() == UnaryOp.NEG
                        && (u.operand() instanceof Expr.IntLit || u.operand() instanceof Expr.FloatLit);
                default -> false;
            };
        }

        private String expression(Expr e) {
            int at = nodes.size();
            nodes.add(null);
            var inputs = new LinkedHashMap<String, Input>();
            Kind kind;
            String name = null;
            Expr.Name namespace = null;
            boolean effect = false;
            switch (e) {
                case Expr.Call c -> {
                    kind = Kind.CALL;
                    name = c.name();
                    // A name that is no variable of the function is a namespace or a file.
                    if (c.receiver() instanceof Expr.Name n && !locals.contains(n.name())) {
                        namespace = n;
                    } else if (c.receiver() != null) {
                        inputs.put("receiver", input(c.receiver()));
                    }
                    for (int i = 0; i < c.args().size(); i++) {
                        inputs.put("arg" + i, input(c.args().get(i)));
                    }
                    c.keywords().forEach(k -> inputs.put("kw:" + k.name(), input(k.value())));
                    effect = effectfulCalls.contains(c.id());
                }
                case Expr.Binary b -> {
                    kind = Kind.BINARY;
                    name = b.op().symbol;
                    inputs.put("left", input(b.left()));
                    inputs.put("right", input(b.right()));
                }
                case Expr.Unary u -> {
                    kind = Kind.UNARY;
                    name = u.op().symbol.strip();
                    inputs.put("operand", input(u.operand()));
                }
                case Expr.Attribute a -> {
                    kind = Kind.ATTRIBUTE;
                    name = a.name();
                    inputs.put("object", input(a.target()));
                }
                case Expr.Index i -> {
                    kind = Kind.INDEX;
                    inputs.put("object", input(i.target()));
                    inputs.put("index", input(i.index()));
                }
                case Expr.Slice s -> {
                    kind = Kind.SLICE;
                    inputs.put("object", input(s.target()));
                    inputs.put("lower", input(s.lower()));
                    inputs.put("upper", input(s.upper()));
                    inputs.put("step", input(s.step()));
                }
                case Expr.ListLit l -> {
                    kind = Kind.LIST;
                    for (int i = 0; i < l.items().size(); i++) {
                        inputs.put("item" + i, input(l.items().get(i)));
                    }
                }
                case Expr.DictLit d -> {
                    kind = Kind.DICT;
                    for (int i = 0; i < d.keys().size(); i++) {
                        inputs.put("key" + i, input(d.keys().get(i)));
                        inputs.put("value" + i, input(d.values().get(i)));
                    }
                }
                case Expr.SetLit l -> {
                    kind = Kind.SET;
                    for (int i = 0; i < l.items().size(); i++) {
                        inputs.put("item" + i, input(l.items().get(i)));
                    }
                }
                default -> throw new IllegalArgumentException("not a node: " + e);
            }
            nodes.set(at, new Node(e.id(), kind, name, namespace, null, inputs, List.of(), null, Notes.NONE, List.of(),
                    effect));
            return e.id();
        }
    }

    // ---- canvas to program ----

    /** The function this canvas shows. */
    public FunctionDef toFunction() {
        var byId = new HashMap<String, Node>();
        nodes.forEach(n -> byId.put(n.id(), n));
        var targets = new HashMap<String, String>();
        links.forEach(l -> targets.put(l.from() + "/" + l.output(), l.to()));
        return new Reader(byId, targets).function(nodes.get(0));
    }

    private record Reader(Map<String, Node> nodes, Map<String, String> targets) {
        FunctionDef function(Node entry) {
            return new FunctionDef(entry.id(), entry.notes(), entry.name(),
                    entry.params().stream().map(p -> new space.controlnet.ae2federation.script.Param(p.id(),
                            p.name(), p.type(), expr(p.defaultValue()))).toList(),
                    entry.type(), entry.docstring(), block(entry.id(), "next"));
        }

        private String target(String from, String output) {
            return targets.get(from + "/" + output);
        }

        private List<Stmt> block(String from, String output) {
            var body = new ArrayList<Stmt>();
            for (var id = target(from, output); id != null; id = target(id, "next")) {
                body.add(statement(nodes.get(id)));
            }
            return body;
        }

        private Stmt statement(Node n) {
            var in = n.inputs();
            return switch (n.kind()) {
                case ASSIGN -> new Stmt.Assign(n.id(), n.notes(), expr(in.get("target")), n.type(), expr(in.get("value")));
                case AUG_ASSIGN -> new Stmt.AugAssign(n.id(), n.notes(), expr(in.get("target")),
                        BinaryOp.ofSymbol(n.name()).orElseThrow(), expr(in.get("value")));
                case CALL_STATEMENT -> new Stmt.CallStmt(n.id(), n.notes(), (Expr.Call) expr(in.get("call")));
                case IF -> {
                    var elifs = new ArrayList<Stmt.Elif>();
                    int arms = (int) in.keySet().stream().filter(k -> k.startsWith("condition")).count();
                    for (int i = 1; i < arms; i++) {
                        elifs.add(new Stmt.Elif(n.armNotes().get(i - 1), expr(in.get("condition" + i)),
                                block(n.id(), "arm" + i)));
                    }
                    // The else's notes follow the elifs'; an else may have an empty body.
                    var orElse = n.armNotes().size() < arms ? null
                            : new Stmt.Else(n.armNotes().get(arms - 1), block(n.id(), "else"));
                    yield new Stmt.If(n.id(), n.notes(), expr(in.get("condition")), block(n.id(), "arm0"), elifs, orElse);
                }
                case WHILE -> new Stmt.While(n.id(), n.notes(), expr(in.get("condition")), block(n.id(), "body"));
                case FOR -> new Stmt.For(n.id(), n.notes(), n.name(), expr(in.get("iterable")), block(n.id(), "body"));
                case RETURN -> new Stmt.Return(n.id(), n.notes(), expr(in.get("value")));
                case BREAK -> new Stmt.Break(n.id(), n.notes());
                case CONTINUE -> new Stmt.Continue(n.id(), n.notes());
                case PASS -> new Stmt.Pass(n.id(), n.notes());
                default -> throw new IllegalStateException("not a statement: " + n);
            };
        }

        private Expr expr(Input input) {
            return switch (input) {
                case Input.Literal l -> l.value();
                case Input.Chip c -> c.name();
                case Input.Wire w -> expression(nodes.get(w.node()));
                case Input.Empty e -> null;
            };
        }

        private Expr expression(Node n) {
            var in = n.inputs();
            return switch (n.kind()) {
                case CALL -> {
                    var args = new ArrayList<Expr>();
                    var keywords = new ArrayList<Expr.Keyword>();
                    in.forEach((port, input) -> {
                        if (port.startsWith("arg")) {
                            args.add(expr(input));
                        } else if (port.startsWith("kw:")) {
                            keywords.add(new Expr.Keyword(port.substring(3), expr(input)));
                        }
                    });
                    var receiver = in.containsKey("receiver") ? expr(in.get("receiver")) : n.namespace();
                    yield new Expr.Call(n.id(), receiver, n.name(), args, keywords);
                }
                case BINARY -> new Expr.Binary(n.id(), BinaryOp.ofSymbol(n.name()).orElseThrow(), expr(in.get("left")),
                        expr(in.get("right")));
                case UNARY -> new Expr.Unary(n.id(), n.name().equals("not") ? UnaryOp.NOT : UnaryOp.NEG,
                        expr(in.get("operand")));
                case ATTRIBUTE -> new Expr.Attribute(n.id(), expr(in.get("object")), n.name());
                case INDEX -> new Expr.Index(n.id(), expr(in.get("object")), expr(in.get("index")));
                case SLICE -> new Expr.Slice(n.id(), expr(in.get("object")), expr(in.get("lower")), expr(in.get("upper")),
                        expr(in.get("step")));
                case LIST -> {
                    var items = new ArrayList<Expr>();
                    in.values().forEach(input -> items.add(expr(input)));
                    yield new Expr.ListLit(n.id(), items);
                }
                case DICT -> {
                    var keys = new ArrayList<Expr>();
                    var values = new ArrayList<Expr>();
                    in.forEach((port, input) -> (port.startsWith("key") ? keys : values).add(expr(input)));
                    yield new Expr.DictLit(n.id(), keys, values);
                }
                case SET -> {
                    var items = new ArrayList<Expr>();
                    in.values().forEach(input -> items.add(expr(input)));
                    yield new Expr.SetLit(n.id(), items);
                }
                default -> throw new IllegalStateException("not a value: " + n);
            };
        }
    }
}
