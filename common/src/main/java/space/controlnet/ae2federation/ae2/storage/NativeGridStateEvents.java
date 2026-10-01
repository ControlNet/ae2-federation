package space.controlnet.ae2federation.ae2.storage;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.events.GridBootingStatusChange;
import appeng.api.networking.events.GridPowerStatusChange;
import space.controlnet.ae2federation.policy.AuthorityEpoch;

/**
 * Follows the native Grid state a storage source's activity depends on besides Grid membership and the mount table,
 * through AE2's public Grid events: power, and booting, which also covers channels (AE2 assigns them only while a
 * Grid boots, right before it posts the end of booting). Held authorizations therefore never re-read that state per
 * operation.
 */
public final class NativeGridStateEvents {
    private NativeGridStateEvents() {
    }

    /** Once, at mod start: AE2's Grid event subscriptions are global and permanent. */
    public static void register() {
        GridHelper.addEventHandler(GridPowerStatusChange.class, (grid, event) -> changed(grid));
        GridHelper.addEventHandler(GridBootingStatusChange.class, (grid, event) -> changed(grid));
    }

    private static void changed(IGrid grid) {
        // Held authorizations check fully from now on. Relationships reconcile at the Grid's storage tick end, once
        // the state is settled: AE2 posts both the start and the end of a reboot within one tick.
        AuthorityEpoch.advance();
        NativeMountLedger.requestReconcile(grid.getStorageService());
    }
}
