package space.controlnet.ae2federation.function;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates expressions of a checked program, and stores into assignment targets. Only expressions without suspension
 * points: the compiler lifts calls of player functions and of library actions out first, so a call reaching here is
 * a built-in function, a method, a record constructor, or a library read, which the host answers at once.
 */
public final class Evaluator {
    /** The game, as the standard library reaches it. */
    public interface Host {
        /**
         * {@code namespace.name(args)}: arguments in parameter order with defaults filled in, already checked
         * against the signature's ranges and for {@code None}. Throws {@link RunError} for a call the game refuses.
         * An interface is a block, so a {@code Block} argument is either a block the host made or an interface's ID
         * or alias (a {@code String}), meaning the interface's own block; where the interface's own block means
         * nothing (it holds nothing and must not break itself), the call does nothing and gives False or 0.
         */
        Object call(String namespace, String name, List<Object> args);
    }

    private final Workspace workspace;
    private final Host host;

    public Evaluator(Workspace workspace, Host host) {
        this.workspace = workspace;
        this.host = host;
    }

    /**
     * The value of {@code e}, written in {@code file}, with the frame's {@code locals}. An error carries the innermost
     * expression that raised it.
     */
    public Object eval(String file, Expr e, Map<String, Object> locals) {
        try {
            return evaluate(file, e, locals);
        } catch (RunError error) {
            throw error.at(e.id());
        }
    }

    private Object evaluate(String file, Expr e, Map<String, Object> locals) {
        return switch (e) {
            case Expr.IntLit l -> l.value();
            case Expr.FloatLit l -> l.value();
            case Expr.StrLit l -> l.value();
            case Expr.BoolLit l -> l.value();
            case Expr.NoneLit l -> null;
            case Expr.ListLit l -> {
                var items = new ArrayList<>();
                l.items().forEach(item -> items.add(eval(file, item, locals)));
                yield new ListValue(items);
            }
            case Expr.Name n -> {
                if (!locals.containsKey(n.name())) {
                    throw new RunError("UnboundLocalError", "cannot access local variable '" + n.name()
                            + "' where it is not associated with a value");
                }
                yield locals.get(n.name());
            }
            case Expr.Unary u -> Ops.unary(u.op(), eval(file, u.operand(), locals));
            case Expr.Binary b -> {
                var left = eval(file, b.left(), locals);
                if (b.op() == BinaryOp.AND) {
                    yield Ops.truthy(left) ? eval(file, b.right(), locals) : left;
                }
                if (b.op() == BinaryOp.OR) {
                    yield Ops.truthy(left) ? left : eval(file, b.right(), locals);
                }
                yield Ops.binary(b.op(), left, eval(file, b.right(), locals));
            }
            case Expr.Attribute a -> attribute(eval(file, a.target(), locals), a.name());
            case Expr.Index i -> Ops.index(eval(file, i.target(), locals), eval(file, i.index(), locals));
            case Expr.Slice s -> Ops.slice(eval(file, s.target(), locals), part(file, s.lower(), locals),
                    part(file, s.upper(), locals), part(file, s.step(), locals));
            case Expr.Call c -> call(file, c, locals);
        };
    }

    private Object part(String file, Expr e, Map<String, Object> locals) {
        return e == null ? null : eval(file, e, locals);
    }

    static Object attribute(Object target, String name) {
        if (target instanceof RecordValue r && r.fields.containsKey(name)) {
            return r.fields.get(name);
        }
        if (target instanceof RangeValue r && Checker.RANGE_FIELDS.contains(name)) {
            return switch (name) {
                case "start" -> r.start();
                case "stop" -> r.stop();
                default -> r.step();
            };
        }
        throw new RunError("AttributeError", "'" + Ops.typeName(target) + "' object has no attribute '" + name + "'");
    }

    /** {@code target = value}; the target's own parts are evaluated here, after the value, as in Python. */
    public void store(String file, Expr target, Object value, Map<String, Object> locals) {
        try {
            storeInto(file, target, value, locals);
        } catch (RunError error) {
            throw error.at(target.id());
        }
    }

