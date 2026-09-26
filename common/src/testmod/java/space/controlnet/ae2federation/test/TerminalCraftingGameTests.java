package space.controlnet.ae2federation.test;

import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.crafting.TerminalCraftingEvidence;
import space.controlnet.ae2federation.test.crafting.TerminalCraftingFixture;
import space.controlnet.ae2federation.test.crafting.TerminalNativeObservation;

@PrefixGameTestTemplate(false)
public final class TerminalCraftingGameTests {
    private TerminalCraftingGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 800, required = true, manualOnly = true)
    public static void terminalNativeCrafting(GameTestHelper helper) {
        var fixture = new TerminalCraftingFixture(helper);
        var phase = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for terminal topology: " + fixture.readinessState());
            if (phase[0] == 0) {
                TerminalNativeObservation.begin("terminalnativecrafting");
                fixture.authorizeAndDiscover();
                fixture.captureAuthority("terminalnativecrafting");
                helper.assertValueEqual(fixture.craftables(), java.util.Set.of(TerminalCraftingFixture.outputKey()),
                        "Authorized terminal must discover only the native remote craftable");
                helper.assertValueEqual(fixture.binding().sourceService().getCraftables(key -> true), java.util.Set.of(
                        TerminalCraftingFixture.outputKey(), TerminalCraftingFixture.forbiddenOutputKey()),
                        "Native source must expose allowed and forbidden craftables");
                helper.assertValueEqual(fixture.session().snapshot().providerSources().getFirst().provider()
                        .getAvailablePatterns().size(), 2,
                        "Native provider must own two genuine patterns");
                helper.assertValueEqual(fixture.binding().sourceService()
                        .getCraftingFor(TerminalCraftingFixture.outputKey()).size(), 1,
                        "Allowed output must resolve one native pattern");
                helper.assertValueEqual(fixture.binding().sourceService()
                        .getCraftingFor(TerminalCraftingFixture.forbiddenOutputKey()).size(), 1,
                        "Forbidden output must remain genuinely craftable at the source");
                helper.assertTrue(fixture.session().snapshot().sourceGrid() == fixture.binding().providerGrid()
                                && fixture.binding().consumerGrid() != fixture.binding().providerGrid(),
                        "Terminal discovery must preserve distinct native Grids");
                fixture.insertMaterials(2);
                fixture.begin(4);
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for terminal native plan");
            }
            if (phase[0] == 1) {
                helper.assertTrue(fixture.planReady(), "Waiting for terminal native plan");
                helper.assertTrue(!fixture.plan().simulation(), "Authorized materials must produce an executable plan");
                helper.assertValueEqual(fixture.plan().usedItems().get(TerminalCraftingFixture.inputKey()), 2L,
                        "Native plan must consume two source-visible planks");
                helper.assertTrue(fixture.binding().sourceService().getCraftingFor(TerminalCraftingFixture.outputKey())
                        .stream().anyMatch(fixture.plan().patternTimes()::containsKey),
                        "Planned pattern must be the exact allowed native source pattern");
                fixture.submit();
                helper.assertTrue(fixture.submittedSuccessfully(), "Native terminal plan must submit");
                helper.assertTrue(fixture.nativeSubmitResult().link() == null,
                        "Terminal-equivalent native submission must remain standalone");
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for native CPU/provider execution");
            }
            helper.assertValueEqual(fixture.materialAmount(), 0L, "Native CPU must extract the planned materials");
            helper.assertValueEqual(fixture.outputAmount(), 4L, "Native execution must produce four sticks");
            var snapshot = TerminalNativeObservation.snapshot();
            helper.assertValueEqual(snapshot.jobIds().size(), 1, "Exactly one native job must execute");
            helper.assertValueEqual(snapshot.providerPushes(), 1, "Exactly one native provider push must execute");
            helper.assertValueEqual(snapshot.cpuSubmissions(), 1, "Exactly one native CPU submission must execute");
            helper.assertTrue(snapshot.requesterNodeCalls() > 0 && snapshot.plannerThread().startsWith("AE Crafting Calculator"),
                    "Planner must use the immutable captured requester on AE2's calculator thread");
            TerminalCraftingEvidence.write("terminalnativecrafting", 21, successFacts(fixture, snapshot));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 800, required = true, manualOnly = true)
    public static void terminalNativeResult(GameTestHelper helper) {
        var fixture = new TerminalCraftingFixture(helper);
        var phase = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for result topology: " + fixture.readinessState());
            if (phase[0] == 0) {
                TerminalNativeObservation.begin("terminalnativeresult");
                fixture.authorizeAndDiscover();
                fixture.captureAuthority("terminalnativeresult");
                fixture.insertMaterials(2);
                fixture.begin(4);
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for result plan");
            }
            if (phase[0] == 1) {
                helper.assertTrue(fixture.planReady(), "Waiting for result plan");
                fixture.captureResultAuthority("terminalnativeresult", 4);
                fixture.submit();
                helper.assertTrue(fixture.submittedSuccessfully(), "Result plan must submit natively");
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for native result callback path");
            }
            var snapshot = TerminalNativeObservation.snapshot();
            helper.assertValueEqual(fixture.outputAmount(), 4L, "Physical source storage must own four result items");
            helper.assertTrue(snapshot.resultCallbacks() > 0, "Native CPU final-result callback must be observed");
            helper.assertTrue(snapshot.physicalInserts() > 0, "Native physical cell insertion must be observed");
            helper.assertValueEqual(snapshot.resultCallbackOwner(), snapshot.cpuLogicIdentity(),
                    "Result callback must belong to the submitted native CPU logic");
            helper.assertTrue(!snapshot.physicalResultOwner().equals("none"),
                    "Result must identify the physical native storage owner");
            helper.assertValueEqual(snapshot.physicalResultOwner(), identity(fixture.binding().sourcePhysicalStorage()),
                    "Result insertion must belong to the pre-established physical source cell");
            helper.assertValueEqual(snapshot.jobIds().size(), 1, "Result callback must correlate one native job");
            helper.assertValueEqual(snapshot.providerPushes(), 1, "Result must correlate one native provider dispatch");
            var facts = new java.util.TreeMap<>(successFacts(fixture, snapshot));
            facts.put("resultRecognition", "native-callback-and-physical-owner");
            facts.put("callbackOwner", snapshot.resultCallbackOwner());
            facts.put("physicalOwner", snapshot.physicalResultOwner());
            facts.put("expectedDestination", identity(fixture.binding().sourcePhysicalStorage()));
            TerminalCraftingEvidence.write("terminalnativeresult", 15, facts);
            fixture.close();
        });
    }

    private static Map<String, String> successFacts(TerminalCraftingFixture fixture,
            TerminalNativeObservation.Snapshot observation) {
        var nativeSource = fixture.session().snapshot().providerSources().getFirst();
        var pattern = fixture.plan().patternTimes().keySet().iterator().next();
        var cpu = fixture.session().snapshot().nativeCpus().iterator().next();
        var service = fixture.binding().sourceService();
        var sourceCraftables = service.getCraftables(key -> true);
        var consumerCraftables = fixture.binding().consumerGrid().getCraftingService().getCraftables(key -> true);
        var providerPatterns = nativeSource.provider().getAvailablePatterns();
        var allowedPattern = service.getCraftingFor(TerminalCraftingFixture.outputKey()).iterator().next();
        var forbiddenPattern = service.getCraftingFor(TerminalCraftingFixture.forbiddenOutputKey()).iterator().next();
        var filter = space.controlnet.ae2federation.policy.PolicyService.get(fixture.binding().level())
                .configured(fixture.binding().key()).orElseThrow().rule().filter();
        return Map.ofEntries(
                Map.entry("discoveredCraftables", Integer.toString(fixture.craftables().size())),
                Map.entry("discoveredKeys", keyIds(fixture.craftables())),
                Map.entry("sourceNativeCraftableCount", Integer.toString(sourceCraftables.size())),
                Map.entry("sourceNativeCraftables", keyIds(sourceCraftables)),
                Map.entry("consumerNativeCraftables", keyIds(consumerCraftables)),
                Map.entry("allowedKey", TerminalCraftingFixture.outputKey().getId().toString()),
                Map.entry("forbiddenKey", TerminalCraftingFixture.forbiddenOutputKey().getId().toString()),
                Map.entry("providerPatterns", identities(providerPatterns)),
                Map.entry("allowedPattern", identity(allowedPattern)),
                Map.entry("forbiddenPattern", identity(forbiddenPattern)),
                Map.entry("filterMode", filter.mode().name()),
                Map.entry("filterEntries", filter.entries().stream()
                        .map(entry -> entry.resourceType() + "|" + entry.resourceId()).sorted()
                        .collect(java.util.stream.Collectors.joining(","))),
                Map.entry("planSimulation", Boolean.toString(fixture.plan().simulation())),
                Map.entry("plannedInput", Long.toString(fixture.plan().usedItems().get(TerminalCraftingFixture.inputKey()))),
                Map.entry("resultAmount", Long.toString(fixture.outputAmount())),
                Map.entry("jobIds", observation.joinedJobIds()),
                Map.entry("jobCardinality", Integer.toString(observation.jobIds().size())),
                Map.entry("sourceNetworkId", fixture.binding().key().providerNetworkId().toString()),
                Map.entry("consumerGrid", identity(fixture.binding().consumerGrid())),
                Map.entry("sourceGrid", identity(fixture.binding().providerGrid())),
                Map.entry("service", identity(fixture.session().snapshot().service())),
                Map.entry("provider", identity(nativeSource.provider())),
                Map.entry("providerNode", identity(nativeSource.node())),
                Map.entry("providerNodeId", nativeSource.registrationNodeId().toString()),
                Map.entry("pattern", identity(pattern)), Map.entry("cpu", identity(cpu)),
                Map.entry("binding", identity(fixture.session().snapshot().binding())),
                Map.entry("gridsDistinct", "true"), Map.entry("providerInjected", "false"),
                Map.entry("aggregateCopied", "false"), Map.entry("patternsCopied", "false"),
                Map.entry("storageWidened", "false"), Map.entry("staleBindingSubmitted", "false"),
                Map.entry("publicAggregateOnly", "false"));
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    private static String keyIds(java.util.Collection<appeng.api.stacks.AEKey> keys) {
        var joined = keys.stream().map(key -> key.getId().toString()).sorted()
                .collect(java.util.stream.Collectors.joining(","));
        return joined.isEmpty() ? "none" : joined;
    }

    private static String identities(java.util.Collection<?> values) {
        return values.stream().map(TerminalCraftingGameTests::identity).sorted()
                .collect(java.util.stream.Collectors.joining(","));
    }
}
