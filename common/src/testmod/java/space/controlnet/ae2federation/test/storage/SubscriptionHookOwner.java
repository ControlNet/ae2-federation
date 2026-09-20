package space.controlnet.ae2federation.test.storage;

import java.util.function.Consumer;
import space.controlnet.ae2federation.storage.subscription.NativeStorageListener;

public final class SubscriptionHookOwner implements AutoCloseable {
    private int pending;
    private int consumed;
    private int cleared;
    private NativeCallbackTrace callbackTrace;

    public void atNextSnapshotBoundary(Runnable action) {
        pending++;
        SubscriptionTestHooks.armSnapshot(this, action);
    }

    public void captureNextRegistration(Consumer<NativeStorageListener> capture) {
        pending++;
        SubscriptionTestHooks.armRegistration(this, capture);
    }

    public void traceNextNativeCallback() {
        pending++;
        SubscriptionTestHooks.armTrace(this);
    }

    public int pendingCount() {
        return pending;
    }

    public int consumedCount() {
        return consumed;
    }

    public int clearedCount() {
        return cleared;
    }

    public NativeCallbackTrace callbackTrace() {
        return callbackTrace;
    }

    void consumed() {
        pending--;
        consumed++;
    }

    void cleared(int count) {
        pending -= count;
        cleared += count;
    }

    void callbackTrace(NativeCallbackTrace trace) {
        callbackTrace = trace;
        consumed();
    }

    public void assertConsumedAndClose() {
        if (pending != 0) {
            throw new IllegalStateException("Subscription test owner has unconsumed hooks: " + pending);
        }
        close();
    }

    @Override
    public void close() {
        SubscriptionTestHooks.clear(this);
    }
}
