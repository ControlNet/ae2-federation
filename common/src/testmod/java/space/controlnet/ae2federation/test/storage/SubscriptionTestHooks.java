package space.controlnet.ae2federation.test.storage;

import java.util.ArrayDeque;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.function.Consumer;
import space.controlnet.ae2federation.storage.subscription.NativeStorageListener;

public final class SubscriptionTestHooks {
    private static final ArrayDeque<OwnedAction<Runnable>> SNAPSHOTS = new ArrayDeque<>();
    private static final ArrayDeque<OwnedAction<Consumer<NativeStorageListener>>> REGISTRATIONS = new ArrayDeque<>();
    private static final ArrayDeque<SubscriptionHookOwner> TRACES = new ArrayDeque<>();
    private static final IdentityHashMap<Object, ActiveTrace> ACTIVE_TRACES = new IdentityHashMap<>();
    private static final ArrayDeque<HubDelivery> DELIVERIES = new ArrayDeque<>();
    private static long order;

    private SubscriptionTestHooks() {
    }

    static void armSnapshot(SubscriptionHookOwner owner, Runnable action) {
        SNAPSHOTS.addLast(new OwnedAction<>(owner, action));
    }

    static void armRegistration(SubscriptionHookOwner owner, Consumer<NativeStorageListener> capture) {
        REGISTRATIONS.addLast(new OwnedAction<>(owner, capture));
    }

    static void armTrace(SubscriptionHookOwner owner) {
        TRACES.addLast(owner);
    }

    public static void fireSnapshotBoundary(Object ledger) {
        var armed = SNAPSHOTS.pollFirst();
        if (armed != null) {
            if (TRACES.remove(armed.owner())) {
                ACTIVE_TRACES.put(ledger, new ActiveTrace(armed.owner(), 0));
            }
            armed.owner().consumed();
            armed.action().run();
        }
    }

    public static void captureRegistration(NativeStorageListener listener) {
        var armed = REGISTRATIONS.pollFirst();
        if (armed != null) {
            armed.owner().consumed();
            armed.action().accept(listener);
        }
    }

    public static void beginHubDelivery(NativeStorageListener listener) {
        DELIVERIES.addLast(new HubDelivery(listener, ++order));
    }

    public static void endHubDelivery(NativeStorageListener listener) {
        var delivery = DELIVERIES.pollLast();
        if (delivery == null || delivery.listener() != listener) {
            throw new IllegalStateException("Subscription hub delivery stack is unbalanced");
        }
    }

    public static void recordLedgerAccept(Object ledger, boolean boundaryOpen, long eventVersion) {
        var active = ACTIVE_TRACES.get(ledger);
        var delivery = DELIVERIES.peekLast();
        if (active != null && delivery != null) {
            ACTIVE_TRACES.put(ledger, active.accepted(delivery, ledger, ++order, boundaryOpen));
        }
    }

    public static void recordReplayStart(Object ledger, int queuedEvents, long eventVersion) {
        var active = ACTIVE_TRACES.get(ledger);
        if (active != null) {
            ACTIVE_TRACES.put(ledger, active.replay(queuedEvents, eventVersion));
        }
    }

    public static void recordSnapshotComplete(Object ledger, long eventVersion) {
        var trace = ACTIVE_TRACES.get(ledger);
        if (trace != null && trace.acceptOrder() != 0) {
            ACTIVE_TRACES.remove(ledger);
            trace.owner().callbackTrace(new NativeCallbackTrace(trace.hubOrder(), trace.acceptOrder(), ++order,
                    trace.boundaryOpen(), trace.queuedEvents(), trace.eventVersionBeforeReplay(), eventVersion,
                    trace.listenerIdentity(), trace.ledgerIdentity()));
        }
    }

    static void clear(SubscriptionHookOwner owner) {
        var cleared = removeOwned(SNAPSHOTS.iterator(), owner) + removeOwned(REGISTRATIONS.iterator(), owner);
        while (TRACES.remove(owner)) {
            cleared++;
        }
        var active = ACTIVE_TRACES.entrySet().iterator();
        while (active.hasNext()) {
            if (active.next().getValue().owner() == owner) {
                active.remove();
                cleared++;
            }
        }
        owner.cleared(cleared);
    }

    private static int removeOwned(Iterator<? extends OwnedAction<?>> actions, SubscriptionHookOwner owner) {
        var removed = 0;
        while (actions.hasNext()) {
            if (actions.next().owner() == owner) {
                actions.remove();
                removed++;
            }
        }
        return removed;
    }

    private record OwnedAction<T>(SubscriptionHookOwner owner, T action) {
    }

    private record ActiveTrace(SubscriptionHookOwner owner, long hubOrder, long acceptOrder, boolean boundaryOpen,
            int queuedEvents, long eventVersionBeforeReplay, int listenerIdentity, int ledgerIdentity) {
        private ActiveTrace(SubscriptionHookOwner owner, long hubOrder) {
            this(owner, hubOrder, 0, false, 0, 0, 0, 0);
        }

        private ActiveTrace accepted(HubDelivery delivery, Object ledger, long nextOrder, boolean open) {
            return new ActiveTrace(owner, delivery.order(), nextOrder, open, queuedEvents, eventVersionBeforeReplay,
                    System.identityHashCode(delivery.listener()), System.identityHashCode(ledger));
        }

        private ActiveTrace replay(int queued, long eventVersion) {
            return new ActiveTrace(owner, hubOrder, acceptOrder, boundaryOpen, queued, eventVersion, listenerIdentity,
                    ledgerIdentity);
        }
    }

    private record HubDelivery(NativeStorageListener listener, long order) {
    }
}
