package space.controlnet.ae2federation.script;

import java.util.List;

/**
 * The comments that belong to a line: whole-line comments above it and one at its end ({@code null} if none). Each is
 * the text after {@code #}, kept as written.
 */
public record Notes(List<String> above, String end) {
    public static final Notes NONE = new Notes(List.of(), null);

    public Notes {
        above = List.copyOf(above);
    }
}
