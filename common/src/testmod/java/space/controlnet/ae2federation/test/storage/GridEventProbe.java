package space.controlnet.ae2federation.test.storage;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.events.GridBootingStatusChange;
import appeng.api.networking.events.GridPowerStatusChange;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * TEST-ONLY: runs a test's action inside AE2's own dispatch of one Grid's power or booting event, the moment AE2 has
 * changed that state and before the rest of the tick runs, as a node listener or another mod reacting to the event
 * would. AE2 calls handlers in subscription order, so Federation's handlers, subscribed at mod start, have already
 * run. AE2 has no unsubscribe: the handlers are subscribed once and do nothing while disarmed.
 */
public final class GridEventProbe {
    private static boolean subscribed;
    private static @Nullable IGrid grid;
    private static @Nullable Runnable onPower;
    private static @Nullable Consumer<Boolean> onBooting;

    private GridEventProbe() {
    }

    /** Runs {@code action} on each power status change of {@code target} until {@link #disarm()}. */
    public static void onPower(IGrid target, Runnable action) {
        subscribe();
        grid = target;
        onPower = action;
        onBooting = null;
    }

    /** Runs {@code action(booting)} on each booting status change of {@code target} until {@link #disarm()}. */
    public static void onBooting(IGrid target, Consumer<Boolean> action) {
        subscribe();
        grid = target;
        onPower = null;
        onBooting = action;
    }

    public static void disarm() {
        grid = null;
        onPower = null;
        onBooting = null;
    }

    private static void subscribe() {
        if (subscribed) {
            return;
        }
        subscribed = true;
        GridHelper.addEventHandler(GridPowerStatusChange.class, (source, event) -> {
            var action = onPower;
            if (action != null && source == grid) {
                action.run();
            }
        });
        GridHelper.addEventHandler(GridBootingStatusChange.class, (source, event) -> {
            var action = onBooting;
            if (action != null && source == grid) {
                action.accept(event.isBooting());
            }
        });
    }
}
