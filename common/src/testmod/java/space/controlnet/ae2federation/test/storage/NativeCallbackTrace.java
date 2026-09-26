package space.controlnet.ae2federation.test.storage;

public record NativeCallbackTrace(long hubOrder, long ledgerAcceptOrder, long snapshotCompleteOrder,
        boolean boundaryOpen, int queuedEvents, long eventVersionBeforeReplay, long eventVersionAfter,
        int listenerIdentity, int ledgerIdentity) {
    public long replayedEvents() {
        return eventVersionAfter - eventVersionBeforeReplay;
    }
}
