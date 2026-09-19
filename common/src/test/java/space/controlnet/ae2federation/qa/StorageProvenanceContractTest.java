package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class StorageProvenanceContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void provenanceUsesTypedCallbackOwnedIdentityAndGeneration() throws IOException {
        var provenance = source("storage/provenance/NativeSourceDomainRegistry.java");
        var nativeBoundary = source("ae2/storage/NativeStorageProvenance.java");
        var source = source("storage/provenance/ExportSource.java");
        var alias = source("storage/provenance/SourceAlias.java");
        assertTrue(provenance.contains("NetworkIdentityService.class"));
        assertTrue(provenance.contains("NativeStorageProvenance"));
        assertTrue(nativeBoundary.contains("provider.mountInventories"));
        assertTrue(provenance.contains("OPAQUE_EXTERNAL_ALIAS"));
        assertTrue(provenance.contains("new CallbackEntry(callbackIndex, entry)"));
        assertTrue(provenance.contains("entry.callbackIndex()"));
        assertTrue(source.contains("ExportSourceId"));
        assertTrue(source.contains("SourceGeneration"));
        assertTrue(alias.contains("SourceAliasId"));
    }

    @Test
    void mountLifecycleExcludesImportsAndInvalidatesStaleHandles() throws IOException {
        var mounts = source("storage/mount/StorageMountService.java");
        var relationship = source("storage/mount/MountedStorageRelationship.java");
        var provider = source("storage/mount/RelationshipStorageProvider.java");
        assertTrue(mounts.contains("NativeSourceDomainRegistry"));
        assertTrue(mounts.contains("mountGenerations"));
        assertTrue(mounts.contains("mounts.get(mounted.relationship().key()) != mounted"));
        assertTrue(relationship.contains("MountGeneration"));
        assertTrue(provider.contains("FederationManagedStorageProvider"));
    }

    @Test
    void nativeFixtureReadinessIncludesSettledDiscoveryOrigin() throws IOException {
        var fixture = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/storage/ProvenanceStorageFixture.java"));
        assertTrue(fixture.contains("FabricRegistryAccess.confirmedNetworkId(grid()).isPresent()"),
                "Native fixture readiness must include the exact settled-origin precondition used by discovery");
    }

    @Test
    void taskTwentyTwoHasExactNativeAndAdversarialEvidenceGates() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var qa = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        for (var caseId : new String[] {"provenance.multi-entry", "provenance.native-rebind",
                "provenance.exclude-import", "provenance.opaque-boundary"}) {
            assertTrue(manifest.contains("\"id\":\"" + caseId + "\""));
        }
        assertTrue(qa.contains("verifyTaskTwentyTwoEvidence"));
        assertTrue(qa.contains("federationTaskTwentyTwoEvidenceSelfTest"));
        assertTrue(qa.contains("Task 22 native rebound identity or generation semantics are incomplete"));
        assertTrue(qa.contains("duplicate-origins"));
        assertTrue(qa.contains("opaque-wrapper-acceptance"));
        assertTrue(qa.contains("cleanup-omission"));
        assertTrue(qa.contains("callback-slot-collision-acceptance"));
        assertTrue(qa.contains("stale-old-projection-acceptance"));
        assertTrue(qa.contains("Task 22 callback-slot continuity semantics are incomplete"));
        assertTrue(qa.contains("Task 22 stale old-projection isolation semantics are incomplete"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(ROOT.resolve("common/src/main/java/space/controlnet/ae2federation/" + relative));
    }
}
