package space.controlnet.ae2federation.function;

import java.util.List;
import java.util.stream.Collectors;

/**
 * A callable function's signature: a built-in from the catalogue, or a player function. {@code effect} marks a
 * library function that acts on the world and so takes a tick; a player function's is inferred by the checker.
 */
public record Signature(String name, List<Arg> args, TypeRef result, String summary, boolean effect) {
    public Signature(String name, List<Arg> args, TypeRef result, String summary) {
        this(name, args, result, summary, false);
    }

    /**
     * {@code defaultValue} is the default as script text, {@code null} when the argument is required; {@code range}
     * bounds an {@code int} argument, checked when the value is a constant, {@code null} when it has none.
     */
    public record Arg(String name, TypeRef type, String defaultValue, Range range) {
        public Arg(String name, TypeRef type) {
            this(name, type, null, null);
        }

        public Arg(String name, TypeRef type, String defaultValue) {
            this(name, type, defaultValue, null);
        }

        Arg withType(TypeRef newType) {
            return new Arg(name, newType, defaultValue, range);
        }
    }

    /** Inclusive bounds of an {@code int} argument; {@code max} is {@link Long#MAX_VALUE} when there is no upper one. */
    public record Range(long min, long max) {
    }

    public Signature {
        args = List.copyOf(args);
    }

    static Signature of(FunctionDef function) {
        return new Signature(function.name(),
                function.params().stream().map(p -> new Arg(p.name(), p.type(),
                        p.defaultValue() == null ? null : ScriptPrinter.expr(p.defaultValue()))).toList(),
                function.result(), function.docstring() == null ? "" : function.docstring());
    }

    /** A record's constructor: its fields in order, returning the record. */
    static Signature of(ClassDef record) {
        return new Signature(record.name(),
                record.fields().stream().map(f -> new Arg(f.name(), f.type(),
                        f.defaultValue() == null ? null : ScriptPrinter.expr(f.defaultValue()))).toList(),
                new TypeRef(record.name(), List.of()), record.docstring() == null ? "" : record.docstring());
    }

    /** {@code (name: Item, min: int = 64) -> bool}, as completion shows it. */
    public String display() {
        return args.stream().map(a -> a.name() + ": " + a.type() + (a.defaultValue() == null ? "" : " = " + a.defaultValue()))
                .collect(Collectors.joining(", ", "(", ")"))
                + " -> " + result;
    }
}
