package space.controlnet.ae2federation.crafting.remote;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.crafting.binding.CraftingCapabilityBinding;
import space.controlnet.ae2federation.crafting.binding.CraftingPolicyFilter;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyService;

/**
 * Makes a published Crafting binding usable from the consumer's own ME crafting service.
 *
 * <ul>
 * <li>Each current binding offers what its provider can craft, as permitted by the rule's filter, as emitable items
 * on the consumer's Grid ({@link CraftingProjection}). Keys the consumer can craft with its own patterns are left
 * out, since AE2 plans an emitable key without looking at patterns.</li>
 * <li>When the consumer's CPUs wait for such a key ({@code getRequestedAmount}) beyond what provider jobs already
 * have in flight, the provider plans and runs a native job for the difference, requested by a
 * {@link RemoteCraftingRequester} on the provider's Grid, which hands the output to the consumer's Grid.</li>
 * <li>The provider's own plan sees what it can craft through its own rules in turn, so a request recurses along
 * Crafting rules; {@code CraftingDependencyCycleGuard} keeps those rules acyclic, so it ends.</li>
 * </ul>
 *
 * A provider plan that is missing materials or finds no CPU is not submitted; it is tried again after
 * {@link #RETRY_TICKS}, and the consumer's CPU keeps waiting, as it does for a vanilla crafting emitter. When the
 * consumer stops waiting for a key (its job was cancelled), the provider jobs for it are cancelled too.
 */
public final class RemoteCraftingService implements AutoCloseable {
    private static final Map<ServerLevel, RemoteCraftingService> SERVICES = new WeakHashMap<>();
    /** Ticks between refreshes of what each provider can craft. */
    static final int REFRESH_TICKS = 20;
    /** Ticks between looks at what consumers wait for. */
    static final int DEMAND_TICKS = 5;
    /** Ticks before a provider plan that could not run is tried again. */
    static final int RETRY_TICKS = 40;
    /**
     * Ticks after the service starts (a server start or a level load) in which no provider job is started or
     * cancelled: requesters and CPUs in chunks loaded later reconnect first. Longer than the 60 ticks AE2 waits for a
     * missing requester before it cancels the job itself.
     */
    static final int STARTUP_TICKS = 100;

    private final ServerLevel level;
    private final Map<PolicyKey, Projected> projections = new HashMap<>();
    private final Set<RemoteCraftingRequester> requesters = Collections.newSetFromMap(new WeakHashMap<>());
    private final Map<Demand, Pending> pending = new HashMap<>();
    private final Map<Demand, Long> retryAt = new HashMap<>();
    private final Set<Demand> idle = new HashSet<>();
    private final Map<PolicyKey, Integer> submissions = new HashMap<>();
    private long ticks;

    private record Projected(IGrid consumer, CraftingProjection provider) {
    }

    /** A key one consumer network waits for. */
    private record Demand(NetworkId consumer, AEKey output) {
    }

    private record Pending(CraftingCapabilityBinding binding, RemoteCraftingRequester requester, long amount,
            Future<ICraftingPlan> plan) {
    }

    private RemoteCraftingService(ServerLevel level) {
        this.level = level;
    }

    public static synchronized RemoteCraftingService get(ServerLevel level) {
        return SERVICES.computeIfAbsent(level, RemoteCraftingService::new);
    }

    /** Runs once per level tick; does nothing for a level without Crafting bindings or remote jobs. */
    public static void tick(ServerLevel level) {
        RemoteCraftingService service;
        synchronized (RemoteCraftingService.class) {
            service = SERVICES.get(level);
        }
        if (service == null && CraftingBindingService.publishedBindingsIfPresent(level).isEmpty()) return;
        (service == null ? get(level) : service).tick();
    }

    public static synchronized void closeLevel(ServerLevel level) {
        var service = SERVICES.remove(level);
        if (service != null) service.close();
    }

    /** Native jobs submitted on {@code key}'s provider for its consumer so far, for tests and diagnostics. */
    public int submissionCount(PolicyKey key) {
        return submissions.getOrDefault(key, 0);
    }

