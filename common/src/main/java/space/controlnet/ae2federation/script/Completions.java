package space.controlnet.ae2federation.script;

import java.util.List;

/**
 * The completion at a cursor: the typed {@code prefix} starting at {@code line}/{@code column} is what a candidate
 * replaces; {@code expected} is the type the cursor's place needs ({@code null} if unknown).
 */
public record Completions(int line, int column, String prefix, TypeRef expected, List<Candidate> candidates) {
    public Completions {
        candidates = List.copyOf(candidates);
    }
}
