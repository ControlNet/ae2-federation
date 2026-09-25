package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

final class PolicyContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void manifestRegistersExactlyFiveTaskFourteenCases() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var entries = Pattern.compile("\\{[^{}]*\"id\":\"(policy\\.[^\"]+)\"[^{}]*}").matcher(manifest);
        var cases = new TreeSet<String>();
        while (entries.find()) {
            assertTrue(cases.add(entries.group(1)));
            assertTrue(entries.group().contains("\"backend\":\"gametest"));
            assertTrue(Pattern.compile("\"testId\":\"policy[a-z]+\"").matcher(entries.group()).find());
        }
        assertEquals(Set.of("policy.lifecycle-matrix", "policy.new-bridge-restore", "policy.reject-stale-edit",
                "policy.delete-reconnect", "policy.sparse-scale"), cases);
    }

    @Test
    void persistenceStoresOnlyStablePolicyConfiguration() throws IOException {
        var codec = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/persistence/PolicyStateCodec.java"));
        var savedData = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/persistence/PolicySavedData.java"));
        assertTrue(codec.contains("consumer"));
        assertTrue(codec.contains("provider"));
        assertTrue(codec.contains("capability"));
        assertTrue(codec.contains("allowReexport"));
        assertTrue(codec.contains("deleted"));
        assertTrue(savedData.contains("getServer().overworld().getDataStorage().computeIfAbsent"));
        assertFalse(codec.contains("IGrid"));
        assertFalse(codec.contains("FederationDomainId"));
    }

    @Test
    void runtimeAndEvidenceBindTaskFourteenSemantics() throws IOException {
        var service = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/policy/PolicyService.java"));
        var activation = Files.readString(ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/policy/PolicyActivation.java"));
        var script = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        assertTrue(service.contains("NetworkIdentityService.class"));
        assertTrue(service.contains("FederationDomainRegistryAccess.get(level)"));
        assertTrue(activation.contains("Collections.disjoint"));
        assertFalse(activation.contains("getGrid() =="));
        assertTrue(script.contains("verifyTaskFourteenEvidence"));
        assertTrue(script.contains("federationTaskFourteenEvidenceSelfTest"));
        assertTrue(script.contains("AE2F_POLICY_NATIVE_TRACE"));
    }
}
