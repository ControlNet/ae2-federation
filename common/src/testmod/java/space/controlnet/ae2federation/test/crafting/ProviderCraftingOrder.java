package space.controlnet.ae2federation.test.crafting;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.world.level.Level;

/**
 * One order on a Grid's own crafting service, as plain AE2 automation places it: planned as a terminal on that Grid
 * plans it, then submitted through a requester's {@code MultiCraftingTracker}, which owns the job's link.
 */
public final class ProviderCraftingOrder {
    private final Level level;
    private final IGrid grid;
    private final AEKey output;
    private final long amount;
    private final Future<ICraftingPlan> future;
    private ICraftingPlan plan;

    private ProviderCraftingOrder(Level level, IGrid grid, IGridNode node, AEKey output, long amount) {
        this.level = level;
        this.grid = grid;
        this.output = output;
        this.amount = amount;
        ICraftingSimulationRequester requester = new ICraftingSimulationRequester() {
            @Override
            public IActionSource getActionSource() {
                return IActionSource.empty();
            }

            @Override
            public IGridNode getGridNode() {
                return node;
            }
        };
        future = grid.getCraftingService().beginCraftingCalculation(level, requester, output, amount,
                CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    /** Plans {@code amount} of {@code output} on {@code grid}, for a requester whose node is {@code node}. */
    public static ProviderCraftingOrder begin(Level level, IGrid grid, IGridNode node, AEKey output, long amount) {
        return new ProviderCraftingOrder(Objects.requireNonNull(level), Objects.requireNonNull(grid),
                Objects.requireNonNull(node), Objects.requireNonNull(output), amount);
    }

    public Optional<ICraftingPlan> completedPlan() {
        if (plan != null) return Optional.of(plan);
        if (!future.isDone()) return Optional.empty();
        try {
            plan = future.get();
            return Optional.of(plan);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Crafting calculation interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Crafting calculation failed", exception);
        }
    }

    /** Hands the order to {@code requester}'s tracker; false while its slot is taken or its own plan is pending. */
    public boolean submitTracked(NativeCraftingRequester requester) {
        return completedPlan().isPresent() && requester.handleCrafting(output, amount, level, grid.getCraftingService());
    }
}
