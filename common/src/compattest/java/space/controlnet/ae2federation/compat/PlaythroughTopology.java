package space.controlnet.ae2federation.compat;

import com.google.gson.GsonBuilder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;

/** Seeded dimensions and result rendering shared by the Phase 1 serial driver. */
final class PlaythroughTopology {
    static final String PREFIX = "ae2federation.playthrough.topology.";
    static final Map<String, List<String>> DIMENSIONS = Map.of(
            "roles", List.of("WEST/EAST/NORTH", "NORTH/EAST/WEST", "EAST/WEST/NORTH", "NORTH/WEST/EAST", "WEST/NORTH/EAST", "EAST/NORTH/WEST"),
            "path", List.of("projection", "federation-provider", "local-endpoint"),
            "capacity", List.of("normal", "at-limit", "over-limit"),
            "networks", List.of("2", "3", "4", "5", "6"),
            "join", List.of("router-switch", "multi-hop", "bundle-2x2", "bundle-3x3", "beam", "p2p", "quantum", "bridge", "router"),
            "power", List.of("WEST", "EAST", "NORTH"),
            "splitPatterns", List.of("false", "true"),
            "extraCpus", List.of("false", "true"));
    static final java.util.Set<String> DEFAULT_TARGETS = java.util.Set.of("ae2:blank_pattern", "minecraft:crafting_table", "functionalstorage:oak_1");
    static final List<String> PERTURBATIONS = List.of("none", "cable", "router", "STORAGE", "CRAFTING", "ME_POWER");
    record Layout(long seed, int networks, String join, String power, boolean splitPatterns, boolean extraCpus, String capacity, String path, String roles) {
        net.minecraft.core.Direction role(int index) { return net.minecraft.core.Direction.valueOf(roles().split("/")[index]); }
        Map<String, String> dimensions() {
            return Map.of("networks", Integer.toString(networks), "join", join, "power", power,
                    "splitPatterns", Boolean.toString(splitPatterns), "extraCpus", Boolean.toString(extraCpus),
                    "capacity", capacity, "path", path, "patternFactory", role(1).name(), "materials", role(0).name(), "requesterCpu", role(2).name());
        }
    }

    static Layout layout(long seed) {
        String capacity = System.getProperty(PREFIX + "capacity", switch ((int) Math.floorMod(seed, 7)) {
            case 5 -> "at-limit"; case 6 -> "over-limit"; default -> "normal";
        });
        if (!DIMENSIONS.get("capacity").contains(capacity)) throw new IllegalArgumentException("Invalid capacity case: " + capacity);
        if (!capacity.equals("normal")) return new Layout(seed, 3, "multi-hop", "WEST", false, false, capacity, "projection", "WEST/EAST/NORTH");
        var random = new Random(seed);
        int networks = Integer.parseInt(value("networks", random));
        String join = value("join", random);
        String power = value("power", random);
        boolean split = Boolean.parseBoolean(value("splitPatterns", random));
        boolean cpus = Boolean.parseBoolean(value("extraCpus", random));
        if (networks < 4) {
            if (Boolean.getBoolean(PREFIX + "splitPatterns") || Boolean.getBoolean(PREFIX + "extraCpus"))
                throw new IllegalArgumentException("Split patterns and extra CPUs require at least four networks");
            split = false; cpus = false;
        }
        String path = System.getProperty(PREFIX + "path", "projection");
        if (!DIMENSIONS.get("path").contains(path)) throw new IllegalArgumentException("Invalid crafting path: " + path);
        String roles = System.getProperty(PREFIX + "roles", DIMENSIONS.get("roles").get((int) Math.floorMod(seed, networks == 2 ? 2 : 6)));
        if (!DIMENSIONS.get("roles").contains(roles)) throw new IllegalArgumentException("Invalid roles: " + roles);
        if (networks == 2 && !roles.split("/")[1].equals("EAST")) throw new IllegalArgumentException("Two-network layouts keep the separate factory east");
        return new Layout(seed, networks, join, power, split, cpus, "normal", path, roles);
    }

    private static String value(String name, Random random) {
        var values = DIMENSIONS.get(name);
        String selected = values.get(random.nextInt(name.equals("join") ? 6 : values.size()));
        selected = System.getProperty(PREFIX + name, selected);
        if (!values.contains(selected)) throw new IllegalArgumentException("Invalid topology " + name + ": " + selected);
        return selected;
    }

