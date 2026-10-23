package space.controlnet.ae2federation.script;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;

/**
 * What an installed function costs its computing device (user decisions 2026-10-20), worked out from the program alone.
 * Its compute is its graph nodes and those of every function it calls, directly or through others and in any file, each
 * counted once: loops and recursion add nothing. Its moves are the calls that move resources, each with the most it can
 * move. Every whole number is bounded by a range: constants, the installation's arguments, arithmetic on them,
 * {@code min} and {@code max}, loops over a {@code range}, and library amounts, which are never negative. A value read
 * from the world, or built up in a loop, has no bound.
 */
public record Cost(int nodes, List<Move> moves) {
    /** The library calls that move resources; crafting and FE move none. */
    private static final Set<String> MOVING = Set.of("ae2.import_from", "ae2.export_to", "ae2.move", "mc.move");
    /** Rounds after which a range that still grows is taken to grow without end. */
    private static final int WIDEN_AFTER = 8;

    /** A call that moves resources: the resources it may move, and the most it moves; empty when there is no bound. */
    public record Move(String call, Resources resource, OptionalLong most) {
    }

    /**
     * The resources a value may name: these IDs, written out or given as installation arguments, and when
     * {@code unknown} is not {@code null}, any resource of that type ({@code Item}, {@code Fluid} or {@code Resource}).
     */
    public record Resources(Set<String> ids, String unknown) {
        /** No value yet, before any assignment is seen. */
        static final Resources NONE = new Resources(Set.of(), null);
        public static final Resources ANY = new Resources(Set.of(), "Resource");

        public Resources {
            ids = Set.copyOf(ids);
        }

        Resources join(Resources other) {
            var joined = new HashSet<>(ids);
            joined.addAll(other.ids);
            var type = unknown == null ? other.unknown : other.unknown == null || unknown.equals(other.unknown)
                    ? unknown : ANY.unknown;
            return new Resources(joined, type);
        }

        /** Any resource of a parameter's type, when it is a resource type. */
        static Resources of(TypeRef type) {
            return type != null && (type.name().equals("Item") || type.name().equals("Fluid"))
                    ? new Resources(Set.of(), type.name()) : ANY;
        }
    }

    public Cost {
        moves = List.copyOf(moves);
    }

    /** Whether every move has a bound. */
    public boolean bounded() {
        return moves.stream().allMatch(m -> m.most().isPresent());
    }

    /**
     * The cost of {@code function} in {@code program}, with its installation arguments by parameter name: whole numbers
     * as {@code Long}, resources as their ID.
     */
    public static Cost of(Program program, String function, Map<String, ?> args, Workspace workspace) {
        return new Analysis(program, workspace).run(function, args);
    }

    /**
     * The graph nodes of {@code functions} in {@code program} and of every function they call, each counted once: an
     * installation's compute when it also has a condition to call.
     */
    public static int nodes(Program program, Collection<String> functions, Workspace workspace) {
        var analysis = new Analysis(program, workspace);
        for (var function : functions) {
            var start = analysis.lookup(function);
            if (start == null) {
                throw new IllegalArgumentException("there is no function " + function);
            }
            analysis.closure(start);
        }
        return analysis.functions.values().stream()
                .mapToInt(fn -> Canvas.of(fn.def(), Map.of(), Set.of()).nodes().size()).sum();
    }

    /** A function of the programs: {@code file} is {@code null} for the installed function's own file. */
    private record Fn(String key, String file, FunctionDef def, Set<String> names, Set<String> locals) {
    }

    private static final class Analysis {
        private final Program program;
        private final Workspace workspace;
        private final Map<String, Fn> functions = new LinkedHashMap<>();
        private final Map<String, Map<String, Span>> params = new HashMap<>();
        private final Map<String, Map<String, Span>> envs = new HashMap<>();
        private final Map<String, Map<String, Resources>> resourceParams = new HashMap<>();
        private final Map<String, Map<String, Resources>> resourceEnvs = new HashMap<>();

        Analysis(Program program, Workspace workspace) {
            this.program = program;
            this.workspace = workspace;
        }

