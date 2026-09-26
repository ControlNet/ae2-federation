package space.controlnet.ae2federation.test.automation;

import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.stacks.AEKey;
import java.util.Set;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.List;
import space.controlnet.ae2federation.test.mixed.MixedFactoryRuntimeReceipt;

public final class AutomationNativeObservation {
    private static Mutable active;

    private AutomationNativeObservation() {
    }

    public static synchronized void begin(String testId) {
        active = new Mutable(testId);
    }

    public static synchronized void trackerCall(ICraftingRequester owner) {
        if (active == null) return;
        if (!AutomationAuthorityObservation.acceptsInterfaceOwner(owner)) {
            throw new IllegalStateException("Native tracker owner lacks pre-operation authority");
        }
        active.trackerOwner = identity(owner);
        active.trackerCalls++;
        MixedFactoryRuntimeReceipt.tracker("CALL", identity(owner));
    }

    public static synchronized void trackerResult(ICraftingRequester owner, boolean submitted) {
        if (active == null) return;
        if (submitted) active.trackerSubmissions++;
        owner.getRequestedJobs().forEach(link -> active.jobIds.add(link.getCraftingID().toString()));
        MixedFactoryRuntimeReceipt.tracker("RESULT", identity(owner), Boolean.toString(submitted),
                owner.getRequestedJobs().stream().map(link -> link.getCraftingID().toString()).sorted()
                        .reduce((left, right) -> left + "," + right).orElse(""));
    }

    public static synchronized void importBus(Object bus, boolean worked) {
        if (active == null) return;
        active.importBus = identity(bus);
        if (worked) active.importWork++;
        MixedFactoryRuntimeReceipt.bus("IMPORT", bus, worked);
    }

    public static synchronized void exportBus(Object bus, boolean worked) {
        if (active == null) return;
        active.exportBus = identity(bus);
        if (worked) active.exportWork++;
        MixedFactoryRuntimeReceipt.bus("EXPORT", bus, worked);
    }

    public static synchronized void projection(Object projection, String operation, AEKey key, long requested,
            long accepted) {
        if (active == null) return;
        if (!AutomationAuthorityObservation.acceptsProjectionOperation(projection, operation, key)) {
            throw new IllegalStateException("Native projection operation lacks pre-operation authority: " + operation
                    + ":" + key.getType().getId() + "|" + key.getId() + " projection=" + identity(projection));
        }
        active.projections.add(identity(projection));
        active.keys.add(key.getType().getId() + "|" + key.getId());
        if (operation.equals("insert")) {
            active.storageInsertCalls++;
            active.storageInserted += accepted;
        } else {
            active.storageExtractCalls++;
            active.storageExtracted += accepted;
        }
        active.maxRequested = Math.max(active.maxRequested, requested);
        active.operations.add(new ProjectionReceipt(identity(projection), operation,
                key.getType().getId() + "|" + key.getId(), requested, accepted));
        MixedFactoryRuntimeReceipt.projection(projection, operation, key, requested, accepted);
    }

    public static synchronized Snapshot snapshot() {
        if (active == null) throw new IllegalStateException("No native automation observation is active");
        return active.snapshot();
    }

    public static synchronized void close() {
        active = null;
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    public record Snapshot(String testId, String trackerOwner, int trackerCalls, int trackerSubmissions,
            Set<String> jobIds, String importBus, String exportBus, int importWork, int exportWork, String projection,
            int storageInsertCalls, int storageExtractCalls, long storageInserted, long storageExtracted,
            long maxRequested, Set<String> keys, List<ProjectionReceipt> operations) {
        public String joinedJobs() {
            return jobIds.isEmpty() ? "none" : String.join(",", jobIds);
        }

        public String joinedKeys() {
            return keys.isEmpty() ? "none" : String.join(",", keys);
        }
    }

    public record ProjectionReceipt(String owner, String operation, String key, long requested, long accepted) {
    }

    private static final class Mutable {
        private final String testId;
        private String trackerOwner = "none";
        private int trackerCalls;
        private int trackerSubmissions;
        private final Set<String> jobIds = new TreeSet<>();
        private String importBus = "none";
        private String exportBus = "none";
        private int importWork;
        private int exportWork;
        private final Set<String> projections = new TreeSet<>();
        private int storageInsertCalls;
        private int storageExtractCalls;
        private long storageInserted;
        private long storageExtracted;
        private long maxRequested;
        private final Set<String> keys = new TreeSet<>();
        private final List<ProjectionReceipt> operations = new ArrayList<>();

        private Mutable(String testId) {
            this.testId = testId;
        }

        private Snapshot snapshot() {
            return new Snapshot(testId, trackerOwner, trackerCalls, trackerSubmissions, Set.copyOf(jobIds), importBus,
                    exportBus, importWork, exportWork, joined(projections), storageInsertCalls, storageExtractCalls,
                    storageInserted, storageExtracted, maxRequested, Set.copyOf(keys), List.copyOf(operations));
        }

        private static String joined(Set<String> values) {
            return values.isEmpty() ? "none" : String.join(",", values);
        }
    }
}
