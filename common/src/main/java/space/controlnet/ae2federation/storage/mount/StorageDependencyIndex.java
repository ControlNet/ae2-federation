package space.controlnet.ae2federation.storage.mount;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.ae2.storage.StorageProvenanceException;
import space.controlnet.ae2federation.domain.FederationDomainRegistry;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.IdentityEpoch;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.policy.BackendStatus;
import space.controlnet.ae2federation.policy.PolicyActivationState;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRecord;
import space.controlnet.ae2federation.policy.PolicyRuntimeEndpoints;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.dependency.DependencyCompilation;
import space.controlnet.ae2federation.storage.dependency.DependencyCompileBudget;
import space.controlnet.ae2federation.storage.dependency.DependencyCompileException;
import space.controlnet.ae2federation.storage.dependency.DirectStorageDependency;
import space.controlnet.ae2federation.storage.dependency.EffectiveSourceRelationship;
import space.controlnet.ae2federation.storage.dependency.EffectiveSourceRelationshipKey;
import space.controlnet.ae2federation.storage.dependency.NativeSourceCandidate;
import space.controlnet.ae2federation.storage.dependency.StorageDependencyCompiler;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomain;
import space.controlnet.ae2federation.storage.provenance.NativeSourceDomainRegistry;
import space.controlnet.ae2federation.storage.provenance.NodeActivity;
import space.controlnet.ae2federation.storage.provenance.OriginNetworkId;
import space.controlnet.ae2federation.storage.provenance.ProvenanceDiagnostic;
import space.controlnet.ae2federation.storage.provenance.ProvenanceException;

final class StorageDependencyIndex {
    private final ServerLevel level;
    private final StorageFederationDomainObserver federationDomains;
    private final NativeSourceDomainRegistry provenance;
    private final StorageDependencyCompiler compiler = new StorageDependencyCompiler(DependencyCompileBudget.standard());
    private Map<PolicyKey, StorageRelationship> directRelationships = Map.of();
    private Map<OriginNetworkId, NativeSourceDomain> domains = Map.of();
    private Map<PolicyKey, ProvenanceDiagnostic> diagnostics = Map.of();
    private DependencyCompilation compilation = new DependencyCompilation(Map.of(), 0, 0);
    private long compilationRevision;
    private long refreshCount;

    StorageDependencyIndex(ServerLevel level, StorageFederationDomainObserver federationDomains, NativeSourceDomainRegistry provenance) {
        this.level = level;
        this.federationDomains = federationDomains;
        this.provenance = provenance;
    }

