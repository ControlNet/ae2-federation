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
import java.util.LinkedHashMap;
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
    /** Debts whose consumer reported nothing requested at the last look; forgotten if it still does at the next. */
    private final Set<CraftingReturnLedger.Owed> idle = new HashSet<>();
    /**
     * AE2's link of each watched job, by crafting id, as last read from its CPU. Not saved: a link restored from a save
     * is a new object, read again from the CPU once it is loaded.
     */
    private final Map<UUID, ICraftingLink> jobLinks = new HashMap<>();
    /** The server tick each consumer Grid's jobs were last watched at, so a burst of pushes reads its CPUs once. */
    private final Map<IGrid, Long> watchedAt = new IdentityHashMap<>();
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
            for (var entry : List.copyOf(projected.entrySet())) {
                sync(entry.getKey(), entry.getValue().consumer(), entry.getValue().provider());
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
        CraftingReach.compute(edges).forEach((consumer, reached) -> {
            for (var executing : reached) {
                var pair = new Pair(consumer, executing);
                active.add(pair);
                sync(pair, grids.get(consumer), grids.get(executing));
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
        // The consumer's CPU takes the provider's materials through this direction's storage rule.
        var storage = policies.configured(new PolicyKey(key.consumerNetworkId(), key.providerNetworkId(),
                PolicyCapability.STORAGE)).orElse(null);
        if (storage == null || !storage.rule().enabled()) {
            return Optional.of(BindingDiagnostic.Reason.CRAFTING_STORAGE_REQUIRED);
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
    private void sync(Pair pair, IGrid consumer, IGrid provider) {
        var entry = projected.get(pair);
        if (entry != null && (entry.consumer() != consumer || entry.provider() != provider)) {
            withdraw(projected.remove(pair));
            entry = null;
        }
        if (entry == null) {
            entry = new Projected(consumer, provider, new LinkedHashMap<>());
            projected.put(pair, entry);
        }
        var crafting = consumer.getCraftingService();
        var reals = RealCraftingProviders.on(level, provider);
        var stale = new ArrayList<ICraftingProvider>();
        for (var real : entry.projections().keySet()) {
            if (!reals.contains(real)) stale.add(real);
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
                projection = new PatternProjection(real, (pushed, details, containerItems) -> owe(pair, details, containerItems));
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
    private void owe(Pair pair, IPatternDetails details, KeyCounter containerItems) {
        var ledger = CraftingReturnLedger.get(level);
        var executing = pair.executing();
        var consumer = pair.consumer();
        for (var output : details.getOutputs()) {
            ledger.add(executing, consumer, output.what(), output.amount());
        }
        for (var containerItem : containerItems) {
            ledger.add(executing, consumer, containerItem.getKey(), containerItem.getLongValue());
        }
        var entry = projected.get(pair);
        if (entry == null) return;
        watchJobs(ledger, consumer, entry.consumer());
        if (!routers.containsKey(entry.provider())) syncRouters();
    }

    /**
     * Watches the consumer's running jobs: one of them made this push. AE2 does not say which, so every busy CPU of the
     * consumer is watched; a job that never pushed here only keeps the consumer's debts while it runs out of sight.
     */
    private void watchJobs(CraftingReturnLedger ledger, NetworkId consumer, IGrid grid) {
        var tick = level.getServer().getTickCount();
        var last = watchedAt.put(grid, (long) tick);
        if (last != null && last == tick) return;
        for (var cpu : grid.getCraftingService().getCpus()) {
            if (!(cpu instanceof CraftingCPUCluster cluster) || !cluster.isBusy()) continue;
            var link = cluster.craftingLogic.getLastLink();
            var cpuLevel = cluster.getLevel();
            if (link == null || cpuLevel == null) continue;
            jobLinks.put(link.getCraftingID(), link);
            ledger.watch(consumer, link.getCraftingID(), cpuLevel.dimension().location().toString(),
                    cluster.getBoundsMin());
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
            if (!wanted.containsKey(grid)) grid.getStorageService().removeGlobalStorageProvider(routers.remove(grid));
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
     * Hands back what the executing networks hold for consumers that can take it again, then forgets debts whose job is
     * over. A debt is over when its loaded consumer waits for nothing of that key on two looks in a row and every job it
     * was watched for is in sight: a CPU in an unloaded chunk drops out of its Grid with its job intact, so a consumer
     * whose CPU is out of sight keeps its debts, as does one that is not loaded at all.
     *
     * <p>How the jobs ended decides where what is still held goes, as AE2's link of each job records it. A job that
     * finished may have been handed its final output before the container items or byproducts of its last push came
     * back (AE2 finishes a job on its final output); in one network they would land in its storage, so what is held is
     * handed to the consumer's storage. After a cancelled job, what arrives late stays on the executing network, where
     * the consumer still sees it through its storage rule.
     */
    private void sweepLedger() {
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
        var seen = new HashSet<CraftingReturnLedger.Owed>();
        for (var owed : ledger.entries()) {
            var consumer = federationDomains.grid(owed.consumer()).orElse(null);
            if (consumer == null || CraftingReturnRouter.waiting(consumer, owed.key(), owed.amount()) > 0) continue;
            var state = jobs.computeIfAbsent(owed.consumer(), network -> jobs(ledger, network));
            if (state.outOfSight()) continue;
            var marker = new CraftingReturnLedger.Owed(owed.executing(), owed.consumer(), owed.key(), 0, 0);
            if (!idle.contains(marker)) {
                seen.add(marker);
                continue;
            }
            var executing = federationDomains.grid(owed.executing()).orElse(null);
            if (state.finished() && owed.held() > 0 && executing != null && executing != consumer
                    && linked(owed.executing(), owed.consumer())) {
                HeldReturnTransfer.handBack(ledger, owed.executing(), executing, owed.consumer(), owed.key(),
                        owed.held(), consumer.getStorageService().getInventory());
            }
            ledger.drop(owed.executing(), owed.consumer(), owed.key());
        }
        idle.clear();
        idle.addAll(seen);
        ledger.forgetJobsOfSettledConsumers().forEach(jobLinks::remove);
        for (var network : ledger.transitNetworks()) {
            federationDomains.grid(network).ifPresent(grid -> HeldReturnTransfer.storeTransit(ledger, network, grid));
        }
        watchedAt.clear();
        if (routers.size() > 0 || !ledger.transitNetworks().isEmpty()) syncRouters();
    }

    /**
     * How a consumer's watched jobs stand: whether one is out of sight, and whether they ended by finishing.
     *
     * @param outOfSight a watched job may still run on a CPU that is not loaded or not formed yet
     * @param finished at least one watched job finished and none was cancelled
     */
    private record Jobs(boolean outOfSight, boolean finished) {
    }

    private Jobs jobs(CraftingReturnLedger ledger, NetworkId consumer) {
        boolean outOfSight = false;
        boolean done = false;
        boolean cancelled = false;
        for (var job : ledger.jobs(consumer)) {
            var end = end(ledger, consumer, job);
            if (end == null) {
                outOfSight = true;
            } else if (end == CraftingReturnLedger.JobEnd.DONE) {
                done = true;
            } else if (end == CraftingReturnLedger.JobEnd.CANCELLED) {
                cancelled = true;
            }
        }
        return new Jobs(outOfSight, done && !cancelled);
    }

    /**
     * How a watched job stands, or null while its CPU is out of sight: its dimension or chunk is not loaded, or the CPU
     * there has not formed yet. The chunk is only looked at, never loaded.
     *
     * <p>A job has ended once its CPU is in sight and runs it no more. How it ended is read from AE2's link of the job:
     * cancelling marks it cancelled (a cancel from the terminal, a CPU that breaks, a job that cannot be restored), and
     * the only other way a job ends is by finishing. A CPU-side link is never marked done itself, so "not cancelled"
     * is what tells a finished job. The link read before a CPU went out of sight is not the live one after it loads
     * again; if the job ends before the new one is read, how it ended is not known and it counts as cancelled, so
     * nothing late is moved.
     */
    private CraftingReturnLedger.@Nullable JobEnd end(CraftingReturnLedger ledger, NetworkId consumer,
            CraftingReturnLedger.Job job) {
        if (job.end() != CraftingReturnLedger.JobEnd.RUNNING) return job.end();
        var link = jobLinks.get(job.craftingId());
        if (link != null && link.isCanceled()) return ended(ledger, consumer, job, CraftingReturnLedger.JobEnd.CANCELLED);
        var dimension = ResourceLocation.tryParse(job.dimension());
        var cpuLevel = dimension == null ? null
                : level.getServer().getLevel(ResourceKey.create(Registries.DIMENSION, dimension));
        if (cpuLevel == null || !cpuLevel.isLoaded(job.cpu())) {
            jobLinks.remove(job.craftingId());
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
        idle.clear();
        jobLinks.clear();
        watchedAt.clear();
        federationDomains.clear();
    }
}
