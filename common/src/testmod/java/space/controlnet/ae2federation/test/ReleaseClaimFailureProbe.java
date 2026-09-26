package space.controlnet.ae2federation.test;

/** Test-only one-shot CAS fault. Never packaged or loaded in production. */
public final class ReleaseClaimFailureProbe {
    private static final ThreadLocal<Boolean> REJECT = ThreadLocal.withInitial(() -> false);

    private ReleaseClaimFailureProbe() {
    }

    public static void rejectNext() {
        REJECT.set(true);
    }

    public static boolean consume() {
        var reject = REJECT.get();
        REJECT.remove();
        return reject;
    }
}