    static List<Layout> layouts() {
        long first = Long.getLong(PREFIX + "seedStart", 0);
        int count = Integer.getInteger(PREFIX + "seedCount", 5);
        if (count < 1 || count > 10000) throw new IllegalArgumentException("seedCount must be 1..10000");
        var result = new ArrayList<Layout>();
        for (int i = 0; i < count; i++) result.add(layout(Math.addExact(first, i)));
        if (System.getProperty(PREFIX + "capacity", "normal").equals("normal")
                && Boolean.parseBoolean(System.getProperty(PREFIX + "capacityCases", "true"))) {
            for (var capacity : List.of("at-limit", "over-limit")) {
                if (result.stream().anyMatch(layout -> layout.capacity().equals(capacity))) continue;
                long seed = Math.addExact(first, count);
                int residue = capacity.equals("at-limit") ? 5 : 6;
                seed = Math.addExact(seed, Math.floorMod(residue - Math.floorMod(seed, 7), 7));
                result.add(new Layout(seed, 3, "multi-hop", "WEST", false, false, capacity, "projection", "WEST/EAST/NORTH"));
            }
        }
        return List.copyOf(result);
    }

    static List<String> perturbations() {
        var selected = List.of(System.getProperty(PREFIX + "perturbations", String.join(",", PERTURBATIONS)).split(","));
        if (selected.isEmpty() || selected.stream().anyMatch(value -> !PERTURBATIONS.contains(value) && !List.of("bridge", "bridge-part", "cancel", "chunk", "restart").contains(value))
                || selected.stream().distinct().count() != selected.size()) throw new IllegalArgumentException("Invalid perturbations: " + selected);
        return selected;
    }

    static boolean inventoriesEqual(PlaythroughFactory.Result a, PlaythroughFactory.Result b) {
        var first = new TreeMap<>(a.inventories()); first.values().removeIf(Map::isEmpty);
        var second = new TreeMap<>(b.inventories()); second.values().removeIf(Map::isEmpty);
        return first.equals(second);
    }

    static void write(Path directory, List<Layout> layouts, List<Map<String, Object>> rows,
                      boolean completed, String harnessError, Map<String, Object> incomplete) throws Exception {
        var dimensions = new TreeMap<String, Map<String, Integer>>();
        var perturbations = new TreeMap<String, Map<String, Integer>>();
        for (var row : rows) {
            if (!(row.get("layout") instanceof Layout layout)) continue;
            String outcome = row.get("classification").toString();
            layout.dimensions().forEach((key, value) -> dimensions.computeIfAbsent(key + "=" + value, ignored -> new TreeMap<>()).merge(outcome, 1, Integer::sum));
            perturbations.computeIfAbsent(row.get("perturbation").toString(), ignored -> new TreeMap<>()).merge(outcome, 1, Integer::sum);
        }
        var report = new LinkedHashMap<String, Object>();
        String checkpoint = java.util.UUID.randomUUID().toString();
        report.put("checkpointId", checkpoint); report.put("schemaVersion", 1); report.put("generatorVersion", 2);
        report.put("completed", completed); report.put("harnessError", harnessError); report.put("incomplete", incomplete);
        report.put("constraints", List.of("Requester uses its own native CPU; extra CPUs are present on other members",
                "Router without Switches uses Federation Pattern Provider front ports to attach native members",
                "Quantum requires an east projection factory and at least three members",
                "Endpoint paths use at most four recipe-specific preprogrammed Molecular Assemblers without container remainders",
                "Cancellation and restart use a single-craft target; chunk unload requires a Quantum factory",
                "Restart uses the existing prepare/recover runner and a router-switch projection layout"));
        report.put("dimensionValues", DIMENSIONS); report.put("layouts", layouts); report.put("cases", rows);
        report.put("byDimension", dimensions); report.put("byPerturbation", perturbations);
        var text = new StringBuilder("# Playthrough topology\n\nCheckpoint: ").append(checkpoint)
                .append(". Completed: ").append(completed).append(".\n\n")
                .append("Expected: every implemented layout and restoration completes with Phase 1 accounting.\n\n")
                .append("| Seed | Dimensions | Target | Perturbation | Result | Confirmation | Evidence |\n|---:|---|---|---|---|---|---|\n");
        for (var row : rows) {
            var layout = (Layout) row.get("layout");
            var result = (PlaythroughFactory.Result) row.get("federated");
            text.append('|').append(layout == null ? "" : layout.seed()).append('|')
                    .append(layout == null ? "" : layout.dimensions()).append('|').append(row.get("item")).append('|')
                    .append(row.getOrDefault("perturbation", "")).append('|').append(row.get("classification")).append('|')
                    .append(row.getOrDefault("repeatability", "not-required")).append('|')
                    .append(result == null ? row.get("reason") : result.invariant() + ": " + result.reason()).append("|\n");
        }
        text.append("\n## By dimension\n\n"); dimensions.forEach((key, value) -> text.append("- ").append(key).append(": ").append(value).append('\n'));
        text.append("\n## By perturbation\n\n"); perturbations.forEach((key, value) -> text.append("- ").append(key).append(": ").append(value).append('\n'));
        text.append("\nHarness error: ").append(harnessError).append("\n\nIncomplete: ").append(incomplete).append('\n');
        PlaythroughCheckpoint.write(directory.resolve("playthrough-topology.md"), text.toString());
        PlaythroughCheckpoint.write(directory.resolve("playthrough-topology.json"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(report));
    }
    private PlaythroughTopology() { }
}