    private void storeInto(String file, Expr target, Object value, Map<String, Object> locals) {
        switch (target) {
            case Expr.Name n -> locals.put(n.name(), value);
            case Expr.Attribute a -> setField(eval(file, a.target(), locals), a.name(), value);
            case Expr.Index i -> Ops.setIndex(eval(file, i.target(), locals), eval(file, i.index(), locals), value);
            case Expr.Slice s -> Ops.setSlice(eval(file, s.target(), locals), part(file, s.lower(), locals),
                    part(file, s.upper(), locals), part(file, s.step(), locals), value);
            default -> throw new IllegalArgumentException("not an assignment target: " + target);
        }
    }

    /**
     * {@code target op= value}: the target's parts are evaluated once, and a list is changed in place by {@code +=}
     * and {@code *=}, as in Python.
     */
    public void augment(String file, Expr target, BinaryOp op, Expr valueExpr, Map<String, Object> locals) {
        try {
            augmentInto(file, target, op, valueExpr, locals);
        } catch (RunError error) {
            throw error.at(target.id());
        }
    }

    private void augmentInto(String file, Expr target, BinaryOp op, Expr valueExpr, Map<String, Object> locals) {
        switch (target) {
            case Expr.Name n -> {
                var current = eval(file, n, locals);
                locals.put(n.name(), Ops.inPlace(op, current, eval(file, valueExpr, locals)));
            }
            case Expr.Attribute a -> {
                var owner = eval(file, a.target(), locals);
                var current = attribute(owner, a.name());
                setField(owner, a.name(), Ops.inPlace(op, current, eval(file, valueExpr, locals)));
            }
            case Expr.Index i -> {
                var owner = eval(file, i.target(), locals);
                var index = eval(file, i.index(), locals);
                var current = Ops.index(owner, index);
                Ops.setIndex(owner, index, Ops.inPlace(op, current, eval(file, valueExpr, locals)));
            }
            case Expr.Slice s -> {
                var owner = eval(file, s.target(), locals);
                var lower = part(file, s.lower(), locals);
                var upper = part(file, s.upper(), locals);
                var step = part(file, s.step(), locals);
                var current = Ops.slice(owner, lower, upper, step);
                Ops.setSlice(owner, lower, upper, step, Ops.inPlace(op, current, eval(file, valueExpr, locals)));
            }
            default -> throw new IllegalArgumentException("not an assignment target: " + target);
        }
    }

    private static void setField(Object target, String name, Object value) {
        if (target instanceof RecordValue r && r.fields.containsKey(name)) {
            if (r.readOnly) {
                throw new RunError("FrozenInstanceError", "cannot assign to field '" + name + "'");
            }
            r.fields.put(name, value);
            return;
        }
        throw new RunError("AttributeError", "'" + Ops.typeName(target) + "' object has no attribute '" + name + "'");
    }

    // ---- calls ----

    private Object call(String file, Expr.Call c, Map<String, Object> locals) {
        if (c.receiver() == null) {
            if (Checker.BUILT_INS.contains(c.name())) {
                var keywords = new LinkedHashMap<String, Object>();
                var args = arguments(file, c, locals);
                c.keywords().forEach(k -> keywords.put(k.name(), eval(file, k.value(), locals)));
                return Ops.builtIn(c.name(), args, keywords);
            }
            var record = findClass(file, c.name());
            if (record != null) {
                return construct(file, file, record, c, locals);
            }
            throw new IllegalStateException("call of " + c.name() + "() must be lifted out by the compiler");
        }
        if (c.receiver() instanceof Expr.Name r && !locals.containsKey(r.name())) {
            var namespace = workspace.catalog().namespaces().get(r.name());
            if (namespace != null) {
                var signature = namespace.stream().filter(s -> s.name().equals(c.name())).findFirst().orElseThrow();
                if (signature.effect()) {
                    throw new IllegalStateException(r.name() + "." + c.name() + "() must be lifted out by the compiler");
                }
                return host.call(r.name(), c.name(), library(r.name(), signature, file, c, locals));
            }
            var record = findClass(r.name(), c.name());
            if (record != null) {
                return construct(file, r.name(), record, c, locals);
            }
            throw new IllegalStateException("call of " + r.name() + "." + c.name() + "() must be lifted out");
        }
        var receiver = eval(file, c.receiver(), locals);
        var args = arguments(file, c, locals);
        var keywords = new LinkedHashMap<String, Object>();
        c.keywords().forEach(k -> keywords.put(k.name(), eval(file, k.value(), locals)));
        return Ops.method(receiver, c.name(), args, keywords);
    }

