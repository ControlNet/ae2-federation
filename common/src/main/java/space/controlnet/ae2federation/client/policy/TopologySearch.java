package space.controlnet.ae2federation.client.policy;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The topology search: a network matches its name, identity or device kinds as text, or a block position it
 * holds when the query is one to three whole numbers ("10, -57, 13", "10 -57 13", or leading axes "10 -57").
 */
public final class TopologySearch {
    private TopologySearch() {
    }

    public static boolean matches(String query, List<String> texts, List<BlockMarks.Mark> places) {
        var normalized = query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) return true;
        var axes = coordinates(normalized);
        if (axes != null) {
            for (var place : places) {
                int[] values = {place.x(), place.y(), place.z()};
                boolean all = true;
                for (int axis = 0; axis < axes.size() && all; axis++) all = values[axis] == axes.get(axis);
                if (all) return true;
            }
        }
        for (var text : texts) {
            if (text.toLowerCase(Locale.ROOT).contains(normalized)) return true;
        }
        return false;
    }

    /** One to three integers separated by commas or spaces, else {@code null}. */
    private static List<Integer> coordinates(String query) {
        var parts = query.replace(',', ' ').trim().split("\\s+");
        if (parts.length > 3) return null;
        var values = new ArrayList<Integer>(parts.length);
        for (var part : parts) {
            try {
                values.add(Integer.parseInt(part));
            } catch (NumberFormatException notANumber) {
                return null;
            }
        }
        return values;
    }
}
