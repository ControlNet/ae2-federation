package space.controlnet.ae2federation.storage.mount;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.ae2.storage.StorageProvenanceException;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
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
import space.controlnet.ae2federation.storage.provenance.OriginNetworkId;
import space.controlnet.ae2federation.storage.provenance.ProvenanceDiagnostic;
import space.controlnet.ae2federation.storage.provenance.ProvenanceException;

final class StorageDependencyIndex {
    private final ServerLevel level;
    private final StorageFabricObserver fabrics;
    private final NativeSourceDomainRegistry provenance;
    private final StorageDependencyCompiler compiler = new StorageDependencyCompiler(DependencyCompileBudget.standard());
    private Map<PolicyKey, StorageRelationship> directRelationships = Map.of();
    private Map<OriginNetworkId, NativeSourceDomain> domains = Map.of();
    private Map<PolicyKey, ProvenanceDiagnostic> diagnostics = Map.of();
    private DependencyCompilation compilation = new DependencyCompilation(Map.of(), 0, 0);
    private long compilationRevision;

    StorageDependencyIndex(ServerLevel level, StorageFabricObserver fabrics, NativeSourceDomainRegistry provenance) {
        this.level = level;
        this.fabrics = fabrics;
        this.provenance = provenance;
    }

    void refresh() {
        directRelationships = fabrics.relationships();
        var nextDomains = new HashMap<OriginNetworkId, NativeSourceDomain>();
        var nextDiagnostics = new HashMap<PolicyKey, ProvenanceDiagnostic>();
        for (var entry : fabrics.loadedGrids().entrySet()) {
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
            var references = fabrics.references(relationship);
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
            compilation = compiler.compile(sources, dependencies, fabrics.topologyRevision(), ++compilationRevision);
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

    NativeSourceDomain domain(OriginNetworkId origin) {
        return domains.get(origin);
    }

    IGrid grid(NetworkId networkId) {
        return fabrics.loadedGrids().get(networkId);
    }

    ProvenanceDiagnostic diagnostic(PolicyKey key) {
        return diagnostics.get(key);
    }

    boolean current(EffectiveSourceRelationship relationship, NativeSourceDomain domain) {
        if (compilation.relationships().get(relationship.key()) != relationship
                || domains.get(domain.origin()) != domain
                || !relationship.revision().isCurrent(fabrics.topologyRevision(), domain.generation(),
                        key -> PolicyService.get(level).revision(key),
                        reference -> FabricRegistryAccess.get(level).isCurrent(reference))) {
            return false;
        }
        var policies = PolicyService.get(level);
        return relationship.revision().policyRevisions().keySet().stream().allMatch(key -> {
            var direct = directRelationships.get(key);
            return direct != null && directActive(policies, direct);
        });
    }

    boolean sourceCurrent(NativeSourceDomain domain) {
        if (!ready(domain) || domains.get(domain.origin()) != domain) {
            return false;
        }
        try {
            return provenance.discover(domain.runtimeGrid()) == domain && provenance.isCurrent(domain);
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
        return policies.activation(relationship.key(), new PolicyRuntimeEndpoints(relationship.consumerGrid(),
                relationship.providerGrid(), BackendStatus.READY)) == PolicyActivationState.ACTIVE;
    }

    private static boolean ready(NativeSourceDomain domain) {
        return !domain.sourceNodes().isEmpty() && domain.sourceNodes().stream()
                .allMatch(node -> node.isActive() && node.hasGridBooted() && node.getGrid() == domain.runtimeGrid());
    }
}