    /** The keys offered to {@code consumer} under {@code key}, for tests and diagnostics. */
    public Set<AEKey> projectedKeys(PolicyKey key) {
        var projected = projections.get(key);
        return projected == null ? Set.of() : projected.provider().keys();
    }

    void register(RemoteCraftingRequester requester) {
        requesters.add(requester);
    }

    /** Puts a provider job's output into its consumer's Grid, where a waiting CPU takes it first. */
    long deliver(NetworkId consumer, AEKey what, long amount, Actionable mode, IActionSource source) {
        var grid = CraftingBindingService.gridIfPresent(level, consumer).orElse(null);
        return grid == null ? 0 : grid.getStorageService().getInventory().insert(what, amount, mode, source);
    }

    private void tick() {
        ticks++;
        if (ticks % REFRESH_TICKS == 1) refreshProjections();
        if (ticks % DEMAND_TICKS == 0) {
            collectPlans();
            serveDemand();
        }
    }

    private void refreshProjections() {
        var seen = new HashSet<PolicyKey>();
        for (var binding : CraftingBindingService.publishedBindingsIfPresent(level)) {
            var key = binding.relationship().key();
            var configured = PolicyService.get(level).configured(key).orElse(null);
            if (configured == null) continue;
            // The provider Grid's live crafting service, not nativeService(): that re-checks the binding by walking
            // every node of the Grid, too costly for each refresh. A binding that stops being current is withdrawn by
            // CraftingBindingService, and a job is started only on a current one.
            var service = binding.relationship().providerGrid().getCraftingService();
            var consumer = binding.relationship().consumerGrid();
            var consumerCrafting = consumer.getCraftingService();
            var filter = configured.rule().filter();
            var keys = new HashSet<AEKey>();
            for (var craftable : service.getCraftables(candidate -> CraftingPolicyFilter.permits(filter, candidate))) {
                if (consumerCrafting.getCraftingFor(craftable).isEmpty()) keys.add(craftable);
            }
            seen.add(key);
            var projected = projections.get(key);
            if (projected != null && projected.consumer() != consumer) {
                withdraw(projected);
                projections.remove(key);
                projected = null;
            }
            if (projected == null) {
                projected = new Projected(consumer, new CraftingProjection());
                projected.provider().update(keys);
                consumerCrafting.addGlobalCraftingProvider(projected.provider());
                projections.put(key, projected);
            } else if (projected.provider().update(keys)) {
                consumerCrafting.refreshGlobalCraftingProvider(projected.provider());
            }
        }
        var stale = new ArrayList<PolicyKey>();
        projections.forEach((key, projected) -> {
            if (!seen.contains(key)) stale.add(key);
        });
        for (var key : stale) withdraw(projections.remove(key));
    }

    private void withdraw(Projected projected) {
        projected.consumer().getCraftingService().removeGlobalCraftingProvider(projected.provider());
    }

    /** Starts provider jobs for what consumers wait for, and cancels them for what they no longer wait for. */
    private void serveDemand() {
        if (ticks < STARTUP_TICKS) return;
        var inFlight = new HashMap<Demand, Long>();
        var running = new HashMap<Demand, List<RemoteCraftingRequester.Job>>();
        for (var requester : List.copyOf(requesters)) {
            for (var job : requester.liveJobs()) {
                var demand = new Demand(job.key.consumerNetworkId(), job.output);
                inFlight.merge(demand, job.remaining(), Long::sum);
                running.computeIfAbsent(demand, ignored -> new ArrayList<>()).add(job);
            }
        }
        var offered = new HashMap<Demand, List<CraftingCapabilityBinding>>();
        var consumers = new HashMap<NetworkId, IGrid>();
        for (var binding : CraftingBindingService.publishedBindingsIfPresent(level)) {
            var projected = projections.get(binding.relationship().key());
            if (projected == null) continue;
            var consumerId = binding.relationship().key().consumerNetworkId();
            consumers.put(consumerId, projected.consumer());
            for (var output : projected.provider().keys()) {
                offered.computeIfAbsent(new Demand(consumerId, output), ignored -> new ArrayList<>()).add(binding);
            }
        }
        var waiting = new HashSet<Demand>();
        offered.forEach((demand, bindings) -> {
            var crafting = consumers.get(demand.consumer()).getCraftingService();
            long requested = crafting.isRequestingAny() ? crafting.getRequestedAmount(demand.output()) : 0;
            if (requested <= 0) return;
            waiting.add(demand);
            long missing = requested - inFlight.getOrDefault(demand, 0L);
            if (missing <= 0 || pending.containsKey(demand) || retryAt.getOrDefault(demand, 0L) > ticks) return;
            bindings.sort(Comparator.comparing(binding -> binding.relationship().key().providerNetworkId().value()));
            start(demand, bindings.getFirst(), missing);
        });
        // A key no longer waited for on two looks in a row: its consumer job ended or was cancelled. A consumer that
        // is not reachable (unloaded, or its binding is being rebuilt) is not asked; the job's output then stays
        // with the provider (RemoteCraftingRequester), and a consumer still waiting later is served from it.
        running.forEach((demand, jobs) -> {
            if (waiting.contains(demand) || !consumers.containsKey(demand.consumer())) {
                idle.remove(demand);
            } else if (!idle.add(demand)) {
                jobs.forEach(job -> job.link.cancel());
                idle.remove(demand);
            }
        });
        idle.retainAll(running.keySet());
    }

