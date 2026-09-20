package space.controlnet.ae2federation.test.mixed;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;

public record MixedFactoryProfile(String profileVersion, long seed, int warmupIterations, int measuredIterations,
        int recipeChainLength, int alternativeInputs, int blockedLanes, int cpuLimit, int stockingCycles,
        int resourceKeyCount, int largeBatchCalls, int largeBatchUnits, int smallBatchCalls, int smallBatchUnits,
        int executionTimeoutSeconds, int shutdownGraceSeconds, Map<String, Long> initial,
        Map<String, Long> requested, String profileSha256) {
    private static final Set<String> FIELDS = Set.of("schemaVersion", "profileVersion", "id", "testId",
            "structure", "seed", "lifecycle", "warmupIterations", "measuredIterations", "recipeChainLength",
            "alternativeInputs", "blockedLanes", "cpuLimit", "stockingCycles", "resourceKeyCount",
            "largeBatchCalls", "largeBatchUnits", "smallBatchCalls", "smallBatchUnits", "variants", "initial",
            "requested");

    public static MixedFactoryProfile load() {
        var configured = System.getProperty("ae2federation.mixedBenchmarkProfile", "");
        if (configured.isBlank()) throw new IllegalStateException("Missing mixed benchmark profile path");
        return load(Path.of(configured));
    }

    public static MixedFactoryProfile load(Path configured) {
        var path = configured.toAbsolutePath().normalize();
        try (var reader = Files.newBufferedReader(path)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            require(root.keySet().equals(FIELDS), "Mixed benchmark profile fields are not exact");
            require(root.get("schemaVersion").getAsInt() == 1
                    && root.get("id").getAsString().equals("mixed-small")
                    && root.get("testId").getAsString().equals("mixedbenchmarksmall")
                    && root.get("structure").getAsString().equals("ae2federation_test:harness_native_smoke"),
                    "Mixed benchmark identity mismatch");
            var variants = root.getAsJsonArray("variants").asList().stream().map(value -> value.getAsString()).toList();
            require(variants.equals(List.of("large-batch-low-frequency", "small-batch-high-frequency")),
                    "Mixed benchmark variants mismatch");
            var lifecycle = root.getAsJsonObject("lifecycle");
            require(lifecycle.keySet().equals(Set.of("executionTimeoutSeconds", "shutdownGraceSeconds")),
                    "Mixed benchmark lifecycle fields are not exact");
            var profile = new MixedFactoryProfile(root.get("profileVersion").getAsString(), root.get("seed").getAsLong(),
                    integer(root, "warmupIterations"), integer(root, "measuredIterations"),
                    integer(root, "recipeChainLength"), integer(root, "alternativeInputs"),
                    integer(root, "blockedLanes"), integer(root, "cpuLimit"), integer(root, "stockingCycles"),
                    integer(root, "resourceKeyCount"), integer(root, "largeBatchCalls"),
                    integer(root, "largeBatchUnits"), integer(root, "smallBatchCalls"),
                    integer(root, "smallBatchUnits"), integer(lifecycle, "executionTimeoutSeconds"),
                    integer(lifecycle, "shutdownGraceSeconds"), quantities(root, "initial"),
                    quantities(root, "requested"), sha256(Files.readAllBytes(path)));
            profile.requireSupported();
            MixedFactoryTopology.from(profile);
            return profile;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read mixed benchmark profile " + path, exception);
        }
    }

    private void requireSupported() {
        require(profileVersion.equals("mixed-small-v1") && seed >= 0 && warmupIterations >= 0
                && warmupIterations <= 2 && measuredIterations >= 1 && measuredIterations <= 5
                && recipeChainLength >= 1 && recipeChainLength <= 6
                && alternativeInputs >= 1 && alternativeInputs <= 4 && blockedLanes >= 0 && blockedLanes <= 3
                && cpuLimit >= 1 && cpuLimit <= 4 && stockingCycles >= 1 && stockingCycles <= 5
                && resourceKeyCount >= 3 && resourceKeyCount <= 16
                && largeBatchCalls >= 1 && largeBatchCalls <= 32 && largeBatchUnits >= 1 && largeBatchUnits <= 64
                && smallBatchCalls >= 1 && smallBatchCalls <= 32 && smallBatchUnits >= 1 && smallBatchUnits <= 64
                && largeBatchCalls * largeBatchUnits == smallBatchCalls * smallBatchUnits
                && initial.keySet().equals(Set.of("diamonds", "cobblestone", "dirt", "redstone"))
                && requested.keySet().equals(Set.of("emeralds", "glass", "stockedDiamonds", "busDiamonds"))
                && executionTimeoutSeconds >= 30 && executionTimeoutSeconds <= 600
                && shutdownGraceSeconds >= 1 && shutdownGraceSeconds <= 30,
                "Unsupported mixed benchmark workload");
    }

    public int totalIterations() {
        return Math.addExact(warmupIterations, measuredIterations);
    }

    public long initial(String key) {
        return initial.get(key);
    }

    public long requested(String key) {
        return requested.get(key);
    }

    public int selectedAlternative(int iteration) {
        return Math.floorMod(seed + iteration, alternativeInputs);
    }

    public String verifyAxisVariations() {
        var canonical = MixedFactoryTopology.from(this);
        var chain = MixedFactoryTopology.from(withAxes(recipeChainLength - 1, alternativeInputs, blockedLanes,
                cpuLimit, resourceKeyCount));
        var alternatives = MixedFactoryTopology.from(withAxes(recipeChainLength, alternativeInputs - 1, blockedLanes,
                cpuLimit, resourceKeyCount));
        var blocked = MixedFactoryTopology.from(withAxes(recipeChainLength, alternativeInputs, blockedLanes - 1,
                cpuLimit, resourceKeyCount));
        var cpu = withAxes(recipeChainLength, alternativeInputs, blockedLanes, cpuLimit - 1, resourceKeyCount);
        var resources = MixedFactoryTopology.from(withAxes(recipeChainLength, alternativeInputs, blockedLanes,
                cpuLimit, resourceKeyCount + 1));
        require(chain.chainOutputs().size() != canonical.chainOutputs().size()
                && alternatives.alternatives().size() != canonical.alternatives().size()
                && blocked.blockedRecipes().size() != canonical.blockedRecipes().size()
                && cpu.cpuLimit() != cpuLimit && resources.resources().size() != canonical.resources().size(),
                "Mixed profile axis variation did not change constructed topology");
        return String.join("|", Integer.toString(chain.chainOutputs().size()),
                Integer.toString(alternatives.alternatives().size()), Integer.toString(blocked.blockedRecipes().size()),
                Integer.toString(cpu.cpuLimit()), Integer.toString(resources.resources().size()));
    }

    private MixedFactoryProfile withAxes(int chain, int alternatives, int blocked, int cpus, int resources) {
        var variation = new MixedFactoryProfile(profileVersion, seed, warmupIterations, measuredIterations, chain,
                alternatives, blocked, cpus, stockingCycles, resources, largeBatchCalls, largeBatchUnits,
                smallBatchCalls, smallBatchUnits, executionTimeoutSeconds, shutdownGraceSeconds, initial, requested,
                profileSha256);
        variation.requireSupported();
        return variation;
    }

    private static int integer(com.google.gson.JsonObject root, String name) {
        return root.get(name).getAsInt();
    }

    private static Map<String, Long> quantities(com.google.gson.JsonObject root, String name) {
        var object = root.getAsJsonObject(name);
        var quantities = new TreeMap<String, Long>();
        object.entrySet().forEach(entry -> {
            var amount = entry.getValue().getAsLong();
            require(amount > 0, "Mixed benchmark quantities must be positive");
            quantities.put(entry.getKey(), amount);
        });
        return Map.copyOf(quantities);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalArgumentException(message);
    }
}
