package space.controlnet.ae2federation.p2p;

import java.util.HashMap;
import java.util.Map;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;

/**
 * How much work Federation P2P tunnels do to keep their links published, for diagnostics and benchmarks: refresh
 * requests, the refresh tasks that ran, node publications and how many changed the registry, and the links each tunnel
 * published last. Counting only; nothing here changes what a tunnel does. Server thread only.
 */
public final class P2PRefreshDiagnostics {
    private static long requests;
    private static long tasks;
    private static long taskNanos;
    private static long publishes;
    private static long changes;
    private static final Map<FederationDomainNodeId, Integer> LINKS = new HashMap<>();

    private P2PRefreshDiagnostics() {
    }

    /**
     * @param requests refresh requests, one per tunnel event
     * @param tasks refresh tasks that ran
     * @param taskNanos the time those tasks took, in nanoseconds, registry updates included
     * @param publishes node publications
     * @param changes publications that changed the registry
     * @param links tunnel-to-tunnel links over every tunnel's last publication
     * @param maxLinks the most tunnel-to-tunnel links one tunnel published last
     */
    public record Snapshot(long requests, long tasks, long taskNanos, long publishes, long changes, long links,
            int maxLinks) {
    }

    public static Snapshot snapshot() {
        long links = 0;
        int maxLinks = 0;
        for (int count : LINKS.values()) {
            links += count;
            maxLinks = Math.max(maxLinks, count);
        }
        return new Snapshot(requests, tasks, taskNanos, publishes, changes, links, maxLinks);
    }

    /** Clears the counters; the links each tunnel published last are kept. */
    public static void reset() {
        requests = 0;
        tasks = 0;
        taskNanos = 0;
        publishes = 0;
        changes = 0;
    }

    static void requested() {
        requests++;
    }

    static void ran(long nanos) {
        tasks++;
        taskNanos += nanos;
    }

    static void published(FederationDomainNodeId node, int links, boolean changed) {
        publishes++;
        if (changed) changes++;
        LINKS.put(node, links);
    }

    static void removed(FederationDomainNodeId node) {
        LINKS.remove(node);
    }
}
