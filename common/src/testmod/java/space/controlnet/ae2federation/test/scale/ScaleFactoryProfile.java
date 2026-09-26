package space.controlnet.ae2federation.test.scale;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record ScaleFactoryProfile(String id, long seed, int gridCount, int logicalPatterns, int orderUnits,
        int warmupSeconds, int sampleSeconds, int repetitions, List<String> layouts) {
    private static final Map<String, List<Integer>> TARGETS = Map.of(
            "small", List.of(16, 256, 1_000, 10_000),
            "late", List.of(128, 4_000, 100_000, 1_000_000),
            "ultra", List.of(512, 16_000, 1_000_000, 10_000_000));
    private static final Set<String> FIELDS = Set.of("schemaVersion", "id", "seed", "gridCount",
            "logicalPatterns", "orderUnits", "warmupSeconds", "sampleSeconds", "repetitions", "layouts",
            "equalAcrossLayouts", "perKeyResourceConservation");

    public static ScaleFactoryProfile load(String tier) {
        if (!TARGETS.containsKey(tier)) throw new IllegalArgumentException("Unknown scale tier: " + tier);
        var path = Path.of("..", "..", "tests", "benchmarks", "scale", tier + ".json");
        try (var reader = Files.newBufferedReader(path)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            if (!root.keySet().equals(FIELDS) || root.get("schemaVersion").getAsInt() != 1
                    || !root.get("id").getAsString().equals(tier)
                    || !root.get("perKeyResourceConservation").getAsBoolean()) {
                throw new IllegalArgumentException("Scale profile schema or identity mismatch: " + tier);
            }
            var layouts = root.getAsJsonArray("layouts").asList().stream().map(value -> value.getAsString()).toList();
            var equal = root.getAsJsonArray("equalAcrossLayouts").asList().stream()
                    .map(value -> value.getAsString()).toList();
            var profile = new ScaleFactoryProfile(tier, root.get("seed").getAsLong(),
                    root.get("gridCount").getAsInt(), root.get("logicalPatterns").getAsInt(),
                    root.get("orderUnits").getAsInt(), root.get("warmupSeconds").getAsInt(),
                    root.get("sampleSeconds").getAsInt(), root.get("repetitions").getAsInt(), layouts);
            var target = TARGETS.get(tier);
            if (profile.seed() <= 0 || profile.gridCount() != target.get(0)
                    || profile.logicalPatterns() != target.get(1) || profile.orderUnits() < target.get(2)
                    || profile.orderUnits() > target.get(3) || profile.warmupSeconds() != 300
                    || profile.sampleSeconds() != 600 || profile.repetitions() != 3
                    || !profile.layouts().equals(List.of("native-big-grid", "native-subnet", "federation"))
                    || !equal.equals(List.of("cpus", "energy", "loadedChunks", "inputReplay"))) {
                throw new IllegalArgumentException("Unsupported scale profile: " + tier);
            }
            return profile;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read scale profile " + path, exception);
        }
    }
}
