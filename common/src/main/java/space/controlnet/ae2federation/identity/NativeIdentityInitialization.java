package space.controlnet.ae2federation.identity;

import appeng.api.networking.IGridNode;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Call-scoped provenance for nodes created while AE2 is assembling a multipart host. Never serialized. */
public final class NativeIdentityInitialization implements AutoCloseable {
    private static final ThreadLocal<NativeIdentityInitialization> CURRENT = new ThreadLocal<>();
    private final NativeIdentityInitialization parent;
    private final Set<IGridNode> nodes;

    private NativeIdentityInitialization() {
        parent = CURRENT.get();
        nodes = parent == null ? Collections.newSetFromMap(new IdentityHashMap<>()) : parent.nodes;
        CURRENT.set(this);
    }

    public static NativeIdentityInitialization begin() {
        return new NativeIdentityInitialization();
    }

    static boolean register(IGridNode node) {
        var scope = CURRENT.get();
        return scope != null && scope.nodes.add(node);
    }

    static boolean contains(IGridNode node) {
        var scope = CURRENT.get();
        return scope != null && scope.nodes.contains(node);
    }

    @Override
    public void close() {
        if (parent != null) {
            CURRENT.set(parent);
            return;
        }
        CURRENT.remove();
        for (var node : nodes) {
            if (node.getGrid().getService(NetworkIdentityService.class) instanceof NetworkIdentityGridService service) {
                service.finishInitialization(node);
            }
        }
    }
}
