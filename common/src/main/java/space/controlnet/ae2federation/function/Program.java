package space.controlnet.ae2federation.function;

import java.util.List;
import java.util.Optional;

/** The functions a player has written: the one representation both the graph and the script show. */
public record Program(List<FunctionDef> functions) {
    public Program {
        functions = List.copyOf(functions);
    }

    public Optional<FunctionDef> function(String name) {
        return functions.stream().filter(function -> function.name().equals(name)).findFirst();
    }
}
