package space.controlnet.ae2federation.beam;

import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;
import space.controlnet.ae2federation.domain.FederationDomainPortEvidence;
import space.controlnet.ae2federation.domain.FederationDomainPortId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.FederationPort;
import space.controlnet.ae2federation.router.DeviceStructure;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterBlockEntity;

/**
 * One block of a Beam Former: a node of its domain that links through its back and to the rest of its body, carrying
 * the capacity of its body's square, as the bundle of that square at its back does. The body's lowest block reports
 * the body to its level's {@link BeamLinkService} and, while the beam is linked, names the partner's lowest block as a
 * remote link, which the partner names back.
 */
public final class BeamFormerBlockEntity extends RouterBlockEntity {

    /** The linked partner's lowest block, set on this body's lowest block only. */
    private @Nullable BlockPos beamPartner;
    /** The body or facing may have changed since this block last reported it. */
    private boolean bodyDirty = true;
    private boolean tracked;

    public BeamFormerBlockEntity(BlockPos position, BlockState state) {
        super(BeamRegistration.BEAM_FORMER_BLOCK_ENTITY.get(), position, state, false);
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        bodyDirty = true;
    }

    /** Reports this block's body to the beam service when it changed, if this is the body's lowest block. */
    void beamTick() {
        if (!bodyDirty || !(level instanceof ServerLevel serverLevel)) return;
        bodyDirty = false;
        var service = BeamLinkService.get(serverLevel);
        if (RouterBlock.formed(getBlockState())) {
            var min = corner(Direction.WEST, Direction.DOWN, Direction.NORTH);
            if (min.equals(worldPosition)) {
                service.track(new BeamPartnerIndex.Former(min, corner(Direction.EAST, Direction.UP, Direction.SOUTH),
                        facing()));
                tracked = true;
                return;
            }
        }
        if (tracked) {
            service.untrack(worldPosition);
            tracked = false;
        }
        setBeamPartner(null);
    }

    @Override
    public void setRemoved() {
        if (tracked && level instanceof ServerLevel serverLevel) BeamLinkService.get(serverLevel).untrack(worldPosition);
        tracked = false;
        beamPartner = null;
        bodyDirty = true;
        super.setRemoved();
    }

    /** The partner's lowest block while the beam is linked, or null; publishes the change at once. */
    void setBeamPartner(@Nullable BlockPos partner) {
        if (Objects.equals(partner, beamPartner)) return;
        beamPartner = partner == null ? null : partner.immutable();
        publishFederationDomainTopology();
    }

    @Override
    protected Map<String, FederationDomainPortEvidence> remotePorts() {
        if (beamPartner == null || !(level instanceof ServerLevel serverLevel)) return Map.of();
        var self = FederationDomainRegistryAccess.nodeId(serverLevel, worldPosition);
        var partner = FederationDomainRegistryAccess.nodeId(serverLevel, beamPartner);
        return Map.of(BeamLinkRule.PORT + partner, new FederationDomainPortEvidence.Federation(
                new FederationDomainPortId(partner, BeamLinkRule.PORT + self)));
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
    public int capacityFactor() {
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

    /** The lowest block of this block's formed body, by which the beam service knows the body. */
    public BlockPos bodyMin() {
        return corner(Direction.WEST, Direction.DOWN, Direction.NORTH);
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
