package space.controlnet.ae2federation.ae2.processing;

/**
 * Notified by a native Lane after AE2 accepted a Pattern push through it. The host uses it only to know that work may
 * be inside the machine; it does not track what was sent or infer completion.
 */
public interface NativeLaneDispatchListener {
    void onLaneDispatched();
}
