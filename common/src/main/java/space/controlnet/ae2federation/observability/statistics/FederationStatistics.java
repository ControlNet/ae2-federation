package space.controlnet.ae2federation.observability.statistics;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.me.service.EnergyService;
import it.unimi.dsi.fastutil.objects.Object2LongMaps;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
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
 * {@link PairMeter} (one hash map add). Each second it drains the meters and reads the gauges of every loaded member
 * network (field reads, a small CPU set, and each energy pool walked once), spread over the next {@link #PASS_TICKS}
 * ticks so that a thousand networks do not all land in one tick. A network's own storage is enumerated once every
 * {@link #STORAGE_PERIOD_SECONDS} seconds, networks spread over those seconds, from its native sources only: never
 * {@code getCachedInventory}, which re-enumerates the mounted storage of other networks too when no terminal is
 * open. That reading also keeps the amounts of the keys each network holds most of,
 * and of the keys players pinned ({@link #TRACKED_TOP}, {@link #TRACKED_CAP}). Every tick it looks at the busy
 * crafting CPUs of the member networks that have any, to count the jobs they start: a network gets polled the tick
 * after AE2 reports a change to its CPUs, and the member networks' CPUs are looked at again every second.
 */
public final class FederationStatistics {
    /** Seconds between two enumerations of one network's own storage. */
    public static final int STORAGE_PERIOD_SECONDS = 10;
    /** A storage reading older than this is not recorded again: the network's storage could not be read since. */
    private static final int STORAGE_STALE_SECONDS = 3 * STORAGE_PERIOD_SECONDS;
    private static final int PRUNE_PERIOD_SECONDS = 60;
    /** Ticks over which a finished second is written, so no single tick writes every meter and network. */
    public static final int PASS_TICKS = 10;
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
        /** The route's ranking name, and its counter name by resource type, made once. */
        private final String ranking;
        private final Map<AEKeyType, String> movedNames = new HashMap<>();
        private Object2LongOpenHashMap<AEKey> moved = new Object2LongOpenHashMap<>();
        /** What moved in the last finished second while it is written into the book, else empty. */
        private Object2LongOpenHashMap<AEKey> finished = new Object2LongOpenHashMap<>();

        private PairMeter(Route route) {
            this.route = route;
            this.ranking = StatisticsNames.pair(StatisticsNames.MOVED, route.from(), route.to());
        }

        /** Records {@code amount} of {@code key} actually accepted on this route; anything not positive is ignored. */
        public void add(AEKey key, long amount) {
            if (amount <= 0) return;
            moved.addTo(key, amount);
        }

        /** Sets what moved so far aside as the finished second's and starts the next; false when nothing moved. */
        private boolean finish() {
            if (moved.isEmpty()) return false;
            var next = finished;
            finished = moved;
            moved = next;
            return true;
        }

        private String movedName(AEKeyType type) {
            return movedNames.computeIfAbsent(type,
                    ignored -> StatisticsNames.moved(route.from(), route.to(), type.getId().toString()));
        }
    }

    /** A member network's series names, made once instead of every second. */
    private static final class NetworkNames {
        private final NetworkId network;
        private final String power;
        private final String pool;
        private final String usage;
        private final String cpus;
        private final String busyCpus;
        private final String channels;
        private final String stock;
        private final Map<String, String> stored = new HashMap<>();

        private NetworkNames(NetworkId network) {
            this.network = network;
            power = StatisticsNames.network(StatisticsNames.POWER, network);
            pool = StatisticsNames.network(StatisticsNames.POOL, network);
            usage = StatisticsNames.network(StatisticsNames.USAGE, network);
            cpus = StatisticsNames.network(StatisticsNames.CPUS, network);
            busyCpus = StatisticsNames.network(StatisticsNames.BUSY_CPUS, network);
            channels = StatisticsNames.network(StatisticsNames.CHANNELS, network);
            stock = StatisticsNames.network(StatisticsNames.STOCK, network);
        }

        private String stored(String keyType) {
            return stored.computeIfAbsent(keyType, ignored -> StatisticsNames.stored(network, keyType));
        }
    }

    /**
     * A finished second being written into the book over the next {@link #PASS_TICKS} ticks: the meters that moved
     * anything in it, then the member networks loaded as it ended.
     */
    private static final class Pass {
        private final long second;
        private final Set<NetworkId> members;
        private final List<PairMeter> meters;
        private final List<Map.Entry<NetworkId, IGrid>> networks;
        /** What each CPU with a job was seen doing as the pass began: only these can be found gone at its end. */
        private final Map<ICraftingCPU, JobSeen> jobsBefore;
        private final int perTick;
        /** Each energy pool read so far, by member service, so a pool shared by many networks is read once. */
        private final Map<EnergyService, EnergyPoolReading> pools = new IdentityHashMap<>();
        /** The crafting CPUs of the networks sampled so far. */
        private final Set<ICraftingCPU> cpus = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        private int nextMeter;
        private int nextNetwork;

        private Pass(long second, Set<NetworkId> members, List<PairMeter> meters,
                List<Map.Entry<NetworkId, IGrid>> networks, Map<ICraftingCPU, JobSeen> jobsBefore) {
            this.second = second;
            this.members = members;
            this.meters = meters;
            this.networks = networks;
            this.jobsBefore = jobsBefore;
            perTick = Math.max(1, Math.ceilDiv(meters.size() + networks.size(), PASS_TICKS));
        }

        private boolean done() {
            return nextMeter == meters.size() && nextNetwork == networks.size();
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

    /** Grids whose crafting CPUs changed since the last tick, from AE2's event; weak, as Grids come and go. */
    private static final Set<IGrid> CPUS_CHANGED = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

    private final ServerLevel level;
    private final LongSupplier millis;
    private final StatisticsBook<AEKey> book;
    private final java.util.function.LongConsumer changed;
    private final Map<Route, PairMeter> meters = new HashMap<>();
    private final Map<String, long[]> counts = new HashMap<>();
    private final Map<NetworkId, StoredReading> stored = new HashMap<>();
    private final Map<NetworkId, NetworkNames> names = new HashMap<>();
    /** The loaded member networks' Grids as of the last finished second. */
    private Map<NetworkId, IGrid> grids = Map.of();
    /** The member networks each Grid of {@link #grids} is. */
    private Map<IGrid, NetworkId> networks = Map.of();
    /**
     * The member networks whose crafting CPUs are polled every tick: those with a CPU at the last finished second,
     * and any whose CPUs changed since.
     */
    private final Map<NetworkId, IGrid> crafting = new HashMap<>();
    private final Map<ICraftingCPU, JobSeen> jobs = new IdentityHashMap<>();
    /** Grids whose CPUs changed while they were no known member network's, looked up again as the next pass begins. */
    private final Set<IGrid> cpusChangedUnknown = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());
    /** Our own index of native sources: reading storage must not change the one storage mounts rely on. */
    private final NativeSourceDomainRegistry provenance = new NativeSourceDomainRegistry();
    private final SecondClock clock = new SecondClock();
    /** Each Federation domain's converter bodies as of the last second, by domain id, for the page's list. */
    private Map<String, List<space.controlnet.ae2federation.power.OperatingPowerService.ConverterBody>> converters = Map.of();
    private long finishedSecond = Long.MIN_VALUE;
    private @org.jetbrains.annotations.Nullable Pass pass;

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

    /** Once, at mod start: AE2's Grid event subscriptions are global and permanent. */
    public static void register() {
        GridHelper.addEventHandler(GridCraftingCpuChange.class, (grid, event) -> CPUS_CHANGED.add(grid));
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
     * Called as each server tick ends: counts the crafting jobs started this tick, starts writing the previous second
     * once the wall clock has passed it, and writes the next slice of the second being written.
     */
    public void tick() {
        followCpuChanges();
        pollJobs();
        var step = clock.ended(millis.getAsLong());
        if (step != null) {
            completePass();
            beginPass(step.finish());
            for (long second = step.observeFrom(); second <= step.observeTo(); second++) book.observe(second);
        }
        var pass = this.pass;
        if (pass != null && advance(pass, pass.perTick)) completePass();
    }

    /**
     * Polls a member network whose CPUs changed from now on, as when a player builds its first CPU, so a job started
     * on it before the next second is counted; the next second keeps it only if it has a CPU then.
     */
    private void followCpuChanges() {
        if (CPUS_CHANGED.isEmpty()) return;
        for (var grid : CPUS_CHANGED) {
            var network = networks.get(grid);
            if (network != null) crafting.put(network, grid);
            else cpusChangedUnknown.add(grid);
        }
        CPUS_CHANGED.clear();
    }

    /** Counts each job a member network's crafting CPU started since the last tick. */
    private void pollJobs() {
        crafting.forEach((network, grid) -> {
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
     * Writes {@code second} into the book at once, after the rest of any second still being written: marks it
     * observed, drains the meters into it and samples every loaded member network. Tests call it with a second of
     * their own; {@link #tick()} writes each wall-clock second over {@link #PASS_TICKS} ticks instead.
     */
    public void finishSecond(long second) {
        completePass();
        beginPass(second);
        completePass();
    }

    /**
     * Starts writing {@code second}: marks it observed, sets the meters' amounts aside, records the counters and the
     * Federation power, and looks up the loaded member networks. Their gauges and the meters are written by
     * {@link #advance}, so a gauge is read up to {@link #PASS_TICKS} ticks after its second ended.
     */
    private void beginPass(long second) {
        book.observe(second);
        var finished = new ArrayList<PairMeter>();
        for (var meter : meters.values()) {
            if (meter.finish()) finished.add(meter);
        }
        counts.forEach((name, amount) -> book.count(name, second, amount[0]));
        counts.clear();
        sampleFederationPower(second);
        var members = members();
        var grids = new HashMap<NetworkId, IGrid>();
        if (!members.isEmpty()) {
            for (var grid : NetworkIdentityRegistry.get(level).liveGrids()) {
                FederationDomainRegistryAccess.confirmedNetworkId(grid).filter(members::contains)
                        .ifPresent(network -> grids.put(network, grid));
            }
        }
        this.grids = grids;
        var networks = new IdentityHashMap<IGrid, NetworkId>();
        grids.forEach((network, grid) -> networks.put(grid, network));
        this.networks = networks;
        // A network that is no loaded member any more is no longer polled; the others are looked at in their turn.
        // A polled network whose Grid was replaced is polled on its new Grid at once, as is a Grid whose CPUs changed
        // before its network was known.
        crafting.replaceAll((network, grid) -> grids.get(network));
        crafting.values().removeIf(java.util.Objects::isNull);
        for (var grid : cpusChangedUnknown) {
            var network = networks.get(grid);
            if (network != null) crafting.put(network, grid);
        }
        cpusChangedUnknown.clear();
        pass = new Pass(second, members, finished, new ArrayList<>(grids.entrySet()), new IdentityHashMap<>(jobs));
    }

    /** Writes up to {@code count} more of the pass's meters and networks; true once all of them are written. */
    private boolean advance(Pass pass, int count) {
        for (; count > 0 && pass.nextMeter < pass.meters.size(); count--) {
            drain(pass.second, pass.meters.get(pass.nextMeter++));
        }
        for (; count > 0 && pass.nextNetwork < pass.networks.size(); count--) {
            var entry = pass.networks.get(pass.nextNetwork++);
            sample(pass, entry.getKey(), entry.getValue());
        }
        return pass.done();
    }

    /** Writes the rest of the second being written, if any, and marks it finished. */
    private void completePass() {
        var pass = this.pass;
        if (pass == null) return;
        advance(pass, Integer.MAX_VALUE);
        this.pass = null;
        // A CPU that had a job as the pass began, that its network did not list and that no poll saw since is gone.
        // AE2 lists only active CPUs, so one inactive as its network was sampled and polled again later stays, and so
        // does one that started a job meanwhile, perhaps built after its network was sampled.
        pass.jobsBefore.forEach((cpu, seen) -> {
            if (!pass.cpus.contains(cpu) && jobs.get(cpu) == seen) jobs.remove(cpu);
        });
        long second = pass.second;
        finishedSecond = Math.max(finishedSecond, second);
        if (Math.floorMod(second, PRUNE_PERIOD_SECONDS) == 0) {
            book.prune(second);
            stored.keySet().retainAll(pass.members);
            names.keySet().retainAll(pass.members);
        }
        changed.accept(second);
    }

    private void drain(long second, PairMeter meter) {
        var moved = meter.finished;
        book.movedAll(meter.ranking, second, moved);
        AEKeyType onlyType = null;
        double onlyAmount = 0;
        Map<AEKeyType, double[]> byType = null;
        for (var entry : Object2LongMaps.fastIterable(moved)) {
            var type = entry.getKey().getType();
            double amount = entry.getLongValue();
            // Most routes move one resource type, whose total needs no map.
            if (onlyType == null || onlyType == type && byType == null) {
                onlyType = type;
                onlyAmount += amount;
                continue;
            }
            if (byType == null) {
                byType = new HashMap<>();
                byType.put(onlyType, new double[] {onlyAmount});
            }
            byType.computeIfAbsent(type, ignored -> new double[1])[0] += amount;
        }
        if (byType != null) byType.forEach((type, amount) -> book.count(meter.movedName(type), second, amount[0]));
        else if (onlyType != null) book.count(meter.movedName(onlyType), second, onlyAmount);
        moved.clear();
    }

    private java.util.Set<NetworkId> members() {
        var members = new HashSet<NetworkId>();
        for (var domain : FederationDomainRegistryAccess.get(level).federationDomains()) {
            members.addAll(domain.memberships().keySet());
        }
        return members;
    }

    /** {@code domain}'s converter bodies as of the last finished second; none while power is not required. */
    public List<space.controlnet.ae2federation.power.OperatingPowerService.ConverterBody> converters(String domain) {
        return converters.getOrDefault(domain, List.of());
    }

    /** Each domain's Federation power over the second, while power is required. */
    private void sampleFederationPower(long second) {
        var server = level.getServer();
        var drained = server == null ? Map.<space.controlnet.ae2federation.domain.FederationDomainId,
                space.controlnet.ae2federation.power.OperatingPowerService.Second>of()
                : space.controlnet.ae2federation.power.OperatingPowerService.drainSecond(server);
        var bodies = new HashMap<String, List<space.controlnet.ae2federation.power.OperatingPowerService.ConverterBody>>();
        drained.forEach((id, power) -> {
            var prefix = StatisticsNames.fedpower(id.value());
            for (var group : space.controlnet.ae2federation.power.OperatingPowerCosts.Group.values()) {
                book.gauge(prefix + StatisticsNames.FEDPOWER_USE + "/" + group.key(), second,
                        power.use().byGroup().getOrDefault(group, 0.0));
            }
            book.gauge(prefix + StatisticsNames.FEDPOWER_INPUT, second, power.input());
            // A domain without a matching converter has no buffer: a gap, not an empty one.
            if (power.capacity() > 0) {
                book.gauge(prefix + StatisticsNames.FEDPOWER_STORED, second, 100 * power.stored() / power.capacity());
            }
            book.gauge(prefix + StatisticsNames.FEDPOWER_CAPACITY, second, power.capacity());
            book.gauge(prefix + StatisticsNames.FEDPOWER_STOPPED, second, power.running() ? 0 : 1);
            bodies.put(id.value(), power.bodies());
        });
        converters = Map.copyOf(bodies);
    }

    /** Samples one member network's gauges into the pass's second, and polls its CPUs from now on if it has any. */
    private void sample(Pass pass, NetworkId network, IGrid grid) {
        // A Grid that came apart since the pass began has nothing to read; its network's next Grid is in the next pass.
        if (grid.isEmpty()) return;
        long second = pass.second;
        var names = this.names.computeIfAbsent(network, NetworkNames::new);
        var energy = grid.getEnergyService();
        double max = energy.getMaxStoredPower();
        if (max > 0) book.gauge(names.power, second, 100 * energy.getStoredPower() / max);
        var pool = energy instanceof EnergyService service ? pass.pools.get(service) : null;
        if (pool == null) {
            pool = EnergyPoolReading.measure(energy);
            if (pool != null) for (var member : pool.services()) pass.pools.put(member, pool);
        }
        if (pool != null && pool.grids() > 1 && pool.max() > 0) {
            book.gauge(names.pool, second, 100 * pool.stored() / pool.max());
        }
        book.gauge(names.usage, second,
                pool != null && pool.grids() > 1 ? pooledUsage(energy, pool) : energy.getAvgPowerUsage());
        var cpus = grid.getCraftingService().getCpus();
        if (cpus.isEmpty()) crafting.remove(network);
        else crafting.put(network, grid);
        pass.cpus.addAll(cpus);
        book.gauge(names.cpus, second, cpus.size());
        book.gauge(names.busyCpus, second, cpus.stream().filter(cpu -> cpu.isBusy()).count());
        book.gauge(names.channels, second, grid.getPathingService().getUsedChannels());
        sampleStorage(second, network, names, grid);
    }

    /**
     * The network's share of what its energy pool drew. AE2 counts a drain on the network whose cells gave the
     * power, not on the one that used it, so the pool's drain is split by each network's idle draw: an estimate.
     */
    private static double pooledUsage(appeng.api.networking.energy.IEnergyService energy, EnergyPoolReading pool) {
        // The pool's sums, read once per pool: summing every member again for each network is quadratic in its size.
        return pool.idle() > 0 ? pool.drain() * energy.getIdlePowerUsage() / pool.idle() : pool.drain() / pool.grids();
    }

    private void sampleStorage(long second, NetworkId network, NetworkNames names, IGrid grid) {
        var reading = stored.get(network);
        boolean due = reading == null
                || Math.floorMod(second + network.value().hashCode(), STORAGE_PERIOD_SECONDS) == 0;
        if (due) {
            var counter = nativeStock(grid);
            if (counter != null) {
                reading = read(names.stock, second, counter, reading);
                stored.put(network, reading);
            }
        }
        if (reading == null || second - reading.second() > STORAGE_STALE_SECONDS) return;
        reading.amounts().forEach((type, amount) -> book.gauge(names.stored(type), second, amount));
        reading.tracked().forEach((key, amount) -> book.keyGauge(names.stock, key, second, amount));
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
