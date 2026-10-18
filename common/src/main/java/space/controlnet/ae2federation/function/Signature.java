package space.controlnet.ae2federation.function;

import java.util.List;
import java.util.stream.Collectors;

/** A callable function's signature: a built-in from the catalogue, or a player function. */
public record Signature(String name, List<Arg> args, TypeRef result, String summary) {
    /** {@code defaultValue} is the default as script text, {@code null} when the argument is required. */
    public record Arg(String name, TypeRef type, String defaultValue) {
        public Arg(String name, TypeRef type) {
            this(name, type, null);
        }
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

    /** {@code (name: Item, min: int = 64) -> bool}, as completion shows it. */
    public String display() {
        return args.stream().map(a -> a.name() + ": " + a.type() + (a.defaultValue() == null ? "" : " = " + a.defaultValue()))
                .collect(Collectors.joining(", ", "(", ")"))
                + " -> " + result;
    }
}
