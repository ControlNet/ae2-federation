package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;
import java.util.Map;
import java.util.Set;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.PolicyFilter;
import space.controlnet.ae2federation.policy.PolicyFilterMode;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyResource;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.storage.mount.StorageLevelLifecycle;
import space.controlnet.ae2federation.storage.subscription.NativeStorageNotificationHub;
import space.controlnet.ae2federation.storage.subscription.NativeStorageAmount;
import space.controlnet.ae2federation.storage.subscription.NativeStorageListener;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;
import space.controlnet.ae2federation.test.storage.DirectSubscriptionFixture;

@PrefixGameTestTemplate(false)
public final class StorageSubscriptionGameTests {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final IActionSource ACTION_SOURCE = IActionSource.empty();

    private StorageSubscriptionGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void subscriptionTwoSameKeyEvents(GameTestHelper helper) {
        var fixture = new DirectSubscriptionFixture(helper);
        var phase = new int[1];
        var baseline = new long[4];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native subscription relationship");
            var mounts = fixture.mounts();
            if (phase[0] == 0) {
                fixture.configure(PolicyRule.storageDefaults());
                baseline[0] = mounts.subscriptionEventVersion(fixture.key());
                baseline[1] = mounts.sourceEventCount();
                baseline[2] = mounts.consumerDeliveryCount(fixture.key());
                baseline[3] = mounts.dependencyRefreshCount();
                fixture.source().insert(IRON, 4, Actionable.MODULATE, ACTION_SOURCE);
                fixture.providerGrid().getStorageService().invalidateCache();
                fixture.providerGrid().getStorageService().getCachedInventory();
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for first absolute native event");
            }
            if (phase[0] == 1 && mounts.subscriptionEventVersion(fixture.key()) > baseline[0]) {
                fixture.source().insert(IRON, 5, Actionable.MODULATE, ACTION_SOURCE);
                fixture.providerGrid().getStorageService().invalidateCache();
                fixture.providerGrid().getStorageService().getCachedInventory();
                phase[0] = 2;
                helper.assertTrue(false, "Waiting for second absolute native event");
            }
            helper.assertTrue(phase[0] == 2 && mounts.subscriptionEventVersion(fixture.key()) >= baseline[0] + 2,
                    "Both same-key native changes must remain distinguishable");
            var quantity = fixture.source().getAvailableStacks().get(IRON);
            helper.assertValueEqual(quantity, 9L, "Absolute events must reconcile to the final native quantity");
            helper.assertValueEqual(mounts.sourceEventCount() - baseline[1], 2L,
                    "Two legitimate same-key changes must publish two source events");
            helper.assertValueEqual(mounts.consumerDeliveryCount(fixture.key()) - baseline[2], 2L,
                    "The effective consumer must receive both changes");
            helper.assertValueEqual(mounts.dependencyRefreshCount(), baseline[3],
                    "Quantity changes must not rebuild dependency topology");
            PolicyEvidence.write("subscriptiontwosamekeyevents", 10, Map.of(
                    "eventDelta", "2", "deliveryDelta", "2", "finalAbsolute", Long.toString(quantity),
                    "nativeSemantics", "absolute", "topologyRefreshDelta", "0", "sameKeyRetained", "true",
                    "eventVersionDelta", "2", "importReoriginated", "false"));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void subscriptionFirstFilter(GameTestHelper helper) {
        var fixture = new DirectSubscriptionFixture(helper);
        var phase = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for first-filter relationship");
            var mounts = fixture.mounts();
            if (phase[0] == 0) {
                fixture.source().insert(IRON, 5, Actionable.MODULATE, ACTION_SOURCE);
                fixture.configure(denyIron());
                var before = mounts.consumerDeliveryCount(fixture.key());
                var registrations = mounts.subscriptionRegistrationCount();
                fixture.configure(allowIron());
                helper.assertValueEqual(mounts.effectiveProjection(fixture.key()).getAvailableStacks().get(IRON), 5L,
                        "The first allowing filter must expose the existing source baseline");
                helper.assertValueEqual(mounts.consumerDeliveryCount(fixture.key()), before + 1,
                        "Filter activation must invalidate the affected consumer once");
                helper.assertValueEqual(mounts.subscriptionRegistrationCount(), registrations,
                        "Filter changes must not replace the native source listener");
                mounts.resetSubscription(fixture.key());
                fixture.source().insert(IRON, 1, Actionable.MODULATE, ACTION_SOURCE);
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for first post-reset native change");
            }
            helper.assertTrue(mounts.effectiveProjection(fixture.key()).getAvailableStacks().get(IRON) == 6,
                    "Listener reset must not drop the first valid change");
            PolicyEvidence.write("subscriptionfirstfilter", 10, Map.of(
                    "visibleBefore", "0", "visibleAfter", "5", "postResetAbsolute", "6",
                    "filterInvalidations", "1", "listenerReplaced", "false", "firstChangeDropped", "false",
                    "snapshotVersion", Long.toString(mounts.subscriptionSnapshotVersion(fixture.key())),
                    "nativeSemantics", "absolute"));
            fixture.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void subscriptionListenerCleanup(GameTestHelper helper) {
        var fixture = new DirectSubscriptionFixture(helper);
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for listener cleanup relationship");
            fixture.configure(PolicyRule.storageDefaults());
            var mounts = fixture.mounts();
            helper.assertValueEqual(mounts.activeSubscriptionCount(), 1,
                    "One true source must own one native listener");
            fixture.configure(disabled());
            helper.assertValueEqual(mounts.activeSubscriptionCount(), 0,
                    "Relationship invalidation must release its last source listener");
            fixture.configure(PolicyRule.storageDefaults());
            helper.assertValueEqual(mounts.activeSubscriptionCount(), 1,
                    "Reconnect must install one fresh listener");
            var registrations = mounts.subscriptionRegistrationCount();
            fixture.close();
            StorageLevelLifecycle.close(helper.getLevel());
            var activeAfterClose = NativeStorageNotificationHub.activeCount();
            var catalogsAfterClose = NativeStorageNotificationHub.serviceCatalogCount();
            helper.assertValueEqual(activeAfterClose, 0,
                    "Level close must release every native listener");
            helper.assertValueEqual(catalogsAfterClose, 0,
                    "Level close must release every native service discovery catalog");
            helper.assertValueEqual(mounts.subscriptionRemovalCount(), registrations,
                    "Every listener registration must have one removal receipt");
            PolicyEvidence.write("subscriptionlistenercleanup", 12, Map.of(
                    "registrations", Integer.toString(registrations),
                    "removals", Integer.toString(mounts.subscriptionRemovalCount()),
                    "activeAfterInvalidation", "0", "activeAfterReconnect", "1",
                    "activeAfterClose", Integer.toString(activeAfterClose),
                    "serviceCatalogsAfterClose", Integer.toString(catalogsAfterClose),
                    "relationshipReleased", "true", "disconnectReleased", "true", "levelReleased", "true"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void subscriptionRejectStaleGeneration(GameTestHelper helper) {
        var fixture = new DirectSubscriptionFixture(helper);
        var phase = new int[1];
        var oldGeneration = new long[1];
        var oldSource = new appeng.api.storage.MEStorage[1];
        var oldListener = new NativeStorageListener[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for stale-generation relationship");
            var mounts = fixture.mounts();
            if (phase[0] == 0) {
                fixture.configure(PolicyRule.storageDefaults());
                fixture.configure(disabled());
                fixture.hooks().captureNextRegistration(listener -> oldListener[0] = listener);
                fixture.configure(PolicyRule.storageDefaults());
                oldGeneration[0] = mounts.subscriptionKey(fixture.key()).generation().value();
                oldSource[0] = fixture.source();
                fixture.providerChest().setCell(AEItems.ITEM_CELL_1K.stack());
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for native source replacement");
            }
            mounts.reconcileAll();
            var current = mounts.subscriptionKey(fixture.key());
            helper.assertTrue(current != null && current.generation().value() > oldGeneration[0],
                    "Source replacement must advance subscription generation");
            var beforeEvents = mounts.sourceEventCount();
            oldSource[0].insert(IRON, 3, Actionable.MODULATE, ACTION_SOURCE);
            helper.assertTrue(oldListener[0] != null, "The retired registration callback must be retained by the test");
            oldListener[0].onAmountChanged(new NativeStorageAmount(IRON, 3));
            helper.assertValueEqual(mounts.sourceEventCount(), beforeEvents,
                    "Detached stale-source mutation must not publish into the new generation");
            helper.assertValueEqual(fixture.source().getAvailableStacks().get(IRON), 0L,
                    "Stale source mutation must not reach the current native source");
            PolicyEvidence.write("subscriptionrejectstalegeneration", 11, Map.of(
                    "oldGeneration", Long.toString(oldGeneration[0]),
                    "newGeneration", Long.toString(current.generation().value()),
                    "staleAccepted", "false", "currentAbsolute", "0", "staleAbsolute", "3",
                    "eventDelta", "0", "listenerReplacement", "true", "mutationGated", "true",
                    "retainedOldCallbackInvoked", "true", "compareAndRemoveProtected", "true"));
            fixture.close();
        });
    }

    private static PolicyRule allowIron() {
        return filtered(PolicyFilterMode.ALLOW_LIST);
    }

    private static PolicyRule denyIron() {
        return filtered(PolicyFilterMode.DENY_LIST);
    }

    private static PolicyRule filtered(PolicyFilterMode mode) {
        var iron = new PolicyResource(IRON.getType().getId(), IRON.getId());
        return new PolicyRule(true, Set.of(PolicyOperation.VIEW, PolicyOperation.INSERT, PolicyOperation.EXTRACT),
                new PolicyFilter(mode, Set.of(iron)), false);
    }

    private static PolicyRule disabled() {
        return new PolicyRule(false, PolicyRule.storageDefaults().operations(), PolicyFilter.allowAll(), false);
    }

}
