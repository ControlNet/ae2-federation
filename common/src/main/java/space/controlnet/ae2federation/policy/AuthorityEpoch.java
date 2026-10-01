package space.controlnet.ae2federation.policy;

/**
 * Advances on every change of what a held authorization's full check reads on the Federation side: Policy edits
 * (the store's high watermark), Federation Domain evidence and topology, Grid identity settlement
 * ({@link space.controlnet.ae2federation.identity.IdentityEpoch}), the storage dependency compilation, mounts and
 * native source domains, and the per-level services that hold them. While it is unchanged, a check that passed at
 * this value reads the same Federation state again, so only native AE2 state (node activity, mount tables) has to be
 * read again. Advancing more often than needed only costs a full check. Server-thread state.
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
