package space.controlnet.ae2federation.observability.statistics;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.me.service.EnergyService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
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
 * stopped or paused shows as a gap; seconds a long tick ran through are observed, with no gauge sampled in them
 * ({@link SecondClock}).
 *
 * <p>What it costs. Storage operations, pattern pushes and crafting returns each add their amount to a
 * {@link PairMeter} (one hash map add); the meters are drained once a second. Each second it reads the gauges of
 * every loaded member network (field reads, a small CPU set, and each energy pool walked once). A network's own
 * storage is enumerated once every {@link #STORAGE_PERIOD_SECONDS} seconds, networks spread over those seconds, from
 * its native sources only: never {@code getCachedInventory}, which re-enumerates the mounted storage of other
 * networks too when no terminal is open. That reading also keeps the amounts of the keys each network holds most of,
 * and of the keys players pinned ({@link #TRACKED_TOP}, {@link #TRACKED_CAP}). Every tick it looks at the busy
 * crafting CPUs of the member networks, to count the jobs they start.
 */
public final class FederationStatistics {
    /** Seconds between two enumerations of one network's own storage. */
    public static final int STORAGE_PERIOD_SECONDS = 10;
    /** A storage reading older than this is not recorded again: the network's storage could not be read since. */
    private static final int STORAGE_STALE_SECONDS = 3 * STORAGE_PERIOD_SECONDS;
    private static final int PRUNE_PERIOD_SECONDS = 60;
    /** The keys of each resource type a network holds most of, whose amounts are kept. */
    public static final int TRACKED_TOP = 16;
    /**
     * The most keys of one resource type whose amounts are kept per network: the top ones, the pinned ones and, while
     * there is room, keys that were tracked before and still are held, so a key slipping out of the top keeps its line.
     */
    public static final int TRACKED_CAP = 48;

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

    /** One reading of a network's own storage: totals by resource type, and the tracked keys' amounts. */
    private record StoredReading(long second, Map<String, Double> amounts, Map<AEKey, Long> tracked) {
    }

    /** What a crafting CPU was doing at the last tick, so a new job is counted once. */
    private record JobSeen(GenericStack output, long total, long elapsed) {
    }

    /** A crafting CPU of a member network now: {@code output} is {@code null} while it is idle. */
    public record CpuReading(NetworkId network, String name, int coProcessors, long storage,
            @org.jetbrains.annotations.Nullable GenericStack output, long total, long progress, long elapsedNanos) {
    }

    private final ServerLevel level;
    private final LongSupplier millis;
    private final StatisticsBook<AEKey> book;
    private final java.util.function.LongConsumer changed;
    private final Map<Route, PairMeter> meters = new HashMap<>();
    private final Map<String, long[]> counts = new HashMap<>();
    private final Map<NetworkId, StoredReading> stored = new HashMap<>();
    /** The loaded member networks' Grids as of the last finished second. */
    private Map<NetworkId, IGrid> grids = Map.of();
    private final Map<ICraftingCPU, JobSeen> jobs = new IdentityHashMap<>();
    /** Our own index of native sources: reading storage must not change the one storage mounts rely on. */
    private final NativeSourceDomainRegistry provenance = new NativeSourceDomainRegistry();
    private final SecondClock clock = new SecondClock();
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

    /** Called as each server tick starts, so a pause between ticks is told apart from a long tick. */
    public void tickStarted() {
        clock.started(millis.getAsLong());
    }

    /**
     * Called as each server tick ends: counts the crafting jobs started this tick, and finishes the previous second
     * once the wall clock has passed it.
     */
    public void tick() {
        pollJobs();
        var step = clock.ended(millis.getAsLong());
        if (step == null) return;
        finishSecond(step.finish());
        for (long second = step.observeFrom(); second <= step.observeTo(); second++) book.observe(second);
    }

    /** Counts each job a member network's crafting CPU started since the last tick. */
    private void pollJobs() {
        grids.forEach((network, grid) -> {
            if (grid.isEmpty()) return;
            for (var cpu : grid.getCraftingService().getCpus()) {
                var status = cpu.isBusy() ? cpu.getJobStatus() : null;
                if (status == null) {
                    jobs.remove(cpu);
                    continue;
                }
                var seen = jobs.get(cpu);
                // AE2 keeps one output stack per job; another CPU may make a new one each time, so its figures decide.
                boolean same = seen != null && (seen.output() == status.crafting()
                        || seen.output().equals(status.crafting()) && seen.total() == status.totalItems()
                                && status.elapsedTimeNanos() >= seen.elapsed());
                if (!same) count(StatisticsNames.network(StatisticsNames.STARTED, network));
                jobs.put(cpu, new JobSeen(status.crafting(), status.totalItems(), status.elapsedTimeNanos()));
            }
        });
    }

    /** Every crafting CPU of {@code network} now, busy or not; none while the network is not loaded. */
    public List<CpuReading> cpus(NetworkId network) {
        var grid = grids.get(network);
        if (grid == null || grid.isEmpty()) return List.of();
        var out = new ArrayList<CpuReading>();
        for (var cpu : grid.getCraftingService().getCpus()) {
            var status = cpu.isBusy() ? cpu.getJobStatus() : null;
            var name = cpu.getName();
            out.add(new CpuReading(network, name == null ? "" : name.getString(), cpu.getCoProcessors(),
                    cpu.getAvailableStorage(), status == null ? null : status.crafting(),
                    status == null ? 0 : status.totalItems(), status == null ? 0 : status.progress(),
                    status == null ? 0 : status.elapsedTimeNanos()));
        }
        return out;
    }

    /**
     * What {@code network}'s own storage holds now, read for the page's search (at most every second while a player
     * searches); empty while the network is not loaded or its storage cannot be read.
     */
    public KeyCounter stock(NetworkId network) {
        var grid = grids.get(network);
        var counter = grid == null || grid.isEmpty() ? null : nativeStock(grid);
        return counter == null ? new KeyCounter() : counter;
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
            var members = members();
            stored.keySet().retainAll(members);
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
        var grids = new HashMap<NetworkId, IGrid>();
        if (!members.isEmpty()) {
            for (var grid : NetworkIdentityRegistry.get(level).liveClaims().keySet()) {
                FederationDomainRegistryAccess.confirmedNetworkId(grid).filter(members::contains)
                        .ifPresent(network -> grids.put(network, grid));
            }
        }
        this.grids = grids;
        jobs.keySet().retainAll(grids.values().stream().flatMap(grid -> grid.getCraftingService().getCpus().stream())
                .collect(java.util.stream.Collectors.toSet()));
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
            book.gauge(StatisticsNames.network(StatisticsNames.USAGE, network), second,
                    pool != null && pool.grids() > 1 ? pooledUsage(energy, pool) : energy.getAvgPowerUsage());
            var cpus = grid.getCraftingService().getCpus();
            book.gauge(StatisticsNames.network(StatisticsNames.CPUS, network), second, cpus.size());
            book.gauge(StatisticsNames.network(StatisticsNames.BUSY_CPUS, network), second,
                    cpus.stream().filter(cpu -> cpu.isBusy()).count());
            book.gauge(StatisticsNames.network(StatisticsNames.CHANNELS, network), second,
                    grid.getPathingService().getUsedChannels());
            sampleStorage(second, network, grid);
        });
    }

    /**
     * The network's share of what its energy pool drew. AE2 counts a drain on the network whose cells gave the
     * power, not on the one that used it, so the pool's drain is split by each network's idle draw: an estimate.
     */
    private static double pooledUsage(appeng.api.networking.energy.IEnergyService energy, EnergyPoolReading pool) {
        double drain = 0;
        double idle = 0;
        for (var service : pool.services()) {
            drain += service.getAvgPowerUsage();
            idle += service.getIdlePowerUsage();
        }
        return idle > 0 ? drain * energy.getIdlePowerUsage() / idle : drain / pool.grids();
    }

    private void sampleStorage(long second, NetworkId network, IGrid grid) {
        var reading = stored.get(network);
        boolean due = reading == null
                || Math.floorMod(second + network.value().hashCode(), STORAGE_PERIOD_SECONDS) == 0;
        if (due) {
            var counter = nativeStock(grid);
            if (counter != null) {
                reading = read(StatisticsNames.network(StatisticsNames.STOCK, network), second, counter, reading);
                stored.put(network, reading);
            }
        }
        if (reading == null || second - reading.second() > STORAGE_STALE_SECONDS) return;
        reading.amounts().forEach((type, amount) -> book.gauge(StatisticsNames.stored(network, type), second, amount));
        var name = StatisticsNames.network(StatisticsNames.STOCK, network);
        reading.tracked().forEach((key, amount) -> book.keyGauge(name, key, second, amount));
    }

    private record Held(AEKey key, long amount) {
    }

    /** A reading of {@code counter}: its totals by resource type and the amounts of the keys tracked under {@code name}. */
    private StoredReading read(String name, long second, KeyCounter counter, StoredReading previous) {
        var amounts = new HashMap<String, Double>();
        var top = new HashMap<AEKeyType, PriorityQueue<Held>>();
        for (var entry : counter) {
            var key = entry.getKey();
            long amount = entry.getLongValue();
            amounts.merge(key.getType().getId().toString(), (double) amount, Double::sum);
            var queue = top.computeIfAbsent(key.getType(),
                    ignored -> new PriorityQueue<>(Comparator.comparingLong(Held::amount)));
            if (queue.size() < TRACKED_TOP) queue.add(new Held(key, amount));
            else if (amount > queue.peek().amount()) {
                queue.poll();
                queue.add(new Held(key, amount));
            }
        }
        // A type the network held before and holds no more reads as 0, not as missing.
        if (previous != null) previous.amounts().keySet().forEach(type -> amounts.putIfAbsent(type, 0d));
        var chosen = new HashMap<AEKeyType, Set<AEKey>>();
        top.forEach((type, queue) -> queue.forEach(held -> chosen.computeIfAbsent(type, ignored -> new HashSet<>())
                .add(held.key())));
        for (var key : book.pins(name)) {
            if (counter.get(key) > 0) chosen.computeIfAbsent(key.getType(), ignored -> new HashSet<>()).add(key);
        }
        // Keys tracked before and still held keep their line while there is room, largest first.
        var earlier = new ArrayList<Held>();
        for (var key : book.keys(name)) {
            long amount = counter.get(key);
            if (amount > 0) earlier.add(new Held(key, amount));
        }
        earlier.sort(Comparator.comparingLong(Held::amount).reversed());
        for (var held : earlier) {
            var keys = chosen.computeIfAbsent(held.key().getType(), ignored -> new HashSet<>());
            if (keys.size() < TRACKED_CAP) keys.add(held.key());
        }
        var tracked = new HashMap<AEKey, Long>();
        chosen.values().forEach(keys -> keys.forEach(key -> tracked.put(key, counter.get(key))));
        // A key held at the last reading and no longer held ends its line at 0.
        if (previous != null) previous.tracked().forEach((key, amount) -> {
            if (amount > 0) tracked.putIfAbsent(key, 0L);
        });
        return new StoredReading(second, amounts, tracked);
    }

    /** What the network's own storage holds, or {@code null} when it cannot be read now. */
    private KeyCounter nativeStock(IGrid grid) {
        try {
            var domain = provenance.discover(grid);
            var counter = new KeyCounter();
            for (var source : domain.sources()) source.storage().getAvailableStacks(counter);
            counter.removeZeros();
            return counter;
        } catch (ProvenanceException unreadable) {
            return null;
        }
    }
}
