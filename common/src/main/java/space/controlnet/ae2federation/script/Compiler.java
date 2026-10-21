package space.controlnet.ae2federation.script;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Compiles a checked program file to one {@link Graph} per function. Calls that suspend the run (player functions
 * and library actions) are lifted out of expressions into their own nodes, in Python's order of evaluation: whatever
 * Python evaluates before such a call is computed into a temporary first, so the call cannot change it.
 */
public final class Compiler {
    private final String file;
    private final Workspace workspace;
    private final FunctionDef function;
    private final Set<String> locals = new HashSet<>();
    private final List<Node> nodes = new ArrayList<>();
    /** For each loop being compiled: where {@code continue} goes, and the jumps of its {@code break}s to patch. */
    private final Deque<Loop> loops = new ArrayDeque<>();
    private int temporaries;

    private record Loop(int head, List<Integer> breaks) {
    }

    private Compiler(String file, Workspace workspace, FunctionDef function) {
        this.file = file;
        this.workspace = workspace;
        this.function = function;
    }

    /** The graphs of a file's functions by name; {@code file} names the program in the workspace. */
    public static Map<String, Graph> compile(String file, Workspace workspace) {
        var graphs = new LinkedHashMap<String, Graph>();
        for (var function : workspace.files().get(file).functions()) {
            graphs.put(function.name(), new Compiler(file, workspace, function).graph());
        }
        return graphs;
    }

    private Graph graph() {
        function.params().forEach(p -> locals.add(p.name()));
        collectLocals(function.body());
        block(function.body());
        nodes.add(new Node.Return(function.id(), null));
        return new Graph(file, function, nodes);
    }

    /** Names assigned anywhere in the body are local to it, as in Python. */
    private void collectLocals(List<Stmt> body) {
        for (var statement : body) {
            switch (statement) {
                case Stmt.Assign s when s.target() instanceof Expr.Name n -> locals.add(n.name());
                case Stmt.AugAssign s when s.target() instanceof Expr.Name n -> locals.add(n.name());
                case Stmt.If s -> {
                    collectLocals(s.body());
                    s.elifs().forEach(e -> collectLocals(e.body()));
                    if (s.orElse() != null) {
                        collectLocals(s.orElse().body());
                    }
                }
                case Stmt.While s -> collectLocals(s.body());
                case Stmt.For s -> {
                    locals.add(s.variable());
                    collectLocals(s.body());
                }
                default -> {
                }
            }
        }
    }

    // ---- statements ----

    private void block(List<Stmt> body) {
        body.forEach(this::statement);
    }

    private void statement(Stmt statement) {
        switch (statement) {
            case Stmt.Assign s -> assign(s);
            case Stmt.AugAssign s -> augment(s);
            case Stmt.CallStmt s -> {
                if (suspension(s.call())) {
                    call(s.call(), null);
                } else {
                    nodes.add(new Node.Evaluate(s.id(), lift(s.call())));
                }
            }
            case Stmt.If s -> conditional(s);
            case Stmt.While s -> {
                int head = nodes.size();
                var condition = lift(s.condition());
                int branch = placeholder();
                loops.push(new Loop(head, new ArrayList<>()));
                block(s.body());
                nodes.add(new Node.Jump(s.id(), head));
                var loop = loops.pop();
                nodes.set(branch, new Node.Branch(s.id(), condition, nodes.size(), true));
                loop.breaks().forEach(b -> nodes.set(b, new Node.Jump(nodes.get(b).source(), nodes.size())));
            }
            case Stmt.For s -> {
                var cursor = temporary();
                nodes.add(new Node.Iterate(s.id(), lift(s.iterable()), cursor));
                int head = placeholder();
                loops.push(new Loop(head, new ArrayList<>()));
                block(s.body());
                nodes.add(new Node.Jump(s.id(), head));
                var loop = loops.pop();
                nodes.set(head, new Node.Next(s.id(), cursor, s.variable(), nodes.size()));
                loop.breaks().forEach(b -> nodes.set(b, new Node.Jump(nodes.get(b).source(), nodes.size())));
            }
            case Stmt.Return s -> nodes.add(new Node.Return(s.id(), s.value() == null ? null : lift(s.value())));
            case Stmt.Break s -> {
                loops.peek().breaks().add(nodes.size());
                nodes.add(new Node.Jump(s.id(), -1));
            }
            case Stmt.Continue s -> nodes.add(new Node.Jump(s.id(), loops.peek().head()));
            case Stmt.Pass s -> {
            }
        }
    }

    /** A node to fill in once its targets are known. */
    private int placeholder() {
        nodes.add(null);
        return nodes.size() - 1;
    }

    /** An {@code if} or {@code elif} test with its body; {@code source} is the {@code if}, or the elif's condition. */
    private record Arm(String source, Expr condition, List<Stmt> body) {
    }

