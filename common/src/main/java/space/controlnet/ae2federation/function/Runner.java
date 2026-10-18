package space.controlnet.ae2federation.function;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs compiled graphs, one game tick at a time. Each node costs one step of the tick's budget; library actions run
 * one per tick; {@code mc.wait(n)} pauses for n ticks. Player function calls keep their own frames here, never on the
 * Java stack, so deep recursion stops with Python's error instead of overflowing the server's stack.
 */
public final class Runner {
    /** Python's default recursion limit. */
    static final int MAX_DEPTH = 1000;

    public enum Status {
        /** The budget ran out, or the tick's action was used; call {@link #tick} again next tick. */
        RUNNING,
        /** Paused by {@code mc.wait}. */
        WAITING,
        DONE,
        FAILED
    }

    /** Where a run stopped with an error: the file and the innermost program node. */
    public record Failure(String file, String node, RunError error) {
    }

    /** Receives each value the run computes, by the program node it belongs to; temporaries are not reported. */
    public interface Trace {
        void value(String file, String node, Object value);
    }

    private final Workspace workspace;
    private final Map<String, Map<String, Graph>> graphs;
    private final Evaluator evaluator;
    private final Evaluator.Host host;
    private final Trace trace;
    private final Deque<Frame> frames = new ArrayDeque<>();
    private Status status = Status.RUNNING;
    private Object result;
    private Failure failure;
    /** Ticks still to wait; the run resumes on the tick that brings it to zero. */
    private long sleeping;

    private static final class Frame {
        final Graph graph;
        final Map<String, Object> locals = new HashMap<>();
        int at;

        Frame(Graph graph) {
            this.graph = graph;
        }
    }

    /** A cursor of a {@code for} loop: a list is read live, as Python's iterator reads it. */
    private static final class Cursor {
        final Object iterable;
        long next;

        Cursor(Object iterable) {
            this.iterable = iterable;
        }
    }

    /** Starts {@code file.function(args)}; {@code graphs} holds every file's graphs by file and function name. */
    public Runner(Workspace workspace, Map<String, Map<String, Graph>> graphs, Evaluator.Host host, Trace trace,
            String file, String function, List<Object> args) {
        this.workspace = workspace;
        this.graphs = graphs;
        this.host = host;
        this.trace = trace;
        this.evaluator = new Evaluator(workspace, host);
        var graph = graphs.get(file).get(function);
        var frame = new Frame(graph);
        var params = graph.function().params();
        for (int i = 0; i < params.size(); i++) {
            var param = params.get(i);
            frame.locals.put(param.name(), i < args.size() ? args.get(i)
                    : evaluator.eval(file, param.defaultValue(), Map.of()));
        }
        frames.push(frame);
    }

    public Status status() {
        return status;
    }

    /** The function's result once {@link Status#DONE}. */
    public Object result() {
        return result;
    }

    public Failure failure() {
        return failure;
    }

    /** Runs up to {@code budget} nodes in this tick. */
    public Status tick(int budget) {
        if (status == Status.DONE || status == Status.FAILED) {
            return status;
        }
        if (sleeping > 0 && --sleeping > 0) {
            return status = Status.WAITING;
        }
        boolean acted = false;
        for (int steps = 0; steps < budget; steps++) {
            var frame = frames.peek();
            var node = frame.graph.nodes().get(frame.at);
            if (node instanceof Node.Action a && acted && !isWait(a)) {
                return status = Status.RUNNING;
            }
            try {
                if (node instanceof Node.Action a && !isWait(a)) {
                    acted = true;
                }
                var next = step(frame, node);
                if (next != null) {
                    return status = next;
                }
            } catch (RunError error) {
                error.at(node.source());
                failure = new Failure(frame.graph.file(), error.node(), error);
                return status = Status.FAILED;
            } catch (RuntimeException bug) {
                // A fault of the runner or the host must stop this run, never the server's tick.
                var error = new RunError("SystemError", "internal error: " + bug).at(node.source());
                failure = new Failure(frame.graph.file(), error.node(), error);
                return status = Status.FAILED;
            }
        }
        return status = Status.RUNNING;
    }

    private static boolean isWait(Node.Action a) {
        return a.namespace().equals("mc") && a.name().equals("wait");
    }

