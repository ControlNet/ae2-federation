package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEItemKey;
import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.storage.subscription.NativeStorageAmount;
import space.controlnet.ae2federation.storage.subscription.NativeStorageListener;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;
import space.controlnet.ae2federation.test.storage.DirectSubscriptionFixture;
import space.controlnet.ae2federation.test.storage.NativeCallbackTrace;
import space.controlnet.ae2federation.test.storage.SubscriptionHookOwner;
import space.controlnet.ae2federation.test.storage.SubscriptionTestHooks;

@PrefixGameTestTemplate(false)
public final class StorageSubscriptionBoundaryGameTest {
    private static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    private static final IActionSource ACTION_SOURCE = IActionSource.empty();

    private StorageSubscriptionBoundaryGameTest() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 500, required = true, manualOnly = true)
    public static void subscriptionSnapshotRace(GameTestHelper helper) {
        var fixture = new DirectSubscriptionFixture(helper);
        var phase = new int[1];
        var before = new long[2];
        var repair = new long[7];
        var traceReceipt = new NativeCallbackTrace[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for snapshot-race relationship");
            var mounts = fixture.mounts();
            if (phase[0] == 0) {
                fixture.configure(PolicyRule.storageDefaults());
                before[0] = mounts.subscriptionSnapshotVersion(fixture.key());
                before[1] = mounts.subscriptionEventVersion(fixture.key());
                fixture.source().insert(IRON, 4, Actionable.MODULATE, ACTION_SOURCE);
                fixture.hooks().traceNextNativeCallback();
                fixture.hooks().atNextSnapshotBoundary(() -> {
                    fixture.source().insert(IRON, 5, Actionable.MODULATE, ACTION_SOURCE);
                    fixture.providerGrid().getStorageService().invalidateCache();
                    fixture.providerGrid().getStorageService().getCachedInventory();
                });
                mounts.resetSubscription(fixture.key());
                phase[0] = 1;
                helper.assertTrue(false, "Waiting for snapshot-race native event");
            }
            if (phase[0] == 1) {
                helper.assertTrue(mounts.subscriptionEventVersion(fixture.key()) >= before[1] + 2,
                        "Snapshot baseline and racing event must both reconcile");
                repair[6] = fixture.source().getAvailableStacks().get(IRON);
                helper.assertValueEqual(repair[6], 9L, "Snapshot reconciliation must retain the racing event");
                repair[1] = mounts.subscriptionEventVersion(fixture.key()) - before[1];
                repair[0] = mounts.subscriptionSnapshotVersion(fixture.key());
                traceReceipt[0] = fixture.hooks().callbackTrace();
                var trace = traceReceipt[0];
                helper.assertTrue(trace != null && trace.boundaryOpen(),
                        "The production hub callback must enter while the ledger boundary is open");
                helper.assertTrue(trace.hubOrder() < trace.ledgerAcceptOrder()
                        && trace.ledgerAcceptOrder() < trace.snapshotCompleteOrder(),
                        "Hub, ledger acceptance, and completion ordering must be monotonic");
                helper.assertValueEqual(trace.queuedEvents(), 1, "Exactly one native callback must queue");
                helper.assertValueEqual(trace.replayedEvents(), 1L, "Exactly one queued callback must replay");
                repair[2] = mounts.subscriptionRegistrationId(fixture.key());
                repair[3] = mounts.subscriptionRemovalCount();
                fixture.hooks().atNextSnapshotBoundary(() -> {
                    for (var event = 0; event <= 256; event++) {
                        fixture.source().insert(IRON, 1, Actionable.MODULATE, ACTION_SOURCE);
                        fixture.providerGrid().getStorageService().invalidateCache();
                        fixture.providerGrid().getStorageService().getCachedInventory();
                    }
                });
                mounts.resetSubscription(fixture.key());
                phase[0] = 2;
                helper.assertTrue(false, "Waiting to verify overflow retirement");
            }
            if (phase[0] == 2) {
                helper.assertValueEqual(mounts.activeSubscriptionCount(), 0,
                        "Snapshot overflow must retire the exact listener");
                helper.assertValueEqual(mounts.subscriptionRemovalCount(), (int) repair[3] + 1,
                        "Snapshot overflow must produce one listener removal receipt");
                mounts.reconcileAll();
                phase[0] = 3;
                helper.assertTrue(false, "Waiting for same-plan overflow recovery");
            }
            if (phase[0] == 3) {
                repair[4] = mounts.subscriptionRegistrationId(fixture.key());
                helper.assertTrue(repair[4] != 0 && repair[4] != repair[2],
                        "Same-plan reconciliation must install a fresh listener after overflow");
                helper.assertValueEqual(mounts.activeSubscriptionCount(), 1,
                        "Same-plan reconciliation must recover one active listener");
                repair[5] = mounts.sourceEventCount();
                fixture.source().insert(IRON, 1, Actionable.MODULATE, ACTION_SOURCE);
                fixture.providerGrid().getStorageService().invalidateCache();
                fixture.providerGrid().getStorageService().getCachedInventory();
                phase[0] = 4;
                helper.assertTrue(false, "Waiting for recovered native callback");
            }
            helper.assertValueEqual(mounts.sourceEventCount(), repair[5] + 1,
                    "A real native callback must reach the recovered listener");
            var isolation = verifyTraceIsolation(helper);
            var abandonedInvocations = new int[1];
            fixture.hooks().atNextSnapshotBoundary(() -> abandonedInvocations[0]++);
            fixture.hooks().captureNextRegistration(listener -> abandonedInvocations[0]++);
            fixture.hooks().traceNextNativeCallback();
            var closeFailure = new IllegalStateException[1];
            try {
                fixture.close();
            } catch (IllegalStateException exception) {
                closeFailure[0] = exception;
            }
            helper.assertTrue(closeFailure[0] != null && closeFailure[0].getMessage().contains("unconsumed hooks: 3"),
                    "Fixture close must surface its unconsumed-hook assertion");
            helper.assertTrue(fixture.bridgeClosed(), "Fixture close must close its bridge after the hook assertion");
            helper.assertValueEqual(fixture.hooks().pendingCount(), 0, "Fixture close must clear abandoned hooks");
            helper.assertValueEqual(fixture.hooks().clearedCount(), 3, "All fixture-owned hook kinds must be cleared");
            var unrelatedLedger = new Object();
            SubscriptionTestHooks.fireSnapshotBoundary(unrelatedLedger);
            SubscriptionTestHooks.captureRegistration(null);
            SubscriptionTestHooks.recordLedgerAccept(unrelatedLedger, true, 0);
            SubscriptionTestHooks.recordReplayStart(unrelatedLedger, 1, 0);
            SubscriptionTestHooks.recordSnapshotComplete(unrelatedLedger, 1);
            helper.assertValueEqual(abandonedInvocations[0], 0, "Later boundaries must not invoke abandoned hooks");
            writeEvidence(new EvidenceState(before, repair, traceReceipt[0], isolation, fixture,
                    abandonedInvocations[0], closeFailure[0]));
        });
    }

    private static void writeEvidence(EvidenceState state) {
        var before = state.before();
        var repair = state.repair();
        var trace = state.trace();
        var isolation = state.isolation();
        PolicyEvidence.write("subscriptionsnapshotrace", 30, Map.ofEntries(
                Map.entry("snapshotBefore", Long.toString(before[0])), Map.entry("snapshotAfter", Long.toString(repair[0])),
                Map.entry("eventVersionDelta", Long.toString(repair[1])), Map.entry("snapshotAbsolute", "4"),
                Map.entry("racingAbsolute", "9"), Map.entry("finalAbsolute", Long.toString(repair[6])),
                Map.entry("raceLost", "false"), Map.entry("boundedQueue", "true"),
                Map.entry("hubOrder", Long.toString(trace.hubOrder())),
                Map.entry("ledgerAcceptOrder", Long.toString(trace.ledgerAcceptOrder())),
                Map.entry("snapshotCompleteOrder", Long.toString(trace.snapshotCompleteOrder())),
                Map.entry("boundaryOpen", Boolean.toString(trace.boundaryOpen())),
                Map.entry("queuedEvents", Integer.toString(trace.queuedEvents())),
                Map.entry("replayedEvents", Long.toString(trace.replayedEvents())),
                Map.entry("traceListenerIdentity", Integer.toString(trace.listenerIdentity())),
                Map.entry("traceLedgerIdentity", Integer.toString(trace.ledgerIdentity())),
                Map.entry("isolatedTraceOwners", "2"), Map.entry("isolatedTraceLedgers", "2"),
                Map.entry("isolatedTracePending", Integer.toString(isolation.pendingAfter())),
                Map.entry("isolatedFirstListenerIdentity", Integer.toString(isolation.first().listenerIdentity())),
                Map.entry("isolatedFirstLedgerIdentity", Integer.toString(isolation.first().ledgerIdentity())),
                Map.entry("isolatedSecondListenerIdentity", Integer.toString(isolation.second().listenerIdentity())),
                Map.entry("isolatedSecondLedgerIdentity", Integer.toString(isolation.second().ledgerIdentity())),
                Map.entry("isolatedCrossAttribution", Boolean.toString(isolation.crossAttributed())),
                Map.entry("overflowRetired", "true"), Map.entry("samePlanRecovered", "true"),
                Map.entry("recoveredNativeEventDelta", "1"),
                Map.entry("abandonedHooksCleared", Integer.toString(state.fixture().hooks().clearedCount())),
                Map.entry("abandonedHookInvocations", Integer.toString(state.abandonedHookInvocations())),
                Map.entry("fixtureBridgeClosed", Boolean.toString(state.fixture().bridgeClosed())),
                Map.entry("fixtureCloseAssertionPreserved", Boolean.toString(state.closeFailure() != null
                        && state.closeFailure().getMessage().contains("unconsumed hooks: 3"))),
                Map.entry("registrationBeforeOverflow", Long.toString(repair[2])),
                Map.entry("registrationAfterOverflow", Long.toString(repair[4]))));
    }

    private static TraceIsolationReceipt verifyTraceIsolation(GameTestHelper helper) {
        var firstOwner = new SubscriptionHookOwner();
        var secondOwner = new SubscriptionHookOwner();
        var firstLedger = new Object();
        var secondLedger = new Object();
        var firstListener = new TraceListener();
        var secondListener = new TraceListener();
        firstOwner.traceNextNativeCallback();
        firstOwner.atNextSnapshotBoundary(() -> {});
        secondOwner.traceNextNativeCallback();
        secondOwner.atNextSnapshotBoundary(() -> {});
        SubscriptionTestHooks.fireSnapshotBoundary(firstLedger);
        SubscriptionTestHooks.fireSnapshotBoundary(secondLedger);
        deliverTrace(secondListener, secondLedger);
        deliverTrace(firstListener, firstLedger);
        var first = firstOwner.callbackTrace();
        var second = secondOwner.callbackTrace();
        var crossAttributed = first == null || second == null
                || first.listenerIdentity() != System.identityHashCode(firstListener)
                || first.ledgerIdentity() != System.identityHashCode(firstLedger)
                || second.listenerIdentity() != System.identityHashCode(secondListener)
                || second.ledgerIdentity() != System.identityHashCode(secondLedger);
        var pending = firstOwner.pendingCount() + secondOwner.pendingCount();
        helper.assertTrue(first != null && second != null, "Both exact listener-ledger traces must complete");
        helper.assertTrue(!crossAttributed, "Interleaved callbacks must not cross-attribute listeners or ledgers");
        helper.assertValueEqual(pending, 0, "Interleaved callback owners must not retain pending trace state");
        firstOwner.assertConsumedAndClose();
        secondOwner.assertConsumedAndClose();
        return new TraceIsolationReceipt(first, second, pending, crossAttributed);
    }

    private static void deliverTrace(NativeStorageListener listener, Object ledger) {
        SubscriptionTestHooks.beginHubDelivery(listener);
        try {
            SubscriptionTestHooks.recordLedgerAccept(ledger, true, 0);
        } finally {
            SubscriptionTestHooks.endHubDelivery(listener);
        }
        SubscriptionTestHooks.recordReplayStart(ledger, 1, 0);
        SubscriptionTestHooks.recordSnapshotComplete(ledger, 1);
    }

    private record EvidenceState(long[] before, long[] repair, NativeCallbackTrace trace,
            TraceIsolationReceipt isolation,
            DirectSubscriptionFixture fixture, int abandonedHookInvocations, IllegalStateException closeFailure) {
    }

    private record TraceIsolationReceipt(NativeCallbackTrace first, NativeCallbackTrace second, int pendingAfter,
            boolean crossAttributed) {
    }

    private static final class TraceListener implements NativeStorageListener {
        @Override
        public void onAmountChanged(NativeStorageAmount amount) {
        }

        @Override
        public long sourceAmount(AEKey key) {
            return 0;
        }

        @Override
        public void discoverKeys(Iterable<? extends AEKey> keys) {
        }

        @Override
        public void onDiscoveryOverflow() {
        }

        @Override
        public int reconcileSource(int keyBudget) {
            return 0;
        }
    }
}
