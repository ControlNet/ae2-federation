package space.controlnet.ae2federation.function;

import java.util.List;
import java.util.stream.Collectors;

/** A callable function's signature: a built-in from the catalogue, or a player function. */
public record Signature(String name, List<Arg> args, TypeRef result, String summary) {
    public record Arg(String name, TypeRef type) {
    }

    public Signature {
        args = List.copyOf(args);
    }

    static Signature of(FunctionDef function) {
        return new Signature(function.name(),
                function.params().stream().map(p -> new Arg(p.name(), p.type())).toList(),
                function.result(), function.docstring() == null ? "" : function.docstring());
    }

    /** {@code (name: Item, min: int) -> bool}, as completion shows it. */
    public String display() {
        return args.stream().map(a -> a.name() + ": " + a.type()).collect(Collectors.joining(", ", "(", ")"))
                + " -> " + result;
    }
}
