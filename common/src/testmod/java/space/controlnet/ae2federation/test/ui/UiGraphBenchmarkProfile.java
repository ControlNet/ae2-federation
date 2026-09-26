package space.controlnet.ae2federation.test.ui;

import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Set;

public record UiGraphBenchmarkProfile(String version, long seed, int members, int providers, int endpoints,
        int physicalEdges, int capabilityEdges, int patterns, int samples, int closedGuiTicks, String sha256) {
    private static final Set<String> FIELDS = Set.of("schemaVersion", "profileVersion", "id", "testId",
            "structure", "seed", "members", "providers", "endpoints", "physicalEdges", "capabilityEdges",
            "patterns", "samples", "closedGuiTicks", "lifecycle");

    public static UiGraphBenchmarkProfile load() {
        var configured = System.getProperty("ae2federation.uiBenchmarkProfile", "");
        if (configured.isBlank()) {
            throw new IllegalStateException("Missing UI graph benchmark profile path");
        }
        var path = Path.of(configured).toAbsolutePath().normalize();
        try (var reader = Files.newBufferedReader(path)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            require(root.keySet().equals(FIELDS), "UI graph benchmark profile fields are not exact");
            require(root.get("schemaVersion").getAsInt() == 1
                    && root.get("id").getAsString().equals("ui-small")
                    && root.get("testId").getAsString().equals("uigraphbenchmarksmall")
                    && root.get("structure").getAsString().equals("ae2federation_test:harness_native_smoke"),
                    "UI graph benchmark identity mismatch");
            var lifecycle = root.getAsJsonObject("lifecycle");
            require(lifecycle.keySet().equals(Set.of("executionTimeoutSeconds", "shutdownGraceSeconds"))
                    && lifecycle.get("executionTimeoutSeconds").getAsInt() == 180
                    && lifecycle.get("shutdownGraceSeconds").getAsInt() == 10,
                    "UI graph benchmark lifecycle mismatch");
            var profile = new UiGraphBenchmarkProfile(root.get("profileVersion").getAsString(),
                    root.get("seed").getAsLong(), root.get("members").getAsInt(),
                    root.get("providers").getAsInt(), root.get("endpoints").getAsInt(),
                    root.get("physicalEdges").getAsInt(), root.get("capabilityEdges").getAsInt(),
                    root.get("patterns").getAsInt(), root.get("samples").getAsInt(),
                    root.get("closedGuiTicks").getAsInt(), digest(Files.readAllBytes(path)));
            require(profile.version.equals("ui-small-v1") && profile.seed == 340034
                    && profile.members == 24 && profile.providers == 8 && profile.endpoints == 8
                    && profile.physicalEdges == 16 && profile.capabilityEdges == 8
                    && profile.patterns == 64 && profile.samples == 32 && profile.closedGuiTicks == 20,
                    "Unsupported UI graph benchmark workload");
            return profile;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read UI graph benchmark profile " + path, exception);
        }
    }

    private static String digest(byte[] bytes) {
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