    private void start(Demand demand, CraftingCapabilityBinding binding, long amount) {
        var service = binding.nativeService().orElse(null);
        var providerGrid = binding.sourceGrid().orElse(null);
        var requester = providerGrid == null ? null : requesterOn(providerGrid);
        if (service == null || requester == null) {
            retryAt.put(demand, ticks + RETRY_TICKS);
            return;
        }
        register(requester);
        var node = requester.getActionableNode();
        var source = IActionSource.ofMachine(requester);
        ICraftingSimulationRequester simulation = new ICraftingSimulationRequester() {
            @Override
            public IActionSource getActionSource() {
                return source;
            }

            @Override
            public IGridNode getGridNode() {
                return node;
            }
        };
        var plan = service.beginCraftingCalculation(level, simulation, demand.output(), amount,
                CalculationStrategy.REPORT_MISSING_ITEMS);
        pending.put(demand, new Pending(binding, requester, amount, plan));
    }

    private void collectPlans() {
        for (var entry : List.copyOf(pending.entrySet())) {
            var demand = entry.getKey();
            var request = entry.getValue();
            if (!request.plan().isDone()) continue;
            pending.remove(demand);
            ICraftingPlan plan;
            try {
                plan = request.plan().get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (ExecutionException exception) {
                retryAt.put(demand, ticks + RETRY_TICKS);
                continue;
            }
            var service = request.binding().nativeService().orElse(null);
            if (plan.simulation() || service == null) {
                retryAt.put(demand, ticks + RETRY_TICKS);
                continue;
            }
            var result = service.submitJob(plan, request.requester(), null, false,
                    IActionSource.ofMachine(request.requester()));
            var link = result.link();
            if (!result.successful() || link == null) {
                retryAt.put(demand, ticks + RETRY_TICKS);
                continue;
            }
            var key = request.binding().relationship().key();
            request.requester().track(link, key, demand.output(), request.amount());
            retryAt.remove(demand);
            submissions.merge(key, 1, Integer::sum);
        }
    }

    /** The requester of the provider Grid's Federation node with the lowest node id, so one host is used throughout. */
    private static @Nullable RemoteCraftingRequester requesterOn(IGrid grid) {
        RemoteCraftingRequester chosen = null;
        String chosenId = null;
        for (var node : grid.getNodes()) {
            if (!node.isActive() || !(node.getService(ICraftingRequester.class) instanceof RemoteCraftingRequester requester)) {
                continue;
            }
            var id = grid.getService(NetworkIdentityService.class).lineage(node).nodeId().toString();
            if (chosenId == null || id.compareTo(chosenId) < 0) {
                chosen = requester;
                chosenId = id;
            }
        }
        return chosen;
    }

    @Override
    public void close() {
        projections.values().forEach(this::withdraw);
        projections.clear();
        pending.clear();
        retryAt.clear();
        idle.clear();
        requesters.clear();
    }
}
