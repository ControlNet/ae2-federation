package space.controlnet.ae2federation.beam;

import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.client.CableBeamStyle;
import space.controlnet.ae2federation.domain.CableCapacity;
import space.controlnet.ae2federation.domain.CableCapacityCounter;
import space.controlnet.ae2federation.domain.CableCapacityLimit;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
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
    /** How often the lowest block looks again at its beam's count and power, as a cable does. */
    private static final int LOOK_TICKS = 10;
    private static final String LOOK_PARTNER = "beam_partner";
    private static final String LOOK_SIDE = "beam_side";
    private static final String LOOK_UNITS = "beam_units";
    private static final String LOOK_CAPACITY = "beam_capacity";
    private static final String LOOK_ENFORCED = "beam_enforced";
    private static final String LOOK_UNPOWERED = "beam_unpowered";

    /** The linked partner's lowest block, set on this body's lowest block only. */
    private @Nullable BlockPos beamPartner;
    /** What clients draw of the linked beam, on the body's lowest block; null while unlinked. */
    private @Nullable BeamLook look;
    private long nextLook;
    private double beamLoad = Double.NaN;
    private long beamNanos;
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
        if (!(level instanceof ServerLevel serverLevel)) return;
        syncLook(serverLevel);
        if (!bodyDirty) return;
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
        nextLook = 0;
        publishFederationDomainTopology();
    }

    /** Looks again at the beam as clients draw it, as often as a cable's count, and tells them of a change. */
    private void syncLook(ServerLevel serverLevel) {
        long now = serverLevel.getGameTime();
        if (now < nextLook) return;
        nextLook = now + LOOK_TICKS;
        BeamLook next = null;
        if (beamPartner != null) {
            int factor = capacityFactor();
            int side = space.controlnet.ae2federation.power.CableCrossSection.ofCapacityFactor(factor)
                    .map(section -> section.wide()).orElse(1);
            int capacity = CableCapacityLimit.capacity()
                    * (factor == FederationDomainNodeEvidence.UNLIMITED ? CableBeamStyle.UNLIMITED_SCALE : factor);
            var node = FederationDomainRegistryAccess.nodeId(serverLevel, worldPosition);
            int units = CableCapacity.demand(serverLevel, node).map(CableCapacityCounter.Demand::units).orElse(0);
            var registry = FederationDomainRegistryAccess.get(serverLevel);
            boolean unpowered = registry.federationDomainOf(node)
                    .filter(domain -> registry.unpowered(domain.federationDomainId())).isPresent();
            next = new BeamLook(beamPartner, side, units, capacity, CableCapacityLimit.current(factor).isPresent(),
                    unpowered);
        }
        if (Objects.equals(next, look)) return;
        look = next;
        serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    /** What clients draw of the linked beam, as the server last sent it; null while unlinked or on other blocks. */
    public @Nullable BeamLook look() {
        return look;
    }

    /** Client only: eases the drawn load towards {@code target}, frame-rate independent, as a cable does. */
    public double beamLoad(double target, long nanos) {
        beamLoad = Double.isNaN(beamLoad) ? target
                : CableBeamStyle.smooth(beamLoad, target, Math.max(0, nanos - beamNanos) / 1e9);
        beamNanos = nanos;
        return beamLoad;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        if (look == null) return tag;
        tag.putLong(LOOK_PARTNER, look.partner().asLong());
        tag.putInt(LOOK_SIDE, look.side());
        tag.putInt(LOOK_UNITS, look.units());
        tag.putInt(LOOK_CAPACITY, look.capacity());
        tag.putBoolean(LOOK_ENFORCED, look.enforced());
        tag.putBoolean(LOOK_UNPOWERED, look.unpowered());
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /** The update tag holds the beam look only, not the saved grid nodes the default would load from it. */
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        readLook(tag);
    }

    @Override
    public void onDataPacket(Connection connection, ClientboundBlockEntityDataPacket packet,
            HolderLookup.Provider registries) {
        readLook(packet.getTag());
    }

    private void readLook(CompoundTag tag) {
        look = !tag.contains(LOOK_PARTNER) ? null : new BeamLook(BlockPos.of(tag.getLong(LOOK_PARTNER)),
                tag.getInt(LOOK_SIDE), tag.getInt(LOOK_UNITS), tag.getInt(LOOK_CAPACITY), tag.getBoolean(LOOK_ENFORCED),
                tag.getBoolean(LOOK_UNPOWERED));
    }

    /**
     * A linked beam as clients draw it: the partner's lowest block, the square's side, the capacity count against its
     * limit, and whether the domain is unpowered.
     */
    public record BeamLook(BlockPos partner, int side, int units, int capacity, boolean enforced, boolean unpowered) {
        public double load() {
            return CableBeamStyle.loadOf(units, capacity, enforced);
        }
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