    private void conditional(Stmt.If s) {
        var arms = new ArrayList<Arm>();
        arms.add(new Arm(s.id(), s.condition(), s.body()));
        s.elifs().forEach(e -> arms.add(new Arm(e.condition().id(), e.condition(), e.body())));
        var ends = new ArrayList<Integer>();
        for (int i = 0; i < arms.size(); i++) {
            var arm = arms.get(i);
            var condition = lift(arm.condition());
            int branch = placeholder();
            block(arm.body());
            boolean last = i == arms.size() - 1 && s.orElse() == null;
            if (!last) {
                ends.add(placeholder());
            }
            nodes.set(branch, new Node.Branch(arm.source(), condition, nodes.size(), true));
        }
        if (s.orElse() != null) {
            block(s.orElse().body());
        }
        ends.forEach(e -> nodes.set(e, new Node.Jump(s.id(), nodes.size())));
    }

    private void assign(Stmt.Assign s) {
        var target = s.target();
        var parts = parts(target);
        if (parts.stream().noneMatch(this::suspends)) {
            nodes.add(new Node.Assign(s.id(), target, lift(s.value())));
            return;
        }
        // Python computes the value before the target's parts.
        var value = materialize(lift(s.value()));
        nodes.add(new Node.Assign(s.id(), rebuild(target, liftAll(parts)), value));
    }

    private void augment(Stmt.AugAssign s) {
        var target = s.target();
        var parts = parts(target);
        if (!suspends(s.value()) && parts.stream().noneMatch(this::suspends)) {
            nodes.add(new Node.Augment(s.id(), target, s.op(), null, s.value()));
            return;
        }
        // Python reads the target's parts and its value, then computes the right side.
        var lifted = new ArrayList<Expr>();
        for (var part : liftAll(parts)) {
            lifted.add(part == null ? null : materialize(part));
        }
        var fixed = rebuild(target, lifted);
        var current = materialize(fixed);
        nodes.add(new Node.Augment(s.id(), fixed, s.op(), current, lift(s.value())));
    }

    /** The parts of an assignment target Python evaluates: none for a name. */
    private static List<Expr> parts(Expr target) {
        return switch (target) {
            case Expr.Attribute a -> List.of(a.target());
            case Expr.Index i -> List.of(i.target(), i.index());
            case Expr.Slice s -> java.util.Arrays.asList(s.target(), s.lower(), s.upper(), s.step());
            default -> List.of();
        };
    }

    private static Expr rebuild(Expr target, List<Expr> parts) {
        return switch (target) {
            case Expr.Attribute a -> new Expr.Attribute(a.id(), parts.get(0), a.name());
            case Expr.Index i -> new Expr.Index(i.id(), parts.get(0), parts.get(1));
            case Expr.Slice s -> new Expr.Slice(s.id(), parts.get(0), parts.get(1), parts.get(2), parts.get(3));
            default -> target;
        };
    }

    // ---- expressions ----

    /** Whether the call suspends the run: a player function, or a library action. */
    private boolean suspension(Expr.Call c) {
        if (c.receiver() == null) {
            return !locals.contains(c.name()) && !Checker.BUILT_INS.contains(c.name())
                    && function(file, c.name()) != null;
        }
        if (c.receiver() instanceof Expr.Name r && !locals.contains(r.name())) {
            var namespace = workspace.catalog().namespaces().get(r.name());
            if (namespace != null) {
                return namespace.stream().anyMatch(sig -> sig.name().equals(c.name()) && sig.effect());
            }
            return function(r.name(), c.name()) != null;
        }
        return false;
    }

    private FunctionDef function(String fileName, String name) {
        var program = workspace.files().get(fileName);
        return program == null ? null
                : program.functions().stream().filter(f -> f.name().equals(name)).findFirst().orElse(null);
    }

    /** Whether evaluating {@code e} would reach a suspending call. */
    private boolean suspends(Expr e) {
        if (e == null) {
            return false;
        }
        return switch (e) {
            case Expr.Call c -> suspension(c) || children(c).stream().anyMatch(this::suspends);
            case Expr.ListLit l -> l.items().stream().anyMatch(this::suspends);
            case Expr.Attribute a -> suspends(a.target());
            case Expr.Index i -> suspends(i.target()) || suspends(i.index());
            case Expr.Slice s -> suspends(s.target()) || suspends(s.lower()) || suspends(s.upper()) || suspends(s.step());
            case Expr.Unary u -> suspends(u.operand());
            case Expr.Binary b -> suspends(b.left()) || suspends(b.right());
            default -> false;
        };
    }

    /** A call's evaluated parts in order: a method's receiver, then the arguments, then the keyword values. */
    private List<Expr> children(Expr.Call c) {
        var out = new ArrayList<Expr>();
        if (c.receiver() != null && !isQualifier(c.receiver())) {
            out.add(c.receiver());
        }
        out.addAll(c.args());
        c.keywords().forEach(k -> out.add(k.value()));
        return out;
    }

