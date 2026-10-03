package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.core.definitions.AEBlocks;
import java.util.LinkedHashMap;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.identity.IdentityStatus;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.policy.BindingDiagnostic.Reason;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyFilter;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.test.automation.NativeAutomationFixture;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;
import space.controlnet.ae2federation.test.storage.ChainStorageFixture;

/** The reason an enabled storage rule gives the pair editor when it shares nothing, and when it is in effect. */
@PrefixGameTestTemplate(false)
public final class StorageStatusGameTests {
    private StorageStatusGameTests() {
    }

    /**
     * One rule through its states: unpaired, allowing nothing, in effect, with nothing to share, and disconnected.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void storageStatusReasons(GameTestHelper helper) {
        var fixtures = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
        fixtures.installStorageCells();
        var phase = new int[1];
        var facts = new LinkedHashMap<String, String>();
        helper.succeedWhen(() -> {
            var level = helper.getLevel();
            if (phase[0] == 0) {
                helper.assertTrue(fixtures.networksSettled(), "Waiting for identities");
                var key = key(fixtures);
                edit(level, key, new PolicyRule(true, Set.of(), PolicyFilter.allowAll(), false));
                StorageMountService.get(level).reconcileAll();
                var unpaired = StorageMountService.status(level, key).orElseThrow();
                helper.assertValueEqual(unpaired.reason(), Optional.of(Reason.IDENTITY_UNCONFIRMED),
                        "Networks the storage service never paired are not loaded or not settled for it");
                helper.assertTrue(!unpaired.inEffect(), "An unpaired rule is not in effect");
                facts.put("unpairedReason", Reason.IDENTITY_UNCONFIRMED.name());
                fixtures.placeFirstBridge();
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for the Federation Domain");
            }
            var key = key(fixtures);
            var mounts = StorageMountService.get(level);
            if (phase[0] == 1) {
                helper.assertTrue(fixtures.firstBridgeReady(), "Waiting for the Federation Domain");
                mounts.reconcileAll();
                var none = StorageMountService.status(level, key).orElseThrow();
                helper.assertValueEqual(none.reason(), Optional.of(Reason.STORAGE_ACCESS_NONE),
                        "A rule allowing no operation says so");
                helper.assertTrue(mounts.projection(key) == null, "A rule allowing nothing mounts nothing");
                facts.put("accessNoneReason", Reason.STORAGE_ACCESS_NONE.name());
                edit(level, key, PolicyRule.storageDefaults());
                var working = StorageMountService.status(level, key).orElseThrow();
                helper.assertTrue(working.inEffect() && working.reason().isEmpty() && working.source().isEmpty(),
                        "A working rule is in effect with no reason");
                helper.assertTrue(mounts.projection(key) != null, "The working rule mounts the provider's storage");
                facts.put("defaultsInEffect", "true");
                fixtures.providerChest().setCell(ItemStack.EMPTY);
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for the provider's cell removal");
            }
            if (phase[0] == 2) {
                var empty = StorageMountService.status(level, key).orElseThrow();
                helper.assertValueEqual(empty.reason(), Optional.of(Reason.STORAGE_SOURCE_EMPTY),
                        "Waiting for the provider network with no storage to be reported");
                helper.assertTrue(!empty.inEffect() && mounts.projection(key) == null,
                        "A provider with no storage shares nothing");
                facts.put("emptyReason", Reason.STORAGE_SOURCE_EMPTY.name());
                fixtures.removeFirstBridge();
                phase[0] = 3;
                helper.assertTrue(false, "Waiting for the Federation Domain to split");
            }
            mounts.reconcileAll();
            var split = StorageMountService.status(level, key).orElseThrow();
            helper.assertValueEqual(split.reason(), Optional.of(Reason.NETWORK_PAIR_DISCONNECTED),
                    "Waiting for two loaded networks without a shared domain to be reported");
            facts.put("splitReason", Reason.NETWORK_PAIR_DISCONNECTED.name());
            PolicyEvidence.write("storagestatusreasons", 9, facts);
            fixtures.close();
        });
    }

    /**
     * B has no storage and passes A's on to D: D's rule on B is in effect, though no mount carries B's own key; without
     * re-export it shares nothing, because B has nothing of its own.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void storageStatusReexport(GameTestHelper helper) {
        var fixture = new ChainStorageFixture(helper);
        fixture.emptyB();
        var phase = new int[1];
        helper.succeedWhen(() -> {
            if (phase[0] == 0 && fixture.networksSettled()) {
                fixture.placeNextBridge();
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for the first Bridge");
            }
            if (phase[0] >= 1 && phase[0] <= 3 && fixture.latestBridgeReady()) {
                fixture.placeNextBridge();
                phase[0]++;
                helper.assertTrue(false, "Waiting for the next Bridge");
            }
            helper.assertTrue(phase[0] == 4 && fixture.bridgesReady(),
                    "Four Bridge Federation Domains must be ready: " + fixture.readiness());
            var level = helper.getLevel();
            var mounts = StorageMountService.get(level);
            edit(level, fixture.aToB(), PolicyRule.storageDefaults().withMode(space.controlnet.ae2federation.policy.RuleMode.REEXPORT));
            edit(level, fixture.bToD(), PolicyRule.storageDefaults());
            var through = StorageMountService.status(level, fixture.bToD()).orElseThrow();
            helper.assertTrue(mounts.effectiveProjection(fixture.aToD()) != null, "D sees A's storage through B");
            helper.assertTrue(mounts.effectiveProjection(fixture.bToD()) == null, "B has no storage of its own");
            helper.assertTrue(through.inEffect() && through.reason().isEmpty(),
                    "D's rule on B is in effect while it carries A's storage");
            edit(level, fixture.aToB(), PolicyRule.storageDefaults());
            var blocked = StorageMountService.status(level, fixture.bToD()).orElseThrow();
            helper.assertTrue(mounts.effectiveProjection(fixture.aToD()) == null, "Without re-export D loses A");
            helper.assertTrue(!blocked.inEffect(), "D's rule on B then shares nothing");
            helper.assertValueEqual(blocked.reason(), Optional.of(Reason.STORAGE_SOURCE_EMPTY),
                    "B has nothing of its own to share");
            PolicyEvidence.write("storagestatusreexport", 6, java.util.Map.of("throughInEffect", "true",
                    "ownMount", "false", "withoutReexportReason", Reason.STORAGE_SOURCE_EMPTY.name()));
            fixture.close();
        });
    }

    private static PolicyKey key(PolicyBridgeFixtures fixtures) {
        return PolicyLifecycleGameTests.storageKey(fixtures);
    }

    private static void edit(net.minecraft.server.level.ServerLevel level, PolicyKey key, PolicyRule rule) {
        var policies = PolicyService.get(level);
        if (!(policies.edit(new PolicyEdit(key, policies.revision(key), rule)) instanceof PolicyMutationResult.Accepted)) {
            throw new IllegalStateException("The rule edit must be accepted");
        }
    }

    /**
     * The provider's identity splits for a moment (a stray block carrying its id sits on a second Grid) and heals on the
     * same Grids, with no Federation block touched: storage and crafting stop while it is split and come back after.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 800, required = true, manualOnly = true)
    public static void storageIdentitySplitRecovers(GameTestHelper helper) {
        var fixture = new NativeAutomationFixture(helper);
        var stray = new BlockPos(8, 6, 1);
        var diamond = AEItemKey.of(Items.DIAMOND);
        var phase = new int[1];
        var heldDuringSplit = new String[1];
        helper.succeedWhen(() -> {
            var binding = fixture.binding();
            var mounts = StorageMountService.get(helper.getLevel());
            var crafting = CraftingProjectionService.get(helper.getLevel());
            switch (phase[0]) {
                case 0 -> {
                    helper.assertTrue(fixture.ready(), "Waiting for shared Storage and Crafting capabilities");
                    helper.setBlock(stray, AEBlocks.CRAFTING_STORAGE_1K.block());
                    helper.<CraftingBlockEntity>getBlockEntity(stray).getMainNode().loadFromNBT(
                            NetworkIdentityNodeSeed.managedNode("proxy", binding.key().providerNetworkId()));
                    phase[0] = 1;
                    helper.assertTrue(false, "Waiting for the provider identity to split");
                }
                case 1 -> {
                    helper.assertTrue(identity(binding.providerGrid()) == IdentityStatus.AMBIGUOUS_SPLIT
                                    && crafting.projectionCount(binding.key()) == 0,
                            "Waiting for the split provider's crafting projection to be withdrawn");
                    var held = mounts.projection(fixture.storageKey());
                    heldDuringSplit[0] = Boolean.toString(held != null);
                    helper.assertTrue(held == null
                                    || held.insert(diamond, 3, Actionable.SIMULATE, IActionSource.empty()) == 0,
                            "A split provider's storage must not accept items");
                    phase[0] = 2;
                    helper.assertTrue(false, "Waiting for the stray block to join the provider Grid");
                }
                case 2 -> {
                    helper.assertTrue(binding.connectNativeCpu(stray)
                                    && identity(binding.providerGrid()) == IdentityStatus.SETTLED,
                            "Waiting for the provider identity to heal");
                    phase[0] = 3;
                    helper.assertTrue(false, "Waiting for storage and crafting to return");
                }
                default -> {
                    var projection = mounts.projection(fixture.storageKey());
                    helper.assertTrue(crafting.projectionCount(binding.key()) > 0 && projection != null,
                            "Waiting for storage and crafting to return after the identity healed: crafting="
                                    + CraftingProjectionService.status(helper.getLevel(), binding.key())
                                    + " storage=" + StorageMountService.status(helper.getLevel(), fixture.storageKey()));
                    helper.assertValueEqual(projection.insert(diamond, 3, Actionable.SIMULATE, IActionSource.empty()),
                            3L, "The returned storage mount must accept items again");
                    PolicyEvidence.write("storageidentitysplitrecovers", 6, java.util.Map.of(
                            "splitStatus", IdentityStatus.AMBIGUOUS_SPLIT.name(),
                            "heldMountDuringSplit", heldDuringSplit[0], "craftingReturned", "true",
                            "storageReturned", "true"));
                    fixture.close();
                }
            }
        });
    }

    private static IdentityStatus identity(appeng.api.networking.IGrid grid) {
        return grid.getService(NetworkIdentityService.class).settlement().status();
    }
}
