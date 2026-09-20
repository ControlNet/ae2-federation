package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class StorageSubscriptionContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void pinnedNativeWatcherUsesAbsoluteCountsAndExplicitCleanup() throws IOException {
        var semantics = source("storage/subscription/NativeStorageNotificationHub.java");
        var registry = source("storage/subscription/IdentityListenerRegistry.java");
        var mixin = source("mixin/StorageServiceNotificationMixin.java");
        assertTrue(semantics.contains("new NativeStorageAmount(key, absoluteAmount)"));
        assertTrue(semantics.contains("IdentityListenerRegistry"));
        assertTrue(semantics.contains("MAX_RETAINED_KEYS = 64"));
        assertTrue(semantics.contains("failClosed(storageService)"));
        assertTrue(semantics.contains("LISTENERS.closeAll(storageService)"));
        assertTrue(semantics.indexOf("LISTENERS.closeAll(storageService)")
                < semantics.indexOf("CATALOGS.remove(storageService)"));
        assertTrue(registry.contains("for (var registration : new ArrayList<>(registered))"));
        assertTrue(registry.contains("if (registration.active())"));
        assertTrue(mixin.contains("postWatcherUpdate"));
        assertTrue(mixin.contains("onServerEndTick"));
        assertTrue(mixin.contains("reconcileBudgeted"));
        assertTrue(!mixin.contains("getCachedInventory"));
    }

    @Test
    void subscriptionsUseQualifiedSourceIdentityAndNeverRecompileTopologyForQuantity() throws IOException {
        var subscriptions = source("storage/subscription/StorageSubscriptionService.java");
        var subscriptionKey = source("storage/subscription/SourceSubscriptionKey.java");
        var mounts = source("storage/mount/StorageMountService.java");
        var planner = source("storage/mount/StorageSubscriptionPlanner.java");
        assertTrue(subscriptionKey.contains("ExportSourceId"));
        assertTrue(subscriptionKey.contains("SourceGeneration"));
        assertTrue(subscriptions.contains("EffectiveSourceRelationshipKey"));
        assertTrue(subscriptions.contains("invalidateCache"));
        assertTrue(planner.contains("subscriptions.reconcile"));
        assertTrue(mounts.contains("subscriptions.close"));
        assertTrue(subscriptions.contains("NativeStorageNotificationHub.discover"));
    }

    @Test
    void fixtureCleanupIsFinallyBoundAndSharedDiscoveryIsBounded() throws IOException {
        var fixture = Files.readString(ROOT.resolve(
                "common/src/testmod/java/space/controlnet/ae2federation/test/storage/DirectSubscriptionFixture.java"));
        var hub = source("storage/subscription/NativeStorageNotificationHub.java");
        var cursor = source("storage/subscription/BoundedKeyCursor.java");
        assertTrue(fixture.contains("finally"));
        assertTrue(fixture.contains("hooks.close()"));
        assertTrue(fixture.contains("bridge.close()"));
        assertTrue(hub.contains("SharedDiscoveryCatalog"));
        assertTrue(hub.contains("removeCatalogWithoutListeners"));
        assertTrue(cursor.contains("throw new KeyRetentionOverflowException"));
    }

    @Test
    void taskTwentyFourHasExactNativeAndAdversarialQa() throws IOException {
        var manifest = Files.readString(ROOT.resolve("tests/scenarios/manifest.json"));
        var qa = Files.readString(ROOT.resolve("gradle/federation-qa.gradle"));
        for (var caseId : new String[] {"subscription.two-same-key-events", "subscription.diamond-once",
                "subscription.snapshot-race", "subscription.first-filter", "subscription.listener-cleanup",
                "subscription.reject-stale-generation", "subscription.masked-equal-opposite"}) {
            assertTrue(manifest.contains("\"id\":\"" + caseId + "\""));
        }
        assertTrue(qa.contains("verifyTaskTwentyFourEvidence"));
        assertTrue(qa.contains("federationTaskTwentyFourEvidenceSelfTest"));
        assertTrue(qa.contains("subscriptionsnapshotrace: 30"));
        assertTrue(qa.contains("subscriptionmaskedequalopposite: 42"));
        assertTrue(qa.contains("subscriptionlistenercleanup: 12"));
        assertTrue(qa.contains("crossAttributedTrace"));
        assertTrue(qa.contains("missingExactOverflowClosure"));
        assertTrue(qa.contains("leakedServiceCatalog"));
    }

    @Test
    void nativeEvidenceUsesExactListenerLedgerCorrelationAndBothRegistrationOrders() throws IOException {
        var hookMixin = testmodSource("mixin/NativeStorageNotificationHubTestHookMixin.java");
        var hooks = testmodSource("storage/SubscriptionTestHooks.java");
        var boundary = testmodSource("StorageSubscriptionBoundaryGameTest.java");
        var masking = testmodSource("StorageSubscriptionMaskingGameTest.java");
        assertTrue(hookMixin.contains("@Redirect(method = \"publishAbsolute\""));
        assertTrue(hookMixin.contains("SubscriptionTestHooks.beginHubDelivery(listener)"));
        assertTrue(hookMixin.contains("SubscriptionTestHooks.endHubDelivery(listener)"));
        assertTrue(hooks.contains("IdentityHashMap<Object, ActiveTrace> ACTIVE_TRACES"));
        assertTrue(hooks.contains("ACTIVE_TRACES.get(ledger)"));
        assertTrue(hooks.contains("System.identityHashCode(delivery.listener())"));
        assertTrue(boundary.contains("deliverTrace(secondListener, secondLedger)"));
        assertTrue(boundary.contains("deliverTrace(firstListener, firstLedger)"));
        assertTrue(boundary.contains("isolatedCrossAttribution"));
        assertTrue(masking.contains("Broadcast first registration must start empty"));
        assertTrue(masking.contains("Replay key must predate the second registration"));
        assertTrue(masking.contains("state.oldListeners[0].onAmountChanged"));
        assertTrue(masking.contains("Recovered registrations must receive a real native event"));
    }

    @Test
    void listenerCleanupEvidenceRequiresFinalCatalogRemoval() throws IOException {
        var gameTests = testmodSource("StorageSubscriptionGameTests.java");
        assertTrue(gameTests.contains("StorageLevelLifecycle.close(helper.getLevel())"));
        assertTrue(gameTests.contains("NativeStorageNotificationHub.serviceCatalogCount()"));
        assertTrue(gameTests.contains("serviceCatalogsAfterClose"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(ROOT.resolve("common/src/main/java/space/controlnet/ae2federation/" + relative));
    }

    private static String testmodSource(String relative) throws IOException {
        return Files.readString(ROOT.resolve("common/src/testmod/java/space/controlnet/ae2federation/test/" + relative));
    }
}
