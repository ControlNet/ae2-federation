package space.controlnet.ae2federation.function;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What each function does to the game ({@link Access}), and so which ones act on the world. A library function says so
 * in its signature; a player function does what everything it calls does, in any file, however indirectly (recursion
 * included). Methods, built-ins and constructors do nothing. The graph view draws acting calls on the exec chain, and
 * the interpreter gives each a tick.
 */
record Effects(Map<String, Map<Access, Checker.Cause>> access, Set<String> functions, Set<String> calls) {
    /** What a call runs: a player function by key, or a library function with its access. */
    private record Target(String function, Set<Access> access) {
    }

    /**
     * Effects of {@code program}'s functions; keys are names in it and {@code file.name} in the other files. Each
     * access of a function is traced to the first call, in evaluation order, that brings it.
     */
    static Effects of(Program program, Workspace workspace) {
        var functions = new HashMap<String, Map<String, Target>>();
        collect(program, null, workspace, functions);
        workspace.files().forEach((name, file) -> collect(file, name, workspace, functions));

        var reached = new HashMap<String, Set<Access>>();
        functions.keySet().forEach(key -> reached.put(key, EnumSet.noneOf(Access.class)));
        boolean changed = true;
        while (changed) {
            changed = false;
            for (var entry : functions.entrySet()) {
                for (var target : entry.getValue().values()) {
                    changed |= reached.get(entry.getKey()).addAll(brings(target, reached));
                }
            }
        }

        var access = new HashMap<String, Map<Access, Checker.Cause>>();
        var ownFunctions = new HashSet<String>();
        var ownCalls = new HashSet<String>();
        for (var function : program.functions()) {
            var calls = functions.get(function.name());
            var causes = new EnumMap<Access, Checker.Cause>(Access.class);
            calls.forEach((call, target) -> {
                var brought = brings(target, reached);
                brought.forEach(a -> causes.putIfAbsent(a, new Checker.Cause(call, target.function())));
                if (brought.stream().anyMatch(Access::acts)) {
                    ownCalls.add(call);
                }
            });
            access.putIfAbsent(function.name(), Map.copyOf(causes));
            if (causes.keySet().stream().anyMatch(Access::acts)) {
                ownFunctions.add(function.name());
            }
        }
        return new Effects(Map.copyOf(access), Set.copyOf(ownFunctions), Set.copyOf(ownCalls));
    }

    private static Set<Access> brings(Target target, Map<String, Set<Access>> reached) {
        return target.function() == null ? target.access() : reached.get(target.function());
    }

    private static void collect(Program program, String fileName, Workspace workspace,
            Map<String, Map<String, Target>> out) {
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
            var targets = new LinkedHashMap<String, Target>();
            calls(function.body(), call -> {
                var target = target(call, fileName, names, locals, workspace);
                if (target != null) {
                    targets.put(call.id(), target);
                }
            });
            out.put(key, targets);
        }
    }

    /** What a call runs; {@code null} for a method, built-in or constructor, and for a name that is not defined. */
    private static Target target(Expr.Call call, String fileName, Set<String> names, Set<String> locals,
            Workspace workspace) {
        if (call.receiver() == null) {
            if (locals.contains(call.name()) || !names.contains(call.name())) {
                return null;
            }
            return new Target(fileName == null ? call.name() : fileName + "." + call.name(), Set.of());
        }
        if (!(call.receiver() instanceof Expr.Name qualifier) || locals.contains(qualifier.name())) {
            return null;
        }
        var namespace = workspace.catalog().namespaces().get(qualifier.name());
        if (namespace != null) {
            return namespace.stream().filter(s -> s.name().equals(call.name())).findFirst()
                    .map(s -> new Target(null, s.access())).orElse(null);
        }
        var file = workspace.files().get(qualifier.name());
        return file != null && file.functions().stream().anyMatch(f -> f.name().equals(call.name()))
                ? new Target(qualifier.name() + "." + call.name(), Set.of()) : null;
    }

    private static void locals(List<Stmt> body, Set<String> out) {
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
            default -> {
            }
        }
    }
}
