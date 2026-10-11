package space.controlnet.ae2federation.crafting.projection;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.IdentityEpoch;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.BackendStatus;
import space.controlnet.ae2federation.policy.BindingDiagnostic;
import space.controlnet.ae2federation.policy.PolicyActivationState;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRuntimeEndpoints;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.policy.RuleMode;

/**
 * Cross-network crafting as AE2 does it within one network: under an active crafting rule "C uses P", every real
 * pattern provider of P is offered to C's crafting service ({@link PatternProjection}). C's own CPU plans and runs
 * the job with what C's storage shows, which includes P's storage through the same direction's storage rule, and
 * pushes each pattern straight to P's provider. P needs no CPU. What P's machines return is handed back to C's CPU by
 * P's {@link CraftingReturnRouter}, as far as the {@link CraftingReturnLedger} says P owes it.
 *
 * <p>Re-export passes providers on as it passes storage on ({@link CraftingReach}): when "C uses M" and "M uses S"
 * with re-export, S's providers are offered to C too. The push still goes straight from C's CPU to S's provider, and
 * S's router returns straight to C; M takes no part and needs nothing but its rules.
 *
 * <p>Rule edits and topology changes reconcile at once; each tick reconciles again only when the domain topology, a
 * rule or a Grid identity changed, and every {@link #REFRESH_TICKS} ticks the providers and their patterns are
 * compared again, since AE2 has no event for a provider's patterns changing.
 */
public final class CraftingProjectionService implements AutoCloseable {
    /** One service per server, like AE2's Grid services: consumers and providers may be in any dimensions. */
    private static final Map<MinecraftServer, CraftingProjectionService> SERVICES = new WeakHashMap<>();
    /** Ticks between looks at each provider's patterns and at what consumers still wait for. */
    static final int REFRESH_TICKS = 20;
    /**
     * Ticks after the service starts before the ledger is first looked at, so the first look does not run while the
     * world is still loading. A debt does not depend on it: one whose consumer's CPU is out of sight is kept however
     * long that lasts (see {@link #sweepLedger}).
     */
    static final int STARTUP_TICKS = 100;

    /** The overworld: it names the server for registry, rule and ledger lookups; nothing here is limited to it. */
    private final ServerLevel level;
    private final CraftingFederationDomainObserver federationDomains;
    /** By (consumer, network whose providers are projected): a rule's own provider, or one reached through re-export. */
    private final Map<Pair, Projected> projected = new HashMap<>();
    private final Map<IGrid, CraftingReturnRouter> routers = new IdentityHashMap<>();
    private final Map<PolicyKey, Status> statuses = new HashMap<>();
    /**
     * AE2's link of each watched job, by crafting id, as last read from its CPU. Not saved: a link restored from a save
     * is a new object, read again from the CPU once it is loaded.
     */
    private final Map<UUID, ICraftingLink> jobLinks = new HashMap<>();
    private Object reconciledRegistry;
    private long reconciledTopology = Long.MIN_VALUE;
    private Object reconciledPolicies;
    private long reconciledWatermark = Long.MIN_VALUE;
    private long reconciledEpoch = Long.MIN_VALUE;
    private long ticks;
    private long sweeps;
    /** The Federation links of the domain topology {@link #linksRegistry} had at {@link #linksTopology}. */
    private FederationLinks links;
    private Object linksRegistry;
    private long linksTopology = Long.MIN_VALUE;

    /** A rule's projection state for the pair editor: active with this many patterns, or why it is not. */
    public record Status(Optional<BindingDiagnostic.Reason> reason, int patterns) {
        public boolean active() {
            return reason.isEmpty();
        }
    }

    private record Pair(NetworkId consumer, NetworkId executing) {
        PolicyKey rule() {
            return new PolicyKey(consumer, executing, PolicyCapability.CRAFTING);
        }
    }

    private record Projected(IGrid consumer, IGrid provider, Map<ICraftingProvider, PatternProjection> projections) {
    }

    private CraftingProjectionService(MinecraftServer server) {
        this.level = server.overworld();
        federationDomains = new CraftingFederationDomainObserver(level);
    }

    /** The service of {@code level}'s server. */
    public static synchronized CraftingProjectionService get(ServerLevel level) {
        return SERVICES.computeIfAbsent(level.getServer(), CraftingProjectionService::new);
    }

    public static synchronized void reconcileIfPresent(ServerLevel level) {
        var service = SERVICES.get(level.getServer());
        if (service != null) service.reconcileAll();
    }

