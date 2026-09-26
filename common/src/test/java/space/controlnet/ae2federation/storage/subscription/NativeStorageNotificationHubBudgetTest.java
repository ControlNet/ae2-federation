package space.controlnet.ae2federation.storage.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import java.lang.reflect.Proxy;
import org.junit.jupiter.api.Test;

final class NativeStorageNotificationHubBudgetTest {
    @Test
    void enforcesProviderAndKeyBudgetWhileAdvancingProviderCursor() {
        var service = (IStorageService) Proxy.newProxyInstance(IStorageService.class.getClassLoader(),
                new Class<?>[] {IStorageService.class}, (proxy, method, arguments) -> null);
        var firstVisits = new int[1];
        var secondVisits = new int[1];
        var catalogsBefore = NativeStorageNotificationHub.serviceCatalogCount();
        var first = NativeStorageNotificationHub.register(service, listener(firstVisits));
        var second = NativeStorageNotificationHub.register(service, listener(secondVisits));
        assertEquals(catalogsBefore + 1, NativeStorageNotificationHub.serviceCatalogCount());

        var firstTick = NativeStorageNotificationHub.reconcileBudgeted(service);
        var secondTick = NativeStorageNotificationHub.reconcileBudgeted(service);

        assertEquals(1, firstTick.providerVisits());
        assertTrue(firstTick.keyProbes() <= NativeStorageNotificationHub.TICK_BUDGET.maximumKeyProbes());
        assertEquals(1, secondTick.providerVisits());
        assertEquals(1, firstVisits[0]);
        assertEquals(1, secondVisits[0]);
        first.close();
        second.close();
        assertEquals(0, NativeStorageNotificationHub.catalogSize(service));
        assertEquals(catalogsBefore, NativeStorageNotificationHub.serviceCatalogCount());
    }

    @Test
    void overflowFailClosedClosesExactRegistrationsWhenListenersDoNothing() {
        var service = service();
        var activeBefore = NativeStorageNotificationHub.activeCount();
        var removalsBefore = NativeStorageNotificationHub.removalCount();
        var catalogsBefore = NativeStorageNotificationHub.serviceCatalogCount();
        var first = NativeStorageNotificationHub.register(service, listener(new int[1]));
        var second = NativeStorageNotificationHub.register(service, listener(new int[1]));
        NativeStorageNotificationHub.failClosed(service);

        assertEquals(activeBefore, NativeStorageNotificationHub.activeCount());
        assertEquals(0, NativeStorageNotificationHub.activeCount(service));
        assertEquals(removalsBefore + 2, NativeStorageNotificationHub.removalCount());
        assertFalse(first.active());
        assertFalse(second.active());
        assertEquals(0, NativeStorageNotificationHub.catalogSize(service));
        assertEquals(catalogsBefore, NativeStorageNotificationHub.serviceCatalogCount());
        first.close();
        second.close();
        assertEquals(removalsBefore + 2, NativeStorageNotificationHub.removalCount());
    }

    private static NativeStorageListener listener(int[] visits) {
        return new NativeStorageListener() {
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
                visits[0]++;
                return keyBudget;
            }
        };
    }

    private static IStorageService service() {
        return (IStorageService) Proxy.newProxyInstance(IStorageService.class.getClassLoader(),
                new Class<?>[] {IStorageService.class}, (proxy, method, arguments) -> null);
    }

}
