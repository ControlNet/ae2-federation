package space.controlnet.ae2federation.function;

/** A top-level definition of a file: a function or a class. */
public sealed interface Definition permits FunctionDef, ClassDef {
    String id();

    Notes notes();

    String name();
}
