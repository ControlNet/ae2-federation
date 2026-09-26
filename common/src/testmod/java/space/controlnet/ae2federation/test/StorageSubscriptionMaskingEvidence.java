package space.controlnet.ae2federation.test;

import java.util.Map;
import space.controlnet.ae2federation.storage.subscription.NativeStorageListener;
import space.controlnet.ae2federation.storage.subscription.NativeStorageNotificationHub;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;
import space.controlnet.ae2federation.test.storage.DirectSubscriptionFixture;

final class StorageSubscriptionMaskingEvidence {
    private StorageSubscriptionMaskingEvidence() {
    }

    static void write(DirectSubscriptionFixture fixture, ScenarioState state, long unchangedInvalidations) {
        var mounts = fixture.mounts();
        var importedOrigins = mounts.activeSubscriptionCount() - fixture.nativeSourceCount();
        var managedImports = mounts.effectiveProjection(fixture.key()) == null ? 0 : 1;
        PolicyEvidence.write("subscriptionmaskedequalopposite", 42, Map.ofEntries(
                Map.entry("broadcastFirstBefore", Long.toString(state.broadcastBefore[0])),
                Map.entry("broadcastSecondBefore", Long.toString(state.broadcastBefore[1])),
                Map.entry("broadcastFirstAfter", Long.toString(state.broadcastAfter[0])),
                Map.entry("broadcastSecondAfter", Long.toString(state.broadcastAfter[1])),
                Map.entry("broadcastEventDelta", Long.toString(state.broadcastEventDelta)),
                Map.entry("broadcastDeliveryDelta", Long.toString(state.broadcastDeliveryDelta)),
                Map.entry("broadcastCatalogBeforeFirst", Integer.toString(state.broadcastCatalogBefore[0])),
                Map.entry("broadcastCatalogBeforeSecond", Integer.toString(state.broadcastCatalogBefore[1])),
                Map.entry("replayFirstBefore", Long.toString(state.replayBefore[0])),
                Map.entry("replaySecondBefore", Long.toString(state.replayBefore[1])),
                Map.entry("replayFirstAfter", Long.toString(state.replayAfter[0])),
                Map.entry("replaySecondAfter", Long.toString(state.replayAfter[1])),
                Map.entry("replayEventDelta", Long.toString(state.replayEventDelta)),
                Map.entry("replayDeliveryDelta", Long.toString(state.replayDeliveryDelta)),
                Map.entry("replayCatalogBeforeFirst", Integer.toString(state.replayCatalogBefore[0])),
                Map.entry("replayCatalogBeforeSecond", Integer.toString(state.replayCatalogBefore[1])),
                Map.entry("registrationOrderStable", Boolean.toString(
                        StorageSubscriptionMaskingGameTest.sameOrder(state.probeIds, state.replayIds))),
                Map.entry("aggregateBefore", Long.toString(state.aggregateBefore)),
                Map.entry("aggregateAfter", Long.toString(state.maskedAggregateAfter)),
                Map.entry("importOriginCount", Integer.toString(importedOrigins)),
                Map.entry("consumerManagedImportCount", Integer.toString(managedImports)),
                Map.entry("topologyRefreshDelta", Long.toString(
                        state.broadcastRefreshDelta + state.replayRefreshDelta)),
                Map.entry("unchangedFollowupInvalidationDelta", Long.toString(unchangedInvalidations)),
                Map.entry("retentionLimit", Integer.toString(NativeStorageNotificationHub.MAX_RETAINED_KEYS)),
                Map.entry("supportedArrivalsBetweenVisits", Integer.toString(
                        NativeStorageNotificationHub.MAX_DISCOVERIES_BETWEEN_LISTENER_VISITS)),
                Map.entry("catalogSizeBeforeOverflow", Integer.toString(state.catalogAtLimit)),
                Map.entry("overflowDistinctKeyCount", Integer.toString(NativeStorageNotificationHub.MAX_RETAINED_KEYS + 1)),
                Map.entry("retentionOverflowDelta", Long.toString(state.overflowDelta)),
                Map.entry("overflowRemovalDelta", Integer.toString(state.removalDelta)),
                Map.entry("activeAfterRetentionOverflow", Integer.toString(state.activeAfterOverflow)),
                Map.entry("oldCallbackEventDelta", Long.toString(state.oldCallbackEventDelta)),
                Map.entry("oldCallbackDeliveryDelta", Long.toString(state.oldCallbackDeliveryDelta)),
                Map.entry("oldCallbackAuthorityDelta", Long.toString(state.oldCallbackAuthorityDelta)),
                Map.entry("freshRegistrationIds", Boolean.toString(
                        StorageSubscriptionMaskingGameTest.freshIds(state.replayIds, state.recoveredIds))),
                Map.entry("postRecoveryNativeEventDelta", Long.toString(state.recoveredEventDelta)),
                Map.entry("postRecoveryDeliveryDelta", Long.toString(state.recoveredDeliveryDelta)),
                Map.entry("activeAfterRetentionRecovery", Integer.toString(mounts.activeSubscriptionCount())),
                Map.entry("recoveredCatalogSize", Integer.toString(
                        NativeStorageNotificationHub.catalogSize(fixture.providerGrid().getStorageService()))),
                Map.entry("providerBudget", Integer.toString(
                        NativeStorageNotificationHub.TICK_BUDGET.providersPerService())),
                Map.entry("keysPerProviderBudget", Integer.toString(
                        NativeStorageNotificationHub.TICK_BUDGET.keysPerProvider()))));
    }
}

final class ScenarioState {
    int phase;
    int unchangedTicks;
    boolean primaryFirst;
    long eventBefore;
    long deliveryBefore;
    long refreshBefore;
    long aggregateBefore;
    long maskedAggregateAfter;
    long broadcastEventDelta;
    long broadcastDeliveryDelta;
    long broadcastRefreshDelta;
    long replayEventDelta;
    long replayDeliveryDelta;
    long replayRefreshDelta;
    long overflowDelta;
    long oldCallbackEventDelta;
    long oldCallbackDeliveryDelta;
    long oldCallbackAuthorityDelta;
    long recoveredEventDelta;
    long recoveredDeliveryDelta;
    int catalogAtLimit;
    int removalDelta;
    int activeAfterOverflow;
    long[] probeIds;
    long[] broadcastIds;
    long[] replayIds;
    long[] recoveredIds;
    long[] broadcastBefore;
    long[] broadcastAfter;
    long[] replayBefore;
    long[] replayAfter;
    final int[] broadcastCatalogBefore = new int[2];
    final int[] replayCatalogBefore = new int[2];
    final NativeStorageListener[] oldListeners = new NativeStorageListener[2];
}