    /** Runs one node; a status when the tick ends here, {@code null} to go on. */
    private Status step(Frame frame, Node node) {
        var file = frame.graph.file();
        var locals = frame.locals;
        switch (node) {
            case Node.Assign n -> {
                var value = evaluator.eval(file, n.value(), locals);
                evaluator.store(file, n.target(), value, locals);
                report(file, n.target(), n.source(), value);
                frame.at++;
            }
            case Node.Augment n -> {
                if (n.current() == null) {
                    evaluator.augment(file, n.target(), n.op(), n.value(), locals);
                } else {
                    var current = evaluator.eval(file, n.current(), locals);
                    var value = evaluator.eval(file, n.value(), locals);
                    Object updated;
                    try {
                        updated = Ops.inPlace(n.op(), current, value);
                    } catch (RunError error) {
                        throw error.at(n.target().id());
                    }
                    evaluator.store(file, n.target(), updated, locals);
                }
                if (n.target() instanceof Expr.Name name) {
                    report(file, n.target(), n.source(), locals.get(name.name()));
                }
                frame.at++;
            }
            case Node.Evaluate n -> {
                evaluator.eval(file, n.value(), locals);
                frame.at++;
            }
            case Node.Branch n -> {
                var condition = evaluator.eval(file, n.condition(), locals);
                boolean truthy = Ops.truthy(condition);
                if (n.decision()) {
                    trace(file, n.source(), truthy);
                }
                frame.at = truthy ? frame.at + 1 : n.otherwise();
            }
            case Node.Jump n -> frame.at = n.target();
            case Node.Iterate n -> {
                var iterable = evaluator.eval(file, n.iterable(), locals);
                Ops.length(iterable);
                if (iterable instanceof String s) {
                    // A string cannot change while it is walked; its characters are split once.
                    var characters = new ArrayList<Object>();
                    s.codePoints().forEach(c -> characters.add(new String(Character.toChars(c))));
                    iterable = new ListValue(characters);
                }
                locals.put(n.cursor(), new Cursor(iterable));
                frame.at++;
            }
            case Node.Next n -> {
                var cursor = (Cursor) locals.get(n.cursor());
                if (cursor.next < Ops.length(cursor.iterable)) {
                    var item = Ops.item(cursor.iterable, cursor.next++);
                    locals.put(n.variable(), item);
                    trace(file, n.source(), item);
                    frame.at++;
                } else {
                    frame.at = n.exit();
                }
            }
            case Node.Call n -> {
                if (frames.size() >= MAX_DEPTH) {
                    throw new RunError("RecursionError", "maximum recursion depth exceeded");
                }
                var callee = graphs.get(n.file()).get(n.function());
                var callFrame = new Frame(callee);
                bind(file, n, callee, locals, callFrame.locals);
                frames.push(callFrame);
            }
            case Node.Action n -> {
                var signature = workspace.catalog().namespaces().get(n.namespace()).stream()
                        .filter(s -> s.name().equals(n.name())).findFirst().orElseThrow();
                var args = new ArrayList<Object>();
                n.args().forEach(a -> args.add(evaluator.eval(file, a, locals)));
                var keywords = new LinkedHashMap<String, Object>();
                n.keywords().forEach(k -> keywords.put(k.name(), evaluator.eval(file, k.value(), locals)));
                var bound = Evaluator.checkLibrary(n.namespace(), signature, args, keywords);
                frame.at++;
                if (isWait(n)) {
                    sleeping = (Long) bound.get(0);
                    if (n.result() != null) {
                        locals.put(n.result(), null);
                    }
                    return sleeping > 0 ? Status.WAITING : null;
                }
                var value = host.call(n.namespace(), n.name(), bound);
                if (n.result() != null) {
                    locals.put(n.result(), value);
                }
                trace(file, n.source(), value);
            }
            case Node.Return n -> {
                var value = n.value() == null ? null : evaluator.eval(file, n.value(), locals);
                trace(file, n.source(), value);
                frames.pop();
                var caller = frames.peek();
                if (caller == null) {
                    result = value;
                    return Status.DONE;
                }
                var call = (Node.Call) caller.graph.nodes().get(caller.at);
                if (call.result() != null) {
                    caller.locals.put(call.result(), value);
                }
                caller.at++;
            }
        }
        return null;
    }

    /** Binds a call's arguments to the callee's parameters, as the checker matched them. */
    private void bind(String file, Node.Call call, Graph callee, Map<String, Object> locals,
            Map<String, Object> into) {
        var params = callee.function().params();
        var keywords = new HashMap<String, Object>();
        var args = new ArrayList<Object>();
        call.args().forEach(a -> args.add(evaluator.eval(file, a, locals)));
        call.keywords().forEach(k -> keywords.put(k.name(), evaluator.eval(file, k.value(), locals)));
        for (int i = 0; i < params.size(); i++) {
            var param = params.get(i);
            Object value;
            if (i < args.size()) {
                value = args.get(i);
            } else if (keywords.containsKey(param.name())) {
                value = keywords.get(param.name());
            } else {
                // Defaults are constants, so evaluating one again gives an equal value.
                value = evaluator.eval(callee.file(), param.defaultValue(), Map.of());
            }
            into.put(param.name(), value);
        }
    }

    private static boolean isTemporary(Expr e) {
        return e instanceof Expr.Name n && n.name().startsWith("$");
    }

    private void report(String file, Expr target, String source, Object value) {
        if (!isTemporary(target)) {
            trace(file, source, value);
        }
    }

    private void trace(String file, String node, Object value) {
        if (trace != null) {
            trace.value(file, node, value);
        }
    }
}
