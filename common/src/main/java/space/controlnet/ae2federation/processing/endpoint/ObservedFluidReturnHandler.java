package space.controlnet.ae2federation.processing.endpoint;

import appeng.api.stacks.AEFluidKey;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import space.controlnet.ae2federation.processing.provider.ProviderObservationRegistry;

final class ObservedFluidReturnHandler implements IFluidHandler {
    private static final long DROPLETS_PER_MILLIBUCKET = 81_000L;
    private final ServerLevel level;
    private final EndpointReturnOwner owner;
    private final IFluidHandler delegate;

    ObservedFluidReturnHandler(ServerLevel level, EndpointReturnOwner owner, IFluidHandler delegate) {
        this.level = java.util.Objects.requireNonNull(level);
        this.owner = java.util.Objects.requireNonNull(owner);
        this.delegate = java.util.Objects.requireNonNull(delegate);
    }

    @Override
    public int getTanks() {
        return delegate.getTanks();
    }

    @Override
    public FluidStack getFluidInTank(int tank) {
        return delegate.getFluidInTank(tank);
    }

    @Override
    public int getTankCapacity(int tank) {
        return delegate.getTankCapacity(tank);
    }

    @Override
    public boolean isFluidValid(int tank, FluidStack stack) {
        return delegate.isFluidValid(tank, stack);
    }

    @Override
    public int fill(FluidStack resource, FluidAction action) {
        var accepted = delegate.fill(resource, action);
        if (action.execute() && accepted > 0) {
            record(resource, accepted);
        }
        return accepted;
    }

    @Override
    public FluidStack drain(FluidStack resource, FluidAction action) {
        var drained = delegate.drain(resource, action);
        if (action.execute() && !drained.isEmpty()) {
            record(drained, drained.getAmount());
        }
        return drained;
    }

    @Override
    public FluidStack drain(int maxDrain, FluidAction action) {
        var drained = delegate.drain(maxDrain, action);
        if (action.execute() && !drained.isEmpty()) {
            record(drained, drained.getAmount());
        }
        return drained;
    }

    private void record(FluidStack stack, int amount) {
        owner.lane().ifPresent(lane -> ProviderObservationRegistry.recordAggregateReturn(level, lane,
                AEFluidKey.of(stack.getFluid()), Math.multiplyExact(amount, DROPLETS_PER_MILLIBUCKET)));
    }
}
