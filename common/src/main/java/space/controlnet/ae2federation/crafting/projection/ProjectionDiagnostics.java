package space.controlnet.ae2federation.crafting.projection;

/** Server-thread operation counters; no decisions, timing thresholds, or cache lifetimes depend on them. */
public final class ProjectionDiagnostics {
    private static long discoveries;
    private static long nodes;
    private static long globals;
    private static long discoveryNanos;
    private static long mountViews;
    private static long mounts;
    private static long viewNanos;
    private static long relationships;

    private ProjectionDiagnostics() {}
    public record Snapshot(long discoveries, long nodes, long globals, long discoveryNanos,
            long mountViews, long mounts, long viewNanos, long relationships) {}
    public static Snapshot snapshot() {
        return new Snapshot(discoveries, nodes, globals, discoveryNanos, mountViews, mounts, viewNanos, relationships);
    }
    public static void reset() {
        discoveries = nodes = globals = discoveryNanos = mountViews = mounts = viewNanos = relationships = 0;
    }
    static void discovered(long visitedNodes, long visitedGlobals, long nanos) {
        discoveries++;
        nodes += visitedNodes;
        globals += visitedGlobals;
        discoveryNanos += nanos;
    }
    static void viewBuilt(long count, long nanos) {
        mountViews++;
        mounts += count;
        viewNanos += nanos;
    }
    static void relationship() { relationships++; }
}
