package space.controlnet.ae2federation.storage.dependency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.fabric.FabricId;
import space.controlnet.ae2federation.fabric.FabricReference;
import space.controlnet.ae2federation.fabric.FabricSourceId;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyFilter;
import space.controlnet.ae2federation.policy.PolicyFilterMode;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.storage.provenance.OriginNetworkId;
import space.controlnet.ae2federation.storage.provenance.SourceGeneration;

final class StorageDependencyCompilerTest {
    private static final NetworkId A = network(1);
    private static final NetworkId B = network(2);
    private static final NetworkId C = network(3);
    private static final NetworkId D = network(4);
    @Test
    void defaultOffReexportStopsTheFrontier() {
        var compilation = compile(Set.of(
                edge(B, A, 1, rule(Set.of(PolicyOperation.VIEW), PolicyFilter.allowAll(), false)),
                edge(C, B, 2, rule(Set.of(PolicyOperation.VIEW), PolicyFilter.allowAll(), true))));

        assertTrue(compilation.relationship(key(B, A)).isPresent());
        assertTrue(compilation.relationship(key(C, A)).isEmpty());
        assertFalse(PolicyRule.storageDefaults().allowReexport());
    }

    @Test
    void intersectsEachChainAndUnionsAlternativeDiamondScopes() {
        var compilation = compile(Set.of(
                edge(B, A, 1, rule(Set.of(PolicyOperation.VIEW, PolicyOperation.EXTRACT), denyNone(), true)),
                edge(C, A, 2, rule(Set.of(PolicyOperation.VIEW, PolicyOperation.INSERT), denyNone(), true)),
                edge(D, B, 3, rule(Set.of(PolicyOperation.VIEW), denyNone(), false)),
                edge(D, C, 4, rule(Set.of(PolicyOperation.VIEW, PolicyOperation.INSERT), denyNone(), false))));

        var relationship = compilation.relationship(key(D, A)).orElseThrow();
        assertEquals(2, relationship.minimumDepth());
        assertEquals(Set.of(PolicyOperation.VIEW, PolicyOperation.INSERT), relationship.authority().operations());
        assertEquals(PolicyFilterMode.DENY_LIST, relationship.authority().filter().mode());
        assertTrue(relationship.authority().filter().entries().isEmpty());
        assertEquals(1, compilation.relationships().keySet().stream().filter(key -> key.consumerNetworkId().equals(D)).count());
    }

    @Test
    void derivedPermissionDoesNotCreateOrActivateDirectPolicy() {
        var direct = new PolicyKey(C, A, PolicyCapability.STORAGE);
        var configured = Map.of(direct, new PolicyRule(false, Set.of(PolicyOperation.VIEW), PolicyFilter.allowAll(), true));
        var compilation = compile(Set.of(
                edge(B, A, 1, rule(Set.of(PolicyOperation.VIEW), PolicyFilter.allowAll(), true)),
                edge(C, B, 2, rule(Set.of(PolicyOperation.VIEW), PolicyFilter.allowAll(), false))));

        assertTrue(compilation.relationship(key(C, A)).isPresent());
        assertFalse(configured.get(direct).enabled());
        assertEquals(1, configured.size());
    }

    @Test
    void rejectsReturnToOriginAndConvergesWithoutCompletePaths() {
        var compiler = new StorageDependencyCompiler(new DependencyCompileBudget(8, 32));
        var compilation = compiler.compile(Set.of(source(A)), Set.of(
                edge(B, A, 1, rule(Set.of(PolicyOperation.VIEW), PolicyFilter.allowAll(), true)),
                edge(A, B, 2, rule(Set.of(PolicyOperation.VIEW), PolicyFilter.allowAll(), true))), 7, 11);

        assertTrue(compilation.relationship(key(A, A)).isEmpty());
        assertEquals(1, compilation.relationships().size());
        assertEquals(1, compilation.originCycleRejections());
        assertTrue(compilation.frontierRelaxations() <= 32);
    }

    @Test
    void candidateRevisionFailsClosedForEveryConstituentGeneration() {
        var relationship = compile(Set.of(
                edge(B, A, 1, rule(Set.of(PolicyOperation.VIEW), PolicyFilter.allowAll(), true)),
                edge(C, B, 2, rule(Set.of(PolicyOperation.VIEW), PolicyFilter.allowAll(), false))))
                .relationship(key(C, A)).orElseThrow();
        var revision = relationship.revision();
        var policies = Map.copyOf(revision.policyRevisions());

        assertTrue(revision.isCurrent(7, new SourceGeneration(1), policies::get, ignored -> true));
        assertFalse(revision.isCurrent(8, new SourceGeneration(1), policies::get, ignored -> true));
        assertFalse(revision.isCurrent(7, new SourceGeneration(2), policies::get, ignored -> true));
        assertFalse(revision.isCurrent(7, new SourceGeneration(1), key -> new PolicyRevision(99), ignored -> true));
        assertFalse(revision.isCurrent(7, new SourceGeneration(1), policies::get, ignored -> false));
    }

    private static DependencyCompilation compile(Set<DirectStorageDependency> edges) {
        return new StorageDependencyCompiler(new DependencyCompileBudget(32, 256))
                .compile(Set.of(source(A)), edges, 7, 11);
    }

    private static NativeSourceCandidate source(NetworkId network) {
        return new NativeSourceCandidate(new OriginNetworkId(network), new SourceGeneration(1));
    }

    private static DirectStorageDependency edge(NetworkId consumer, NetworkId provider, long revision, PolicyRule rule) {
        var source = new FabricSourceId("task23:fabric-" + revision);
        return new DirectStorageDependency(new PolicyKey(consumer, provider, PolicyCapability.STORAGE),
                new PolicyRevision(revision), rule, Set.of(new FabricReference(FabricId.direct(source), revision)));
    }

    private static EffectiveSourceRelationshipKey key(NetworkId consumer, NetworkId origin) {
        return new EffectiveSourceRelationshipKey(consumer, new OriginNetworkId(origin), PolicyCapability.STORAGE);
    }

    private static PolicyRule rule(Set<PolicyOperation> operations, PolicyFilter filter, boolean reexport) {
        return new PolicyRule(true, operations, filter, reexport);
    }

    private static PolicyFilter denyNone() {
        return new PolicyFilter(PolicyFilterMode.DENY_LIST, Set.of());
    }


    private static NetworkId network(long value) {
        return new NetworkId(new UUID(0, value));
    }
}
