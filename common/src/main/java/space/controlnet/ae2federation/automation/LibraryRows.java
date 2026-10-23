package space.controlnet.ae2federation.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.script.Signature;
import space.controlnet.ae2federation.script.StandardLibrary;

/**
 * The Automation tab's Library panel as rows: each namespace, sorted, with its function count; under an open one its
 * functions in the library's order. A search (ignoring case, on the function's name or its qualified name such as
 * {@code mc.amount}) lists only the matches, under their namespaces, open and counting the matches.
 */
public final class LibraryRows {
    /** A namespace row ({@code function} empty), or a function row under its namespace. */
    public record Row(String namespace, String function, int count, boolean open, boolean acts, boolean lasts) {
    }

    private LibraryRows() {
    }

    public static List<Row> rows(Map<String, List<Signature>> library, String search, Set<String> open) {
        var query = search.strip().toLowerCase(Locale.ROOT);
        var rows = new ArrayList<Row>();
        library.keySet().stream().sorted().forEach(namespace -> {
            var functions = library.get(namespace).stream().filter(function -> query.isEmpty()
                    || function.name().toLowerCase(Locale.ROOT).contains(query)
                    || (namespace + "." + function.name()).toLowerCase(Locale.ROOT).contains(query)).toList();
            if (functions.isEmpty()) return;
            boolean shown = !query.isEmpty() || open.contains(namespace);
            rows.add(new Row(namespace, "", functions.size(), shown, false, false));
            if (!shown) return;
            functions.forEach(function -> rows.add(new Row(namespace, function.name(), 0, false, function.effect(),
                    StandardLibrary.lasts(function))));
        });
        return rows;
    }

    /** The function {@code name} of {@code namespace}, or null. */
    public static @Nullable Signature find(Map<String, List<Signature>> library, String namespace, String name) {
        return library.getOrDefault(namespace, List.of()).stream().filter(function -> function.name().equals(name))
                .findFirst().orElse(null);
    }
}
