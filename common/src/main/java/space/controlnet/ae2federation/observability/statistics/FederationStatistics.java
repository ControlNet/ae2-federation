package space.controlnet.ae2federation.observability.statistics;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import appeng.me.service.EnergyService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.LongSupplier;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.energy.EnergyPoolReading;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityRegistry;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomainRegistry;
import space.controlnet.ae2federation.storage.provenance.ProvenanceException;

/**
 * Keeps the Federation's statistics for the domain screen's statistics page: one per server, ticked every server
 * tick, writing its {@link StatisticsBook} once per wall-clock second. Wall-clock seconds, so time the server was
 * stopped or paused shows as a gap; a second without a tick (a stall) is simply not observed.
 *
 * <p>What it costs. Storage operations, pattern pushes and crafting returns each add their amount to a
 * {@link PairMeter} (one hash map add); the meters are drained once a second. Each second it reads the gauges of
 * every loaded member network (field reads, a small CPU set, and each energy pool walked once). A network's own
 * storage is enumerated once every {@link #STORAGE_PERIOD_SECONDS} seconds, networks spread over those seconds, from
 * its native sources only: never {@code getCachedInventory}, which re-enumerates the mounted storage of other
 * networks too when no terminal is open.
 */
public final class FederationStatistics {
    /** Seconds between two enumerations of one network's own storage. */
    public static final int STORAGE_PERIOD_SECONDS = 10;
    /** A storage reading older than this is not recorded again: the network's storage could not be read since. */
    private static final int STORAGE_STALE_SECONDS = 3 * STORAGE_PERIOD_SECONDS;
    private static final int PRUNE_PERIOD_SECONDS = 60;

    /** One direction between two networks. */
    public record Route(NetworkId from, NetworkId to) {
    }

    /** What moved along one route since the last second, by key; added to on the storage hot path. */
    public static final class PairMeter {
        private final Route route;
        private final Map<AEKey, long[]> moved = new HashMap<>();

        private PairMeter(Route route) {
            this.route = route;
        }

        /** Records {@code amount} of {@code key} actually accepted on this route; anything not positive is ignored. */
        public void add(AEKey key, long amount) {
            if (amount <= 0) return;
            moved.computeIfAbsent(key, ignored -> new long[1])[0] += amount;
        }
    }

    private record StoredReading(long second, Map<String, Double> amounts) {
    }

    private final ServerLevel level;
    private final LongSupplier millis;
    private final StatisticsBook<AEKey> book;
    private final java.util.function.LongConsumer changed;
    private final Map<Route, PairMeter> meters = new HashMap<>();
    private final Map<String, long[]> counts = new HashMap<>();
    private final Map<NetworkId, StoredReading> stored = new HashMap<>();
    /** Our own index of native sources: reading storage must not change the one storage mounts rely on. */
    private final NativeSourceDomainRegistry provenance = new NativeSourceDomainRegistry();
    private long lastSecond = Long.MIN_VALUE;
    private long finishedSecond = Long.MIN_VALUE;

    /**
     * @param level   the overworld; the registries it reaches are the server's
     * @param book    the book to write, usually the saved one
     * @param changed called with each second once it is recorded, so the saved book is written with the world
     */
    public FederationStatistics(ServerLevel level, LongSupplier millis, StatisticsBook<AEKey> book,
            java.util.function.LongConsumer changed) {
        this.level = level;
        this.millis = millis;
        this.book = book;
        this.changed = changed;
    }

    public StatisticsBook<AEKey> book() {
        return book;
    }

    /** The wall-clock second now. */
    public long nowSecond() {
        return Math.floorDiv(millis.getAsLong(), 1000L);
    }

    /**
     * The meter of what moves from {@code from} to {@code to}; the same object for as long as the server runs, so a
     * storage mount may keep it. Meters are never dropped: there is one per pair of networks that moved anything.
     */
    public PairMeter meter(NetworkId from, NetworkId to) {
        return meters.computeIfAbsent(new Route(from, to), PairMeter::new);
    }

    /** Counts one pattern push a CPU of {@code requester} sent to a provider of {@code executor}. */
    public void pushed(NetworkId requester, NetworkId executor) {
        count(StatisticsNames.pair(StatisticsNames.PUSHED, requester, executor));
    }

    /** Counts one ended job of {@code requester} that pushed patterns to another network. */
    public void jobEnded(NetworkId requester, boolean done) {
        count(StatisticsNames.network(done ? StatisticsNames.DONE : StatisticsNames.CANCELLED, requester));
    }

    private void count(String name) {
        counts.computeIfAbsent(name, ignored -> new long[1])[0]++;
    }

    /** The newest second written into the book, or the second before now while none is. */
    public long finishedSecond() {
        return finishedSecond == Long.MIN_VALUE ? nowSecond() - 1 : finishedSecond;
    }

