package space.controlnet.ae2federation.function;

import java.util.List;

/** One program file: its functions in order, and the comments after the last one. */
public record Program(List<FunctionDef> functions, List<String> endComments) {
    public Program {
        functions = List.copyOf(functions);
        endComments = List.copyOf(endComments);
    }
}
