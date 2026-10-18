package space.controlnet.ae2federation.function;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The graph view of a program, saved as nodes and port-named wires as a Videnoa workflow is (see
 * {@code .omo/knowledges/videnoa-function-system-reference-2026-10-10.md}); positions live apart, by node ID
 * ({@link Layout}). Unlike Videnoa's pure data flow, statements run in order: each statement node has an
 * {@code exec} input and a {@code next} output, and a function's entry node starts the chain. Expressions are pure
 * data flow into value ports. A literal is a value written on its port ({@link Node#inline()}), as a Videnoa node's
 * parameter can be, so it is never a node of its own.
 *
 * <p>Node types and ports:
 * <ul>
 * <li>{@code function}: the entry; out {@code exec}. Data: kind, name, result, params, comment.</li>
 * <li>{@code local}: in {@code exec}, {@code value}; out {@code next}. {@code return}: in {@code exec}, {@code value}
 * unless an action's. {@code do}: an effect call; in {@code exec}, {@code arg0}...; out {@code next}.</li>
 * <li>{@code if}: in {@code exec}, {@code cond0}...; out {@code then0}..., {@code else}, {@code next}.</li>
 * <li>{@code var}, {@code unary} (in {@code a}), {@code binary} (in {@code a}, {@code b}), {@code call} (in
 * {@code arg0}...): out {@code value}.</li>
 * </ul>
 * A value output feeds one port: the program is a tree, so a value used twice belongs in a local.
 */
public final class GraphView {
    public record Graph(List<Node> nodes, List<Wire> wires) {
        public Graph {
            nodes = List.copyOf(nodes);
            wires = List.copyOf(wires);
        }
    }

    public record Node(String id, String type, Map<String, Object> data, Map<String, Expr.Literal> inline) {
        public Node {
            data = Map.copyOf(data);
            inline = Map.copyOf(inline);
        }
    }

    public record Wire(String fromNode, String fromPort, String toNode, String toPort) {
    }

    /** A graph that does not read as a program, with what to change. */
    public static final class GraphError extends RuntimeException {
        GraphError(String message) {
            super(message);
        }
    }

    private final List<Node> nodes = new ArrayList<>();
    private final List<Wire> wires = new ArrayList<>();

    private GraphView() {
    }

    // ---- program to graph ----

    public static Graph of(Program program) {
        var view = new GraphView();
        for (var function : program.functions()) {
            view.nodes.add(new Node(function.id(), "function", Map.of("kind", function.kind().word(),
                    "name", function.name(), "result", function.result().word(), "params", function.params(),
                    "comment", function.comment()), Map.of()));
            view.block(function.id(), "exec", function.body());
        }
        return new Graph(view.nodes, view.wires);
    }

    private void block(String from, String port, List<Stmt> body) {
        for (var stmt : body) {
            wires.add(new Wire(from, port, stmt.id(), "exec"));
            statement(stmt);
            from = stmt.id();
            port = "next";
        }
    }

    private void statement(Stmt stmt) {
        var inline = new LinkedHashMap<String, Expr.Literal>();
        var data = new HashMap<String, Object>();
        data.put("comment", stmt.comment());
        int at = nodes.size();
        nodes.add(null);
        String type;
        switch (stmt) {
            case Stmt.Let let -> {
                type = "local";
                data.put("name", let.name());
                input(stmt.id(), "value", let.value(), inline);
            }
            case Stmt.Return ret -> {
                type = "return";
                if (ret.value() != null) input(stmt.id(), "value", ret.value(), inline);
            }
            case Stmt.Do call -> {
                type = "do";
                data.put("function", call.call().function());
                data.put("call", call.call().id());
                for (int i = 0; i < call.call().args().size(); i++) {
                    input(stmt.id(), "arg" + i, call.call().args().get(i), inline);
                }
            }
            case Stmt.If branch -> {
                type = "if";
                data.put("branches", branch.branches().stream().map(Stmt.Branch::id).toList());
                for (int i = 0; i < branch.branches().size(); i++) {
                    input(stmt.id(), "cond" + i, branch.branches().get(i).condition(), inline);
                }
                for (int i = 0; i < branch.branches().size(); i++) {
                    block(stmt.id(), "then" + i, branch.branches().get(i).body());
                }
                block(stmt.id(), "else", branch.otherwise());
            }
        }
        nodes.set(at, new Node(stmt.id(), type, data, inline));
    }

    /** {@code expr} into {@code port} of {@code node}: written on the port if a literal, else wired from its node. */
    private void input(String node, String port, Expr expr, Map<String, Expr.Literal> inline) {
        if (expr instanceof Expr.Literal literal) {
            inline.put(port, literal);
            return;
        }
        wires.add(new Wire(expr.id(), "value", node, port));
        var own = new LinkedHashMap<String, Expr.Literal>();
        int at = nodes.size();
        nodes.add(null);
        Node made = switch (expr) {
            case Expr.Var var -> new Node(var.id(), "var", Map.of("name", var.name()), own);
            case Expr.Unary unary -> {
                input(unary.id(), "a", unary.operand(), own);
                yield new Node(unary.id(), "unary", Map.of("op", unary.op().symbol()), own);
            }
            case Expr.Binary binary -> {
                input(binary.id(), "a", binary.left(), own);
                input(binary.id(), "b", binary.right(), own);
                yield new Node(binary.id(), "binary", Map.of("op", binary.op().symbol()), own);
            }
            case Expr.Call call -> {
                for (int i = 0; i < call.args().size(); i++) input(call.id(), "arg" + i, call.args().get(i), own);
                yield new Node(call.id(), "call", Map.of("function", call.function()), own);
            }
            case Expr.Literal literal -> throw new IllegalStateException();
        };
        nodes.set(at, made);
    }

    // ---- graph to program ----

    private static final class Reader {
        final Map<String, Node> byId = new HashMap<>();
        /** (node, output port) to the node its wire leads to. */
        final Map<String, String> exec = new HashMap<>();
        /** (node, input port) to the value node wired into it. */
        final Map<String, String> value = new HashMap<>();
        final Set<String> read = new HashSet<>();

        Reader(Graph graph) {
            for (var node : graph.nodes()) {
                if (byId.put(node.id(), node) != null) throw new GraphError("Two nodes share the ID " + node.id());
            }
            var uses = new HashMap<String, Integer>();
            for (var wire : graph.wires()) {
                if (!byId.containsKey(wire.fromNode()) || !byId.containsKey(wire.toNode())) {
                    throw new GraphError("A wire leads to a missing node: " + wire);
                }
                if (wire.fromPort().equals("value")) {
                    if (uses.merge(wire.fromNode(), 1, Integer::sum) > 1) {
                        throw new GraphError("The value of " + wire.fromNode()
                                + " feeds more than one port; store it in a local and use the local instead");
                    }
                    if (value.put(wire.toNode() + "/" + wire.toPort(), wire.fromNode()) != null) {
                        throw new GraphError("Two wires lead into " + wire.toNode() + "/" + wire.toPort());
                    }
                } else if (exec.put(wire.fromNode() + "/" + wire.fromPort(), wire.toNode()) != null) {
                    throw new GraphError("Two wires leave " + wire.fromNode() + "/" + wire.fromPort());
                }
            }
        }

        Node visit(String id) {
            if (!read.add(id)) throw new GraphError("Node " + id + " is reached twice: wires form a loop or a fork");
            return byId.get(id);
        }

        String string(Node node, String key) {
            return (String) node.data().get(key);
        }

        List<Stmt> chain(String from, String port) {
            var body = new ArrayList<Stmt>();
            var next = exec.get(from + "/" + port);
            while (next != null) {
                var node = visit(next);
                body.add(statement(node));
                next = exec.get(node.id() + "/next");
            }
            return body;
        }

        Stmt statement(Node node) {
            var comment = string(node, "comment");
            return switch (node.type()) {
                case "local" -> new Stmt.Let(node.id(), comment, string(node, "name"), expr(node, "value"));
                case "return" -> new Stmt.Return(node.id(), comment, has(node, "value") ? expr(node, "value") : null);
                case "do" -> new Stmt.Do(node.id(), comment,
                        new Expr.Call(string(node, "call"), string(node, "function"), args(node)));
                case "if" -> {
                    @SuppressWarnings("unchecked")
                    var ids = (List<String>) node.data().get("branches");
                    var branches = new ArrayList<Stmt.Branch>();
                    for (int i = 0; i < ids.size(); i++) {
                        branches.add(new Stmt.Branch(ids.get(i), expr(node, "cond" + i), chain(node.id(), "then" + i)));
                    }
                    yield new Stmt.If(node.id(), comment, branches, chain(node.id(), "else"));
                }
                default -> throw new GraphError("Node " + node.id() + " (" + node.type() + ") is not a statement");
            };
        }

        boolean has(Node node, String port) {
            return node.inline().containsKey(port) || value.containsKey(node.id() + "/" + port);
        }

        List<Expr> args(Node node) {
            var args = new ArrayList<Expr>();
            for (int i = 0; has(node, "arg" + i); i++) args.add(expr(node, "arg" + i));
            return args;
        }

        Expr expr(Node node, String port) {
            var literal = node.inline().get(port);
            if (literal != null) return literal;
            var from = value.get(node.id() + "/" + port);
            if (from == null) throw new GraphError("Nothing is wired into " + node.id() + "/" + port);
            var source = visit(from);
            return switch (source.type()) {
                case "var" -> new Expr.Var(source.id(), string(source, "name"));
                case "unary" -> new Expr.Unary(source.id(), op(source, true), expr(source, "a"));
                case "binary" -> new Expr.Binary(source.id(), op(source, false), expr(source, "a"), expr(source, "b"));
                case "call" -> new Expr.Call(source.id(), string(source, "function"), args(source));
                default -> throw new GraphError("Node " + source.id() + " (" + source.type() + ") gives no value");
            };
        }

        Expr.Op op(Node node, boolean unary) {
            var symbol = string(node, "op");
            for (var op : Expr.Op.values()) {
                if (op.unary() == unary && op.symbol().equals(symbol)) return op;
            }
            throw new GraphError("Unknown operator " + symbol + " on " + node.id());
        }
    }

    public static Program toProgram(Graph graph) {
        var reader = new Reader(graph);
        var functions = new ArrayList<FunctionDef>();
        for (var node : graph.nodes()) {
            if (!node.type().equals("function")) continue;
            reader.visit(node.id());
            @SuppressWarnings("unchecked")
            var params = (List<FunctionDef.Param>) node.data().get("params");
            var kind = reader.string(node, "kind").equals("query") ? FunctionDef.Kind.QUERY : FunctionDef.Kind.ACTION;
            functions.add(new FunctionDef(node.id(), reader.string(node, "comment"), kind, reader.string(node, "name"),
                    params, ValueType.of(reader.string(node, "result")), reader.chain(node.id(), "exec")));
        }
        for (var node : graph.nodes()) {
            if (!reader.read.contains(node.id())) throw new GraphError("Node " + node.id() + " is not connected");
        }
        return new Program(functions);
    }
}