    /**
     * Called after AE2 removes an official global registration. Stop retained consumer mediums synchronously:
     * a global provider need not disable its own push method after native unregistration. Additions and pattern
     * changes still use coalesced discovery; removal only touches already observed projections by object identity.
     */
    public static synchronized void globalProviderRemoved(MinecraftServer server, IGrid source, ICraftingProvider provider) {
        if (provider instanceof PatternProjection) return;
        var service = SERVICES.get(server);
        if (service == null) return;
        for (var entry : List.copyOf(service.projected.entrySet())) {
            var projected = entry.getValue();
            if (projected.provider() != source) continue;
            var projection = projected.projections().remove(provider);
            if (projection == null) continue;
            projection.withdraw();
            projected.consumer().getCraftingService().removeGlobalCraftingProvider(projection);
            var status = service.statuses.get(entry.getKey().rule());
            if (status != null && status.active()) {
                int patterns = projected.projections().values().stream()
                        .mapToInt(remaining -> remaining.getAvailablePatterns().size()).sum();
                service.statuses.put(entry.getKey().rule(), new Status(Optional.empty(), patterns));
            }
        }
    }

    public static synchronized void tick(MinecraftServer server) {
        var service = SERVICES.get(server);
        if (service != null) service.tick();
    }

    /**
     * After a level's nodes left the registry: reconciles at once while the server runs. During shutdown the first
     * level to close withdraws every projection while AE2 still has every Grid, and nothing is rebuilt.
     */
    public static synchronized void levelClosed(ServerLevel level) {
        var service = SERVICES.get(level.getServer());
        if (service == null) return;
        if (level.getServer().isRunning()) service.reconcileAll();
        else service.close();
    }

    public static synchronized void closeServer(MinecraftServer server) {
        var service = SERVICES.remove(server);
        if (service != null) service.close();
    }

    /** The rule's projection state as last reconciled; empty when the server has not looked at the rule. */
    public static synchronized Optional<Status> status(ServerLevel level, PolicyKey key) {
        var service = SERVICES.get(level.getServer());
        return service == null ? Optional.empty() : Optional.ofNullable(service.statuses.get(key));
    }

    public void observeConnectedGrids(IGrid first, IGrid second) {
        federationDomains.register(first);
        federationDomains.register(second);
        reconcileAll();
    }

    public void observeFederationDomainMembers(Iterable<IGrid> grids) {
        federationDomains.register(grids);
        reconcileAll();
    }

    /** The projections of {@code key}'s provider network on its consumer, for tests and diagnostics. */
    public int projectionCount(PolicyKey key) {
        return projectionCount(key.consumerNetworkId(), key.providerNetworkId());
    }

    /** The projections of {@code executing}'s providers on {@code consumer}, directly or through re-export. */
    public int projectionCount(NetworkId consumer, NetworkId executing) {
        var entry = projected.get(new Pair(consumer, executing));
        return entry == null ? 0 : entry.projections().size();
    }

    /** How many times the ledger was looked at, for tests and diagnostics. */
    public long ledgerSweeps() {
        return sweeps;
    }

    /** Whether {@code grid} carries a return router, for tests and diagnostics. */
    public boolean routes(IGrid grid) {
        return routers.containsKey(grid);
    }

    private void tick() {
        ticks++;
        if (changed()) {
            reconcileAll();
        } else if (ticks % REFRESH_TICKS == 0) {
            var discoveries = new IdentityHashMap<IGrid, List<ICraftingProvider>>();
            for (var entry : List.copyOf(projected.entrySet())) {
                sync(entry.getKey(), entry.getValue().consumer(), entry.getValue().provider(), discoveries);
            }
        }
        if (ticks % REFRESH_TICKS == 0 && ticks >= STARTUP_TICKS) sweepLedger();
    }

    private boolean changed() {
        var registry = FederationDomainRegistryAccess.get(level);
        var policies = PolicyService.get(level);
        return registry != reconciledRegistry || registry.topologyRevision() != reconciledTopology
                || policies != reconciledPolicies || policies.highWatermark() != reconciledWatermark
                || IdentityEpoch.current() != reconciledEpoch;
    }

