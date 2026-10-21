package space.controlnet.ae2federation.script;

import java.util.List;
import java.util.Map;

/**
 * What parsing gives: the program as far as it parsed, every error in source order, and where each node starts and
 * ends (just after its last token; a definition ends after its body). With errors, the program leaves out the
 * statements and functions that failed, so the editor can still offer completion.
 */
public record ParseResult(Program program, List<ScriptError> errors, Map<String, Position> positions,
        Map<String, Position> ends) {
    public ParseResult {
        errors = List.copyOf(errors);
        positions = Map.copyOf(positions);
        ends = Map.copyOf(ends);
    }

    public boolean ok() {
        return errors.isEmpty();
    }
}
