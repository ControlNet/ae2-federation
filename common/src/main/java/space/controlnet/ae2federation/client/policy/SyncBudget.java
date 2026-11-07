package space.controlnet.ae2federation.client.policy;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Keeps the screens' JSON texts within what LDLib2's String bindings can send: they encode with
 * {@code ByteBufCodecs.STRING_UTF8}, which refuses more than {@link #MAX_CHARS} characters by throwing on the server
 * thread. A text that fits is sent exactly as built. A larger one keeps the head of its long lists, plus the entries
 * the screen has selected, and names under {@code omitted} how many each list lost; the trimming is deterministic.
 * Measured on 2026-11-07: about 1,075 characters per Endpoint and 720 per member network with two rules, so the limit
 * is reached at about 30 Endpoints or 45 networks.
 */
public final class SyncBudget {
    public static final int MAX_CHARS = 32767;

    private SyncBudget() {
    }

    /**
     * A root list a fit may shorten from its tail. Entries {@code anchored} keeps stay whatever the length; the
     * {@code dependents} are other root lists that follow from this one (the fit's repair filters them), so their size
     * counts with it when the longest list is chosen.
     */
    public record Trimmed(String field, Predicate<JsonObject> anchored, List<String> dependents) {
        public Trimmed(String field) {
            this(field, entry -> false, List.of());
        }
    }

    /**
     * {@code root}'s text, or, when it is longer than {@link #MAX_CHARS}, the text of a copy whose {@code lists} are
     * shortened: the list with the most characters loses entries in proportion to the excess until the text fits.
     * {@code repair} receives each copy after its lists are shortened, to drop what refers to entries no longer there.
     */
    public static String fit(JsonObject root, List<Trimmed> lists, Consumer<JsonObject> repair) {
        return fit(root, lists, repair, MAX_CHARS);
    }

    static String fit(JsonObject root, List<Trimmed> lists, Consumer<JsonObject> repair, int budget) {
        var text = root.toString();
        if (text.length() <= budget) return text;
        var counts = new int[lists.size()];
        for (int index = 0; index < counts.length; index++) counts[index] = array(root, lists.get(index).field()).size();
        while (true) {
            var copy = new JsonObject();
            root.entrySet().forEach(entry -> copy.add(entry.getKey(), entry.getValue()));
            var omitted = new JsonObject();
            for (int index = 0; index < counts.length; index++) {
                var list = lists.get(index);
                if (!root.has(list.field())) continue;
                var all = array(root, list.field());
                var kept = head(all, counts[index], list.anchored());
                copy.add(list.field(), kept);
                if (kept.size() < all.size()) omitted.addProperty(list.field(), all.size() - kept.size());
            }
            repair.accept(copy);
            if (!omitted.isEmpty()) copy.add("omitted", omitted);
            text = copy.toString();
            if (text.length() <= budget) return text;
            var chars = new long[counts.length];
            boolean any = false;
            for (int index = 0; index < counts.length; index++) {
                if (counts[index] == 0) continue;
                any = true;
                chars[index] = array(copy, lists.get(index).field()).toString().length();
                for (var dependent : lists.get(index).dependents()) chars[index] += array(copy, dependent).toString().length();
            }
            if (!any) {
                // Only the selected entries and the fixed fields are left, and they are still too long: nothing of the
                // lists is sent. Not reached by any domain measured, whose fixed fields stay far below the limit.
                for (var list : lists) if (copy.has(list.field())) copy.add(list.field(), new JsonArray());
                repair.accept(copy);
                text = copy.toString();
                return text.length() <= budget ? text : "{}";
            }
            // The longest lists give up the excess down to one common size, so they end up alike rather than one of
            // them emptied; every list above that size loses at least one entry.
            long level = level(chars, text.length() - (long) budget);
            for (int index = 0; index < counts.length; index++) {
                if (counts[index] == 0 || chars[index] <= level) continue;
                counts[index] = (int) Math.min(counts[index] - 1L, counts[index] * level / chars[index]);
            }
        }
    }

    /** The size the lists of {@code chars} are cut down to so that together they lose at least {@code excess}. */
    private static long level(long[] chars, long excess) {
        var sorted = chars.clone();
        java.util.Arrays.sort(sorted);
        long removable = 0;
        for (int above = 1; above <= sorted.length; above++) {
            long current = sorted[sorted.length - above];
            long next = above < sorted.length ? sorted[sorted.length - above - 1] : 0;
            removable += (current - next) * above;
            if (removable >= excess) return next + (removable - excess) / above;
        }
        return 0;
    }

    /** {@code values}' text, or that of its longest head that fits {@link #MAX_CHARS}; entries are kept whole. */
    public static String fitArray(JsonArray values) {
        return fitArray(values, MAX_CHARS);
    }

    static String fitArray(JsonArray values, int budget) {
        var text = values.toString();
        if (text.length() <= budget) return text;
        // "[" and "]", and a comma before every entry but the first.
        long length = 1;
        int count = 0;
        for (var value : values) {
            long next = length + (count == 0 ? 0 : 1) + value.toString().length();
            if (next + 1 > budget) break;
            length = next;
            count++;
        }
        return head(values, count, entry -> false).toString();
    }

    /** The first {@code count} entries of {@code values} and every later one {@code anchored} keeps, in their order. */
    static JsonArray head(JsonArray values, int count, Predicate<JsonObject> anchored) {
        var kept = new JsonArray();
        for (int index = 0; index < values.size(); index++) {
            JsonElement value = values.get(index);
            if (index < count || value.isJsonObject() && anchored.test(value.getAsJsonObject())) kept.add(value);
        }
        return kept;
    }

    private static JsonArray array(JsonObject root, String field) {
        return root.has(field) && root.get(field).isJsonArray() ? root.getAsJsonArray(field) : new JsonArray();
    }
}