    public void reconcileAll() {
        var registry = FederationDomainRegistryAccess.get(level);
        var policies = PolicyService.get(level);
        reconciledRegistry = registry;
        reconciledTopology = registry.topologyRevision();
        reconciledPolicies = policies;
        reconciledWatermark = policies.highWatermark();
        reconciledEpoch = IdentityEpoch.current();
        statuses.clear();
        var grids = new HashMap<NetworkId, IGrid>();
        var edges = new ArrayList<CraftingReach.Edge>();
        for (var relationship : federationDomains.relationships().values()) {
            var key = relationship.key();
            var reason = blocked(policies, relationship);
            if (reason.isPresent()) {
                if (policies.configured(key).isPresent()) statuses.put(key, new Status(reason, 0));
                continue;
            }
            grids.put(key.consumerNetworkId(), relationship.consumerGrid());
            grids.put(key.providerNetworkId(), relationship.providerGrid());
            var mode = RuleMode.of(policies.configured(key).orElseThrow().rule());
            edges.add(new CraftingReach.Edge(key.consumerNetworkId(), key.providerNetworkId(),
                    mode == RuleMode.REEXPORT));
            statuses.put(key, new Status(Optional.empty(), 0));
        }
        var active = new HashSet<Pair>();
        var discoveries = new IdentityHashMap<IGrid, List<ICraftingProvider>>();
        CraftingReach.compute(edges).forEach((consumer, reached) -> {
            for (var executing : reached) {
                var pair = new Pair(consumer, executing);
                active.add(pair);
                sync(pair, grids.get(consumer), grids.get(executing), discoveries);
            }
        });
        for (var pair : List.copyOf(projected.keySet())) {
            if (!active.contains(pair)) withdraw(projected.remove(pair));
        }
        syncRouters();
    }

    /** Why {@code relationship}'s crafting rule projects nothing, or empty when it projects. */
    private Optional<BindingDiagnostic.Reason> blocked(PolicyService policies, CraftingRelationship relationship) {
        var key = relationship.key();
        var configured = policies.configured(key).orElse(null);
        if (configured == null) return Optional.of(BindingDiagnostic.Reason.POLICY_UNCONFIGURED);
        if (!configured.rule().enabled()) return Optional.of(BindingDiagnostic.Reason.POLICY_DISABLED);
        if (!configured.rule().operations().contains(PolicyOperation.REQUEST)) {
            return Optional.of(BindingDiagnostic.Reason.CRAFTING_REQUEST_MISSING);
        }
        var activation = policies.activation(key, new PolicyRuntimeEndpoints(relationship.consumerGrid(),
                relationship.providerGrid(), BackendStatus.READY));
        if (activation != PolicyActivationState.ACTIVE) return Optional.of(BindingDiagnostic.inactiveReason(activation));
        if (federationDomains.references(relationship).isEmpty()) {
            return Optional.of(BindingDiagnostic.Reason.DOMAIN_REFERENCE_MISSING);
        }
        return Optional.empty();
    }

    /** Projects the executing network's current real providers onto the consumer, adding, refreshing and removing. */
    private void sync(Pair pair, IGrid consumer, IGrid provider,
            Map<IGrid, List<ICraftingProvider>> discoveries) {
        ProjectionDiagnostics.relationship();
        var entry = projected.get(pair);
        if (entry != null && (entry.consumer() != consumer || entry.provider() != provider)) {
            withdraw(projected.remove(pair));
            entry = null;
        }
        if (entry == null) {
            entry = new Projected(consumer, provider, new IdentityHashMap<>());
            projected.put(pair, entry);
        }
        var crafting = consumer.getCraftingService();
        var reals = discoveries.computeIfAbsent(provider, grid -> RealCraftingProviders.on(level, grid));
        var membership = Collections.newSetFromMap(new IdentityHashMap<ICraftingProvider, Boolean>());
        membership.addAll(reals);
        var stale = new ArrayList<ICraftingProvider>();
        for (var real : entry.projections().keySet()) {
            if (!membership.contains(real)) stale.add(real);
        }
        for (var real : stale) {
            var projection = entry.projections().remove(real);
            projection.withdraw();
            crafting.removeGlobalCraftingProvider(projection);
        }
        int patterns = 0;
        for (var real : reals) {
            var projection = entry.projections().get(real);
            if (projection == null) {
                projection = new PatternProjection(real, (pushed, details, containerItems) -> {
                    var jobs = observeJobs(consumer);
                    return () -> owe(pair, provider, details, containerItems, jobs);
                });
                entry.projections().put(real, projection);
                crafting.addGlobalCraftingProvider(projection);
            } else if (projection.refresh()) {
                crafting.refreshGlobalCraftingProvider(projection);
            }
            patterns += projection.getAvailablePatterns().size();
        }
        // A rule's own provider network: the pair editor shows how many patterns it offers.
        var status = statuses.get(pair.rule());
        if (status != null && status.active()) statuses.put(pair.rule(), new Status(Optional.empty(), patterns));
    }

