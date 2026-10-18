package space.controlnet.ae2federation.function;

import java.util.List;
import java.util.Map;

/**
 * What parsing gives: the program as far as it parsed, every error in source order, and where each node starts. With
 * errors, the program leaves out the statements and functions that failed, so the editor can still offer completion.
 */
public record ParseResult(Program program, List<ScriptError> errors, Map<String, Position> positions) {
    public ParseResult {
        errors = List.copyOf(errors);
        positions = Map.copyOf(positions);
    }

    public boolean ok() {
        return errors.isEmpty();
    }
}
