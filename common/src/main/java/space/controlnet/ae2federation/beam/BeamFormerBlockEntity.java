package space.controlnet.ae2federation.beam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;
import space.controlnet.ae2federation.domain.port.FederationPort;
import space.controlnet.ae2federation.router.DeviceStructure;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterBlockEntity;

/**
 * One block of a Beam Former: a node of its domain that links through its back and to the rest of its body, carrying
 * the capacity of its body's square, as the bundle of that square at its back does.
 */
public final class BeamFormerBlockEntity extends RouterBlockEntity {
    public BeamFormerBlockEntity(BlockPos position, BlockState state) {
        super(BeamRegistration.BEAM_FORMER_BLOCK_ENTITY.get(), position, state, false);
    }

    @Override
    protected boolean links(Direction face) {
        return RouterBlock.formed(getBlockState()) && faces(face);
    }

    /** The back, and faces toward the rest of the body. */
    private boolean faces(Direction face) {
        var state = getBlockState();
        return face == state.getValue(BeamFormerBlock.FACING).getOpposite()
                || (RouterBlock.connections(state) & 1 << face.get3DDataValue()) != 0;
    }

    @Override
    protected FederationDomainNodeKind kind() {
        return FederationDomainNodeKind.BEAM_FORMER;
    }

    /** The capacity of the body's square, found from this block's connections; one cable's while not formed. */
    @Override
    protected int capacityFactor() {
        if (level == null || !RouterBlock.formed(getBlockState())) return 1;
        return BeamFormerShape.of(corner(Direction.WEST, Direction.DOWN, Direction.NORTH),
                corner(Direction.EAST, Direction.UP, Direction.SOUTH)).map(section -> section.capacityFactor()).orElse(1);
    }

    @Nullable
    @Override
    public FederationPort federationDomainPort(@Nullable Direction face) {
        return face != null && faces(face) ? super.federationDomainPort(face) : null;
    }

    public Direction facing() {
        return getBlockState().getValue(BeamFormerBlock.FACING);
    }

    /**
     * The body's corner toward {@code sides}: a formed member's connections name the faces toward the rest of its
     * cuboid, so walking them reaches the corner.
     */
    BlockPos corner(Direction... sides) {
        var position = getBlockPos();
        for (var side : sides) {
            for (int step = 0; step < DeviceStructure.MAX_EDGE; step++) {
                var state = level.getBlockState(position);
                if (!(state.getBlock() instanceof BeamFormerBlock) || !RouterBlock.formed(state)
                        || (RouterBlock.connections(state) & 1 << side.get3DDataValue()) == 0) {
                    break;
                }
                position = position.relative(side);
            }
        }
        return position;
    }
}
