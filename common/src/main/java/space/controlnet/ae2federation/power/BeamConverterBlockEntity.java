package space.controlnet.ae2federation.power;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;
import space.controlnet.ae2federation.domain.port.FederationPort;
import space.controlnet.ae2federation.router.DeviceStructure;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterBlockEntity;

/**
 * One block of a Beam Converter: a node of its domain that links through its front and to the rest of its body, and an
 * equal share of its body's buffer, {@link #CAPACITY} FE per block. A formed body is one buffer: FE entering any face
 * but the front fills the whole body, and every block reads the body's total. The domain's converters pool their
 * buffers ({@link PowerLedger}); a block broken out of its body takes its share with it.
 */
public final class BeamConverterBlockEntity extends RouterBlockEntity {
    public static final double CAPACITY = 16_000;

    private final IEnergyStorage energy = new Input();
    private double stored;
    /** FE taken in since the operating power service last read it. */
    private double input;
    /** The formed body's blocks, this one included, as found on {@link #bodyTick}; null until looked up. */
    private @Nullable List<BeamConverterBlockEntity> body;
    private long bodyTick = Long.MIN_VALUE;

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

    /**
     * The blocks of this block's formed body, this one included, looked up at most once a tick; this block alone while it
     * is not formed. A formed member's connections name the faces toward the rest of its cuboid, so walking them to
     * the lowest and highest corner finds the body.
     */
    public List<BeamConverterBlockEntity> body() {
        if (level == null || !RouterBlock.formed(getBlockState())) return List.of(this);
        long tick = level.getGameTime();
        if (body != null && bodyTick == tick && body.stream().noneMatch(BeamConverterBlockEntity::isRemoved)) return body;
        var min = corner(Direction.WEST, Direction.DOWN, Direction.NORTH);
        var max = corner(Direction.EAST, Direction.UP, Direction.SOUTH);
        var members = new ArrayList<BeamConverterBlockEntity>();
        for (var position : BlockPos.betweenClosed(min, max)) {
            if (level.isLoaded(position) && level.getBlockEntity(position) instanceof BeamConverterBlockEntity member
                    && RouterBlock.formed(member.getBlockState())) {
                members.add(member);
            }
        }
        if (!members.contains(this)) members.add(this);
        body = List.copyOf(members);
        bodyTick = tick;
        return body;
    }

    private BlockPos corner(Direction... sides) {
        var position = getBlockPos();
        for (var side : sides) {
            for (int step = 0; step < DeviceStructure.MAX_EDGE; step++) {
                var state = level.getBlockState(position);
                if (!(state.getBlock() instanceof BeamConverterBlock) || !RouterBlock.formed(state)
                        || (RouterBlock.connections(state) & 1 << side.get3DDataValue()) == 0) {
                    break;
                }
                position = position.relative(side);
            }
        }
        return position;
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
            var members = body();
            double room = 0;
            for (var member : members) room += CAPACITY - member.stored;
            int accepted = (int) Math.min(toReceive, Math.floor(room));
            if (!simulate && accepted > 0) {
                // Each block takes its part of the body's room, so the shares stay even.
                for (var member : members) {
                    var part = accepted * (CAPACITY - member.stored) / room;
                    if (part <= 0) continue;
                    member.stored = Math.min(CAPACITY, member.stored + part);
                    member.setChanged();
                }
                input += accepted;
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return (int) body().stream().mapToDouble(BeamConverterBlockEntity::stored).sum();
        }

        @Override
        public int getMaxEnergyStored() {
            return (int) (body().size() * CAPACITY);
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
