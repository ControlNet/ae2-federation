package space.controlnet.ae2federation.ae2.storage;

import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import space.controlnet.ae2federation.mixin.compat.NetworkStorageAccess;

/**
 * Stops a projection from reaching its source network's storage again while that storage is already in use on this
 * thread. A projection exports the source network's own handles, but native AE2 paths can lead back into it: an ME
 * Storage Bus on the source network facing an unconfigured ME Interface of the consumer reads the consumer's whole
 * {@link NetworkStorage}, which holds the projection of the source's handles. AE2 guards each {@link NetworkStorage}
 * against its own recursion only, so without this the source's drives are listed (and offered) twice.
 *
 * <p>The source network is in use when its {@link NetworkStorage} is inside one of its own operations (AE2's private
 * {@code mountsInUse} flag, read through an accessor because AE2 exposes no API for it) or when a projection of it is
 * already executing higher on this thread's stack (a third network reading the source through the same loop). A
 * re-entered projection behaves as AE2's own recursion guard does: it lists nothing and accepts nothing.
 */
public final class NativeStorageReentry {
    private static final ThreadLocal<Set<MEStorage>> ENTERED =
            ThreadLocal.withInitial(() -> Collections.newSetFromMap(new IdentityHashMap<>()));

    private NativeStorageReentry() {
    }

    /**
     * Marks {@code network} as in use by a projection, unless it already is in use on this thread. Returns false
     * (and marks nothing) when it is; otherwise the caller must {@link #exit} it in a {@code finally} block.
     */
    public static boolean enter(MEStorage network) {
        if (nativeInUse(network)) {
            return false;
        }
        return ENTERED.get().add(network);
    }

    public static void exit(MEStorage network) {
        ENTERED.get().remove(network);
    }

    private static boolean nativeInUse(MEStorage network) {
        return network instanceof NetworkStorageAccess access && access.ae2federation$mountsInUse();
    }
}
