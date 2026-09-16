package space.controlnet.ae2federation.test.processing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Set;

public record ProcessingBenchmarkProfile(String profileVersion, long seed, int gridCount, int physicalPatterns,
        int logicalPatterns, int logicalLanes, int routes, int providerEntries, int targetRelationships, int cpuLimit,
        int startingResourceUnits, int plannedOutputUnits, int batchUnits, int primaryOutputUnits,
        int byproductOutputUnits, int warmupRejectedAttempts, int measurementSamples, int rejectedAttemptsPerScene,
        int largeBatchCalls, int largeBatchUnits, int smallBatchCalls, int smallBatchUnits,
        int executionTimeoutSeconds, int shutdownGraceSeconds, String profileSha256) {
    private static final Set<String> FIELDS = Set.of("schemaVersion", "profileVersion", "id", "testId",
            "structure", "seed", "comparison", "equalResources", "inputResource", "primaryOutputResource",
            "byproductOutputResource", "gridCount", "physicalPatterns", "logicalPatterns", "logicalLanes", "routes",
            "providerEntries", "targetRelationships", "cpuLimit", "startingResourceUnits", "plannedOutputUnits",
            "batchUnits", "primaryOutputUnits", "byproductOutputUnits", "warmupRejectedAttempts",
            "measurementSamples", "rejectedAttemptsPerScene", "largeBatchCalls", "largeBatchUnits",
            "smallBatchCalls", "smallBatchUnits", "replayVariants", "endpointStates", "lifecycle");

    public static ProcessingBenchmarkProfile load() {
        var configured = System.getProperty("ae2federation.processingBenchmarkProfile", "");
        if (configured.isBlank()) {
            throw new IllegalStateException("Missing Processing benchmark profile path");
        }
        var path = Path.of(configured).toAbsolutePath().normalize();
        try (var reader = Files.newBufferedReader(path)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            require(root.keySet().equals(FIELDS), "Processing benchmark profile fields are not exact");
            require(integer(root, "schemaVersion") == 2, "Unsupported Processing benchmark schema");
            require(text(root, "id").equals("processing-small"), "Processing benchmark profile ID mismatch");
            require(text(root, "testId").equals("processingbenchmarksmall"), "Processing benchmark test ID mismatch");
            require(text(root, "structure").equals("ae2federation_test:harness_native_smoke"),
                    "Processing benchmark structure mismatch");
            require(text(root, "comparison").equals("native-single-grid-vs-federation-multi-provider"),
                    "Processing benchmark comparison mismatch");
            require(root.get("equalResources").getAsBoolean(), "Processing benchmark resources must be equal");
            require(text(root, "inputResource").equals("minecraft:cobblestone")
                    && text(root, "primaryOutputResource").equals("minecraft:diamond")
                    && text(root, "byproductOutputResource").equals("minecraft:gold_ingot"),
                    "Processing benchmark resource keys mismatch");
            require(strings(root, "replayVariants").equals(java.util.List.of(
                    "large-batch-low-frequency", "small-batch-high-frequency")),
                    "Processing benchmark T-S04 variants mismatch");
            require(strings(root, "endpointStates").equals(java.util.List.of(
                    "busy", "result-locked", "rejecting", "return-congested", "eligible")),
                    "Processing benchmark T-S06 states mismatch");
            require(root.get("lifecycle") != null && root.get("lifecycle").isJsonObject(),
                    "Processing benchmark lifecycle is malformed");
            var lifecycle = root.getAsJsonObject("lifecycle");
            require(lifecycle.keySet().equals(Set.of("executionTimeoutSeconds", "shutdownGraceSeconds")),
                    "Processing benchmark lifecycle fields are not exact");
            var executionTimeoutSeconds = positiveInteger(lifecycle, "executionTimeoutSeconds");
            var shutdownGraceSeconds = positiveInteger(lifecycle, "shutdownGraceSeconds");
            require(executionTimeoutSeconds <= 604_800 && shutdownGraceSeconds <= 30,
                    "Processing benchmark lifecycle is unreasonably large");
            var canonicalSeed = root.get("seed").getAsLong();
            var configuredSeed = System.getProperty("ae2federation.processingBenchmarkSeed", "");
            var runtimeSeed = configuredSeed.isBlank() ? canonicalSeed : Long.parseLong(configuredSeed);
            var profile = new ProcessingBenchmarkProfile(text(root, "profileVersion"), runtimeSeed,
                    integer(root, "gridCount"), integer(root, "physicalPatterns"), integer(root, "logicalPatterns"),
                    integer(root, "logicalLanes"), integer(root, "routes"), integer(root, "providerEntries"),
                    integer(root, "targetRelationships"), integer(root, "cpuLimit"),
                    integer(root, "startingResourceUnits"), integer(root, "plannedOutputUnits"),
                    integer(root, "batchUnits"), integer(root, "primaryOutputUnits"),
                    integer(root, "byproductOutputUnits"), integer(root, "warmupRejectedAttempts"),
                    integer(root, "measurementSamples"), integer(root, "rejectedAttemptsPerScene"),
                    integer(root, "largeBatchCalls"), integer(root, "largeBatchUnits"),
                    integer(root, "smallBatchCalls"), integer(root, "smallBatchUnits"),
                    executionTimeoutSeconds, shutdownGraceSeconds,
                    sha256(Files.readAllBytes(path)));
            profile.requireSupportedShape();
            return profile;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Processing benchmark profile " + path, exception);
        }
    }

    public int acceptedOperationsPerScene() {
        return measurementSamples;
    }

    public int measuredInputCallsPerScene() {
        return measurementSamples + rejectedAttemptsPerScene;
    }

    public ProcessingBenchmarkProfile largeVariant() {
        return workload(largeBatchCalls, largeBatchUnits, 64, 32);
    }

    public ProcessingBenchmarkProfile smallVariant() {
        return workload(smallBatchCalls, smallBatchUnits, 4, 2);
    }

    private ProcessingBenchmarkProfile workload(int calls, int units, int primary, int byproduct) {
        return new ProcessingBenchmarkProfile(profileVersion, seed, gridCount, physicalPatterns, logicalPatterns,
                logicalLanes, routes, providerEntries, targetRelationships, cpuLimit, startingResourceUnits,
                plannedOutputUnits, units, primary, byproduct, warmupRejectedAttempts, calls,
                rejectedAttemptsPerScene, largeBatchCalls, largeBatchUnits, smallBatchCalls, smallBatchUnits,
                executionTimeoutSeconds, shutdownGraceSeconds, profileSha256);
    }

    private void requireSupportedShape() {
        require(profileVersion.equals("processing-small-v3") && seed > 0,
                "Processing benchmark version or seed mismatch");
        require(gridCount == 16 && physicalPatterns == 256 && logicalPatterns == 256 && logicalLanes == 15
                && routes == 270 && providerEntries == 270 && targetRelationships == 15 && cpuLimit == 1,
                "Unsupported Processing benchmark topology");
        require(startingResourceUnits >= 1_000 && startingResourceUnits <= 10_000
                && plannedOutputUnits == 960 && batchUnits == 256 && primaryOutputUnits == 64
                && byproductOutputUnits == 32 && warmupRejectedAttempts == 1 && measurementSamples == 15
                && rejectedAttemptsPerScene == 1 && largeBatchCalls == 15 && largeBatchUnits == 256
                && smallBatchCalls == 240 && smallBatchUnits == 16
                && executionTimeoutSeconds == 300 && shutdownGraceSeconds == 10
                && largeBatchCalls * largeBatchUnits == smallBatchCalls * smallBatchUnits,
                "Unsupported Processing benchmark workload");
    }

    private static int integer(JsonObject root, String name) {
        return root.get(name).getAsInt();
    }

    private static int positiveInteger(JsonObject root, String name) {
        var value = root.get(name);
        require(value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),
                "Processing benchmark lifecycle value is malformed: " + name);
        try {
            var number = value.getAsBigDecimal().intValueExact();
            require(number > 0, "Processing benchmark lifecycle value is not positive: " + name);
            return number;
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Processing benchmark lifecycle value is malformed: " + name, exception);
        }
    }

    private static String text(JsonObject root, String name) {
        return root.get(name).getAsString();
    }

    private static java.util.List<String> strings(JsonObject root, String name) {
        var values = new java.util.ArrayList<String>();
        root.getAsJsonArray(name).forEach(value -> values.add(value.getAsString()));
        return java.util.List.copyOf(values);
    }

    private static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
