package space.controlnet.ae2federation.test.mixed;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Base64;
import java.util.Map;

public final class MixedFactoryRuntimeReceipt {
    private static Path path;
    private static long sequence;
    private static int iteration = -1;
    private static String phase = "benchmark";

    private MixedFactoryRuntimeReceipt() {
    }

    public static synchronized void begin(MixedFactoryProfile profile) {
        path = null;
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (configured.isBlank()) return;
        path = Path.of(configured).toAbsolutePath().normalize().resolveSibling("benchmark-runtime.receipts");
        sequence = 0;
        iteration = -1;
        phase = "benchmark";
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot initialize mixed runtime receipts", exception);
        }
        append("HEADER", profile.profileSha256(), Long.toString(profile.seed()));
    }

    public static synchronized void beginScene(int sceneIteration, boolean warmup) {
        iteration = sceneIteration;
        phase = warmup ? "warmup" : "measured";
        append("SCENE_BEGIN");
    }

    public static synchronized void terminal(String event, String... fields) {
        append("TERMINAL_" + event, fields);
    }

    public static synchronized void tracker(String event, String... fields) {
        append("TRACKER_" + event, fields);
    }

    public static synchronized void projection(Object owner, String operation, AEKey key, long requested, long accepted) {
        append("PROJECTION", identity(owner), operation, key.getType().getId() + "|" + key.getId(),
                Long.toString(requested), Long.toString(accepted));
    }

    public static synchronized void bus(String kind, Object owner, boolean worked) {
        append(kind + "_BUS", identity(owner), Boolean.toString(worked));
    }

    public static synchronized void processing(String operation, Object owner, String detail) {
        append("PROCESSING", operation, identity(owner), detail);
    }

    public static synchronized void machineAccepted(Object machine, Object inputOwner, AEKey input, long amount) {
        append("MACHINE_ACCEPTED", identity(machine), identity(inputOwner), input.getId().toString(),
                Long.toString(amount));
    }

    public static synchronized void machineTransition(Object machine, Object inputOwner, Object outputOwner,
            Object returnOwner, AEKey input, AEKey output, long amount) {
        append("MACHINE_TRANSITION", identity(machine), identity(inputOwner), identity(outputOwner),
                identity(returnOwner), input.getId().toString(), output.getId().toString(), Long.toString(amount));
    }

    public static synchronized void waiting(String jobId, long busyCpus, long blockedInput) {
        append("WAITING", jobId, Long.toString(busyCpus), Long.toString(blockedInput));
    }

    public static synchronized void cpuInFlight(String jobId, long busyCpus, long blockedInput, boolean active) {
        append("CPU_IN_FLIGHT", jobId, Long.toString(busyCpus), Long.toString(blockedInput),
                Boolean.toString(active));
    }

    public static synchronized void stocking(Object owner, long before, long moved, long after) {
        append("STOCKING", identity(owner), Long.toString(before), Long.toString(moved), Long.toString(after));
    }

    public static synchronized void callback(Object owner, Object link, AEKey key, long requested, Actionable mode,
            long accepted) {
        if (mode == Actionable.MODULATE) {
            append("CALLBACK", identity(owner), link.toString(), key.getId().toString(), Long.toString(requested),
                    Long.toString(accepted));
        }
    }

    public static synchronized void inventory(String boundary, Map<String, Long> amounts, long stocked, long exported) {
        amounts.forEach((key, amount) -> append("INVENTORY", boundary, key, Long.toString(amount)));
        append("DESTINATION", boundary, Long.toString(stocked), Long.toString(exported));
    }

    public static synchronized void endScene(long elapsedNanos) {
        append("SCENE_END", Long.toString(elapsedNanos));
        iteration = -1;
        phase = "benchmark";
    }

    private static void append(String event, String... fields) {
        if (path == null || (iteration < 0 && !event.equals("HEADER"))) return;
        var line = new StringBuilder().append(sequence++).append('|').append(iteration).append('|').append(phase)
                .append('|').append(event);
        for (var field : fields) {
            line.append('|').append(Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(field.getBytes(StandardCharsets.UTF_8)));
        }
        line.append('\n');
        try {
            Files.writeString(path, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot append mixed runtime receipt", exception);
        }
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }
}