    private void withdraw(Projected entry) {
        var crafting = entry.consumer().getCraftingService();
        for (var projection : entry.projections().values()) {
            projection.withdraw();
            crafting.removeGlobalCraftingProvider(projection);
        }
        entry.projections().clear();
    }

    /**
     * Records what the executing network now owes the consumer's CPU for one accepted push: the pattern's outputs and
     * the container items its inputs leave behind, such as empty buckets, which the CPU waits for as well.
     */
    private void owe(Pair pair, IGrid provider, IPatternDetails details, KeyCounter containerItems, WatchedJobs jobs) {
        var ledger = CraftingReturnLedger.get(level);
        var executing = pair.executing();
        var consumer = pair.consumer();
        for (var output : details.getOutputs()) {
            ledger.add(executing, consumer, output.what(), output.amount());
        }
        for (var containerItem : containerItems) {
            ledger.add(executing, consumer, containerItem.getKey(), containerItem.getLongValue());
        }
        // A successful provider callback may already have removed this projection or its physical connection.
        // Responsibility belongs to the accepted dispatch, not to the current projection map entry.
        watchJobs(ledger, consumer, jobs);
        if (!routers.containsKey(provider)) syncRouters();
    }

    private record WatchedJobs(List<CraftingCpuLifecycle.Observation> known, boolean unknown) {}

    /**
     * Reads the consumer's running jobs before invoking a provider. AE2 does not identify the dispatching job, so every
     * busy CPU is captured. Only an accepted push commits this snapshot; a refused push creates no lifecycle records.
     * A job that never pushed here only keeps the consumer's debts while it runs out of sight.
     */
    private WatchedJobs observeJobs(IGrid grid) {
        var known = new ArrayList<CraftingCpuLifecycle.Observation>();
        boolean unknown = false;
        // Another CPU can start after an earlier push in this same tick. A Grid/tick cache would miss that job.
        for (var cpu : grid.getCraftingService().getCpus()) {
            if (!cpu.isBusy()) continue;
            var observation = CraftingCpuLifecycle.observe(cpu);
            if (observation == null || observation.link() == null) {
                unknown = true;
                continue;
            }
            known.add(observation);
        }
        return new WatchedJobs(List.copyOf(known), unknown);
    }

    private void watchJobs(CraftingReturnLedger ledger, NetworkId consumer, WatchedJobs jobs) {
        if (jobs.unknown()) ledger.watchUnknown(consumer);
        for (var observation : jobs.known()) {
            var link = observation.link();
            jobLinks.put(link.getCraftingID(), link);
            ledger.watch(consumer, link.getCraftingID(), observation.level().dimension().location().toString(),
                    observation.position(), observation.nativeCpu());
        }
    }

    /**
     * A router on every network that projects its providers, still owes returns or holds transit stock. Each change
     * remounts that network's storage, so routers come and go only when a network starts or stops needing one.
     */
    private void syncRouters() {
        var ledger = CraftingReturnLedger.get(level);
        var wanted = new IdentityHashMap<IGrid, NetworkId>();
        projected.forEach((pair, entry) -> {
            if (!entry.projections().isEmpty()) wanted.put(entry.provider(), pair.executing());
        });
        for (var owed : ledger.entries()) {
            federationDomains.grid(owed.executing()).ifPresent(grid -> wanted.put(grid, owed.executing()));
        }
        for (var network : ledger.transitNetworks()) {
            federationDomains.grid(network).ifPresent(grid -> wanted.put(grid, network));
        }
        for (var grid : List.copyOf(routers.keySet())) {
            // A native Grid can survive a same-tick merge while fresh nodes adopt another durable identity.
            // The router captures its executing identity; retaining it would assign new arrivals to the old owner.
            if (!routers.get(grid).executing().equals(wanted.get(grid))) {
                grid.getStorageService().removeGlobalStorageProvider(routers.remove(grid));
            }
        }
        wanted.forEach((grid, network) -> {
            if (routers.containsKey(grid)) return;
            // An output returns only while the networks are linked; otherwise it is held where it arrived.
            var router = new CraftingReturnRouter(network, grid, ledger, consumer -> linked(network, consumer)
                    ? federationDomains.grid(consumer).orElse(null) : null);
            routers.put(grid, router);
            grid.getStorageService().addGlobalStorageProvider(router);
        });
    }

