package space.controlnet.ae2federation.automation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.script.Palette;

/**
 * The Automation tab's Library panel as rows: each group of the palette ({@link Palette#groups}) in its order, with
 * its entry count; under an open one its entries in order. A search (ignoring case, on the entry's name or, in a
 * namespace, its qualified name such as {@code mc.amount}) lists only the matches, under their groups, open and
 * counting the matches.
 */
public final class LibraryRows {
    /**
     * A group's row ({@code entry} null), or an entry's row under its group. {@code namespace} when the group is a
     * namespace, whose name shows as written; the others show their translated names.
     */
    public record Row(String group, boolean namespace, @Nullable Palette.Entry entry, int count, boolean open) {
        public String function() {
            return entry == null ? "" : entry.name();
        }

        public boolean acts() {
            return entry != null && entry.acts();
        }

        public boolean lasts() {
            return entry != null && entry.lasts();
        }
    }

    private LibraryRows() {
    }

    public static List<Row> rows(List<Palette.Group> groups, String search, Set<String> open) {
        var query = search.strip().toLowerCase(Locale.ROOT);
        var rows = new ArrayList<Row>();
        for (var group : groups) {
            var entries = group.entries().stream().filter(entry -> query.isEmpty()
                    || entry.name().toLowerCase(Locale.ROOT).contains(query)
                    || group.namespace() && (group.key() + "." + entry.name()).toLowerCase(Locale.ROOT)
                            .contains(query)).toList();
            if (entries.isEmpty()) continue;
            boolean shown = !query.isEmpty() || open.contains(group.key());
            rows.add(new Row(group.key(), group.namespace(), null, entries.size(), shown));
            if (!shown) continue;
            entries.forEach(entry -> rows.add(new Row(group.key(), group.namespace(), entry, 0, false)));
        }
        return rows;
    }

    /** The entry {@code name} of the group {@code group}, or null. */
    public static @Nullable Palette.Entry find(List<Palette.Group> groups, String group, String name) {
        return groups.stream().filter(g -> g.key().equals(group)).flatMap(g -> g.entries().stream())
                .filter(entry -> entry.name().equals(name)).findFirst().orElse(null);
    }
}
