package space.controlnet.ae2federation.function;

/** A function parameter with its type hint, and its default value ({@code null} when it has none). */
public record Param(String id, String name, TypeRef type, Expr defaultValue) {
}
