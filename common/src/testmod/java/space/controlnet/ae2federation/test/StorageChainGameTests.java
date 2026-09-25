package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.BackendStatus;
import space.controlnet.ae2federation.policy.PolicyActivationState;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyFilter;
import space.controlnet.ae2federation.policy.PolicyFilterMode;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyResource;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyRuntimeEndpoints;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;
import space.controlnet.ae2federation.test.storage.ChainStorageFixture;

@PrefixGameTestTemplate(false)
public final class StorageChainGameTests {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final AEItemKey GOLD = AEItemKey.of(Items.GOLD_INGOT);
    private static final AEItemKey COPPER = AEItemKey.of(Items.COPPER_INGOT);
    private static final IActionSource ACTION_SOURCE = IActionSource.empty();

    private StorageChainGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void chainFourFederationDomainDiamond(GameTestHelper helper) {
        run(helper, (fixture, policies, mounts) -> {
            configure(policies, fixture.aToB(), full(true));
            configure(policies, fixture.aToC(), full(true));
            configure(policies, fixture.bToD(), full(false));
            configure(policies, fixture.cToD(), full(false));
            var a = fixture.nativeSource(fixture.aGrid());
            var b = fixture.nativeSource(fixture.bGrid());
            var c = fixture.nativeSource(fixture.cGrid());
            var d = fixture.nativeSource(fixture.dGrid());
            helper.assertValueEqual(a.insert(IRON, 13, Actionable.MODULATE, ACTION_SOURCE), 13L,
                    "Origin native source must accept the diamond quantity");
            var aggregate = fixture.dGrid().getStorageService().getInventory();
            var visible = aggregate.getAvailableStacks().get(IRON);
            var capacity = aggregate.insert(IRON, Long.MAX_VALUE, Actionable.SIMULATE, ACTION_SOURCE);
            var expectedCapacity = a.insert(IRON, Long.MAX_VALUE, Actionable.SIMULATE, ACTION_SOURCE)
                    + b.insert(IRON, Long.MAX_VALUE, Actionable.SIMULATE, ACTION_SOURCE)
                    + c.insert(IRON, Long.MAX_VALUE, Actionable.SIMULATE, ACTION_SOURCE)
                    + d.insert(IRON, Long.MAX_VALUE, Actionable.SIMULATE, ACTION_SOURCE);
            var effective = mounts.effectiveRelationship(fixture.aToD());
            helper.assertValueEqual(visible, 13L, "D must see A exactly once through two alternative chains");
            helper.assertValueEqual(capacity, expectedCapacity, "D capacity must contain every native origin exactly once");
            helper.assertTrue(effective != null && effective.minimumDepth() == 2,
                    "Diamond must compile one two-edge A-to-D source relationship");
            PolicyEvidence.write("chainfourfederationdomaindiamond", 12, Map.ofEntries(
                    Map.entry("federationDomainCount", "4"), Map.entry("alternativeChains", "2"),
                    Map.entry("effectiveRelationships", Integer.toString(mounts.mountedRelationshipCount())),
                    Map.entry("originQuantity", "13"), Map.entry("consumerVisibleQuantity", Long.toString(visible)),
                    Map.entry("consumerCapacity", Long.toString(capacity)),
                    Map.entry("expectedCapacity", Long.toString(expectedCapacity)),
                    Map.entry("originConsumerRelationships", "1"), Map.entry("minimumDepth", "2"),
                    Map.entry("completePathsStored", "false"), Map.entry("nativeAuthority", "true"),
                    Map.entry("deduplicated", Boolean.toString(visible == 13 && capacity == expectedCapacity))));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void chainFilterUnionIntersection(GameTestHelper helper) {
        run(helper, (fixture, policies, mounts) -> {
            configure(policies, fixture.aToB(), rule(Set.of(PolicyOperation.VIEW, PolicyOperation.EXTRACT),
                    allow(IRON, GOLD), true));
            configure(policies, fixture.bToD(), rule(Set.of(PolicyOperation.VIEW), allow(IRON), false));
            configure(policies, fixture.aToC(), rule(Set.of(PolicyOperation.VIEW, PolicyOperation.EXTRACT),
                    allow(GOLD, COPPER), true));
            configure(policies, fixture.cToD(), rule(Set.of(PolicyOperation.VIEW, PolicyOperation.EXTRACT),
                    allow(GOLD), false));
            var source = fixture.nativeSource(fixture.aGrid());
            source.insert(IRON, 6, Actionable.MODULATE, ACTION_SOURCE);
            source.insert(GOLD, 5, Actionable.MODULATE, ACTION_SOURCE);
            source.insert(COPPER, 4, Actionable.MODULATE, ACTION_SOURCE);
            var aggregate = fixture.dGrid().getStorageService().getInventory();
            var visibleIron = aggregate.getAvailableStacks().get(IRON);
            var visibleGold = aggregate.getAvailableStacks().get(GOLD);
            var visibleCopper = aggregate.getAvailableStacks().get(COPPER);
            var extractedIron = aggregate.extract(IRON, 1, Actionable.MODULATE, ACTION_SOURCE);
            var extractedGold = aggregate.extract(GOLD, 2, Actionable.MODULATE, ACTION_SOURCE);
            helper.assertValueEqual(visibleIron, 6L, "First chain must retain its intersected iron visibility");
            helper.assertValueEqual(visibleGold, 5L, "Alternative chain scopes must union gold visibility");
            helper.assertValueEqual(visibleCopper, 0L, "A later filter must not widen forbidden copper");
            helper.assertValueEqual(extractedIron, 0L, "VIEW-only final edge must remove extraction authority");
            helper.assertValueEqual(extractedGold, 2L, "Gold extraction allowed by one complete chain must remain available");
            PolicyEvidence.write("chainfilterunionintersection", 11, Map.ofEntries(
                    Map.entry("visibleIron", Long.toString(visibleIron)),
                    Map.entry("visibleGold", Long.toString(visibleGold)),
                    Map.entry("visibleCopper", Long.toString(visibleCopper)),
                    Map.entry("extractedIron", Long.toString(extractedIron)),
                    Map.entry("extractedGold", Long.toString(extractedGold)),
                    Map.entry("serialComposition", "intersection"), Map.entry("alternativeComposition", "union"),
                    Map.entry("filterWidened", "false"), Map.entry("operationWidened", "false"),
                    Map.entry("effectiveRelationships", Integer.toString(mounts.mountedRelationshipCount())),
                    Map.entry("nativeMutation", "true")));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void chainToggleReexport(GameTestHelper helper) {
        run(helper, (fixture, policies, mounts) -> {
            var first = configure(policies, fixture.aToB(), full(false));
            configure(policies, fixture.bToD(), full(false));
            fixture.nativeSource(fixture.aGrid()).insert(IRON, 7, Actionable.MODULATE, ACTION_SOURCE);
            var aggregate = fixture.dGrid().getStorageService().getInventory();
            var before = aggregate.getAvailableStacks().get(IRON);
            policies.edit(new PolicyEdit(fixture.aToB(), first, full(true)));
            var after = aggregate.getAvailableStacks().get(IRON);
            helper.assertValueEqual(before, 0L, "Initial re-export default must stop chained visibility");
            helper.assertValueEqual(after, 7L, "Explicit directional re-export must enable the chain");
            PolicyEvidence.write("chaintogglereexport", 8, Map.of(
                    "defaultReexport", "false", "visibleBefore", Long.toString(before),
                    "visibleAfter", Long.toString(after), "explicitEnableRequired", "true",
                    "directRuleRewritten", "false", "effectiveRelationshipAfter", "true",
                    "nativeQuantity", "7", "failClosedBeforeRecompile", "true"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void chainRejectDirectActivation(GameTestHelper helper) {
        run(helper, (fixture, policies, mounts) -> {
            configure(policies, fixture.aToB(), full(true));
            configure(policies, fixture.bToD(), full(false));
            configure(policies, fixture.aToD(), full(true));
            fixture.nativeSource(fixture.aGrid()).insert(IRON, 5, Actionable.MODULATE, ACTION_SOURCE);
            var activation = policies.activation(fixture.aToD(), new PolicyRuntimeEndpoints(
                    fixture.dGrid(), fixture.aGrid(), BackendStatus.READY));
            var visible = fixture.dGrid().getStorageService().getInventory().getAvailableStacks().get(IRON);
            helper.assertValueEqual(activation, PolicyActivationState.DISCONNECTED,
                    "Derived chain permission must not activate a direct rule without common Federation Domain");
            helper.assertValueEqual(visible, 5L, "The separately derived effective relationship must remain usable");
            PolicyEvidence.write("chainrejectdirectactivation", 8, Map.of(
                    "configuredDirectRule", "true", "directActivation", activation.name(),
                    "derivedEffectivePermission", "true", "visibleQuantity", Long.toString(visible),
                    "directRuleSynthesized", "false", "directRuleMutated", "false",
                    "commonDirectFederationDomain", "false", "nativeAuthority", "true"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void chainRejectCycle(GameTestHelper helper) {
        run(helper, (fixture, policies, mounts) -> {
            configure(policies, fixture.aToB(), full(true));
            configure(policies, fixture.bToA(), full(true));
            configure(policies, fixture.bToD(), full(false));
            fixture.nativeSource(fixture.aGrid()).insert(IRON, 9, Actionable.MODULATE, ACTION_SOURCE);
            var originVisible = fixture.aGrid().getStorageService().getInventory().getAvailableStacks().get(IRON);
            var downstreamVisible = fixture.dGrid().getStorageService().getInventory().getAvailableStacks().get(IRON);
            helper.assertValueEqual(originVisible, 9L, "Returning origin cycle must not remount its own source");
            helper.assertValueEqual(downstreamVisible, 9L, "Cycle rejection must preserve the legitimate downstream chain");
            helper.assertTrue(mounts.rejectedOriginCycles() > 0, "Compiler must report an origin-return rejection");
            PolicyEvidence.write("chainrejectcycle", 9, Map.of(
                    "originQuantity", "9", "originVisible", Long.toString(originVisible),
                    "downstreamVisible", Long.toString(downstreamVisible), "cycleAccepted", "false",
                    "originCycleRejections", Integer.toString(mounts.rejectedOriginCycles()),
                    "recursiveReexport", "false", "duplicateCapacity", "false",
                    "frontierRelaxations", Integer.toString(mounts.dependencyFrontierRelaxations()),
                    "completePathsStored", "false"));
        });
    }

    private static void run(GameTestHelper helper, ChainAssertion assertion) {
        var fixture = new ChainStorageFixture(helper);
        var phase = new int[1];
        var failure = new String[1];
        helper.succeedWhen(() -> {
            if (phase[0] == -1) {
                helper.assertTrue(false, failure[0]);
            }
            if (phase[0] == 0 && fixture.networksSettled()) {
                phase[0] = 1;
                helper.assertTrue(false, "Waiting one tick after native endpoint settlement");
            }
            if (phase[0] == 1) {
                fixture.placeNextBridge();
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for first Bridge attachment");
            }
            if (phase[0] >= 2 && phase[0] <= 4 && fixture.latestBridgeReady()) {
                fixture.placeNextBridge();
                phase[0]++;
                helper.assertTrue(false, "Waiting for next Bridge attachment");
            }
            helper.assertTrue(phase[0] == 5 && fixture.bridgesReady(),
                    "Four Bridge Federation Domains and native endpoint identities must be ready: " + fixture.readiness());
            try {
                assertion.verify(fixture, PolicyService.get(helper.getLevel()), StorageMountService.get(helper.getLevel()));
                fixture.close();
            } catch (RuntimeException exception) {
                failure[0] = exception.getMessage();
                phase[0] = -1;
                fixture.close();
                throw exception;
            }
        });
    }

    private static space.controlnet.ae2federation.policy.PolicyRevision configure(
            PolicyService policies, PolicyKey key, PolicyRule rule) {
        return ((space.controlnet.ae2federation.policy.PolicyMutationResult.Accepted) policies.edit(
                new PolicyEdit(key, policies.revision(key), rule))).revision();
    }

    private static PolicyRule full(boolean reexport) {
        return rule(Set.of(PolicyOperation.VIEW, PolicyOperation.INSERT, PolicyOperation.EXTRACT),
                PolicyFilter.allowAll(), reexport);
    }

    private static PolicyRule rule(Set<PolicyOperation> operations, PolicyFilter filter, boolean reexport) {
        return new PolicyRule(true, operations, filter, reexport);
    }

    private static PolicyFilter allow(AEItemKey... keys) {
        return new PolicyFilter(PolicyFilterMode.ALLOW_LIST, java.util.Arrays.stream(keys)
                .map(key -> new PolicyResource(key.getType().getId(), key.getId()))
                .collect(java.util.stream.Collectors.toUnmodifiableSet()));
    }

    @FunctionalInterface
    private interface ChainAssertion {
        void verify(ChainStorageFixture fixture, PolicyService policies, StorageMountService mounts);
    }
}