    /** {@code ae2} in {@code ae2.amount(...)} or a file's name: not a value. */
    private boolean isQualifier(Expr receiver) {
        return receiver instanceof Expr.Name n && !locals.contains(n.name())
                && (workspace.catalog().namespaces().containsKey(n.name()) || workspace.files().containsKey(n.name()));
    }

    /** {@code e} without suspending calls: they become nodes, and {@code e} reads their results. */
    private Expr lift(Expr e) {
        if (!suspends(e)) {
            return e;
        }
        return switch (e) {
            case Expr.Call c -> {
                if (suspension(c)) {
                    var result = temporary();
                    call(c, result);
                    yield new Expr.Name(c.id(), result);
                }
                yield withChildren(c, liftAll(children(c)));
            }
            case Expr.Binary b when (b.op() == BinaryOp.AND || b.op() == BinaryOp.OR) && suspends(b.right()) -> {
                // The right side runs only when the left one does not decide: a branch around it.
                var result = temporary();
                var name = new Expr.Name(b.id(), result);
                nodes.add(new Node.Assign(b.id(), name, lift(b.left())));
                int branch = placeholder();
                nodes.add(new Node.Assign(b.id(), name, lift(b.right())));
                var condition = b.op() == BinaryOp.AND ? name : new Expr.Unary(b.id(), UnaryOp.NOT, name);
                nodes.set(branch, new Node.Branch(b.id(), condition, nodes.size(), false));
                yield name;
            }
            case Expr.Binary b -> {
                var parts = liftAll(List.of(b.left(), b.right()));
                yield new Expr.Binary(b.id(), b.op(), parts.get(0), parts.get(1));
            }
            case Expr.Unary u -> new Expr.Unary(u.id(), u.op(), lift(u.operand()));
            case Expr.ListLit l -> new Expr.ListLit(l.id(), liftAll(l.items()));
            case Expr.Attribute a -> new Expr.Attribute(a.id(), lift(a.target()), a.name());
            case Expr.Index i -> {
                var parts = liftAll(List.of(i.target(), i.index()));
                yield new Expr.Index(i.id(), parts.get(0), parts.get(1));
            }
            case Expr.Slice s -> rebuild(s, liftAll(parts(s)));
            default -> e;
        };
    }

    /**
     * Lifts expressions Python evaluates left to right. Those before the last one that suspends are computed into
     * temporaries first, so the suspending call cannot change what they saw; {@code null} parts stay {@code null}.
     */
    private List<Expr> liftAll(List<Expr> parts) {
        int last = -1;
        for (int i = 0; i < parts.size(); i++) {
            if (suspends(parts.get(i))) {
                last = i;
            }
        }
        var out = new ArrayList<Expr>();
        for (int i = 0; i < parts.size(); i++) {
            var part = parts.get(i);
            if (part == null || i > last) {
                out.add(part);
            } else if (i < last) {
                out.add(materialize(lift(part)));
            } else {
                out.add(lift(part));
            }
        }
        return out;
    }

    /** {@code e} computed now into a temporary, unless reading it later gives the same value. */
    private Expr materialize(Expr e) {
        if (stable(e)) {
            return e;
        }
        var name = new Expr.Name(e.id(), temporary());
        nodes.add(new Node.Assign(e.id(), name, e));
        return name;
    }

    /** Literals, and locals: only an assignment statement changes a local, and none runs inside an expression. */
    private static boolean stable(Expr e) {
        return e instanceof Expr.IntLit || e instanceof Expr.FloatLit || e instanceof Expr.StrLit
                || e instanceof Expr.BoolLit || e instanceof Expr.NoneLit || e instanceof Expr.Name;
    }

    private Expr.Call withChildren(Expr.Call c, List<Expr> children) {
        int at = 0;
        var receiver = c.receiver();
        if (receiver != null && !isQualifier(receiver)) {
            receiver = children.get(at++);
        }
        var args = new ArrayList<Expr>();
        for (int i = 0; i < c.args().size(); i++) {
            args.add(children.get(at++));
        }
        var keywords = new ArrayList<Expr.Keyword>();
        for (var keyword : c.keywords()) {
            keywords.add(new Expr.Keyword(keyword.name(), children.get(at++)));
        }
        return new Expr.Call(c.id(), receiver, c.name(), args, keywords);
    }

    /** Emits the node of a suspending call, its arguments lifted first. */
    private void call(Expr.Call c, String result) {
        var lifted = withChildren(c, liftAll(children(c)));
        if (c.receiver() == null) {
            nodes.add(new Node.Call(c.id(), file, c.name(), lifted.args(), lifted.keywords(), result));
            return;
        }
        var qualifier = ((Expr.Name) c.receiver()).name();
        if (workspace.catalog().namespaces().containsKey(qualifier)) {
            nodes.add(new Node.Action(c.id(), qualifier, c.name(), lifted.args(), lifted.keywords(), result));
        } else {
            nodes.add(new Node.Call(c.id(), qualifier, c.name(), lifted.args(), lifted.keywords(), result));
        }
    }

    private String temporary() {
        return "$" + temporaries++;
    }
}
