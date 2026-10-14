package space.controlnet.ae2federation.observability.statistics;

import space.controlnet.ae2federation.identity.NetworkId;

/**
 * The names of the series a {@link StatisticsBook} keeps for the Federation, so the sampler that writes them and the
 * page that reads them agree. Networks are named by their UUID, resource types by their AE2 key type id.
 */
public final class StatisticsNames {
    /** Gauge: a network's own stored ME power, as a percentage of its own capacity. */
    public static final String POWER = "power";
    /** Gauge: the stored ME power of the energy pool the network shares, as a percentage. */
    public static final String POOL = "pool";
    /** Gauges: crafting CPUs, busy crafting CPUs, channels in use. */
    public static final String CPUS = "cpus";
    public static final String BUSY_CPUS = "busy";
    public static final String CHANNELS = "channels";
    /** Gauge per resource type: what the network's own storage holds. */
    public static final String STORED = "stored";
    /** Counter per pair and resource type, and the ranking per pair: what moved from one network to the other. */
    public static final String MOVED = "moved";
    /** Counter per pair: pattern pushes one network's CPUs sent to another network's provider. */
    public static final String PUSHED = "pushed";
    /** Counters per requesting network: its jobs that used another network's patterns and finished or were cancelled. */
    public static final String DONE = "done";
    public static final String CANCELLED = "cancelled";

    private StatisticsNames() {
    }

    public static String network(String series, NetworkId network) {
        return series + "/" + network.value();
    }

    public static String stored(NetworkId network, String keyType) {
        return STORED + "/" + network.value() + "/" + keyType;
    }

    public static String pair(String series, NetworkId from, NetworkId to) {
        return series + "/" + from.value() + "/" + to.value();
    }

    public static String moved(NetworkId from, NetworkId to, String keyType) {
        return pair(MOVED, from, to) + "/" + keyType;
    }
}
