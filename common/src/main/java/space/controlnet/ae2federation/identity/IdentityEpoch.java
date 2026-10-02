package space.controlnet.ae2federation.identity;

import space.controlnet.ae2federation.policy.AuthorityEpoch;

/**
 * Advances on every change that can change a Grid's {@link NetworkIdentityService#settlement()}: a claim change in
 * any level's claim index, and a node, provisional-node, duplicate-lineage or registry change in any identity
 * service. While it is unchanged, a settled Grid's {@code settlement()} returns the same object it returned before.
 * Server-thread state, like the identity services it follows.
 */
public final class IdentityEpoch {
    private static long value;

    private IdentityEpoch() {
    }

    public static long current() {
        return value;
    }

    static void advance() {
        value++;
        AuthorityEpoch.advance();
    }
}
