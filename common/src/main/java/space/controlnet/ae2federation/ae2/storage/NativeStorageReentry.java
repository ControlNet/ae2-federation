package space.controlnet.ae2federation.ae2.storage;

import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stops a projection from reaching its source network's storage again while that storage is already in use on this
 * thread. A projection exports the source network's own handles, but native AE2 paths can lead back into it: an ME
 * Storage Bus on the source network facing an unconfigured ME Interface of the consumer reads the consumer's whole
 * {@link NetworkStorage}, which holds the projection of the source's handles. AE2 guards each {@link NetworkStorage}
 * against its own recursion only, so without this the source's drives are listed (and offered) twice.
 *
 * <p>The source network is in use when its {@link NetworkStorage} is inside one of its own operations (AE2's private
 * {@code mountsInUse} flag, read by reflection because AE2 exposes no API for it) or when a projection of it is
 * already executing higher on this thread's stack (a third network reading the source through the same loop). A
 * re-entered projection behaves as AE2's own recursion guard does: it lists nothing and accepts nothing.
 */
public final class NativeStorageReentry {
    private static final Logger LOGGER = LoggerFactory.getLogger(NativeStorageReentry.class);
    private static final @Nullable Field IN_USE = inUseField();
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
        if (IN_USE == null || !(network instanceof NetworkStorage)) {
            return false;
        }
        try {
            return IN_USE.getBoolean(network);
        } catch (IllegalAccessException | RuntimeException exception) {
            return false;
        }
    }

    private static @Nullable Field inUseField() {
        try {
            var field = NetworkStorage.class.getDeclaredField("mountsInUse");
            if (field.getType() == boolean.class && field.trySetAccessible()) {
                return field;
            }
        } catch (NoSuchFieldException | RuntimeException ignored) {
            // Reported below.
        }
        // Projections still stop at each other; a loop through native AE2 parts may then count storage twice.
        LOGGER.warn("AE2 NetworkStorage has no readable mountsInUse flag; storage loops through native parts are "
                + "not detected");
        return null;
    }
}