        Cost run(String function, Map<String, ?> args) {
            var start = lookup(function);
            if (start == null) {
                throw new IllegalArgumentException("there is no function " + function);
            }
            closure(start);
            var entry = new HashMap<String, Span>();
            var named = new HashMap<String, Resources>();
            for (var param : start.def().params()) {
                var value = args.get(param.name());
                entry.put(param.name(), value instanceof Long number ? Span.of(number) : param.defaultValue() != null
                        ? eval(param.defaultValue(), Map.of(), start) : Span.ANY);
                named.put(param.name(), value instanceof String id ? new Resources(Set.of(id), null)
                        : param.defaultValue() != null ? resource(param.defaultValue(), Map.of())
                        : Resources.of(param.type()));
            }
            params.put(start.key(), entry);
            resourceParams.put(start.key(), named);
            solve();
            solveResources();
            int nodes = 0;
            var moves = new ArrayList<Move>();
            for (var fn : functions.values()) {
                nodes += Canvas.of(fn.def(), Map.of(), Set.of()).nodes().size();
                Effects.calls(fn.def().body(), call -> move(fn, call, moves));
            }
            return new Cost(nodes, moves);
        }

        /** The function a key names: {@code name} in the installed file, {@code file.name} in another. */
        private Fn lookup(String key) {
            var dot = key.indexOf('.');
            var file = dot < 0 ? null : key.substring(0, dot);
            var source = file == null ? program : workspace.files().get(file);
            if (source == null) {
                return null;
            }
            var name = key.substring(dot + 1);
            var names = new HashSet<String>();
            source.functions().forEach(f -> names.add(f.name()));
            for (var def : source.functions()) {
                if (def.name().equals(name)) {
                    var locals = new HashSet<String>();
                    def.params().forEach(p -> locals.add(p.name()));
                    Effects.locals(def.body(), locals);
                    return new Fn(key, file, def, names, locals);
                }
            }
            return null;
        }

        /** The player function a call runs, or {@code null} for a library function, a built-in or a method. */
        private Fn callee(Fn caller, Expr.Call call) {
            var target = Effects.target(call, caller.file(), caller.names(), caller.locals(), workspace);
            if (target == null || target.equals(Effects.LIBRARY_EFFECT) || target.equals(Effects.LIBRARY_LONG)) {
                return null;
            }
            return functions.containsKey(target) ? functions.get(target) : lookup(target);
        }

        private void closure(Fn start) {
            if (functions.putIfAbsent(start.key(), start) != null) {
                return;
            }
            var queue = new ArrayDeque<Fn>(List.of(start));
            while (!queue.isEmpty()) {
                var fn = queue.poll();
                Effects.calls(fn.def().body(), call -> {
                    var callee = callee(fn, call);
                    if (callee != null && !functions.containsKey(callee.key())) {
                        functions.put(callee.key(), callee);
                        queue.add(callee);
                    }
                });
            }
        }

        /** Ranges of every function's variables, and of its parameters from every call of it, until none grows. */
        private void solve() {
            for (int round = 0; ; round++) {
                var changed = false;
                for (var fn : functions.values()) {
                    var env = env(fn);
                    envs.put(fn.key(), env);
                    var calls = new ArrayList<Expr.Call>();
                    Effects.calls(fn.def().body(), calls::add);
                    for (var call : calls) {
                        var callee = callee(fn, call);
                        if (callee != null) {
                            changed |= bind(callee, call, env, fn, round);
                        }
                    }
                }
                if (!changed) {
                    return;
                }
            }
        }

        /** The resources every function's variables may name, and its parameters from every call of it. */
        private void solveResources() {
            var changed = true;
            while (changed) {
                changed = false;
                for (var fn : functions.values()) {
                    var env = resourceEnv(fn);
                    resourceEnvs.put(fn.key(), env);
                    var calls = new ArrayList<Expr.Call>();
                    Effects.calls(fn.def().body(), calls::add);
                    for (var call : calls) {
                        var callee = callee(fn, call);
                        if (callee != null) {
                            changed |= bindResources(callee, call, env);
                        }
                    }
                }
            }
        }

        private boolean bindResources(Fn callee, Expr.Call call, Map<String, Resources> env) {
            var current = resourceParams.computeIfAbsent(callee.key(), k -> new HashMap<>());
            var changed = false;
            var declared = callee.def().params();
            for (int i = 0; i < declared.size(); i++) {
                var param = declared.get(i);
                Expr given = i < call.args().size() ? call.args().get(i) : null;
                for (var keyword : call.keywords()) {
                    if (keyword.name().equals(param.name())) {
                        given = keyword.value();
                    }
                }
                var value = given != null ? resource(given, env) : param.defaultValue() != null
                        ? resource(param.defaultValue(), Map.of()) : Resources.of(param.type());
                var old = current.getOrDefault(param.name(), Resources.NONE);
                var grown = old.join(value);
                if (!grown.equals(old)) {
                    current.put(param.name(), grown);
                    changed = true;
                }
            }
            return changed;
        }