    private List<Object> arguments(String file, Expr.Call c, Map<String, Object> locals) {
        var args = new ArrayList<>();
        c.args().forEach(a -> args.add(eval(file, a, locals)));
        return args;
    }

    /** A library call's arguments in parameter order, checked as the checker cannot when they are not constants. */
    List<Object> library(String namespace, Signature signature, String file, Expr.Call c, Map<String, Object> locals) {
        var keywords = new LinkedHashMap<String, Object>();
        var args = arguments(file, c, locals);
        c.keywords().forEach(k -> keywords.put(k.name(), eval(file, k.value(), locals)));
        return checkLibrary(namespace, signature, args, keywords);
    }

    /** Binds evaluated arguments to a library signature and checks {@code None} and ranges. */
    static List<Object> checkLibrary(String namespace, Signature signature, List<Object> args,
            Map<String, Object> keywords) {
        var display = namespace + "." + signature.name() + "()";
        var bound = Ops.bind(signature, args, keywords);
        for (int i = 0; i < bound.length; i++) {
            var param = signature.args().get(i);
            // The checker lets a bool stand for an int and an int for a float; the host gets the declared kind.
            if (Types.INT.equals(param.type()) && bound[i] instanceof Boolean b) {
                bound[i] = b ? 1L : 0L;
            } else if (Types.FLOAT.equals(param.type()) && (bound[i] instanceof Long || bound[i] instanceof Boolean)) {
                bound[i] = bound[i] instanceof Long l ? (double) l : (Boolean) bound[i] ? 1.0 : 0.0;
            }
            var value = bound[i];
            if (value == null && !"None".equals(param.defaultValue())) {
                throw Ops.typeError("argument '" + param.name() + "' of " + display + " must be " + param.type()
                        + ", not None");
            }
            var range = param.range();
            if (range != null && value instanceof Long l && (l < range.min() || l > range.max())) {
                throw Ops.valueError(param.name() + " of " + display + " must be "
                        + (range.max() == Long.MAX_VALUE ? "at least " + range.min()
                                : "from " + range.min() + " to " + range.max()) + ", not " + l);
            }
        }
        return java.util.Arrays.asList(bound);
    }

    private ClassDef findClass(String file, String name) {
        var program = workspace.files().get(file);
        return program == null ? null
                : program.classes().stream().filter(c -> c.name().equals(name)).findFirst().orElse(null);
    }

    /** A new record; its type is named with its file, so the same class reached from two files is one type. */
    private Object construct(String file, String classFile, ClassDef record, Expr.Call c, Map<String, Object> locals) {
        var args = arguments(file, c, locals);
        var keywords = new LinkedHashMap<String, Object>();
        c.keywords().forEach(k -> keywords.put(k.name(), eval(file, k.value(), locals)));
        var fields = new LinkedHashMap<String, Object>();
        var all = record.fields();
        for (int i = 0; i < all.size(); i++) {
            var field = all.get(i);
            Object value;
            if (i < args.size()) {
                value = args.get(i);
            } else if (keywords.containsKey(field.name())) {
                value = keywords.get(field.name());
            } else {
                // Defaults are constants (the checker's rule), so evaluating one again gives an equal value.
                value = eval(classFile, field.defaultValue(), Map.of());
            }
            fields.put(field.name(), value);
        }
        return new RecordValue(classFile + "." + record.name(), fields, false);
    }
}
