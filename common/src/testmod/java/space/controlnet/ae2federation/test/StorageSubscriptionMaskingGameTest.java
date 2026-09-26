package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.PolicyFilter;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.storage.subscription.NativeStorageAmount;
import space.controlnet.ae2federation.storage.subscription.NativeStorageListener;
import space.controlnet.ae2federation.storage.subscription.NativeStorageNotificationHub;
import space.controlnet.ae2federation.test.storage.DirectSubscriptionFixture;

@PrefixGameTestTemplate(false)
public final class StorageSubscriptionMaskingGameTest {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final IActionSource ACTION_SOURCE = IActionSource.empty();

    private StorageSubscriptionMaskingGameTest() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void subscriptionMaskedEqualOpposite(GameTestHelper helper) {
        var fixture = new DirectSubscriptionFixture(helper, true);
        var state = new ScenarioState();
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for multi-contributor subscription relationship");
            var mounts = fixture.mounts();
            if (state.phase == 0) {
                helper.assertValueEqual(fixture.nativeSourceCount(), 2,
                        "The provider Grid must expose two qualified native contributors");
                fixture.configure(PolicyRule.storageDefaults());
                state.probeIds = registrationIds(fixture);
                helper.assertTrue(state.probeIds[0] > 0 && state.probeIds[1] > 0
                        && state.probeIds[0] != state.probeIds[1], "Both true sources need exact registrations");
                state.primaryFirst = state.probeIds[0] < state.probeIds[1];
                fixture.configure(disabled());
                laterSource(fixture, state).insert(IRON, 8, Actionable.MODULATE, ACTION_SOURCE);
                armRegistrationReceipts(fixture, state.broadcastCatalogBefore, null);
                fixture.configure(PolicyRule.storageDefaults());
                state.broadcastIds = registrationIds(fixture);
                fixture.providerGrid().getStorageService().invalidateCache();
                fixture.providerGrid().getStorageService().getCachedInventory();
                state.phase = 1;
                helper.assertTrue(false, "Waiting for empty-first broadcast baseline");
            }
            if (state.phase == 1) {
                state.broadcastBefore = sourceAmounts(fixture, state);
                helper.assertValueEqual(state.broadcastBefore[0], 0L,
                        "First registered source must begin empty for live broadcast");
                helper.assertValueEqual(state.broadcastBefore[1], 8L,
                        "Second registered source must seed live broadcast");
                state.eventBefore = mounts.sourceEventCount();
                state.deliveryBefore = mounts.consumerDeliveryCount(fixture.key());
                state.refreshBefore = mounts.dependencyRefreshCount();
                state.aggregateBefore = aggregate(fixture);
                firstSource(fixture, state).insert(IRON, 8, Actionable.MODULATE, ACTION_SOURCE);
                laterSource(fixture, state).extract(IRON, 8, Actionable.MODULATE, ACTION_SOURCE);
                state.phase = 2;
                helper.assertTrue(false, "Waiting for empty-first masked reconciliation");
            }
            if (state.phase == 2) {
                helper.assertTrue(mounts.sourceEventCount() >= state.eventBefore + 2,
                        "Empty-first broadcast must detect both source-local changes");
                state.broadcastAfter = sourceAmounts(fixture, state);
                state.broadcastEventDelta = mounts.sourceEventCount() - state.eventBefore;
                state.broadcastDeliveryDelta = mounts.consumerDeliveryCount(fixture.key()) - state.deliveryBefore;
                state.broadcastRefreshDelta = mounts.dependencyRefreshCount() - state.refreshBefore;
                fixture.configure(disabled());
                armRegistrationReceipts(fixture, state.replayCatalogBefore, state.oldListeners);
                fixture.configure(PolicyRule.storageDefaults());
                state.replayIds = registrationIds(fixture);
                state.phase = 3;
                helper.assertTrue(false, "Waiting for populated-first replay baseline");
            }
            if (state.phase == 3) {
                state.replayBefore = sourceAmounts(fixture, state);
                helper.assertValueEqual(state.replayBefore[0], 8L,
                        "First registered source must seed late replay");
                helper.assertValueEqual(state.replayBefore[1], 0L,
                        "Second registered source must begin empty for late replay");
                state.eventBefore = mounts.sourceEventCount();
                state.deliveryBefore = mounts.consumerDeliveryCount(fixture.key());
                state.refreshBefore = mounts.dependencyRefreshCount();
                firstSource(fixture, state).extract(IRON, 8, Actionable.MODULATE, ACTION_SOURCE);
                laterSource(fixture, state).insert(IRON, 8, Actionable.MODULATE, ACTION_SOURCE);
                state.phase = 4;
                helper.assertTrue(false, "Waiting for populated-first masked reconciliation");
            }
            if (state.phase == 4) {
                helper.assertTrue(mounts.sourceEventCount() >= state.eventBefore + 2,
                        "Populated-first replay must detect both source-local changes");
                state.replayAfter = sourceAmounts(fixture, state);
                state.replayEventDelta = mounts.sourceEventCount() - state.eventBefore;
                state.replayDeliveryDelta = mounts.consumerDeliveryCount(fixture.key()) - state.deliveryBefore;
                state.replayRefreshDelta = mounts.dependencyRefreshCount() - state.refreshBefore;
                state.maskedAggregateAfter = aggregate(fixture);
                state.deliveryBefore = mounts.consumerDeliveryCount(fixture.key());
                state.unchangedTicks = mounts.activeSubscriptionCount() + 1;
                state.phase = 5;
                helper.assertTrue(false, "Waiting for unchanged listener rotation");
            }
            if (state.unchangedTicks-- > 0) {
                helper.assertTrue(false, "Waiting for bounded unchanged probes");
            }
            finishScenario(helper, fixture, state);
        });
    }

    private static void finishScenario(GameTestHelper helper, DirectSubscriptionFixture fixture, ScenarioState state) {
        var mounts = fixture.mounts();
        helper.assertValueEqual(state.broadcastCatalogBefore[0], 0, "Broadcast first registration must start empty");
        helper.assertValueEqual(state.broadcastCatalogBefore[1], 0, "Broadcast must discover after both registrations");
        helper.assertValueEqual(state.replayCatalogBefore[0], 0, "Replay first registration must start empty");
        helper.assertValueEqual(state.replayCatalogBefore[1], 1, "Replay key must predate the second registration");
        helper.assertTrue(sameOrder(state.probeIds, state.broadcastIds) && sameOrder(state.probeIds, state.replayIds),
                "Both native cycles must preserve deterministic source registration order");
        helper.assertValueEqual(state.broadcastEventDelta, 2L, "Broadcast path must publish two source events");
        helper.assertValueEqual(state.broadcastDeliveryDelta, 2L, "Broadcast path must publish two deliveries");
        helper.assertValueEqual(state.replayEventDelta, 2L, "Replay path must publish two source events");
        helper.assertValueEqual(state.replayDeliveryDelta, 2L, "Replay path must publish two deliveries");
        helper.assertValueEqual(aggregate(fixture), state.aggregateBefore, "Both masked cycles must preserve aggregate amount");
        var unchangedInvalidations = mounts.consumerDeliveryCount(fixture.key()) - state.deliveryBefore;
        helper.assertValueEqual(unchangedInvalidations, 0L, "Unchanged listener rotation must not invalidate consumers");
        var churnKeys = overflowKeys();
        insertKeys(fixture, churnKeys.subList(0, 63));
        realizeCache(fixture);
        state.catalogAtLimit = NativeStorageNotificationHub.catalogSize(fixture.providerGrid().getStorageService());
        helper.assertValueEqual(state.catalogAtLimit, 64, "The catalog must reach its exact ceiling before overflow");
        var overflowBefore = NativeStorageNotificationHub.discoveryOverflowCount();
        var removalsBefore = NativeStorageNotificationHub.removalCount();
        if (fixture.secondSource().insert(churnKeys.get(63), 1, Actionable.MODULATE, ACTION_SOURCE) != 1) {
            throw new IllegalStateException("Physical source rejected overflow key 65");
        }
        realizeCache(fixture);
        state.overflowDelta = NativeStorageNotificationHub.discoveryOverflowCount() - overflowBefore;
        state.removalDelta = NativeStorageNotificationHub.removalCount() - removalsBefore;
        state.activeAfterOverflow = NativeStorageNotificationHub.activeCount(fixture.providerGrid().getStorageService());
        helper.assertValueEqual(state.overflowDelta, 1L, "Key 65 must emit one overflow diagnostic");
        helper.assertValueEqual(state.removalDelta, 2, "Router overflow must close both exact registrations");
        helper.assertValueEqual(state.activeAfterOverflow, 0, "Router overflow must leave no service listeners");
        helper.assertValueEqual(NativeStorageNotificationHub.catalogSize(fixture.providerGrid().getStorageService()), 0,
                "Router overflow must remove its service catalog after retirement");
        var staleEvents = mounts.sourceEventCount();
        var staleDeliveries = mounts.consumerDeliveryCount(fixture.key());
        var staleAuthority = aggregate(fixture);
        state.oldListeners[0].onAmountChanged(new NativeStorageAmount(IRON, staleAuthority + 1));
        state.oldCallbackEventDelta = mounts.sourceEventCount() - staleEvents;
        state.oldCallbackDeliveryDelta = mounts.consumerDeliveryCount(fixture.key()) - staleDeliveries;
        state.oldCallbackAuthorityDelta = aggregate(fixture) - staleAuthority;
        helper.assertValueEqual(state.oldCallbackEventDelta, 0L, "Retired callback must not publish an event");
        helper.assertValueEqual(state.oldCallbackDeliveryDelta, 0L, "Retired callback must not deliver invalidation");
        helper.assertValueEqual(state.oldCallbackAuthorityDelta, 0L, "Retired callback must not change authority");
        removeKeys(fixture, churnKeys);
        mounts.reconcileAll();
        state.recoveredIds = registrationIds(fixture);
        helper.assertTrue(freshIds(state.replayIds, state.recoveredIds),
                "Overflow recovery must install fresh exact registrations for both sources");
        var recoveryEvents = mounts.sourceEventCount();
        var recoveryDeliveries = mounts.consumerDeliveryCount(fixture.key());
        firstSource(fixture, state).insert(IRON, 1, Actionable.MODULATE, ACTION_SOURCE);
        realizeCache(fixture);
        state.recoveredEventDelta = mounts.sourceEventCount() - recoveryEvents;
        state.recoveredDeliveryDelta = mounts.consumerDeliveryCount(fixture.key()) - recoveryDeliveries;
        helper.assertValueEqual(state.recoveredEventDelta, 1L, "Recovered registrations must receive a real native event");
        helper.assertValueEqual(state.recoveredDeliveryDelta, 1L, "Recovered native event must deliver once");
        StorageSubscriptionMaskingEvidence.write(fixture, state, unchangedInvalidations);
        fixture.close();
    }

    private static void armRegistrationReceipts(DirectSubscriptionFixture fixture, int[] catalogBefore,
            NativeStorageListener[] listeners) {
        for (var index = 0; index < 2; index++) {
            var receiptIndex = index;
            fixture.hooks().captureNextRegistration(listener -> {
                catalogBefore[receiptIndex] = NativeStorageNotificationHub.catalogSize(
                        fixture.providerGrid().getStorageService());
                if (listeners != null) {
                    listeners[receiptIndex] = listener;
                }
            });
        }
    }

    private static long[] registrationIds(DirectSubscriptionFixture fixture) {
        return new long[] {fixture.mounts().subscriptionRegistrationId(fixture.source()),
                fixture.mounts().subscriptionRegistrationId(fixture.secondSource())};
    }

    private static long[] sourceAmounts(DirectSubscriptionFixture fixture, ScenarioState state) {
        return new long[] {amount(firstSource(fixture, state)), amount(laterSource(fixture, state))};
    }

    private static appeng.api.storage.MEStorage firstSource(DirectSubscriptionFixture fixture, ScenarioState state) {
        return state.primaryFirst ? fixture.source() : fixture.secondSource();
    }

    private static appeng.api.storage.MEStorage laterSource(DirectSubscriptionFixture fixture, ScenarioState state) {
        return state.primaryFirst ? fixture.secondSource() : fixture.source();
    }

    private static long amount(appeng.api.storage.MEStorage source) {
        return source.extract(IRON, Long.MAX_VALUE, Actionable.SIMULATE, ACTION_SOURCE);
    }

    private static long aggregate(DirectSubscriptionFixture fixture) {
        return fixture.providerGrid().getStorageService().getInventory()
                .extract(IRON, Long.MAX_VALUE, Actionable.SIMULATE, ACTION_SOURCE);
    }

    private static void realizeCache(DirectSubscriptionFixture fixture) {
        fixture.providerGrid().getStorageService().invalidateCache();
        fixture.providerGrid().getStorageService().getCachedInventory();
    }

    private static void insertKeys(DirectSubscriptionFixture fixture, List<AEItemKey> keys) {
        for (var index = 0; index < keys.size(); index++) {
            var source = index % 2 == 0 ? fixture.source() : fixture.secondSource();
            if (source.insert(keys.get(index), 1, Actionable.MODULATE, ACTION_SOURCE) != 1) {
                throw new IllegalStateException("Physical source rejected a retention key");
            }
        }
    }

    private static void removeKeys(DirectSubscriptionFixture fixture, List<AEItemKey> keys) {
        for (var index = 0; index < keys.size(); index++) {
            var source = index % 2 == 0 ? fixture.source() : fixture.secondSource();
            if (source.extract(keys.get(index), 1, Actionable.MODULATE, ACTION_SOURCE) != 1) {
                throw new IllegalStateException("Physical source lost a retention key");
            }
        }
    }

    static boolean sameOrder(long[] expected, long[] actual) {
        return (expected[0] < expected[1]) == (actual[0] < actual[1]);
    }

    static boolean freshIds(long[] previous, long[] recovered) {
        return recovered[0] > 0 && recovered[1] > 0
                && recovered[0] != previous[0] && recovered[1] != previous[1];
    }

    private static PolicyRule disabled() {
        return new PolicyRule(false, PolicyRule.storageDefaults().operations(), PolicyFilter.allowAll(), false);
    }

    private static List<AEItemKey> overflowKeys() {
        return BuiltInRegistries.ITEM.stream()
                .filter(item -> item != Items.AIR)
                .map(AEItemKey::of)
                .filter(key -> !key.equals(IRON))
                .limit(NativeStorageNotificationHub.MAX_RETAINED_KEYS)
                .toList();
    }

}
