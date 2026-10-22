package space.controlnet.ae2federation.script;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which functions act on the world. A library function says so in its signature; a player function does if it calls
 * one that does, in any file, however indirectly (recursion included). Methods, built-ins and constructors never do.
 * The graph view draws effectful calls on the exec chain, and the interpreter gives each a tick.
 */
record Effects(Set<String> functions, Set<String> calls) {
    /** A function's calls, each resolved to the key of the function it calls, or {@code null} if none. */
    private record Calls(boolean direct, Map<String, String> targets) {
    }

    /** Effects of {@code program}'s functions; keys are names in it and {@code file.name} in the other files. */
    static Effects of(Program program, Workspace workspace) {
        var functions = new HashMap<String, Calls>();
        collect(program, null, workspace, functions);
        workspace.files().forEach((name, file) -> collect(file, name, workspace, functions));

        var effectful = new HashSet<String>();
        functions.forEach((key, calls) -> {
            if (calls.direct()) {
                effectful.add(key);
            }
        });
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var entry : functions.entrySet()) {
                if (!effectful.contains(entry.getKey())
                        && entry.getValue().targets().values().stream().anyMatch(effectful::contains)) {
                    effectful.add(entry.getKey());
                    changed = true;
                }
            }
        }

        var ownFunctions = new HashSet<String>();
        var ownCalls = new HashSet<String>();
        for (var function : program.functions()) {
            if (effectful.contains(function.name())) {
                ownFunctions.add(function.name());
            }
            var calls = functions.get(function.name());
            if (calls == null) {
                continue;
            }
            calls.targets().forEach((call, target) -> {
                if (target != null && (effectful.contains(target) || target.equals(LIBRARY_EFFECT))) {
                    ownCalls.add(call);
                }
            });
        }
        return new Effects(Set.copyOf(ownFunctions), Set.copyOf(ownCalls));
    }

    /** Stands for "a library function that acts on the world" among call targets. */
    static final String LIBRARY_EFFECT = "<library effect>";

    private static void collect(Program program, String fileName, Workspace workspace, Map<String, Calls> out) {
        var names = new HashSet<String>();
        program.functions().forEach(f -> names.add(f.name()));
        for (var function : program.functions()) {
            var key = fileName == null ? function.name() : fileName + "." + function.name();
            if (out.containsKey(key)) {
                continue;
            }
            var locals = new HashSet<String>();
            function.params().forEach(p -> locals.add(p.name()));
            locals(function.body(), locals);
            var targets = new HashMap<String, String>();
            boolean[] direct = {false};
            calls(function.body(), call -> {
                var target = target(call, fileName, names, locals, workspace);
                targets.put(call.id(), target);
                if (LIBRARY_EFFECT.equals(target)) {
                    direct[0] = true;
                }
            });
            out.put(key, new Calls(direct[0], targets));
        }
    }

    /** The key of the function a call runs; {@link #LIBRARY_EFFECT} for an effectful library function. */
    static String target(Expr.Call call, String fileName, Set<String> names, Set<String> locals,
            Workspace workspace) {
        if (call.receiver() == null) {
            if (locals.contains(call.name()) || !names.contains(call.name())) {
                return null;
            }
            return fileName == null ? call.name() : fileName + "." + call.name();
        }
        if (!(call.receiver() instanceof Expr.Name qualifier) || locals.contains(qualifier.name())) {
            return null;
        }
        var namespace = workspace.catalog().namespaces().get(qualifier.name());
        if (namespace != null) {
            return namespace.stream().anyMatch(s -> s.name().equals(call.name()) && s.effect()) ? LIBRARY_EFFECT : null;
        }
        return workspace.files().containsKey(qualifier.name()) ? qualifier.name() + "." + call.name() : null;
    }

    static void locals(List<Stmt> body, Set<String> out) {
        for (var statement : body) {
            switch (statement) {
                case Stmt.Assign s -> {
                    if (s.target() instanceof Expr.Name n) {
                        out.add(n.name());
                    }
                }
                case Stmt.For s -> {
                    out.add(s.variable());
                    locals(s.body(), out);
                }
                case Stmt.While s -> locals(s.body(), out);
                case Stmt.If s -> {
                    locals(s.body(), out);
                    s.elifs().forEach(e -> locals(e.body(), out));
                    if (s.orElse() != null) {
                        locals(s.orElse().body(), out);
                    }
                }
                default -> {
                }
            }
        }
    }

    /** Every call in the body, in Python's evaluation order. */
    static void calls(List<Stmt> body, java.util.function.Consumer<Expr.Call> out) {
        for (var statement : body) {
            switch (statement) {
                case Stmt.Assign s -> {
                    calls(s.value(), out);
                    calls(s.target(), out);
                }
                case Stmt.AugAssign s -> {
                    calls(s.target(), out);
                    calls(s.value(), out);
                }
                case Stmt.CallStmt s -> calls(s.call(), out);
                case Stmt.If s -> {
                    calls(s.condition(), out);
                    calls(s.body(), out);
                    for (var elif : s.elifs()) {
                        calls(elif.condition(), out);
                        calls(elif.body(), out);
                    }
                    if (s.orElse() != null) {
                        calls(s.orElse().body(), out);
                    }
                }
                case Stmt.While s -> {
                    calls(s.condition(), out);
                    calls(s.body(), out);
                }
                case Stmt.For s -> {
                    calls(s.iterable(), out);
                    calls(s.body(), out);
                }
                case Stmt.Return s -> {
                    if (s.value() != null) {
                        calls(s.value(), out);
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void calls(Expr e, java.util.function.Consumer<Expr.Call> out) {
        switch (e) {
            case Expr.Call c -> {
                if (c.receiver() != null) {
                    calls(c.receiver(), out);
                }
                c.args().forEach(a -> calls(a, out));
                c.keywords().forEach(k -> calls(k.value(), out));
                out.accept(c);
            }
            case Expr.Attribute a -> calls(a.target(), out);
            case Expr.Index i -> {
                calls(i.target(), out);
                calls(i.index(), out);
            }
            case Expr.Slice s -> {
                calls(s.target(), out);
                for (var part : java.util.Arrays.asList(s.lower(), s.upper(), s.step())) {
                    if (part != null) {
                        calls(part, out);
                    }
                }
            }
            case Expr.Unary u -> calls(u.operand(), out);
            case Expr.Binary b -> {
                calls(b.left(), out);
                calls(b.right(), out);
            }
            case Expr.ListLit l -> l.items().forEach(item -> calls(item, out));
            case Expr.DictLit d -> {
                for (int i = 0; i < d.keys().size(); i++) {
                    calls(d.keys().get(i), out);
                    calls(d.values().get(i), out);
                }
            }
            case Expr.SetLit l -> l.items().forEach(item -> calls(item, out));
            default -> {
            }
        }
    }
}
