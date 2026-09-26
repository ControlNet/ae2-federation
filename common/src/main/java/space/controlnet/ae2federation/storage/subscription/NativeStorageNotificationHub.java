package space.controlnet.ae2federation.storage.subscription;

import appeng.api.networking.storage.IStorageService;
import appeng.api.stacks.AEKey;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

public final class NativeStorageNotificationHub {
    public static final ReconciliationBudget TICK_BUDGET = new ReconciliationBudget(1, 8);
    public static final int MAX_RETAINED_KEYS = 64;
    public static final int MAX_DISCOVERIES_BETWEEN_LISTENER_VISITS = TICK_BUDGET.keysPerProvider() - 1;
    private static final IdentityListenerRegistry<IStorageService, NativeStorageListener> LISTENERS =
            new IdentityListenerRegistry<>();
    private static final Map<IStorageService, SharedDiscoveryCatalog<AEKey>> CATALOGS = new IdentityHashMap<>();
    private static long discoveryOverflows;

    private NativeStorageNotificationHub() {
    }

    public static NativeStorageRegistration register(IStorageService storageService, NativeStorageListener listener) {
        var catalog = CATALOGS.computeIfAbsent(storageService, ignored -> new SharedDiscoveryCatalog<>(MAX_RETAINED_KEYS));
        var registration = LISTENERS.register(storageService, listener);
        try {
            listener.discoverKeys(catalog.replay());
        } catch (KeyRetentionOverflowException exception) {
            listener.onDiscoveryOverflow();
            registration.close();
            removeCatalogWithoutListeners(storageService);
        }
        return new NativeStorageRegistration() {
            @Override
            public long id() {
                return registration.id();
            }

            @Override
            public boolean active() {
                return registration.active();
            }

            @Override
            public void close() {
                registration.close();
                removeCatalogWithoutListeners(storageService);
            }
        };
    }

    public static void publishAbsolute(IStorageService storageService, AEKey key, long absoluteAmount) {
        var aggregate = new NativeStorageAmount(key, absoluteAmount);
        var observations = new ArrayList<SourceObservation>();
        LISTENERS.visitAll(storageService,
                listener -> observations.add(new SourceObservation(listener, listener.sourceAmount(aggregate.key()))));
        var catalog = CATALOGS.get(storageService);
        if (catalog == null || !catalog.contains(aggregate.key())
                && observations.stream().noneMatch(observation -> observation.absolute() > 0)) {
            return;
        }
        if (!discover(storageService, java.util.List.of(aggregate.key()))) {
            return;
        }
        for (var observation : observations) {
            observation.listener().onAmountChanged(new NativeStorageAmount(aggregate.key(), observation.absolute()));
        }
    }

    public static boolean discover(IStorageService storageService, Iterable<? extends AEKey> keys) {
        var catalog = CATALOGS.get(storageService);
        if (catalog == null) {
            return false;
        }
        try {
            var added = catalog.discover(keys);
            if (!added.isEmpty()) {
                LISTENERS.visitAll(storageService, listener -> listener.discoverKeys(added));
            }
            return true;
        } catch (KeyRetentionOverflowException exception) {
            failClosed(storageService);
            return false;
        }
    }

    public static ReconciliationWork reconcileBudgeted(IStorageService storageService) {
        var work = new int[2];
        for (var provider = 0; provider < TICK_BUDGET.providersPerService(); provider++) {
            LISTENERS.visitNext(storageService, listener -> {
                work[0]++;
                work[1] = Math.addExact(work[1], listener.reconcileSource(TICK_BUDGET.keysPerProvider()));
            });
        }
        if (work[1] > TICK_BUDGET.maximumKeyProbes()) {
            throw new IllegalStateException("Subscription reconciliation exceeded its key budget");
        }
        return new ReconciliationWork(work[0], work[1]);
    }

    public static boolean hasListeners(IStorageService storageService) {
        return LISTENERS.hasListeners(storageService);
    }

    public static int registrationCount() {
        return LISTENERS.registrationCount();
    }

    public static int removalCount() {
        return LISTENERS.removalCount();
    }

    public static int activeCount() {
        return LISTENERS.activeCount();
    }

    public static int activeCount(IStorageService storageService) {
        return LISTENERS.activeCount(storageService);
    }

    public static int catalogSize(IStorageService storageService) {
        var catalog = CATALOGS.get(storageService);
        return catalog == null ? 0 : catalog.size();
    }

    public static int serviceCatalogCount() {
        return CATALOGS.size();
    }

    public static long discoveryOverflowCount() {
        return discoveryOverflows;
    }

    static void failClosed(IStorageService storageService) {
        discoveryOverflows = Math.incrementExact(discoveryOverflows);
        LISTENERS.visitAll(storageService, NativeStorageListener::onDiscoveryOverflow);
        LISTENERS.closeAll(storageService);
        CATALOGS.remove(storageService);
    }

    private static void removeCatalogWithoutListeners(IStorageService storageService) {
        if (!LISTENERS.hasListeners(storageService)) {
            CATALOGS.remove(storageService);
        }
    }

    private record SourceObservation(NativeStorageListener listener, long absolute) {
        private SourceObservation {
            if (absolute < 0) {
                throw new IllegalArgumentException("Native source amount cannot be negative");
            }
        }
    }
}
