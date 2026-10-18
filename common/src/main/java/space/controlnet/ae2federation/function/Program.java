package space.controlnet.ae2federation.function;

import java.util.List;

/** One program file: its functions and classes in order, and the comments after the last one. */
public record Program(List<Definition> definitions, List<String> endComments) {
    public Program {
        definitions = List.copyOf(definitions);
        endComments = List.copyOf(endComments);
    }

    public List<FunctionDef> functions() {
        return definitions.stream().filter(FunctionDef.class::isInstance).map(FunctionDef.class::cast).toList();
    }

    public List<ClassDef> classes() {
        return definitions.stream().filter(ClassDef.class::isInstance).map(ClassDef.class::cast).toList();
    }
}
