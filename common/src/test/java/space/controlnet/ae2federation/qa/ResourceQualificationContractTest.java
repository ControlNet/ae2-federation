package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class ResourceQualificationContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void taskTwentyFiveHasExactCasesAndQualifiedAddonExecution() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var qa = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        for (var caseId : new String[] {"resources.item-fluid-components", "resources.stored-fe",
                "resources.optional-absent", "resources.reject-overflow", "resources.reject-fe-power-coupling"}) {
            assertTrue(manifest.contains("\"id\":\"" + caseId + "\""));
        }
        assertTrue(qa.contains("verifyTaskTwentyFiveEvidence"));
        assertTrue(qa.contains("federationTaskTwentyFiveEvidenceSelfTest"));
        assertTrue(qa.contains("runAppfluxGameTestServer"));
        assertTrue(qa.contains("a54eafb72d72bd259bc3b5fa226b4f5542c4c3c4"));
    }

    @Test
    void defaultBuildHasNoAppliedFluxLinkage() throws IOException {
        var build = Files.readString(ROOT.resolve("neoforge-1.21.1/build.gradle"));
        var production = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/storage/resources/NativeResourceAmounts.java"));
        assertFalse(build.contains("implementation(\"maven.modrinth:appflux"));
        assertFalse(build.contains("runtimeOnly(\"maven.modrinth:appflux"));
        assertFalse(production.contains("com.glodblock.github.appflux"));
        assertTrue(build.contains("appliedFluxCompatibility"));
        assertTrue(build.contains("sourceSets.create('appfluxTest')"));
        assertTrue(build.contains("enableAppfluxCompatibility"));
    }
}
