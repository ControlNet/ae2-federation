package space.controlnet.ae2federation.test;

import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.crafting.CraftingBindingFixture;
import space.controlnet.ae2federation.test.crafting.NativeCraftingAuthorityReceipt;
import space.controlnet.ae2federation.test.crafting.NativeCraftingEvidence;

@PrefixGameTestTemplate(false)
public final class CraftingBindingFailureGameTests {
    private CraftingBindingFailureGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void craftingNativeStateOwner(GameTestHelper helper) {
        var fixture = new CraftingBindingFixture(helper, true);
        var phase = new int[1];
        var bindingA = new space.controlnet.ae2federation.crafting.binding.CraftingCapabilityBinding[1];
        var sourceA = new space.controlnet.ae2federation.crafting.binding.NativeCraftingProviderSource[1];
        var gridA = new appeng.api.networking.IGrid[1];
        var patternA = new Object[1];
        var generationA = new long[1];
        var serviceAIdentity = new String[1];
        var cpuAIdentities = new String[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                helper.assertTrue(fixture.ready(), "Waiting for native Crafting source: " + fixture.readinessState());
                fixture.enable();
                bindingA[0] = fixture.binding();
                sourceA[0] = bindingA[0].providerSources().getFirst();
                gridA[0] = fixture.providerGrid();
                patternA[0] = sourceA[0].provider().getAvailablePatterns().getFirst();
                generationA[0] = bindingA[0].revision().providerGeneration().value();
                serviceAIdentity[0] = identity(bindingA[0].nativeService().orElseThrow());
                cpuAIdentities[0] = identities(bindingA[0].nativeCpus());
                helper.assertTrue(bindingA[0].nativeService().orElseThrow() == fixture.sourceService(),
                        "AE2 service must remain operational owner");
                helper.assertTrue(bindingA[0].nativeProviders().getFirst() == sourceA[0].provider(),
                        "AE2 provider must remain operational owner");
                helper.assertTrue(bindingA[0].nativeProviders().getFirst().getAvailablePatterns().getFirst() == patternA[0],
                        "Pattern details identity must not be copied");
                helper.assertTrue(sourceA[0].node().getGrid() == fixture.providerGrid(),
                        "Provider attribution must retain the real source Grid");
                helper.assertTrue(bindingA[0].sourceGrid().orElseThrow() != fixture.consumerGrid(),
                        "Source attribution must not become a consumer aggregate");
                NativeCraftingAuthorityReceipt.captureCurrent(
                        new NativeCraftingAuthorityReceipt.AuthorityLabel("craftingnativestateowner", "provider-a"),
                        fixture.level(), bindingA[0]);
                fixture.removeProvider();
                helper.assertTrue(bindingA[0].nativeService().isEmpty()
                                && fixture.bindings().capability(fixture.key()).isEmpty(),
                        "Provider A removal must invalidate its generation");
                fixture.beginProviderReplacement();
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for distinct native provider B");
            }
            helper.assertTrue(fixture.replacementReady(), "Waiting for provider B registration and pattern availability");
            var bindingB = fixture.binding();
            var sourceB = bindingB.providerSources().getFirst();
            var patternB = sourceB.provider().getAvailablePatterns().getFirst();
            helper.assertTrue(bindingB.sourceGrid().orElseThrow() == fixture.providerGrid()
                            && fixture.key().providerNetworkId().equals(bindingB.relationship().key().providerNetworkId()),
                    "Provider B must retain the settled logical source lineage and current Grid");
            helper.assertTrue(bindingB != bindingA[0]
                            && bindingB.revision().providerGeneration().value() > generationA[0],
                    "Provider B must publish a new binding at a strictly newer generation");
            helper.assertTrue(!sourceB.registrationNodeId().equals(sourceA[0].registrationNodeId()),
                    "Provider B must have a distinct registration node UUID");
            helper.assertTrue(sourceB.node() != sourceA[0].node(), "Provider B must use a distinct native node object");
            helper.assertTrue(sourceB.provider() != sourceA[0].provider(),
                    "Provider B must use a distinct native provider object");
            helper.assertTrue(patternB != patternA[0], "Provider B must expose a distinct native pattern object");
            helper.assertTrue(bindingB.nativeService().orElseThrow() == fixture.sourceService()
                            && bindingB.nativeProviders().getFirst() == sourceB.provider()
                            && bindingB.nativeCpus().size() == 1,
                    "New binding must expose provider B through the exact native service and CPU set");
            helper.assertTrue(!bindingA[0].isCurrent() && bindingA[0].nativeService().isEmpty()
                            && bindingA[0].sourceGrid().isEmpty() && bindingA[0].nativeProviders().isEmpty()
                            && bindingA[0].providerSources().isEmpty() && bindingA[0].nativeCpus().isEmpty(),
                    "Retained binding A must remain unusable and unable to expose or submit through provider B");
            NativeCraftingAuthorityReceipt.captureCurrent(
                    new NativeCraftingAuthorityReceipt.AuthorityLabel("craftingnativestateowner", "provider-b"),
                    fixture.level(), bindingB);
            NativeCraftingEvidence.write("craftingnativestateowner", 18, Map.ofEntries(
                    Map.entry("sourceNetworkId", fixture.key().providerNetworkId().toString()),
                    Map.entry("sourceGridAIdentity", identity(gridA[0])),
                    Map.entry("sourceGridBIdentity", identity(fixture.providerGrid())),
                    Map.entry("serviceAIdentity", serviceAIdentity[0]),
                    Map.entry("cpuAIdentities", cpuAIdentities[0]),
                    Map.entry("providerANodeId", sourceA[0].registrationNodeId().toString()),
                    Map.entry("providerANodeIdentity", identity(sourceA[0].node())),
                    Map.entry("providerAIdentity", identity(sourceA[0].provider())),
                    Map.entry("patternAIdentity", identity(patternA[0])),
                    Map.entry("bindingAIdentity", identity(bindingA[0])),
                    Map.entry("generationA", Long.toString(generationA[0])),
                    Map.entry("fabricReferencesA", fabricReferences(bindingA[0])),
                    Map.entry("topologyA", Long.toString(bindingA[0].revision().topologyRevision())),
                    Map.entry("providerBNodeId", sourceB.registrationNodeId().toString()),
                    Map.entry("providerBNodeIdentity", identity(sourceB.node())),
                    Map.entry("providerBIdentity", identity(sourceB.provider())),
                    Map.entry("patternBIdentity", identity(patternB)),
                    Map.entry("bindingBIdentity", identity(bindingB)),
                    Map.entry("generationB", Long.toString(bindingB.revision().providerGeneration().value())),
                    Map.entry("fabricReferencesB", fabricReferences(bindingB)),
                    Map.entry("topologyB", Long.toString(bindingB.revision().topologyRevision())),
                    Map.entry("serviceBIdentity", identity(bindingB.nativeService().orElseThrow())),
                    Map.entry("cpuBIdentities", identities(bindingB.nativeCpus())),
                    Map.entry("providerReplaced", "true"), Map.entry("generationAdvanced", "true"),
                    Map.entry("oldBindingDenied", "true"), Map.entry("oldSubmitAvailable", "false"),
                    Map.entry("patternCopied", "false"), Map.entry("federationPlanner", "false"),
                    Map.entry("federationCpu", "false"), Map.entry("federationResultLedger", "false"),
                    Map.entry("sourceGenerationWithdrawn", "true"), Map.entry("aggregateRepublished", "false")));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void craftingRejectUnavailable(GameTestHelper helper) {
        var fixture = new CraftingBindingFixture(helper, false);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for provider without native CPU capacity: "
                    + fixture.readinessState());
            fixture.enable();
            helper.assertTrue(fixture.sourceService().getCpus().isEmpty(), "Fixture must expose no native CPU");
            helper.assertTrue(fixture.bindings().capability(fixture.key()).isEmpty(),
                    "Unavailable native backend must not advertise a capability");
            helper.assertValueEqual(fixture.bindings().relationshipCount(), 0,
                    "Unavailable backend must retain no relationship binding");
            helper.assertValueEqual(fixture.provider().getLogic().getAvailablePatterns().size(), 1,
                    "No-CPU rejection must retain the real native provider and pattern");
            NativeCraftingAuthorityReceipt.captureUnavailable(
                    new NativeCraftingAuthorityReceipt.AuthorityLabel("craftingrejectunavailable", "no-cpu"),
                    fixture.level(), fixture.providerGrid());
            NativeCraftingEvidence.write("craftingrejectunavailable", 5, Map.ofEntries(
                    Map.entry("nativeCpus", "0"), Map.entry("relationshipBindings", "0"),
                    Map.entry("capabilityAdvertised", "false"), Map.entry("fallbackExecution", "false"),
                    Map.entry("cannedAvailable", "false"), Map.entry("nativeProviderReady", "true"),
                    Map.entry("sourceNetworkId", fixture.key().providerNetworkId().toString()),
                    Map.entry("sourceGridIdentity", identity(fixture.providerGrid())),
                    Map.entry("serviceIdentity", identity(fixture.sourceService())),
                    Map.entry("providerNodeId", fixture.providerNodeId().toString()),
                    Map.entry("providerNodeIdentity", identity(fixture.providerNode())),
                    Map.entry("providerIdentity", identity(fixture.provider().getLogic())),
                    Map.entry("patternIdentity",
                            identity(fixture.provider().getLogic().getAvailablePatterns().getFirst()))));
            fixture.close();
        });
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    private static String identities(Iterable<?> values) {
        var result = new java.util.ArrayList<String>();
        values.forEach(value -> result.add(identity(value)));
        result.sort(java.util.Comparator.naturalOrder());
        return String.join(",", result);
    }

    private static String fabricReferences(space.controlnet.ae2federation.crafting.binding.CraftingCapabilityBinding binding) {
        return binding.revision().fabrics().stream()
                .map(reference -> reference.fabricId().value() + "@" + reference.generation())
                .sorted().collect(java.util.stream.Collectors.joining(","));
    }
}