        /** The resources a function's variables may name over all its assignments, in any order. */
        private Map<String, Resources> resourceEnv(Fn fn) {
            var env = new HashMap<String, Resources>();
            fn.locals().forEach(name -> env.put(name, Resources.NONE));
            env.putAll(resourceParams.getOrDefault(fn.key(), Map.of()));
            while (true) {
                var before = new HashMap<>(env);
                assignResources(fn.def().body(), env);
                if (env.equals(before)) {
                    return env;
                }
            }
        }

        private void assignResources(List<Stmt> body, Map<String, Resources> env) {
            for (var statement : body) {
                switch (statement) {
                    case Stmt.Assign s -> {
                        if (s.target() instanceof Expr.Name name) {
                            env.merge(name.name(), resource(s.value(), env), Resources::join);
                        }
                    }
                    case Stmt.AugAssign s -> {
                        if (s.target() instanceof Expr.Name name) {
                            env.merge(name.name(), Resources.ANY, Resources::join);
                        }
                    }
                    case Stmt.For s -> {
                        env.merge(s.variable(), Resources.ANY, Resources::join);
                        assignResources(s.body(), env);
                    }
                    case Stmt.While s -> assignResources(s.body(), env);
                    case Stmt.If s -> {
                        assignResources(s.body(), env);
                        s.elifs().forEach(elif -> assignResources(elif.body(), env));
                        if (s.orElse() != null) {
                            assignResources(s.orElse().body(), env);
                        }
                    }
                    default -> {
                    }
                }
            }
        }

        /** The resources a value may name: a string as written, a variable's, or anything. */
        private static Resources resource(Expr expr, Map<String, Resources> env) {
            return switch (expr) {
                case Expr.StrLit literal -> new Resources(Set.of(literal.value()), null);
                case Expr.Name name -> env.getOrDefault(name.name(), Resources.ANY);
                default -> Resources.ANY;
            };
        }

        /** Joins a call's arguments into the callee's parameters; whether any grew. */
        private boolean bind(Fn callee, Expr.Call call, Map<String, Span> env, Fn caller, int round) {
            var current = params.computeIfAbsent(callee.key(), k -> new HashMap<>());
            var changed = false;
            var declared = callee.def().params();
            for (int i = 0; i < declared.size(); i++) {
                var param = declared.get(i);
                Expr given = i < call.args().size() ? call.args().get(i) : null;
                for (var keyword : call.keywords()) {
                    if (keyword.name().equals(param.name())) {
                        given = keyword.value();
                    }
                }
                var value = given != null ? eval(given, env, caller) : param.defaultValue() != null
                        ? eval(param.defaultValue(), Map.of(), callee) : Span.ANY;
                var old = current.getOrDefault(param.name(), Span.NONE);
                var grown = widen(old, old.join(value), round);
                if (!grown.equals(old)) {
                    current.put(param.name(), grown);
                    changed = true;
                }
            }
            return changed;
        }

        /** The ranges of a function's variables over all its assignments, in any order. */
        private Map<String, Span> env(Fn fn) {
            var env = new HashMap<String, Span>();
            fn.locals().forEach(name -> env.put(name, Span.NONE));
            env.putAll(params.getOrDefault(fn.key(), Map.of()));
            for (int round = 0; ; round++) {
                var before = new HashMap<>(env);
                assign(fn.def().body(), env, fn);
                if (round >= WIDEN_AFTER) {
                    env.replaceAll((name, span) -> widen(before.getOrDefault(name, Span.NONE), span, WIDEN_AFTER));
                }
                if (env.equals(before)) {
                    return env;
                }
            }
        }

        private void assign(List<Stmt> body, Map<String, Span> env, Fn fn) {
            for (var statement : body) {
                switch (statement) {
                    case Stmt.Assign s -> {
                        if (s.target() instanceof Expr.Name name) {
                            join(env, name.name(), eval(s.value(), env, fn));
                        }
                    }
                    case Stmt.AugAssign s -> {
                        if (s.target() instanceof Expr.Name name) {
                            join(env, name.name(), binary(s.op(), env.getOrDefault(name.name(), Span.ANY),
                                    eval(s.value(), env, fn)));
                        }
                    }
                    case Stmt.For s -> {
                        join(env, s.variable(), element(s.iterable(), env, fn));
                        assign(s.body(), env, fn);
                    }
                    case Stmt.While s -> assign(s.body(), env, fn);
                    case Stmt.If s -> {
                        assign(s.body(), env, fn);
                        s.elifs().forEach(elif -> assign(elif.body(), env, fn));
                        if (s.orElse() != null) {
                            assign(s.orElse().body(), env, fn);
                        }
                    }
                    default -> {
                    }
                }
            }
        }

