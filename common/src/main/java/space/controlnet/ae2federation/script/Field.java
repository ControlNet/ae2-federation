package space.controlnet.ae2federation.script;

/** A field of a {@link ClassDef}: {@code name: type}, with a default value or {@code null}. */
public record Field(String id, Notes notes, String name, TypeRef type, Expr defaultValue) {
}
