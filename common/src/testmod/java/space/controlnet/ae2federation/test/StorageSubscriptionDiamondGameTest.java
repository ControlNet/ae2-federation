package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;
import space.controlnet.ae2federation.test.storage.ChainStorageFixture;

@PrefixGameTestTemplate(false)
public final class StorageSubscriptionDiamondGameTest {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);

    private StorageSubscriptionDiamondGameTest() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void subscriptionDiamondOnce(GameTestHelper helper) {
        var fixture = new ChainStorageFixture(helper);
        var phase = new int[1];
        var before = new long[3];
        helper.succeedWhen(() -> {
            if (phase[0] == 0 && fixture.networksSettled()) {
                phase[0] = 1;
                helper.assertTrue(false, "Waiting one tick after native endpoint settlement");
            }
            if (phase[0] == 1) {
                fixture.placeNextBridge();
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for first diamond Bridge");
            }
            if (phase[0] >= 2 && phase[0] <= 4 && fixture.latestBridgeReady()) {
                fixture.placeNextBridge();
                phase[0]++;
                helper.assertTrue(false, "Waiting for next diamond Bridge");
            }
            helper.assertTrue(phase[0] >= 5, "Waiting for staged diamond topology");
            if (phase[0] == 5) {
                helper.assertTrue(fixture.bridgesReady(), "Waiting for four-Fabric diamond");
            }
            var mounts = StorageMountService.get(helper.getLevel());
            if (phase[0] == 5) {
                var policies = PolicyService.get(helper.getLevel());
                configure(policies, fixture.aToB(), true);
                configure(policies, fixture.aToC(), true);
                configure(policies, fixture.bToD(), false);
                configure(policies, fixture.cToD(), false);
                before[0] = mounts.subscriptionEventVersion(fixture.aToD());
                before[1] = mounts.consumerDeliveryCount(fixture.aToD());
                before[2] = mounts.dependencyRefreshCount();
                fixture.nativeSource(fixture.aGrid()).insert(IRON, 13, Actionable.MODULATE, IActionSource.empty());
                fixture.aGrid().getStorageService().invalidateCache();
                fixture.aGrid().getStorageService().getCachedInventory();
                phase[0] = 6;
                helper.assertTrue(false, "Waiting for diamond native event");
            }
            helper.assertTrue(mounts.subscriptionEventVersion(fixture.aToD()) > before[0],
                    "Diamond source event must reach the effective relationship");
            var deliveryDelta = mounts.consumerDeliveryCount(fixture.aToD()) - before[1];
            var visible = fixture.dGrid().getStorageService().getInventory().getAvailableStacks().get(IRON);
            helper.assertValueEqual(deliveryDelta, 1L, "Diamond consumer must receive one effective update");
            helper.assertValueEqual(visible, 13L, "Diamond consumer must expose one absolute origin quantity");
            helper.assertValueEqual(mounts.dependencyRefreshCount(), before[2],
                    "Diamond quantity changes must not rebuild topology");
            PolicyEvidence.write("subscriptiondiamondonce", 10, Map.of(
                    "alternativeChains", "2", "effectiveUpdates", Long.toString(deliveryDelta),
                    "visibleAbsolute", Long.toString(visible), "sourceEvents", "1", "diamondDeduplicated", "true",
                    "topologyRefreshDelta", "0", "completePathsStored", "false", "nativeAuthority", "true"));
            fixture.close();
        });
    }

    private static void configure(PolicyService policies, space.controlnet.ae2federation.policy.PolicyKey key,
            boolean reexport) {
        var defaults = PolicyRule.storageDefaults();
        var rule = new PolicyRule(true, defaults.operations(), defaults.filter(), reexport);
        var result = policies.edit(new PolicyEdit(key, policies.revision(key), rule));
        if (!(result instanceof PolicyMutationResult.Accepted accepted)
                || accepted.revision().equals(PolicyRevision.NONE)) {
            throw new IllegalStateException("Diamond Policy configuration was rejected");
        }
    }
}