        private static void join(Map<String, Span> env, String name, Span value) {
            env.put(name, env.getOrDefault(name, Span.NONE).join(value));
        }

        /** The range of a {@code for} loop's variable: a {@code range}'s numbers, anything else unbounded. */
        private Span element(Expr iterable, Map<String, Span> env, Fn fn) {
            if (iterable instanceof Expr.Call call && call.receiver() == null && call.name().equals("range")
                    && !fn.locals().contains("range") && !call.args().isEmpty() && call.keywords().isEmpty()) {
                var args = call.args().stream().map(a -> eval(a, env, fn)).toList();
                if (args.size() == 1) {
                    return new Span(0, Span.add(args.get(0).hi(), -1)).orNone(args);
                }
                if (args.size() == 2) {
                    return new Span(args.get(0).lo(), Span.add(args.get(1).hi(), -1)).orNone(args);
                }
                return new Span(Math.min(args.get(0).lo(), args.get(1).lo()),
                        Math.max(args.get(0).hi(), args.get(1).hi())).orNone(args);
            }
            return Span.ANY;
        }

        private Span eval(Expr expr, Map<String, Span> env, Fn fn) {
            return switch (expr) {
                case Expr.IntLit i -> Span.of(i.value());
                case Expr.BoolLit b -> new Span(0, 1);
                case Expr.Name n -> env.getOrDefault(n.name(), Span.ANY);
                case Expr.Unary u -> u.op() == UnaryOp.NEG ? eval(u.operand(), env, fn).negate() : new Span(0, 1);
                case Expr.Binary b -> switch (b.op()) {
                    case ADD, SUB, MUL, FLOOR_DIV, MOD -> binary(b.op(), eval(b.left(), env, fn),
                            eval(b.right(), env, fn));
                    case EQ, NE, LT, LE, GT, GE, IN, NOT_IN, IS, IS_NOT -> new Span(0, 1);
                    default -> Span.ANY;
                };
                case Expr.Call c -> call(c, env, fn);
                default -> Span.ANY;
            };
        }

        private Span call(Expr.Call call, Map<String, Span> env, Fn fn) {
            if (call.receiver() == null) {
                if (fn.locals().contains(call.name()) || fn.names().contains(call.name()) || !call.keywords().isEmpty()) {
                    return Span.ANY;
                }
                var args = call.args().stream().map(a -> eval(a, env, fn)).toList();
                return switch (call.name()) {
                    case "min" -> args.size() < 2 ? Span.ANY : new Span(args.stream().mapToLong(Span::lo).min()
                            .orElseThrow(), args.stream().mapToLong(Span::hi).min().orElseThrow()).orNone(args);
                    case "max" -> args.size() < 2 ? Span.ANY : new Span(args.stream().mapToLong(Span::lo).max()
                            .orElseThrow(), args.stream().mapToLong(Span::hi).max().orElseThrow()).orNone(args);
                    case "abs" -> args.size() != 1 ? Span.ANY : args.get(0).abs();
                    case "len" -> new Span(0, Span.MAX);
                    default -> Span.ANY;
                };
            }
            if (call.receiver() instanceof Expr.Name qualifier && !fn.locals().contains(qualifier.name())) {
                var namespace = workspace.catalog().namespaces().get(qualifier.name());
                if (namespace != null && namespace.stream().anyMatch(s -> s.name().equals(call.name())
                        && s.result() != null && s.result().name().equals("int"))) {
                    // Every whole number the library gives is an amount, a count, a level or a time: never negative.
                    return new Span(0, Span.MAX);
                }
            }
            return Span.ANY;
        }

        private static Span binary(BinaryOp op, Span left, Span right) {
            return switch (op) {
                case ADD -> left.plus(right);
                case SUB -> left.plus(right.negate());
                case MUL -> left.times(right);
                case FLOOR_DIV -> left.floorDiv(right);
                case MOD -> left.mod(right);
                default -> Span.ANY;
            };
        }

        /** A range that grew after the first rounds grows without end on the side it grew. */
        private static Span widen(Span old, Span next, int round) {
            if (round < WIDEN_AFTER || old.none() || next.none()) {
                return next;
            }
            return new Span(next.lo() < old.lo() ? Span.MIN : next.lo(), next.hi() > old.hi() ? Span.MAX : next.hi());
        }