    /** Whether {@code first} and {@code second} are joined through Federation Domains now. */
    private boolean linked(NetworkId first, NetworkId second) {
        var registry = FederationDomainRegistryAccess.get(level);
        var topology = registry.topologyRevision();
        if (links == null || registry != linksRegistry || topology != linksTopology) {
            links = FederationLinks.of(registry.federationDomains().stream()
                    .map(federationDomain -> federationDomain.memberships().keySet()).toList());
            linksRegistry = registry;
            linksTopology = topology;
        }
        return links.linked(first, second);
    }

    /**
     * Retries proven arrivals. Native task completion/cancellation does not discharge machine return responsibility;
     * only ledger.take after actual delivery does. Unknown CPU lifecycles retain stock and debt across restarts.
     */
    private void sweepLedger() {
        try (var scope = HeldReturnTransfer.scope()) {
            sweepReturns();
        }
    }

    private void sweepReturns() {
        sweeps++;
        var ledger = CraftingReturnLedger.get(level);
        for (var owed : ledger.entries()) {
            if (owed.held() <= 0 || !linked(owed.executing(), owed.consumer())) continue;
            var consumer = federationDomains.grid(owed.consumer()).orElse(null);
            var executing = federationDomains.grid(owed.executing()).orElse(null);
            if (consumer == null || executing == null || consumer == executing) continue;
            long waiting = CraftingReturnRouter.waiting(consumer, owed.key(), owed.amount());
            HeldReturnTransfer.handBack(ledger, owed.executing(), executing, owed.consumer(), owed.key(),
                    Math.min(owed.held(), waiting), consumer.getStorageService().getInventory());
        }
        var jobs = new HashMap<NetworkId, Jobs>();
        for (var owed : ledger.entries()) {
            var consumer = federationDomains.grid(owed.consumer()).orElse(null);
            if (consumer == null || CraftingReturnRouter.waiting(consumer, owed.key(), owed.amount()) > 0) continue;
            var state = jobs.computeIfAbsent(owed.consumer(), network -> jobs(ledger, network));
            if (state.outOfSight() || !state.finished()) continue;
            var executing = federationDomains.grid(owed.executing()).orElse(null);
            if (owed.held() > 0 && executing != null && executing != consumer
                    && linked(owed.executing(), owed.consumer())) {
                HeldReturnTransfer.handBack(ledger, owed.executing(), executing, owed.consumer(), owed.key(),
                        owed.held(), consumer.getStorageService().getInventory());
            }
            // Completion/cancellation says nothing about unreturned containers or late machine outputs. Only actual
            // delivery in ledger.take settles debt. Unknown provenance remains visible and recoverable indefinitely.
        }
        ledger.forgetJobsOfSettledConsumers().forEach(jobLinks::remove);
        for (var network : ledger.transitNetworks()) {
            federationDomains.grid(network).ifPresent(grid -> HeldReturnTransfer.storeTransit(ledger, network, grid));
        }
        if (routers.size() > 0 || !ledger.transitNetworks().isEmpty()) syncRouters();
    }

    /**
     * How a consumer's watched jobs stand: whether one is out of sight, and whether all observed jobs ended.
     *
     * @param outOfSight a watched job may still run on a CPU that is not loaded or not formed yet
     * @param finished all observed jobs have ended, so held outputs can reach ordinary consumer storage
     */
    private record Jobs(boolean outOfSight, boolean finished) {
    }

    private Jobs jobs(CraftingReturnLedger ledger, NetworkId consumer) {
        boolean unresolved = ledger.hasUnknownJobs(consumer);
        var watched = ledger.jobs(consumer);
        for (var job : watched) {
            var end = end(ledger, consumer, job);
            if (end == null || end == CraftingReturnLedger.JobEnd.RUNNING) unresolved = true;
        }
        return new Jobs(unresolved, !unresolved && !watched.isEmpty());
    }

