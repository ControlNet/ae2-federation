package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SyncBudgetTest {
    @Test
    void aTextThatFitsIsSentAsBuilt() {
        var root = root(5, 5);
        assertEquals(root.toString(), SyncBudget.fit(root, lists(), copy -> {}, 10_000));
        var values = entries("n", 5);
        assertEquals(values.toString(), SyncBudget.fitArray(values, 10_000));
    }

    @Test
    void aLongTextKeepsHeadsAndCountsWhatItLeftOut() {
        var root = root(200, 50);
        var text = SyncBudget.fit(root, lists(), copy -> {}, 2_000);
        assertTrue(text.length() <= 2_000, "fits: " + text.length());
        var fitted = JsonParser.parseString(text).getAsJsonObject();
        assertEquals("kept", fitted.get("scalar").getAsString(), "fields that are not lists stay");
        for (var field : List.of("networks", "endpoint")) {
            var all = root.getAsJsonArray(field);
            var kept = fitted.getAsJsonArray(field);
            var omitted = fitted.getAsJsonObject("omitted").get(field).getAsInt();
            assertEquals(all.size(), kept.size() + omitted, field + ": shown and omitted add up");
            assertEquals(all.get(0), kept.get(0), field + ": the head is kept");
        }
        assertEquals(text, SyncBudget.fit(root, lists(), copy -> {}, 2_000), "the same input trims the same way");
    }

    @Test
    void selectedEntriesStayAndDependentsFollowTheShownEntries() {
        var root = root(200, 0);
        var rules = new JsonArray();
        for (int index = 0; index < 200; index++) {
            var rule = new JsonObject();
            rule.addProperty("consumer", "n" + index);
            rules.add(rule);
        }
        root.add("rules", rules);
        var lists = List.of(new SyncBudget.Trimmed("networks", entry -> entry.get("id").getAsString().equals("n150"),
                List.of("rules")));
        var text = SyncBudget.fit(root, lists, copy -> {
            var shown = new java.util.HashSet<String>();
            copy.getAsJsonArray("networks").forEach(value -> shown.add(value.getAsJsonObject().get("id").getAsString()));
            var kept = new JsonArray();
            copy.getAsJsonArray("rules").forEach(value -> {
                if (shown.contains(value.getAsJsonObject().get("consumer").getAsString())) kept.add(value);
            });
            copy.add("rules", kept);
        }, 2_000);
        assertTrue(text.length() <= 2_000);
        var fitted = JsonParser.parseString(text).getAsJsonObject();
        var shown = new java.util.HashSet<String>();
        fitted.getAsJsonArray("networks").forEach(value -> shown.add(value.getAsJsonObject().get("id").getAsString()));
        assertTrue(shown.contains("n150"), "the selected network stays");
        fitted.getAsJsonArray("rules").forEach(value -> assertTrue(shown.contains(
                value.getAsJsonObject().get("consumer").getAsString()), "a rule of a network not shown is dropped"));
        assertEquals(Set.of(), Set.copyOf(fitted.getAsJsonObject("omitted").keySet()).stream()
                .filter(key -> !key.equals("networks")).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void aLongArrayKeepsItsLongestHeadThatFits() {
        var values = entries("n", 500);
        var text = SyncBudget.fitArray(values, 1_000);
        assertTrue(text.length() <= 1_000);
        var kept = JsonParser.parseString(text).getAsJsonArray();
        assertEquals(values.get(0), kept.get(0));
        var oneMore = SyncBudget.head(values, kept.size() + 1, entry -> false).toString();
        assertTrue(oneMore.length() > 1_000, "no longer head fits");
    }

    private static List<SyncBudget.Trimmed> lists() {
        return List.of(new SyncBudget.Trimmed("networks"), new SyncBudget.Trimmed("endpoint"));
    }

    private static JsonObject root(int networks, int endpoints) {
        var root = new JsonObject();
        root.addProperty("scalar", "kept");
        root.add("networks", entries("n", networks));
        root.add("endpoint", entries("e", endpoints));
        return root;
    }

    private static JsonArray entries(String prefix, int count) {
        var values = new JsonArray();
        for (int index = 0; index < count; index++) {
            var entry = new JsonObject();
            entry.addProperty("id", prefix + index);
            entry.addProperty("label", "an entry with some text");
            values.add(entry);
        }
        return values;
    }
}
