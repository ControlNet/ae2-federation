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

final class NativeCraftingBindingContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void manifestRegistersFiveExecutableNativeCraftingCases() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var entries = Pattern.compile("\\{[^{}]*\"id\":\"(craft-proof\\.[^\"]+)\"[^{}]*}").matcher(manifest);
        var cases = new TreeSet<String>();
        while (entries.find()) {
            assertTrue(cases.add(entries.group(1)));
            assertTrue(entries.group().contains("\"backend\":\"gametest\""));
            assertTrue(Pattern.compile("\"testId\":\"craftproof[a-z]+\"").matcher(entries.group()).find());
            assertTrue(Pattern.compile("\"assertions\":[1-9][0-9]*").matcher(entries.group()).find());
        }
        assertEquals(Set.of("craft-proof.native-terminal", "craft-proof.native-stocking",
                "craft-proof.missing-material", "craft-proof.no-cpu", "craft-proof.disconnect-cancel-restart"), cases);
    }

    @Test
    void testmodUsesLockedPublicLifecycleAndPhysicalAe2Machines() throws IOException {
        var fixture = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/crafting/NativeCraftingFixtures.java"));
        var requester = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/crafting/NativeCraftingRequester.java"));
        var tests = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/NativeCraftingGameTests.java"))
                + Files.readString(ROOT.resolve(
                        "common/src/testmod/java/space/controlnet/ae2federation/test/NativeCraftingFailureGameTests.java"));
        var testMod = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/FederationTestMod.java"));
        assertTrue(fixture.contains("AEBlocks.CRAFTING_STORAGE_1K"));
        assertTrue(fixture.contains("AEBlocks.PATTERN_PROVIDER"));
        assertTrue(fixture.contains("AEBlocks.MOLECULAR_ASSEMBLER"));
        assertTrue(fixture.contains("beginCraftingCalculation"));
        assertTrue(fixture.contains("submitJob"));
        assertTrue(fixture.contains("result.link()"));
        assertTrue(requester.contains("implements ICraftingRequester"));
        assertTrue(requester.contains("tracker.readFromNBT(persistedState)"));
        assertTrue(requester.contains("managedNode.loadFromNBT(persistedState)"));
        assertTrue(requester.contains("insertCraftedItems"));
        assertTrue(requester.contains("jobStateChange"));
        assertTrue(requester.contains("observedCraftingIds"));
        assertTrue(requester.contains("uniqueNativeJobCount"));
        assertTrue(tests.contains("craftProofNativeTerminal"));
        assertTrue(tests.contains("craftProofNativeStocking"));
        assertTrue(tests.contains("craftProofMissingMaterial"));
        assertTrue(tests.contains("craftProofNoCpu"));
        assertTrue(tests.contains("craftProofDisconnectCancelRestart"));
        assertTrue(tests.contains("duplicateInvocationSubmitted"));
        assertTrue(tests.contains("materialBeforeSuspend"));
        assertFalse(tests.contains("Map.entry(\"uniqueNativeTasks\", \"1\")"));
        assertTrue(testMod.contains("NativeCraftingFailureGameTests.class"));
        assertFalse(fixture.contains("beginCraftingJob"));
    }

    @Test
    void persistedConsumerRequiresNativeCraftingSemanticsAndAdversarialProbes() throws IOException {
        var script = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        assertTrue(script.contains("verifyTaskNineEvidence(attempt"));
        assertTrue(script.contains("AE2F_CRAFT_NATIVE_ENTRY"));
        assertTrue(script.contains("Task 9 native artifact set is duplicated, missing, or substituted"));
        assertTrue(script.contains("Task 9 terminal native lifecycle semantics are incomplete"));
        assertTrue(script.contains("Task 9 stocking native lifecycle semantics are incomplete"));
        assertTrue(script.contains("Task 9 missing-material semantics are incomplete"));
        assertTrue(script.contains("Task 9 no-CPU semantics are incomplete"));
        assertTrue(script.contains("Task 9 disconnect/cancel/restart semantics are incomplete"));
        assertTrue(script.contains("duplicateInvocationSubmitted"));
        assertTrue(script.contains("nativeJobIds"));
        assertTrue(script.contains("materialBeforeSuspend"));
        assertTrue(script.contains("restart-pre-cancel-extraction-forged"));
        assertTrue(script.contains("federationTaskNineEvidenceSelfTest"));
        assertFalse(script.contains("mandatory-native-binding-unavailable"));
    }
}
