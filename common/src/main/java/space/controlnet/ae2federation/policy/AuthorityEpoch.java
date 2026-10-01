package space.controlnet.ae2federation.policy;

/**
 * Advances on every change of what a held authorization's full check reads on the Federation side: Policy edits
 * (the store's high watermark), Federation Domain evidence and topology, Grid identity settlement
 * ({@link space.controlnet.ae2federation.identity.IdentityEpoch}), the storage dependency compilation, mounts and
 * native source domains, and the per-level services that hold them; and on the native AE2 side, every node joining or
 * leaving a Grid (through identity), every mount table change ({@code NativeMountLedger}) and every Grid power or
 * booting change, which covers channels ({@code NativeGridStateEvents}). While it is unchanged, a check that passed
 * at this value reads the same state again except AE2 storage wrappers' delegates, which change without any event and
 * are read again. Advancing more often than needed only costs a full check. Server-thread state.
 */
public final class AuthorityEpoch {
    private static long value;

    private AuthorityEpoch() {
    }

    public static long current() {
        return value;
    }

    public static void advance() {
        value++;
    }
}
