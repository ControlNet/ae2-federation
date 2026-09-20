package space.controlnet.ae2federation.test.automation;

import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.stacks.AEKey;
import java.util.Set;
import java.util.TreeSet;

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
    }

    public static synchronized void trackerResult(ICraftingRequester owner, boolean submitted) {
        if (active == null) return;
        if (submitted) active.trackerSubmissions++;
        owner.getRequestedJobs().forEach(link -> active.jobIds.add(link.getCraftingID().toString()));
    }

    public static synchronized void importBus(Object bus, boolean worked) {
        if (active == null) return;
        active.importBus = identity(bus);
        if (worked) active.importWork++;
    }

    public static synchronized void exportBus(Object bus, boolean worked) {
        if (active == null) return;
        active.exportBus = identity(bus);
        if (worked) active.exportWork++;
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
            long maxRequested, Set<String> keys) {
        public String joinedJobs() {
            return jobIds.isEmpty() ? "none" : String.join(",", jobIds);
        }

        public String joinedKeys() {
            return keys.isEmpty() ? "none" : String.join(",", keys);
        }
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

        private Mutable(String testId) {
            this.testId = testId;
        }

        private Snapshot snapshot() {
            return new Snapshot(testId, trackerOwner, trackerCalls, trackerSubmissions, Set.copyOf(jobIds), importBus,
                    exportBus, importWork, exportWork, joined(projections), storageInsertCalls, storageExtractCalls,
                    storageInserted, storageExtracted, maxRequested, Set.copyOf(keys));
        }

        private static String joined(Set<String> values) {
            return values.isEmpty() ? "none" : String.join(",", values);
        }
    }
}
