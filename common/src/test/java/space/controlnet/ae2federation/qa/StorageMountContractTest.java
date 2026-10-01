package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class StorageMountContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void relationshipMountUsesNativeStorageAndDynamicPolicyAuthority() throws IOException {
        var projection = source("storage/mount/AuthorizedStorageProjection.java");
        var authority = source("storage/mount/StorageRelationshipAuthority.java");
        var effectiveAuthority = source("storage/dependency/EffectiveStorageAuthority.java");
        assertTrue(projection.contains("MEStorage.checkPreconditions"));
        assertTrue(projection.contains("delegate.insert(what, amount, mode, source)"));
        assertTrue(projection.contains("delegate.extract(what, amount, mode, source)"));
        assertTrue(authority.contains("EffectiveSourceRelationship"));
        assertTrue(authority.contains("readyAuthority.get()"));
        assertTrue(authority.contains("PolicyOperation.VIEW"));
        assertTrue(effectiveAuthority.contains("PolicyFilterMode.ALLOW_LIST"));
    }

    @Test
    void relationshipRegistryDeduplicatesAndUsesQualifiedNodeCallbacks() throws IOException {
        var mounts = source("storage/mount/StorageMountService.java");
        var federationDomains = source("storage/mount/StorageFederationDomainObserver.java");
        var provenance = source("storage/provenance/NativeSourceDomainRegistry.java");
        // Discovery reads AE2's real mount table (node and global providers) instead of scanning Grid nodes and
        // replaying provider callbacks.
        assertTrue(provenance.contains("NativeMountLedger.snapshot(service)"));
        assertTrue(provenance.contains("snapshot.nodeProviders()"));
        assertTrue(provenance.contains("snapshot.globalProviders()"));
        assertTrue(provenance.contains("cached.stamp().matches(grid, service)"));
        assertFalse(provenance.contains("grid.getNodes()"));
        assertFalse(provenance.contains(".qualify("));
        assertFalse(provenance.contains("mountInventories("));
        assertTrue(mounts.contains("addGlobalStorageProvider"));
        assertTrue(mounts.contains("removeGlobalStorageProvider"));
        assertTrue(mounts.contains("Map<PolicyKey, MountedStorageRelationship>"));
        var dependencies = source("storage/mount/StorageDependencyIndex.java");
        assertTrue(dependencies.contains("catch (ProvenanceException | StorageProvenanceException exception)"));
        assertTrue(dependencies.contains(
                "relationship.revision().isCurrent(domain.generation(), policies::revision, registry::isCurrent)"));
        // Every operation re-evaluates its authority: in full, or, while nothing Federation-side changed since a full
        // pass (AuthorityEpoch), by matching the source Grid's native state again.
        assertTrue(mounts.contains("sourceCurrent(mounted, check)"));
        assertTrue(mounts.contains("dependencies.current(candidate, domain, check)"));
        assertTrue(mounts.contains("check.stillAuthorized(provenance)"));
        assertTrue(dependencies.contains("authorizedEpoch == AuthorityEpoch.current() && provenance.stillMatches(probe)"));
        assertTrue(provenance.contains("cached.stamp().matches(probe.grid, probe.service)"));
        assertTrue(mounts.contains("public MountGeneration mountGeneration"));
        assertTrue(mounts.contains("removedProviderCount++"));
        assertTrue(federationDomains.contains("FederationDomainRegistryAccess.get(level).federationDomains()"));
        assertTrue(federationDomains.contains("federationDomain.memberships().keySet()"));
    }

    @Test
    void topologyAndLevelLifecycleDriveReconciliationAndCleanup() throws IOException {
        var cable = source("router/FederationCableBlockEntity.java");
        var mounts = source("storage/mount/StorageMountService.java");
        var lifecycle = source("storage/mount/StorageLevelLifecycle.java");
        var registries = source("domain/FederationDomainRegistryAccess.java");
        var entrypoint = Files.readString(ROOT.resolve(
                "neoforge-1.21.1/src/main/java/space/controlnet/ae2federation/neoforge/NeoForgeEntrypoint.java"));
        var refresh = source("domain/FederationBindingRefresh.java");
        // A Cable's topology change reaches Storage through the coalesced binding refresh, which the level flushes.
        assertTrue(cable.contains("FederationBindingRefresh.request(serverLevel)"));
        assertTrue(refresh.contains("StorageMountService.reconcileIfPresent(level)"));
        assertTrue(entrypoint.contains("FederationBindingRefresh.flush(level)"));
        assertTrue(entrypoint.contains("FederationBindingRefresh.closeLevel(level)"));
        assertTrue(mounts.contains("SERVICES.remove(level)"));
        assertTrue(mounts.contains("mountedProvidersRemoved"));
        assertTrue(registries.contains("removedRegisteredInstance"));
        assertTrue(lifecycle.contains("StorageMountService.closeLevel(level)"));
        assertTrue(lifecycle.contains("FederationDomainRegistryAccess.closeLevel(level)"));
        assertTrue(entrypoint.contains("LevelEvent.Unload"));
        assertTrue(entrypoint.contains("StorageLevelLifecycle.close(level)"));
    }

    @Test
    void taskTwentyOneCasesHaveDedicatedSemanticEvidenceGate() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var qa = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        for (var caseId : new String[] {"storage.native-access", "storage.priority", "storage.view-only",
                "storage.simulate", "storage.reject-revoked"}) {
            assertTrue(manifest.contains("\"id\":\"" + caseId + "\""));
        }
        assertTrue(qa.contains("verifyTaskTwentyOneEvidence"));
        assertTrue(qa.contains("federationTaskTwentyOneEvidenceSelfTest"));
        assertTrue(qa.contains("Task 21 runtime trace mismatch"));
        assertTrue(qa.contains("providerQuantity"));
        assertTrue(qa.contains("consumerVisibleQuantity"));
        assertTrue(qa.contains("consumerDeduplicatedCapacity"));
        assertTrue(qa.contains("cleanupMountedProvidersRemoved"));
        assertTrue(qa.contains("Task 21 level cleanup semantics are incomplete"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(ROOT.resolve("common/src/main/java/space/controlnet/ae2federation/" + relative));
    }
}
