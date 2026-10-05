package space.controlnet.ae2federation.processing.provider;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * The FE a Federation Pattern Provider sends out of its Federation face, passed on to the machines touching the
 * Endpoints it holds. A Provider sends FE into the block in front of it as AE2's own Pattern Provider sends it into its
 * neighbours, for instance with Applied Flux's Induction Card; that block is a Federation Cable, a Router or an
 * Endpoint, and asked from the Provider's side it answers with this relay. The relay reaches only Endpoints the
 * Provider's Lanes may push to now, so a cut link or a released claim stops the FE as it stops the patterns.
 */
public final class ProviderEnergyRelay implements IEnergyStorage {
    private final FederationPatternProviderBlockEntity provider;
    private final Direction front;
    private int next;

    private ProviderEnergyRelay(FederationPatternProviderBlockEntity provider, Direction front) {
        this.provider = provider;
        this.front = front;
    }

    /**
     * The relay of the block at {@code position}, asked from {@code side}: present only when the block on that side is a
     * Provider whose Federation face points at {@code position}.
     */
    public static @Nullable IEnergyStorage facing(Level level, BlockPos position, @Nullable Direction side) {
        if (side == null || !(level instanceof ServerLevel)) {
            return null;
        }
        var neighbour = position.relative(side);
        return level.isLoaded(neighbour)
                && level.getBlockEntity(neighbour) instanceof FederationPatternProviderBlockEntity provider
                && provider.federationFace() == side.getOpposite()
                ? new ProviderEnergyRelay(provider, side.getOpposite())
                : null;
    }

    private List<IEnergyStorage> targets() {
        return provider.isRemoved() || provider.federationFace() != front ? List.of() : provider.endpointEnergyTargets();
    }

    /** Fills the machines in turn, starting one further along after each transfer, so none is always last. */
    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        var targets = targets();
        if (targets.isEmpty() || maxReceive <= 0) {
            return 0;
        }
        int start = Math.floorMod(next, targets.size());
        int received = 0;
        for (int offset = 0; offset < targets.size() && received < maxReceive; offset++) {
            var target = targets.get((start + offset) % targets.size());
            if (target.canReceive()) {
                received += target.receiveEnergy(maxReceive - received, simulate);
            }
        }
        if (!simulate && received > 0) {
            next = start + 1;
        }
        return received;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        long stored = 0;
        for (var target : targets()) stored += target.getEnergyStored();
        return (int) Math.min(Integer.MAX_VALUE, stored);
    }

    @Override
    public int getMaxEnergyStored() {
        long capacity = 0;
        for (var target : targets()) capacity += target.getMaxEnergyStored();
        return (int) Math.min(Integer.MAX_VALUE, capacity);
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return targets().stream().anyMatch(IEnergyStorage::canReceive);
    }
}