    /**
     * How a watched job stands, or null while its CPU is out of sight: its dimension or chunk is not loaded, or the CPU
     * there has not formed yet. The chunk is only looked at, never loaded.
     *
     * <p>A job has ended once its CPU is in sight and runs it no more. How it ended is read from AE2's link of the job:
     * cancelling marks it cancelled (a cancel from the terminal, a CPU that breaks, a job that cannot be restored), and
     * the only other way a job ends is by finishing. A CPU-side link is never marked done itself, so "not cancelled"
     * is what tells a finished job. The link read before a CPU went out of sight is not the live one after it loads
     * again; if the job ends before the new one is read, how it ended is not known and it counts as cancelled; either terminal state retains
     * ownership of late outputs until they are actually delivered.
     */
    private CraftingReturnLedger.@Nullable JobEnd end(CraftingReturnLedger ledger, NetworkId consumer,
            CraftingReturnLedger.Job job) {
        if (job.end() != CraftingReturnLedger.JobEnd.RUNNING) return job.end();
        var link = jobLinks.get(job.craftingId());
        var dimension = ResourceLocation.tryParse(job.dimension());
        var cpuLevel = dimension == null ? null
                : level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (cpuLevel == null || !cpuLevel.isLoaded(job.cpu())) {
            jobLinks.remove(job.craftingId());
            return null;
        }
        if (!job.nativeCpu()) {
            var grid = federationDomains.grid(consumer).orElse(null);
            if (grid != null) {
                for (var cpu : grid.getCraftingService().getCpus()) {
                    var observation = CraftingCpuLifecycle.observe(cpu);
                    if (observation == null || observation.level() != cpuLevel
                            || !observation.position().equals(job.cpu())) continue;
                    var current = observation.link();
                    if (current != null && current.getCraftingID().equals(job.craftingId())) {
                        jobLinks.put(job.craftingId(), current);
                        if (current.isCanceled()) return ended(ledger, consumer, job, CraftingReturnLedger.JobEnd.CANCELLED);
                        if (cpu.isBusy()) return CraftingReturnLedger.JobEnd.RUNNING;
                        return ended(ledger, consumer, job, CraftingReturnLedger.JobEnd.DONE);
                    }
                    // Addons can run several independent CPUs at one block anchor. A different job here is not
                    // evidence that the watched job ended; continue looking for its exact crafting id.
                }
            }
            if (link != null && link.isCanceled()) return ended(ledger, consumer, job, CraftingReturnLedger.JobEnd.CANCELLED);
            if (link != null && link.isDone()) return ended(ledger, consumer, job, CraftingReturnLedger.JobEnd.DONE);
            // An addon cluster can disappear from the active CPU list while unformed. Missing is not completion.
            var anchor = cpuLevel.getBlockEntity(job.cpu());
            if (anchor == null) {
                return ended(ledger, consumer, job, CraftingReturnLedger.JobEnd.CANCELLED);
            }
            if (CraftingCpuLifecycle.terminated(anchor, job.craftingId())) {
                // The addon proves termination but does not expose whether it completed or was cancelled.
                return ended(ledger, consumer, job, CraftingReturnLedger.JobEnd.TERMINATED);
            }
            return null;
        }
        if (!(cpuLevel.getBlockEntity(job.cpu()) instanceof CraftingBlockEntity crafting)) {
            // The CPU was broken: AE2 cancels the job of a CPU that breaks and drops what it held.
            return ended(ledger, consumer, job, CraftingReturnLedger.JobEnd.CANCELLED);
        }
        var cluster = crafting.getCluster();
        if (cluster == null || cluster.isDestroyed()) {
            jobLinks.remove(job.craftingId());
            return null;
        }
        var current = cluster.craftingLogic.getLastLink();
        if (current != null && current.getCraftingID().equals(job.craftingId())) {
            jobLinks.put(job.craftingId(), current);
            return CraftingReturnLedger.JobEnd.RUNNING;
        }
        return ended(ledger, consumer, job, link == null || link.isCanceled() ? CraftingReturnLedger.JobEnd.CANCELLED
                : CraftingReturnLedger.JobEnd.DONE);
    }

    private static CraftingReturnLedger.JobEnd ended(CraftingReturnLedger ledger, NetworkId consumer,
            CraftingReturnLedger.Job job, CraftingReturnLedger.JobEnd end) {
        ledger.end(consumer, job.craftingId(), end);
        return end;
    }

    @Override
    public void close() {
        projected.values().forEach(this::withdraw);
        projected.clear();
        routers.forEach((grid, router) -> grid.getStorageService().removeGlobalStorageProvider(router));
        routers.clear();
        statuses.clear();
        jobLinks.clear();
        federationDomains.clear();
    }
}