        private void move(Fn fn, Expr.Call call, List<Move> out) {
            if (!(call.receiver() instanceof Expr.Name qualifier) || fn.locals().contains(qualifier.name())
                    || !MOVING.contains(qualifier.name() + "." + call.name())) {
                return;
            }
            var signature = workspace.catalog().namespaces().getOrDefault(qualifier.name(), List.of()).stream()
                    .filter(s -> s.name().equals(call.name())).findFirst().orElse(null);
            if (signature == null) {
                return;
            }
            var amount = argument(signature, call, "amount");
            var span = amount == null ? Span.ANY : eval(amount, envs.getOrDefault(fn.key(), Map.of()), fn);
            var named = argument(signature, call, "resource");
            var resource = named == null ? Resources.ANY
                    : resource(named, resourceEnvs.getOrDefault(fn.key(), Map.of()));
            out.add(new Move(call.id(), resource.equals(Resources.NONE) ? Resources.ANY : resource,
                    span.none() || span.hi() == Span.MAX ? OptionalLong.empty() : OptionalLong.of(Math.max(0, span.hi()))));
        }

        private static Expr argument(Signature signature, Expr.Call call, String name) {
            for (var keyword : call.keywords()) {
                if (keyword.name().equals(name)) {
                    return keyword.value();
                }
            }
            for (int i = 0; i < signature.args().size() && i < call.args().size(); i++) {
                if (signature.args().get(i).name().equals(name)) {
                    return call.args().get(i);
                }
            }
            return null;
        }
    }

    /**
     * The whole numbers a value may take, {@code lo} to {@code hi}; {@link #MIN} and {@link #MAX} stand for no bound.
     * {@link #NONE} is no value yet, before any assignment is seen.
     */
    private record Span(long lo, long hi) {
        static final long MIN = Long.MIN_VALUE;
        static final long MAX = Long.MAX_VALUE;
        static final Span ANY = new Span(MIN, MAX);
        static final Span NONE = new Span(1, 0);

        static Span of(long value) {
            return new Span(value, value);
        }

        boolean none() {
            return lo > hi;
        }

        /** This span, or {@link #NONE} when any of {@code parts} has no value yet. */
        Span orNone(List<Span> parts) {
            return parts.stream().anyMatch(Span::none) ? NONE : this;
        }

        Span join(Span other) {
            if (none()) {
                return other;
            }
            if (other.none()) {
                return this;
            }
            return new Span(Math.min(lo, other.lo), Math.max(hi, other.hi));
        }

        Span negate() {
            return none() ? NONE : new Span(neg(hi), neg(lo));
        }

        Span abs() {
            if (none()) {
                return NONE;
            }
            if (lo >= 0) {
                return this;
            }
            return new Span(hi <= 0 ? neg(hi) : 0, Math.max(neg(lo), hi));
        }

        Span plus(Span other) {
            return none() || other.none() ? NONE : new Span(add(lo, other.lo), add(hi, other.hi));
        }

        Span times(Span other) {
            if (none() || other.none()) {
                return NONE;
            }
            long[] corners = {mul(lo, other.lo), mul(lo, other.hi), mul(hi, other.lo), mul(hi, other.hi)};
            long min = MAX;
            long max = MIN;
            for (var corner : corners) {
                min = Math.min(min, corner);
                max = Math.max(max, corner);
            }
            return new Span(min, max);
        }

        /** Python's {@code //} by a positive constant; anything else is unbounded. */
        Span floorDiv(Span divisor) {
            if (none() || divisor.none()) {
                return NONE;
            }
            if (divisor.lo != divisor.hi || divisor.lo <= 0) {
                return ANY;
            }
            return new Span(lo == MIN ? MIN : Math.floorDiv(lo, divisor.lo), hi == MAX ? MAX : Math.floorDiv(hi,
                    divisor.lo));
        }

        /** Python's {@code %} by a positive divisor is never negative and below the divisor. */
        Span mod(Span divisor) {
            if (none() || divisor.none()) {
                return NONE;
            }
            return divisor.lo > 0 && divisor.hi != MAX ? new Span(0, divisor.hi - 1) : ANY;
        }

        static long neg(long value) {
            return value == MIN ? MAX : value == MAX ? MIN : -value;
        }

        static long add(long a, long b) {
            if (a == MIN || a == MAX) {
                return a;
            }
            if (b == MIN || b == MAX) {
                return b;
            }
            try {
                return Math.addExact(a, b);
            } catch (ArithmeticException e) {
                return a > 0 ? MAX : MIN;
            }
        }

        static long mul(long a, long b) {
            if (a == 0 || b == 0) {
                return 0;
            }
            var positive = (a > 0) == (b > 0);
            if (a == MIN || a == MAX || b == MIN || b == MAX) {
                return positive ? MAX : MIN;
            }
            try {
                return Math.multiplyExact(a, b);
            } catch (ArithmeticException e) {
                return positive ? MAX : MIN;
            }
        }
    }
}
