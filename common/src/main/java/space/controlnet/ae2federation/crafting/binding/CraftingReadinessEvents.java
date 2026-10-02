package space.controlnet.ae2federation.crafting.binding;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.events.GridBootingStatusChange;
import appeng.api.networking.events.GridCraftingCpuChange;
import appeng.api.networking.events.GridPowerStatusChange;

/**
 * Follows what a provider network's Crafting readiness depends on, through AE2's public Grid events: its crafting CPUs,
 * and the activity of its pattern providers, which changes only with power or a reboot (a node joining or leaving
 * reboots the Grid). A rule switched on before the provider has a CPU or a provider, or a world whose domains load before
 * the provider's CPUs form, therefore gets its binding without another rule or topology change.
 */
public final class CraftingReadinessEvents {
    private CraftingReadinessEvents() {
    }

    /** Once, at mod start: AE2's Grid event subscriptions are global and permanent. */
    public static void register() {
        GridHelper.addEventHandler(GridCraftingCpuChange.class, (grid, event) -> changed(grid));
        GridHelper.addEventHandler(GridPowerStatusChange.class, (grid, event) -> changed(grid));
        GridHelper.addEventHandler(GridBootingStatusChange.class, (grid, event) -> changed(grid));
    }

    private static void changed(IGrid grid) {
        // AE2 posts these while it rebuilds clusters and reboots Grids; missing bindings are published when the level
        // tick ends.
        var pivot = grid.isEmpty() ? null : grid.getPivot();
        if (pivot != null) {
            CraftingBindingService.readinessChanged(pivot.getLevel(), grid);
        }
    }
}
