package space.controlnet.ae2federation.storage.dependency;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import space.controlnet.ae2federation.domain.FederationDomainReference;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.storage.provenance.OriginNetworkId;

public final class StorageDependencyCompiler {
    private final DependencyCompileBudget budget;

    public StorageDependencyCompiler(DependencyCompileBudget budget) {
        this.budget = java.util.Objects.requireNonNull(budget);
    }

    public DependencyCompilation compile(Set<NativeSourceCandidate> sources, Set<DirectStorageDependency> dependencies,
            long topologyRevision, long compilationRevision) {
        var outgoing = new HashMap<NetworkId, ArrayList<DirectStorageDependency>>();
        dependencies.forEach(dependency -> outgoing.computeIfAbsent(dependency.key().providerNetworkId(),
                ignored -> new ArrayList<>()).add(dependency));
        var relationships = new LinkedHashMap<EffectiveSourceRelationshipKey, EffectiveSourceRelationship>();
        var totalRelaxations = 0;
        var totalCycles = 0;
        var origins = new HashSet<OriginNetworkId>();
        for (var source : sources) {
            if (!origins.add(source.origin())) {
                throw new IllegalArgumentException("One source generation is allowed per origin");
            }
            var result = compileSource(source, outgoing, topologyRevision, compilationRevision);
            totalRelaxations = Math.addExact(totalRelaxations, result.relaxations());
            totalCycles = Math.addExact(totalCycles, result.cycles());
            result.states().forEach((network, state) -> {
                if (!network.equals(source.origin().value()) && !state.authority().isEmpty()) {
                    var key = new EffectiveSourceRelationshipKey(network, source.origin(), PolicyCapability.STORAGE);
                    var revision = new CandidateRelationshipRevision(compilationRevision, topologyRevision,
                            source.generation(), state.policyRevisions(), state.federationDomainReferences());
                    relationships.put(key, new EffectiveSourceRelationship(key, state.authority(),
                            state.transitAuthority(), revision, state.minimumDepth()));
                }
            });
            if (relationships.size() > budget.maxRelationships()) {
                throw new DependencyCompileException("Effective relationship budget exhausted");
            }
        }
        return new DependencyCompilation(relationships, totalRelaxations, totalCycles);
    }

    private SourceResult compileSource(NativeSourceCandidate source,
            Map<NetworkId, ArrayList<DirectStorageDependency>> outgoing, long topologyRevision,
            long compilationRevision) {
        var states = new HashMap<NetworkId, FrontierState>();
        states.put(source.origin().value(), FrontierState.origin());
        var queue = new ArrayDeque<NetworkId>();
        var queued = new HashSet<NetworkId>();
        queue.add(source.origin().value());
        queued.add(source.origin().value());
        var rejectedCycles = new HashSet<PolicyKey>();
        var relaxations = 0;
        while (!queue.isEmpty()) {
            var provider = queue.removeFirst();
            queued.remove(provider);
            var state = states.get(provider);
            for (var dependency : outgoing.getOrDefault(provider, new ArrayList<>())) {
                if (++relaxations > budget.maxFrontierRelaxations()) {
                    throw new DependencyCompileException("Dependency frontier relaxation budget exhausted");
                }
                if (dependency.key().consumerNetworkId().equals(source.origin().value())) {
                    rejectedCycles.add(dependency.key());
                    continue;
                }
                var authority = state.transitAuthority().intersect(EffectiveStorageAuthority.from(dependency.rule()));
                if (authority.isEmpty()) {
                    continue;
                }
                var consumer = dependency.key().consumerNetworkId();
                var previous = states.getOrDefault(consumer, FrontierState.empty());
                var next = previous.merge(authority, dependency.rule().allowReexport(), state, dependency);
                if (!next.equals(previous)) {
                    states.put(consumer, next);
                    if (queued.add(consumer)) {
                        queue.addLast(consumer);
                    }
                }
            }
        }
        return new SourceResult(states, relaxations, rejectedCycles.size());
    }

    private record SourceResult(Map<NetworkId, FrontierState> states, int relaxations, int cycles) {
    }

    private record FrontierState(EffectiveStorageAuthority authority, EffectiveStorageAuthority transitAuthority,
            Map<PolicyKey, PolicyRevision> policyRevisions, Set<FederationDomainReference> federationDomainReferences, int minimumDepth) {
        static FrontierState origin() {
            return new FrontierState(EffectiveStorageAuthority.empty(), EffectiveStorageAuthority.unbounded(),
                    Map.of(), Set.of(), 0);
        }

        static FrontierState empty() {
            return new FrontierState(EffectiveStorageAuthority.empty(), EffectiveStorageAuthority.empty(),
                    Map.of(), Set.of(), Integer.MAX_VALUE);
        }

        FrontierState merge(EffectiveStorageAuthority candidate, boolean reexport, FrontierState provider,
                DirectStorageDependency dependency) {
            var revisions = new HashMap<>(policyRevisions);
            revisions.putAll(provider.policyRevisions);
            revisions.put(dependency.key(), dependency.policyRevision());
            var federationDomains = new HashSet<>(federationDomainReferences);
            federationDomains.addAll(provider.federationDomainReferences);
            federationDomains.addAll(dependency.federationDomainReferences());
            var transit = reexport ? transitAuthority.union(candidate) : transitAuthority;
            return new FrontierState(authority.union(candidate), transit, revisions, federationDomains,
                    Math.min(minimumDepth, provider.minimumDepth + 1));
        }
    }
}
