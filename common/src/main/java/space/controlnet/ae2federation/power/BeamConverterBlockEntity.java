package space.controlnet.ae2federation.power;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;
import space.controlnet.ae2federation.domain.port.FederationPort;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterBlockEntity;

/**
 * One block of a Beam Converter: a node of its domain that links through its front and to the rest of its body, and a
 * buffer of {@link #CAPACITY} FE, as an AE2 Controller block holds its own share of the grid's buffer. The domain's
 * converters pool their buffers ({@link PowerLedger}); FE enters through any face but the front, only while the body is
 * formed.
 */
public final class BeamConverterBlockEntity extends RouterBlockEntity {
    public static final double CAPACITY = 16_000;

    private final IEnergyStorage energy = new Input();
    private double stored;
    /** FE taken in since the operating power service last read it. */
    private double input;

    public BeamConverterBlockEntity(BlockPos position, BlockState state) {
        super(PowerRegistration.BEAM_CONVERTER_BLOCK_ENTITY.get(), position, state, false);
    }

    @Override
    protected boolean links(Direction face) {
        return RouterBlock.formed(getBlockState()) && faces(face);
    }

    /** The front, and faces toward the rest of the body. */
    private boolean faces(Direction face) {
        var state = getBlockState();
        return face == state.getValue(BeamConverterBlock.FACING)
                || (RouterBlock.connections(state) & 1 << face.get3DDataValue()) != 0;
    }

    @Override
    protected FederationDomainNodeKind kind() {
        return FederationDomainNodeKind.CONVERTER;
    }

    @Nullable
    @Override
    public FederationPort federationDomainPort(@Nullable Direction face) {
        return face != null && faces(face) ? super.federationDomainPort(face) : null;
    }

    /** The FE input on {@code side}: every face but the front. */
    @Nullable
    public IEnergyStorage energy(@Nullable Direction side) {
        return side == getBlockState().getValue(BeamConverterBlock.FACING) ? null : energy;
    }

    public boolean creative() {
        return getBlockState().getBlock() instanceof BeamConverterBlock block && block.creative();
    }

    public Direction facing() {
        return getBlockState().getValue(BeamConverterBlock.FACING);
    }

    /** The FE this block holds; a creative converter is always full. */
    public double stored() {
        return creative() ? CAPACITY : stored;
    }

    /** What remains after the domain drew its use; a creative converter keeps its own. */
    public void setStored(double value) {
        if (creative() || value == stored) return;
        stored = Math.max(0, Math.min(CAPACITY, value));
        setChanged();
    }

    /** The FE taken in since the last call, which the operating power service reports as the converter's input. */
    public double takeInput() {
        var taken = input;
        input = 0;
        return taken;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        stored = Math.max(0, Math.min(CAPACITY, tag.getDouble("stored")));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("stored", stored);
    }

    private final class Input implements IEnergyStorage {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            if (!canReceive() || toReceive <= 0) return 0;
            int accepted = (int) Math.min(toReceive, Math.floor(CAPACITY - stored));
            if (!simulate && accepted > 0) {
                stored += accepted;
                input += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) stored();
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) CAPACITY;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return !creative() && RouterBlock.formed(getBlockState());
        }
    }
}