    /** Called every server tick: finishes the previous second once the wall clock has passed it. */
    public void tick() {
        long now = nowSecond();
        if (lastSecond == Long.MIN_VALUE) {
            lastSecond = now;
            return;
        }
        if (now == lastSecond) return;
        finishSecond(lastSecond);
        lastSecond = now;
    }

    /**
     * Writes {@code second} into the book: marks it observed, drains the meters into it and samples every loaded
     * member network. {@link #tick()} calls it once per wall-clock second; tests call it with a second of their own.
     */
    public void finishSecond(long second) {
        finishedSecond = Math.max(finishedSecond, second);
        book.observe(second);
        drain(second);
        sample(second);
        if (Math.floorMod(second, PRUNE_PERIOD_SECONDS) == 0) {
            book.prune(second);
            stored.keySet().retainAll(members());
        }
        changed.accept(second);
    }

    private void drain(long second) {
        for (var meter : meters.values()) {
            if (meter.moved.isEmpty()) continue;
            var byType = new HashMap<AEKeyType, double[]>();
            var ranking = StatisticsNames.pair(StatisticsNames.MOVED, meter.route.from(), meter.route.to());
            meter.moved.forEach((key, amount) -> {
                byType.computeIfAbsent(key.getType(), ignored -> new double[1])[0] += amount[0];
                book.moved(ranking, second, key, amount[0]);
            });
            byType.forEach((type, amount) -> book.count(
                    StatisticsNames.moved(meter.route.from(), meter.route.to(), type.getId().toString()), second, amount[0]));
            meter.moved.clear();
        }
        counts.forEach((name, amount) -> book.count(name, second, amount[0]));
        counts.clear();
    }

    private java.util.Set<NetworkId> members() {
        var members = new HashSet<NetworkId>();
        for (var domain : FederationDomainRegistryAccess.get(level).federationDomains()) {
            members.addAll(domain.memberships().keySet());
        }
        return members;
    }

    private void sample(long second) {
        var members = members();
        if (members.isEmpty()) return;
        var grids = new HashMap<NetworkId, IGrid>();
        for (var grid : NetworkIdentityRegistry.get(level).liveClaims().keySet()) {
            FederationDomainRegistryAccess.confirmedNetworkId(grid).filter(members::contains)
                    .ifPresent(network -> grids.put(network, grid));
        }
        var pools = new IdentityHashMap<EnergyService, EnergyPoolReading>();
        grids.forEach((network, grid) -> {
            var energy = grid.getEnergyService();
            double max = energy.getMaxStoredPower();
            if (max > 0) book.gauge(StatisticsNames.network(StatisticsNames.POWER, network), second,
                    100 * energy.getStoredPower() / max);
            var pool = energy instanceof EnergyService service ? pools.get(service) : null;
            if (pool == null) {
                pool = EnergyPoolReading.measure(energy);
                if (pool != null) for (var member : pool.services()) pools.put(member, pool);
            }
            if (pool != null && pool.grids() > 1 && pool.max() > 0) {
                book.gauge(StatisticsNames.network(StatisticsNames.POOL, network), second, 100 * pool.stored() / pool.max());
            }
            var cpus = grid.getCraftingService().getCpus();
            book.gauge(StatisticsNames.network(StatisticsNames.CPUS, network), second, cpus.size());
            book.gauge(StatisticsNames.network(StatisticsNames.BUSY_CPUS, network), second,
                    cpus.stream().filter(cpu -> cpu.isBusy()).count());
            book.gauge(StatisticsNames.network(StatisticsNames.CHANNELS, network), second,
                    grid.getPathingService().getUsedChannels());
            sampleStorage(second, network, grid);
        });
    }

    private void sampleStorage(long second, NetworkId network, IGrid grid) {
        var reading = stored.get(network);
        boolean due = reading == null
                || Math.floorMod(second + network.value().hashCode(), STORAGE_PERIOD_SECONDS) == 0;
        if (due) {
            var amounts = nativeStock(grid);
            if (amounts != null) {
                // A type the network held before and holds no more reads as 0, not as missing.
                if (reading != null) reading.amounts().keySet().forEach(type -> amounts.putIfAbsent(type, 0d));
                reading = new StoredReading(second, amounts);
                stored.put(network, reading);
            }
        }
        if (reading == null || second - reading.second() > STORAGE_STALE_SECONDS) return;
        reading.amounts().forEach((type, amount) -> book.gauge(StatisticsNames.stored(network, type), second, amount));
    }

    /** What the network's own storage holds by resource type, or {@code null} when it cannot be read now. */
    private Map<String, Double> nativeStock(IGrid grid) {
        try {
            var domain = provenance.discover(grid);
            var counter = new KeyCounter();
            for (var source : domain.sources()) source.storage().getAvailableStacks(counter);
            var amounts = new HashMap<String, Double>();
            for (var entry : counter) {
                amounts.merge(entry.getKey().getType().getId().toString(), (double) entry.getLongValue(), Double::sum);
            }
            return amounts;
        } catch (ProvenanceException unreadable) {
            return null;
        }
    }
}
