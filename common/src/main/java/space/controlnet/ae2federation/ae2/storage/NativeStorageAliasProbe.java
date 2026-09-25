package space.controlnet.ae2federation.ae2.storage;

import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import appeng.me.storage.NullInventory;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/**
 * Alias evidence for mounted native storage handles, used only while a source domain is rebuilt.
 *
 * <p>Two kinds of evidence exist and they are deliberately treated differently:
 * <ul>
 * <li><b>Provable</b>: AE2's own {@code DelegatingMEInventory} is specified to forward every call to its delegate, so
 * its chain can be followed ({@link #chain}). A mounted handle whose chain reaches another mounted handle is an alias
 * of that handle and may be deduplicated.</li>
 * <li><b>Suspected</b>: a third-party (non-AE2) handle that directly holds a reference to another mounted handle (or
 * to an element of its AE2 delegate chain) may be an opaque wrapper over the same inventory. Federation cannot prove
 * whether it forwards, filters or composes, so it never deduplicates on this evidence; callers raise an explicit
 * diagnostic instead ({@link #opaqueReference}).</li>
 * </ul>
 * Limitation: the opaque probe inspects one level of instance fields of non-AE2 classes that the JVM lets us read;
 * wrappers that reach the shared inventory indirectly (suppliers, lookups, deeper object graphs) or live in packages
 * that are not open to reflection are not detected and are treated as independent inventories, exactly like AE2's
 * own {@code NetworkStorage} treats them.
 */
public final class NativeStorageAliasProbe {
    private static final int MAX_CHAIN = 32;
    private static final ClassValue<List<Field>> STORAGE_FIELDS = new ClassValue<>() {
        @Override
        protected List<Field> computeValue(Class<?> type) {
            var fields = new ArrayList<Field>();
            for (var current = type; current != null && current != Object.class; current = current.getSuperclass()) {
                if (current.getName().startsWith("java.")) {
                    break;
                }
                for (var field : current.getDeclaredFields()) {
                    if (Modifier.isStatic(field.getModifiers()) || field.getType().isPrimitive()) {
                        continue;
                    }
                    try {
                        if (field.trySetAccessible()) {
                            fields.add(field);
                        }
                    } catch (RuntimeException ignored) {
                        // Inaccessible module/package: documented limitation, treated as independent.
                    }
                }
            }
            return List.copyOf(fields);
        }
    };

    private NativeStorageAliasProbe() {
    }

    /** One observed AE2 delegate link, re-validated cheaply before a cached domain is reused. */
    public record DelegateLink(MEStorage wrapper, @Nullable MEStorage delegate) {
        public boolean current() {
            return NativeMountLedger.delegateOf(wrapper) == delegate;
        }
    }

    /**
     * Follows AE2's {@code DelegatingMEInventory} chain from {@code storage}: element 0 is {@code storage} itself.
     * Every followed link is appended to {@code links}. The walk stops at non-delegating handles, complete
     * {@link NetworkStorage} aggregates (another Grid's inventory is never unwrapped into), cycles or depth limits.
     */
    public static List<MEStorage> chain(MEStorage storage, List<DelegateLink> links) {
        var result = new ArrayList<MEStorage>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<MEStorage, Boolean>());
        var current = storage;
        while (current != null && seen.add(current) && result.size() < MAX_CHAIN) {
            if (current instanceof NullInventory && current != storage) {
                // AE2's shared empty singleton (e.g. an unconnected storage bus): holds nothing, proves nothing.
                break;
            }
            result.add(current);
            if (current instanceof NetworkStorage) {
                break;
            }
            var delegate = NativeMountLedger.delegateOf(current);
            if (delegate == null && !(current instanceof appeng.me.storage.DelegatingMEInventory)) {
                break;
            }
            links.add(new DelegateLink(current, delegate));
            current = delegate;
        }
        return List.copyOf(result);
    }

    /**
     * Returns a mounted handle (or delegate-chain element) outside {@code ownGroup} that the non-AE2 handle
     * {@code storage} directly references, or null when there is no such evidence.
     */
    public static @Nullable MEStorage opaqueReference(MEStorage storage, Set<MEStorage> ownGroup,
            Set<MEStorage> foreignElements) {
        if (storage.getClass().getName().startsWith("appeng.")) {
            return null;
        }
        for (var field : STORAGE_FIELDS.get(storage.getClass())) {
            Object value;
            try {
                value = field.get(storage);
            } catch (IllegalAccessException | RuntimeException exception) {
                continue;
            }
            if (value instanceof MEStorage referenced && referenced != storage && !ownGroup.contains(referenced)
                    && foreignElements.contains(referenced)) {
                return referenced;
            }
        }
        return null;
    }
}
