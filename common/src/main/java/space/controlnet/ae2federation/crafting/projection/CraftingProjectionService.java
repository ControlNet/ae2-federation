package space.controlnet.ae2federation.crafting.projection;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerLevel;
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
    private static final Map<ServerLevel, CraftingProjectionService> SERVICES = new WeakHashMap<>();
    /** Ticks between looks at each provider's patterns and at what consumers still wait for. */
    static final int REFRESH_TICKS = 20;
    /**
     * Ticks after the service starts in which no debt is forgotten: CPUs in chunks loaded later reconnect first, and
     * until then their consumer reports nothing requested.
     */
    static final int STARTUP_TICKS = 100;

    private final ServerLevel level;
    private final CraftingFederationDomainObserver federationDomains;
    /** By (consumer, network whose providers are projected): a rule's own provider, or one reached through re-export. */
    private final Map<Pair, Projected> projected = new HashMap<>();
    private final Map<IGrid, CraftingReturnRouter> routers = new IdentityHashMap<>();
    private final Map<PolicyKey, Status> statuses = new HashMap<>();
    /** Debts whose consumer reported nothing requested at the last look; forgotten if it still does at the next. */
    private final Set<CraftingReturnLedger.Owed> idle = new HashSet<>();
    private Object reconciledRegistry;
    private long reconciledTopology = Long.MIN_VALUE;
    private Object reconciledPolicies;
    private long reconciledWatermark = Long.MIN_VALUE;
    private long reconciledEpoch = Long.MIN_VALUE;
    private long ticks;
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

    private CraftingProjectionService(ServerLevel level) {
        this.level = level;
        federationDomains = new CraftingFederationDomainObserver(level);
    }

    public static synchronized CraftingProjectionService get(ServerLevel level) {
        return SERVICES.computeIfAbsent(level, CraftingProjectionService::new);
    }

    public static synchronized void reconcileIfPresent(ServerLevel level) {
        var service = SERVICES.get(level);
        if (service != null) service.reconcileAll();
    }

    public static synchronized void tick(ServerLevel level) {
        var service = SERVICES.get(level);
        if (service != null) service.tick();
    }

    public static synchronized void closeLevel(ServerLevel level) {
        var service = SERVICES.remove(level);
        if (service != null) service.close();
    }

    /** The rule's projection state as last reconciled; empty when this level has not looked at the rule. */
    public static synchronized Optional<Status> status(ServerLevel level, PolicyKey key) {
        var service = SERVICES.get(level);
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
                projection = new PatternProjection(real, (pushed, details, inputs) -> owe(pair, details, inputs));
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

    /** Records what the executing network now owes the consumer's CPU for one accepted push. */
    private void owe(Pair pair, IPatternDetails details, KeyCounter[] inputs) {
        var ledger = CraftingReturnLedger.get(level);
        var executing = pair.executing();
        var consumer = pair.consumer();
        for (var output : details.getOutputs()) {
            ledger.add(executing, consumer, output.what(), output.amount());
        }
        // Container items the inputs leave behind, such as empty buckets, which the CPU waits for as well.
        var patternInputs = details.getInputs();
        for (int index = 0; index < inputs.length && index < patternInputs.length; index++) {
            var input = patternInputs[index];
            for (var stack : inputs[index]) {
                var remaining = input.getRemainingKey(stack.getKey());
                if (remaining != null) {
                    ledger.add(executing, consumer, remaining, stack.getLongValue() / templateAmount(input, stack.getKey()));
                }
            }
        }
        var entry = projected.get(pair);
        if (entry != null && !routers.containsKey(entry.provider())) syncRouters();
    }

    private static long templateAmount(IPatternDetails.IInput input, AEKey key) {
        for (var template : input.getPossibleInputs()) {
            if (template.what().equals(key)) return Math.max(1, template.amount());
        }
        return 1;
    }

    /**
     * A router on every network that projects its providers or still owes returns. Each change remounts that
     * network's storage, so routers come and go only when a network starts or stops needing one.
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
        for (var grid : List.copyOf(routers.keySet())) {
            if (!wanted.containsKey(grid)) grid.getStorageService().removeGlobalStorageProvider(routers.remove(grid));
        }
        wanted.forEach((grid, network) -> {
            if (routers.containsKey(grid)) return;
            // An output may return only while the networks are still linked; otherwise it stays where it arrived.
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
     * Forgets debts whose loaded consumer has waited for nothing of that key on two looks in a row: its job finished
     * or was cancelled, and what still arrives stays on the executing network. A consumer that is not loaded keeps
     * its debts.
     */
    private void sweepLedger() {
        var ledger = CraftingReturnLedger.get(level);
        var seen = new HashSet<CraftingReturnLedger.Owed>();
        for (var owed : ledger.entries()) {
            var consumer = federationDomains.grid(owed.consumer()).orElse(null);
            if (consumer == null || CraftingReturnRouter.waiting(consumer, owed.key(), owed.amount()) > 0) continue;
            var marker = new CraftingReturnLedger.Owed(owed.executing(), owed.consumer(), owed.key(), 0);
            if (idle.contains(marker)) {
                ledger.drop(owed.executing(), owed.consumer(), owed.key());
            } else {
                seen.add(marker);
            }
        }
        idle.clear();
        idle.addAll(seen);
        if (routers.size() > 0) syncRouters();
    }

    @Override
    public void close() {
        projected.values().forEach(this::withdraw);
        projected.clear();
        routers.forEach((grid, router) -> grid.getStorageService().removeGlobalStorageProvider(router));
        routers.clear();
        statuses.clear();
        idle.clear();
        federationDomains.clear();
    }
}
