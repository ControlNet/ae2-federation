package space.controlnet.ae2federation.function;

import java.util.List;

/**
 * A {@code def}: every parameter carries a type hint; the result's is {@code null} when it is not written, and the
 * {@link Checker} infers it from the returns. The docstring is {@code null} when there is none; its continuation lines
 * are stored without the body's indentation.
 */
public record FunctionDef(String id, Notes notes, String name, List<Param> params, TypeRef result, String docstring,
        List<Stmt> body) implements Definition {
    public FunctionDef {
        params = List.copyOf(params);
        body = List.copyOf(body);
    }
}
