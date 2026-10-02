package space.controlnet.ae2federation.test.perf;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** TEST-ONLY: the server tick in which each menu last sent its changes. */
public final class MenuBroadcasts {
    private static final Map<AbstractContainerMenu, Integer> LAST = new WeakHashMap<>();

    private MenuBroadcasts() {
    }

    public static synchronized void record(AbstractContainerMenu menu) {
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null && server.isSameThread()) {
            LAST.put(menu, server.getTickCount());
        }
    }

    /** Sends {@code menu}'s changes unless its player already did in the current server tick. */
    public static void ensureBroadcast(AbstractContainerMenu menu, int tick) {
        Integer last;
        synchronized (MenuBroadcasts.class) {
            last = LAST.get(menu);
        }
        if (last == null || last != tick) {
            menu.broadcastChanges();
        }
    }
}
