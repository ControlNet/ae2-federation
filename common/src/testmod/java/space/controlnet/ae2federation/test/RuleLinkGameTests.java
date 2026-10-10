package space.controlnet.ae2federation.test;

import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyAction;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyActionRequest;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyActionResult;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu;
import space.controlnet.ae2federation.client.policy.PolicySwitchTarget;
import space.controlnet.ae2federation.neoforge.network.FederationDomainPolicyActionPayload;
import space.controlnet.ae2federation.neoforge.network.FederationDomainPolicyActionPayloads;
import space.controlnet.ae2federation.persistence.PolicySavedData;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;
import space.controlnet.ae2federation.test.world.MockServerPlayers;

/** Crafting and storage rules switch apart, through the real pair-editor packet path; energy stays one pool per pair. */
@PrefixGameTestTemplate(false)
public final class RuleLinkGameTests {
    private RuleLinkGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 400)
    public static void rulesCraftingAndStorageApart(GameTestHelper helper) {
        var fixtures = new PolicyBridgeFixtures(helper, new BlockPos(5, 3, 5));
        var bridgePlaced = new boolean[1];
        // The edits below run once: a retry would see the rules the first run left, so it keeps the first failure.
        var failure = new String[1];
        helper.succeedWhen(() -> {
            if (!bridgePlaced[0] && fixtures.networksSettled()) {
                fixtures.placeFirstBridge();
                bridgePlaced[0] = true;
                helper.assertTrue(false, "Waiting for the Federation Domain");
            }
            helper.assertTrue(fixtures.firstBridgeReady(), "Waiting for the pair editor's context");
            var player = MockServerPlayers.inLevel(helper);
            player.setPos(Vec3.atCenterOf(fixtures.firstBridgeContext().position()));
            ObservationGameTestPlayerTransport.install(player);
            helper.assertTrue(FederationDomainPolicyMenu.openBridge(player, fixtures.firstBridgeContext()),
                    "The pair editor must open");
            if (failure[0] != null) helper.fail(failure[0]);
            try {
                editApart(helper, fixtures, player);
            } catch (net.minecraft.gametest.framework.GameTestAssertException e) {
                failure[0] = e.getMessage();
                throw e;
            }

            player.doCloseContainer();
            fixtures.close();
        });
    }

    private static void editApart(GameTestHelper helper, PolicyBridgeFixtures fixtures, ServerPlayer player) {
        var policy = PolicyService.get(helper.getLevel());
        var crafting = new PolicyKey(fixtures.mainNetwork(), fixtures.outerNetwork(), PolicyCapability.CRAFTING);
        var storage = new PolicyKey(fixtures.mainNetwork(), fixtures.outerNetwork(), PolicyCapability.STORAGE);
        var reverseStorage = new PolicyKey(fixtures.outerNetwork(), fixtures.mainNetwork(), PolicyCapability.STORAGE);
        var watermark = policy.highWatermark();

        // 1. Crafting on changes crafting alone, in one edit.
        helper.assertValueEqual(set(player, crafting, RuleMode.ENABLED, policy.revision(crafting)),
                FederationDomainPolicyActionResult.ACCEPTED, "Crafting on must be accepted");
        helper.assertValueEqual(mode(policy, crafting), RuleMode.ENABLED, "Crafting must be on");
        helper.assertTrue(policy.configured(storage).isEmpty(), "Crafting on must leave the storage rule alone");
        helper.assertValueEqual(policy.highWatermark(), watermark + 1, "Crafting on must advance one revision");
        helper.assertTrue(policy.configured(reverseStorage).isEmpty(), "The other direction must stay untouched");

        // 2. Storage on and off leaves crafting on.
        set(player, storage, RuleMode.ENABLED, policy.revision(storage));
        helper.assertValueEqual(mode(policy, storage), RuleMode.ENABLED, "Storage must be on");
        set(player, storage, RuleMode.DISABLED, policy.revision(storage));
        helper.assertValueEqual(mode(policy, storage), RuleMode.DISABLED, "Storage must be off");
        helper.assertValueEqual(mode(policy, crafting), RuleMode.ENABLED, "Storage off must leave crafting on");

        // 3. Crafting with re-export, then off, leaves storage off.
        set(player, crafting, RuleMode.REEXPORT, policy.revision(crafting));
        helper.assertValueEqual(mode(policy, crafting), RuleMode.REEXPORT, "Crafting must re-export");
        helper.assertValueEqual(mode(policy, storage), RuleMode.DISABLED, "Crafting re-export must leave storage off");

        // 4. A switch seen at an old revision changes nothing.
        var before = policy.highWatermark();
        set(player, crafting, RuleMode.DISABLED, new PolicyRevision(policy.revision(crafting).value() - 1));
        helper.assertValueEqual(policy.highWatermark(), before, "A stale switch must not edit any rule");
        helper.assertValueEqual(mode(policy, crafting), RuleMode.REEXPORT, "A stale switch must leave crafting on");

        // 5. A world saved with crafting but no storage loads as it was saved.
        var old = new PolicySavedData();
        old.edit(new PolicyEdit(crafting, PolicyRevision.NONE, PolicyRule.enabled(Set.of(PolicyOperation.REQUEST))));
        var registries = helper.getLevel().registryAccess();
        var loaded = PolicySavedData.load(old.save(new CompoundTag(), registries), registries);
        helper.assertTrue(loaded.configured(storage).isEmpty(), "Loading must not add a storage rule");
        helper.assertTrue(loaded.configured(crafting).map(record -> record.rule().enabled()).orElse(false),
                "Loading must keep the crafting rule");
        helper.assertFalse(loaded.isDirty(), "Loading must change nothing");
        PolicyEvidence.write("rulescraftingandstorageapart", 15, Map.of("craftingAlone", "true",
                "oneRevisionForCrafting", "true", "otherDirectionUntouched", "true", "storageOffKeepsCrafting", "true",
                "reexportKeepsStorageOff", "true", "staleSwitchEditsNothing", "true", "loadKeepsRules", "true"));
    }

    private static FederationDomainPolicyActionResult set(ServerPlayer player, PolicyKey key, RuleMode mode,
            PolicyRevision observed) {
        var current = FederationDomainPolicyMenu.currentRequest(player, FederationDomainPolicyAction.TOGGLE_POLICY)
                .orElseThrow();
        return FederationDomainPolicyActionPayloads.handle(player, new FederationDomainPolicyActionPayload(
                new FederationDomainPolicyActionRequest(FederationDomainPolicyAction.SET_POLICY, current.containerId(),
                        current.menuNonce(), current.menuSequence(), current.context(), current.expectedRevision(),
                        new PolicySwitchTarget(key, mode, observed).encode())));
    }

    private static RuleMode mode(PolicyService policy, PolicyKey key) {
        return policy.configured(key).map(record -> RuleMode.of(record.rule())).orElse(RuleMode.DISABLED);
    }
}
