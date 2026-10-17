package space.controlnet.ae2federation.quantum;

import java.util.Map;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;
import space.controlnet.ae2federation.domain.FederationDomainPortEvidence;
import space.controlnet.ae2federation.domain.FederationDomainPortId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.FederationPort;
import space.controlnet.ae2federation.router.DeviceStructure;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterBlockEntity;

/**
 * One block of a Quantum Cube: a node of its domain that links to the rest of its body and, on an outer face, inside
 * that face's centre only, carrying the capacity of the square its cube takes. Every block keeps the body's pair id, so
 * the id survives a block broken and put back; the body's lowest block reports the body to the
 * {@link QuantumLinkService} and, while linked, names the partner's lowest block as a remote link, which the partner
 * names back.
 */
public final class QuantumCubeBlockEntity extends RouterBlockEntity {
    private static final String PAIR = "quantum_pair";
    private static final String BOUND_TO = "quantum_bound_to";

    /** The body's pair id; 0 when not bound. */
    private long pairId;
    /** Where the partner's lowest block was when the pair was bound, for Jade while the partner is not loaded. */
    private @Nullable GlobalPos boundTo;
    /** The linked partner, set on the body's lowest block only. */
    private @Nullable QuantumPairIndex.Cube partner;
    /** The body or pair may have changed since this block last reported it. */
    private boolean bodyDirty = true;
    private boolean tracked;

    public QuantumCubeBlockEntity(BlockPos position, BlockState state) {
        super(QuantumRegistration.QUANTUM_CUBE_BLOCK_ENTITY.get(), position, state, false);
    }

    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        bodyDirty = true;
    }

    /** Reports this block's body to the link service when it changed, if this is the body's lowest block. */
    void quantumTick() {
        if (!(level instanceof ServerLevel serverLevel) || !bodyDirty) return;
        bodyDirty = false;
        var service = QuantumLinkService.get(serverLevel.getServer());
        var dimension = FederationDomainRegistryAccess.dimension(serverLevel);
        if (RouterBlock.formed(getBlockState()) && bodyMin().equals(worldPosition)) {
            var max = bodyMax();
            var section = QuantumCubeShape.of(worldPosition, max);
            if (section.isPresent()) {
                service.track(new QuantumPairIndex.Cube(dimension, worldPosition, section.get(),
                        settle(serverLevel, worldPosition, max)));
                tracked = true;
                return;
            }
        }
        if (tracked) {
            service.untrack(dimension, worldPosition);
            tracked = false;
        }
        setQuantumPartner(null);
    }

    /**
     * The body's pair id: the one id its bound blocks hold, which the blocks without one (put back after a break) then
     * take; 0 when none holds one or they disagree, as when two bound bodies were built into one.
     */
    private static long settle(ServerLevel level, BlockPos min, BlockPos max) {
        long id = 0;
        GlobalPos to = null;
        for (var position : BlockPos.betweenClosed(min, max)) {
            if (!(level.getBlockEntity(position) instanceof QuantumCubeBlockEntity member) || member.pairId == 0) {
                continue;
            }
            if (id != 0 && member.pairId != id) return 0;
            id = member.pairId;
            if (to == null) to = member.boundTo;
        }
        if (id != 0) bind(level, min, max, id, to);
        return id;
    }

    /**
     * Gives every block of the body from {@code min} to {@code max} a pair id; when any block changed, its lowest block
     * reports the body again.
     */
    static void bind(Level level, BlockPos min, BlockPos max, long id, @Nullable GlobalPos to) {
        boolean changed = false;
        for (var position : BlockPos.betweenClosed(min, max)) {
            if (!(level.getBlockEntity(position) instanceof QuantumCubeBlockEntity member)
                    || member.pairId == id && Objects.equals(member.boundTo, to)) {
                continue;
            }
            member.pairId = id;
            member.boundTo = to;
            member.setChanged();
            changed = true;
        }
        if (changed && level.getBlockEntity(min) instanceof QuantumCubeBlockEntity anchor) anchor.bodyDirty = true;
    }

    @Override
    public void setRemoved() {
        if (tracked && level instanceof ServerLevel serverLevel) {
            var service = QuantumLinkService.get(serverLevel.getServer());
            service.untrack(FederationDomainRegistryAccess.dimension(serverLevel), worldPosition);
        }
        tracked = false;
        partner = null;
        bodyDirty = true;
        super.setRemoved();
    }

    /** The linked partner, or null; publishes the change at once. */
    void setQuantumPartner(@Nullable QuantumPairIndex.Cube next) {
        if (Objects.equals(next, partner)) return;
        partner = next;
        publishFederationDomainTopology();
    }

    public long pairId() {
        return pairId;
    }

    public @Nullable GlobalPos boundTo() {
        return boundTo;
    }

    @Override
    protected Map<String, FederationDomainPortEvidence> remotePorts() {
        if (partner == null || !(level instanceof ServerLevel serverLevel)) return Map.of();
        var self = FederationDomainRegistryAccess.nodeId(serverLevel, worldPosition);
        var other = new FederationDomainNodeId(partner.dimension(), partner.min().asLong());
        return Map.of(QuantumLinkService.PORT + other, new FederationDomainPortEvidence.Federation(
                new FederationDomainPortId(other, QuantumLinkService.PORT + self)));
    }

    @Override
    protected boolean links(Direction face) {
        return QuantumCubeBlock.links(getBlockState(), face);
    }

    @Nullable
    @Override
    public FederationPort federationDomainPort(@Nullable Direction face) {
        return face != null && links(face) ? super.federationDomainPort(face) : null;
    }

    @Override
    protected FederationDomainNodeKind kind() {
        return FederationDomainNodeKind.QUANTUM_CUBE;
    }

    /** The capacity of the square the body takes, found from this block's connections; one cable's while not formed. */
    @Override
    public int capacityFactor() {
        if (level == null || !RouterBlock.formed(getBlockState())) return 1;
        return QuantumCubeShape.of(bodyMin(), bodyMax()).map(section -> section.capacityFactor()).orElse(1);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        pairId = tag.getLong(PAIR);
        boundTo = tag.contains(BOUND_TO) ? GlobalPos.CODEC.parse(NbtOps.INSTANCE, tag.get(BOUND_TO)).result()
                .orElse(null) : null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (pairId != 0) tag.putLong(PAIR, pairId);
        if (boundTo != null) {
            GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, boundTo).result().ifPresent(value -> tag.put(BOUND_TO, value));
        }
    }

    /** The lowest block of this block's formed body, by which the link service knows the body. */
    public BlockPos bodyMin() {
        return corner(Direction.WEST, Direction.DOWN, Direction.NORTH);
    }

    public BlockPos bodyMax() {
        return corner(Direction.EAST, Direction.UP, Direction.SOUTH);
    }

    /**
     * The body's corner toward {@code sides}: a formed member's connections name the faces toward the rest of its
     * cuboid, so walking them reaches the corner.
     */
    private BlockPos corner(Direction... sides) {
        var position = getBlockPos();
        for (var side : sides) {
            for (int step = 0; step < DeviceStructure.MAX_EDGE; step++) {
                var state = level.getBlockState(position);
                if (!(state.getBlock() instanceof QuantumCubeBlock) || !RouterBlock.formed(state)
                        || (RouterBlock.connections(state) & 1 << side.get3DDataValue()) == 0) {
                    break;
                }
                position = position.relative(side);
            }
        }
        return position;
    }
}
