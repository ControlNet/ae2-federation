package space.controlnet.ae2federation.test;

import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.test.crafting.CraftingBindingFixture;
import space.controlnet.ae2federation.test.crafting.NativeCraftingAuthorityReceipt;
import space.controlnet.ae2federation.test.crafting.NativeCraftingEvidence;

@PrefixGameTestTemplate(false)
public final class CraftingBindingGameTests {
    private CraftingBindingGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void craftingNativeBinding(GameTestHelper helper) {
        var fixture = new CraftingBindingFixture(helper, true);
        var phase = new int[1];
        var initialRevision = new PolicyRevision[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native Crafting topology: " + fixture.readinessState());
            if (phase[0] == 0) {
                initialRevision[0] = fixture.enable();
                var binding = fixture.binding();
                helper.assertTrue(binding.nativeService().orElseThrow() == fixture.sourceService(),
                        "Binding must expose the exact native source service");
                helper.assertTrue(binding.sourceGrid().orElseThrow() == fixture.providerGrid(),
                        "Binding source attribution must retain the provider Grid");
                helper.assertTrue(binding.relationship().consumerGrid() == fixture.consumerGrid()
                                && fixture.consumerGrid() != fixture.providerGrid(),
                        "Federation must not merge native Grids");
                helper.assertValueEqual(binding.nativeProviders().size(), 1,
                        "Exactly one genuine native provider must be published");
                helper.assertTrue(binding.nativeProviders().getFirst() == fixture.provider().getLogic(),
                        "Publication must retain the exact native provider object");
                fixture.insertMaterials(2);
                fixture.begin(4);
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for native planner");
            }
            if (phase[0] == 1) {
                helper.assertTrue(fixture.planReady(), "Waiting for native executable plan");
                helper.assertTrue(fixture.submit(), "Native source service must submit to its native CPU");
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for native provider execution");
            }
            helper.assertValueEqual(fixture.outputAmount(), 4L, "Native CPU/provider must return four sticks");
            var oldBinding = fixture.binding();
            var disabledRevision = fixture.setEnabled(initialRevision[0], false);
            helper.assertTrue(oldBinding.nativeService().isEmpty()
                            && fixture.bindings().capability(fixture.key()).isEmpty(),
                    "Policy disable must withdraw before another request boundary");
            var reenabledRevision = fixture.setEnabled(disabledRevision, true);
            helper.assertTrue(fixture.staleEdit(initialRevision[0]) instanceof PolicyMutationResult.Rejected,
                    "A stale Policy revision must not authorize publication");
            var replacement = fixture.binding();
            helper.assertTrue(replacement != oldBinding && oldBinding.nativeService().isEmpty(),
                    "Policy revision replacement must leave the stale binding fail closed");
            var source = replacement.providerSources().getFirst();
            var pattern = source.provider().getAvailablePatterns().getFirst();
            var cpuIdentities = identities(replacement.nativeCpus());
            var fabricReferences = fabricReferences(replacement);
            var topologyBefore = fixture.topologyRevision();
            var withdrawalsBefore = fixture.bindings().withdrawalCount();
            var bindingIdentity = identity(replacement);
            helper.assertValueEqual(fixture.commonFabricCount(), 1L,
                    "Live binding must begin with exactly one common Fabric");
            NativeCraftingAuthorityReceipt.captureCurrent(
                    new NativeCraftingAuthorityReceipt.AuthorityLabel("craftingnativebinding", "fabric-live"),
                    fixture.level(), replacement);
            fixture.removeBridges();
            fixture.bindings().reconcileAll();
            helper.assertTrue(!replacement.isCurrent() && replacement.nativeService().isEmpty()
                            && replacement.sourceGrid().isEmpty() && replacement.nativeProviders().isEmpty()
                            && replacement.providerSources().isEmpty() && replacement.nativeCpus().isEmpty()
                            && fixture.bindings().capability(fixture.key()).isEmpty(),
                    "Last-common-Fabric removal must deny every retained binding accessor before later access");
            helper.assertValueEqual(fixture.commonFabricCount(), 0L,
                    "Real Bridge removal must eliminate the final common Fabric");
            helper.assertValueEqual(fixture.bindings().withdrawalCount(), withdrawalsBefore + 1,
                    "Fabric loss must produce one binding withdrawal");
            NativeCraftingAuthorityReceipt.captureWithdrawal(
                    new NativeCraftingAuthorityReceipt.AuthorityLabel("craftingnativebinding", "fabric-withdrawn"),
                    new NativeCraftingAuthorityReceipt.WithdrawalObservation(fixture.level(), fixture.key(),
                            fixture.providerGrid(), replacement, fixture.bindings()));
            fixture.delete(reenabledRevision);
            helper.assertTrue(replacement.nativeService().isEmpty()
                            && fixture.bindings().capability(fixture.key()).isEmpty(),
                    "Policy deletion must withdraw publication");
            NativeCraftingEvidence.write("craftingnativebinding", 18, Map.ofEntries(
                    Map.entry("relationshipBindings", "1"), Map.entry("nativeProviders", "1"),
                    Map.entry("nativeCpus", "1"), Map.entry("nativeTaskSubmitted", "true"),
                    Map.entry("childExecution", "true"), Map.entry("outputAmount", "4"),
                    Map.entry("serviceIdentity", identity(fixture.sourceService())),
                    Map.entry("providerIdentity", identity(source.provider())),
                    Map.entry("providerNodeId", source.registrationNodeId().toString()),
                    Map.entry("providerNodeIdentity", identity(source.node())),
                    Map.entry("patternIdentity", identity(pattern)),
                    Map.entry("cpuIdentities", cpuIdentities),
                    Map.entry("sourceGridIdentity", identity(fixture.providerGrid())),
                    Map.entry("sourceNetworkId", fixture.key().providerNetworkId().toString()),
                    Map.entry("consumerGridIdentity", identity(fixture.consumerGrid())),
                    Map.entry("bindingIdentity", bindingIdentity),
                    Map.entry("providerGeneration", Long.toString(replacement.revision().providerGeneration().value())),
                    Map.entry("fabricReferences", fabricReferences),
                    Map.entry("fabricTopologyBefore", Long.toString(topologyBefore)),
                    Map.entry("fabricTopologyAfter", Long.toString(fixture.topologyRevision())),
                    Map.entry("fabricCommonBefore", "1"), Map.entry("fabricCommonAfter", "0"),
                    Map.entry("withdrawalsBefore", Integer.toString(withdrawalsBefore)),
                    Map.entry("withdrawalsAfter", Integer.toString(fixture.bindings().withdrawalCount())),
                    Map.entry("heldBindingWithdrawn", "true"), Map.entry("heldAccessDenied", "true"),
                    Map.entry("gridsDistinct", "true"), Map.entry("disabledWithdrawn", "true"),
                    Map.entry("deletedWithdrawn", "true"), Map.entry("staleRevisionRejected", "true"),
                    Map.entry("nativeOwnership", "true")));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void craftingDeduplicateCapability(GameTestHelper helper) {
        var fixture = new CraftingBindingFixture(helper, true);
        var initialized = new boolean[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native Crafting topology: " + fixture.readinessState());
            if (!initialized[0]) {
                fixture.enable();
                fixture.addDuplicateBridge();
                initialized[0] = true;
                helper.assertTrue(false, "Waiting for redundant Bridge Fabric");
            }
            helper.assertTrue(fixture.duplicateBridgeReady(), "Waiting for duplicate physical route");
            fixture.bindings().reconcileAll();
            var binding = fixture.binding();
            helper.assertValueEqual(fixture.bindings().relationshipCount(), 1,
                    "Duplicate routes must retain one logical relationship");
            helper.assertValueEqual(binding.nativeProviders().size(), 1,
                    "Duplicate routes must not multiply provider capacity");
            helper.assertValueEqual(binding.nativeCpus().size(), 1,
                    "Duplicate routes must not multiply native CPU capacity");
            helper.assertValueEqual(binding.nativeService().orElseThrow()
                    .getCraftingFor(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.STICK)).size(), 1,
                    "Duplicate routes must not multiply native patterns");
            var source = binding.providerSources().getFirst();
            var pattern = source.provider().getAvailablePatterns().getFirst();
            NativeCraftingAuthorityReceipt.captureCurrent(
                    new NativeCraftingAuthorityReceipt.AuthorityLabel(
                            "craftingdeduplicatecapability", "deduplicated"), fixture.level(), binding);
            NativeCraftingEvidence.write("craftingdeduplicatecapability", 8, Map.ofEntries(
                    Map.entry("physicalRoutes", "2"), Map.entry("relationshipBindings", "1"),
                    Map.entry("providerPatterns", "1"), Map.entry("nativeProviders", "1"),
                    Map.entry("nativeCpus", "1"), Map.entry("duplicateCapacity", "false"),
                    Map.entry("providerIdentity", identity(source.provider())),
                    Map.entry("providerNodeId", source.registrationNodeId().toString()),
                    Map.entry("providerNodeIdentity", identity(source.node())),
                    Map.entry("patternIdentity", identity(pattern)),
                    Map.entry("cpuIdentities", identities(binding.nativeCpus())),
                    Map.entry("sourceGridIdentity", identity(fixture.providerGrid())),
                    Map.entry("sourceNetworkId", fixture.key().providerNetworkId().toString()),
                    Map.entry("serviceIdentity", identity(fixture.sourceService())),
                    Map.entry("bindingIdentity", identity(binding)),
                    Map.entry("providerGeneration", Long.toString(binding.revision().providerGeneration().value())),
                    Map.entry("fabricReferences", fabricReferences(binding)),
                    Map.entry("topologyRevision", Long.toString(binding.revision().topologyRevision())),
                    Map.entry("routeKeyed", "false")));
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