    void refresh() {
        refreshCount = Math.incrementExact(refreshCount);
        directRelationships = federationDomains.relationships();
        var nextDomains = new HashMap<OriginNetworkId, NativeSourceDomain>();
        var nextDiagnostics = new HashMap<PolicyKey, ProvenanceDiagnostic>();
        for (var entry : federationDomains.loadedGrids().entrySet()) {
            try {
                var domain = provenance.discover(entry.getValue());
                if (!domain.sources().isEmpty() && ready(domain)) {
                    nextDomains.put(domain.origin(), domain);
                }
            } catch (ProvenanceException exception) {
                directRelationships.values().stream()
                        .filter(relationship -> relationship.key().providerNetworkId().equals(entry.getKey()))
                        .forEach(relationship -> nextDiagnostics.put(relationship.key(), exception.diagnostic()));
            } catch (StorageProvenanceException exception) {
            }
        }
        domains = Map.copyOf(nextDomains);
        diagnostics = Map.copyOf(nextDiagnostics);
        var policies = PolicyService.get(level);
        var dependencies = new HashSet<DirectStorageDependency>();
        for (var relationship : directRelationships.values()) {
            var configured = policies.configured(relationship.key()).orElse(null);
            var references = federationDomains.references(relationship);
            if (configured != null && configured.rule().enabled() && !references.isEmpty()
                    && directActive(policies, relationship)) {
                dependencies.add(new DirectStorageDependency(relationship.key(), configured.revision(),
                        configured.rule(), references));
            }
        }
        var sources = domains.values().stream()
                .map(domain -> new NativeSourceCandidate(domain.origin(), domain.generation()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        try {
            compilation = compiler.compile(sources, dependencies, federationDomains.topologyRevision(), ++compilationRevision);
        } catch (DependencyCompileException exception) {
            compilation = new DependencyCompilation(Map.of(), 0, 0);
        }
    }

    Map<EffectiveSourceRelationshipKey, EffectiveSourceRelationship> relationships() {
        return compilation.relationships();
    }

    EffectiveSourceRelationship relationship(EffectiveSourceRelationshipKey key) {
        return compilation.relationships().get(key);
    }

    EffectiveSourceRelationship relationship(PolicyKey key) {
        return compilation.relationships().get(new EffectiveSourceRelationshipKey(key.consumerNetworkId(),
                new OriginNetworkId(key.providerNetworkId()), key.capability()));
    }

    int frontierRelaxations() {
        return compilation.frontierRelaxations();
    }

    int originCycleRejections() {
        return compilation.originCycleRejections();
    }

    long refreshCount() {
        return refreshCount;
    }

    NativeSourceDomain domain(OriginNetworkId origin) {
        return domains.get(origin);
    }

    IGrid grid(NetworkId networkId) {
        return federationDomains.loadedGrids().get(networkId);
    }

    ProvenanceDiagnostic diagnostic(PolicyKey key) {
        return diagnostics.get(key);
    }

    boolean current(EffectiveSourceRelationship relationship, NativeSourceDomain domain) {
        return current(relationship, domain, null);
    }

    /** As {@link #current(EffectiveSourceRelationship, NativeSourceDomain)}, remembering a pass in {@code check}. */
    boolean current(EffectiveSourceRelationship relationship, NativeSourceDomain domain,
            @org.jetbrains.annotations.Nullable CurrentCheck check) {
        var policies = PolicyService.get(level);
        var registry = FederationDomainRegistryAccess.get(level);
        var topology = registry.topologyRevision();
        var watermark = policies.highWatermark();
        if (check != null && check.passedAt(relationship, domain, compilation, domains, directRelationships, registry,
                topology, policies, watermark)) {
            return check.identitiesMatch(policies);
        }
        if (compilation.relationships().get(relationship.key()) != relationship
                || domains.get(domain.origin()) != domain) {
            return false;
        }
        if (!relationship.revision().isCurrent(domain.generation(), policies::revision, registry::isCurrent)) {
            return false;
        }
        for (var key : relationship.revision().policyRevisions().keySet()) {
            var direct = directRelationships.get(key);
            if (direct == null || !directActive(policies, direct, registry)) {
                return false;
            }
        }
        if (check != null) {
            check.passed(relationship, domain, compilation, domains, directRelationships, registry, topology, policies,
                    watermark);
        }
        return true;
    }

    /** {@code key}'s relationship in the current compilation, looked up once per compilation for {@code check}. */
    EffectiveSourceRelationship relationship(EffectiveSourceRelationshipKey key, CurrentCheck check) {
        var current = compilation;
        if (check.lookedUpIn != current) {
            check.lookedUp = current.relationships().get(key);
            check.lookedUpIn = current;
        }
        return check.lookedUp;
    }

    /**
     * One mount's record of the last {@link #current} check it passed. The compilation, domain and direct
     * relationship maps are immutable and replaced whole on refresh, the registry's domains change only with its
     * topology revision, and rules only with the policy watermark; while all of them are the ones that check saw,
     * its result stands except for the direct relationships' Grid identities, which are read again unless no
     * settlement changed since they matched ({@link IdentityEpoch}).
     */
    static final class CurrentCheck {
        private final NativeSourceDomainRegistry.Probe probe = new NativeSourceDomainRegistry.Probe();
        /** {@link StorageMountService}'s mount revision when this check's mount last found itself mounted. */
        long mountsRevision = -1;
        private Map<OriginNetworkId, NativeSourceDomain> sourceDomains;
        private NativeSourceDomain sourceDomain;
        private DependencyCompilation lookedUpIn;
        private EffectiveSourceRelationship lookedUp;
        private EffectiveSourceRelationship relationship;
        private NativeSourceDomain domain;
        private DependencyCompilation compilation;
        private Map<OriginNetworkId, NativeSourceDomain> domains;
        private Map<PolicyKey, StorageRelationship> directRelationships;
        private FederationDomainRegistry registry;
        private long topology;
        private PolicyService policies;
        private long watermark;
        private PolicyKey[] keys;
        private NetworkIdentityService[] consumers;
        private NetworkIdentityService[] providers;
        /** {@link IdentityEpoch} when every direct relationship's Grids last matched their key. */
        private long matchedEpoch = -1;

        private boolean passedAt(EffectiveSourceRelationship relationship, NativeSourceDomain domain,
                DependencyCompilation compilation, Map<OriginNetworkId, NativeSourceDomain> domains,
                Map<PolicyKey, StorageRelationship> directRelationships, FederationDomainRegistry registry,
                long topology, PolicyService policies, long watermark) {
            return relationship == this.relationship && domain == this.domain && compilation == this.compilation
                    && domains == this.domains && directRelationships == this.directRelationships
                    && registry == this.registry && topology == this.topology && policies == this.policies
                    && watermark == this.watermark;
        }

        private void passed(EffectiveSourceRelationship relationship, NativeSourceDomain domain,
                DependencyCompilation compilation, Map<OriginNetworkId, NativeSourceDomain> domains,
                Map<PolicyKey, StorageRelationship> directRelationships, FederationDomainRegistry registry,
                long topology, PolicyService policies, long watermark) {
            var policyKeys = relationship.revision().policyRevisions().keySet();
            var nextKeys = new PolicyKey[policyKeys.size()];
            var nextConsumers = new NetworkIdentityService[nextKeys.length];
            var nextProviders = new NetworkIdentityService[nextKeys.length];
            var index = 0;
            for (var key : policyKeys) {
                var direct = directRelationships.get(key);
                nextKeys[index] = key;
                nextConsumers[index] = direct.consumerGrid().getService(NetworkIdentityService.class);
                nextProviders[index] = direct.providerGrid().getService(NetworkIdentityService.class);
                index++;
            }
            keys = nextKeys;
            consumers = nextConsumers;
            providers = nextProviders;
            matchedEpoch = -1;
            this.relationship = relationship;
            this.domain = domain;
            this.compilation = compilation;
            this.domains = domains;
            this.directRelationships = directRelationships;
            this.registry = registry;
            this.topology = topology;
            this.policies = policies;
            this.watermark = watermark;
        }

        private boolean identitiesMatch(PolicyService policies) {
            // No settlement changed since every pair last matched, so each would return what matched.
            var epoch = IdentityEpoch.current();
            if (epoch == matchedEpoch) {
                return true;
            }
            for (var index = 0; index < keys.length; index++) {
                if (!policies.identitiesMatch(keys[index], consumers[index], providers[index])) {
                    return false;
                }
            }
            matchedEpoch = epoch;
            return true;
        }
    }

    boolean sourceCurrent(NativeSourceDomain domain) {
        return sourceCurrent(domain, null);
    }

    /** As {@link #sourceCurrent(NativeSourceDomain)}, with the lookups {@code check} remembers for its mount. */
    boolean sourceCurrent(NativeSourceDomain domain, @org.jetbrains.annotations.Nullable CurrentCheck check) {
        if (domain.sources().isEmpty()) {
            return false;
        }
        // domains is immutable and replaced whole, so a domain it held once it holds until the next refresh.
        if (check == null || check.sourceDomains != domains || check.sourceDomain != domain) {
            if (domains.get(domain.origin()) != domain) {
                return false;
            }
            if (check != null) {
                check.sourceDomains = domains;
                check.sourceDomain = domain;
            }
        }
        try {
            // discover returns only the origin's current domain, so equality also proves provenance.isCurrent. It
            // returns this domain only while its capture stamp matches, and the stamp holds each source node with the
            // activity it had when captured (active), so a match re-checks every source node's readiness now; a
            // separate scan of the same nodes would repeat that.
            var grid = domain.runtimeGrid();
            return (check != null ? provenance.discover(grid, check.probe) : provenance.discover(grid)) == domain;
        } catch (ProvenanceException | StorageProvenanceException exception) {
            return false;
        }
    }

    void clear() {
        directRelationships = Map.of();
        domains = Map.of();
        diagnostics = Map.of();
        compilation = new DependencyCompilation(Map.of(), 0, 0);
    }

    private boolean directActive(PolicyService policies, StorageRelationship relationship) {
        return directActive(policies, relationship, FederationDomainRegistryAccess.get(level));
    }

    private static boolean directActive(PolicyService policies, StorageRelationship relationship,
            FederationDomainRegistry registry) {
        return policies.activation(relationship.key(), new PolicyRuntimeEndpoints(relationship.consumerGrid(),
                relationship.providerGrid(), BackendStatus.READY), registry) == PolicyActivationState.ACTIVE;
    }

    /**
     * O(source nodes) readiness. Global-provider sources have no node; their lifecycle is AE2's global provider
     * registration, which the native mount ledger already stamps.
     */
    private static boolean ready(NativeSourceDomain domain) {
        if (domain.sources().isEmpty()) {
            return false;
        }
        var nodes = domain.sourceNodes();
        if (nodes.isEmpty()) {
            return true;
        }
        var grid = domain.runtimeGrid();
        var booted = NodeActivity.gridBooted(grid);
        for (var node : nodes) {
            if (!NodeActivity.activeOn(node, grid, booted)) {
                return false;
            }
        }
        return true;
    }
}
