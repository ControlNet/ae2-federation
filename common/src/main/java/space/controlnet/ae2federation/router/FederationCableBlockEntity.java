package space.controlnet.ae2federation.router;

import appeng.api.networking.GridHelper;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationBindingRefresh;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainPortEvidence;
import space.controlnet.ae2federation.domain.FederationDomainPortId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.CableFacePort;

public final class FederationCableBlockEntity extends BlockEntity {
    private final Map<Direction, CableFacePort> ports = new EnumMap<>(Direction.class);
    private boolean initialized;
    private boolean federationDomainDirty = true;
    private @Nullable FederationDomainNodeId federationDomainNodeId;
    /** Client only: the flow renderer's neighbour mask and the game tick it was read in. */
    private int flowMask;
    private long flowMaskTick = Long.MIN_VALUE;

    public FederationCableBlockEntity(BlockPos position, BlockState state) {
        super(RouterRegistration.FEDERATION_CABLE_BLOCK_ENTITY.get(), position, state);
        for (var face : Direction.values()) {
            ports.put(face, new CableFacePort(position, face));
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, FederationCableBlockEntity::initialize);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, FederationCableBlockEntity cable) {
        var changed = cable.ports.values().stream().map(CableFacePort::tick).reduce(false, Boolean::logicalOr);
        if (changed || cable.federationDomainDirty) {
            cable.publishFederationDomainTopology();
        }
    }

    public void neighborChanged(BlockPos neighborPosition) {
        for (var face : Direction.values()) {
            if (worldPosition.relative(face).equals(neighborPosition)) {
                if (ports.get(face).revalidate()) {
                    publishFederationDomainTopology();
                }
                return;
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        destroyPorts();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        destroyPorts();
        super.setRemoved();
    }

    private void initialize() {
        if (initialized || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        initialized = true;
        federationDomainNodeId = FederationDomainRegistryAccess.nodeId(serverLevel, worldPosition);
        ports.values().forEach(port -> port.initialize(serverLevel));
    }

    private void publishFederationDomainTopology() {
        if (!(level instanceof ServerLevel serverLevel) || federationDomainNodeId == null) {
            return;
        }
        var evidence = new java.util.TreeMap<String, FederationDomainPortEvidence>();
        ports.forEach((face, port) -> {
            var peer = port.peer();
            if (peer != null) {
                var remoteNode = FederationDomainRegistryAccess.nodeId(serverLevel, peer);
                evidence.put(face.getSerializedName(), new FederationDomainPortEvidence.Federation(
                        new FederationDomainPortId(remoteNode, peer.outwardFace().getSerializedName())));
            }
        });
        if (FederationDomainRegistryAccess.get(serverLevel).upsertNode(
                new FederationDomainNodeEvidence(federationDomainNodeId, evidence))) {
            FederationBindingRefresh.request(serverLevel);
        }
        federationDomainDirty = false;
    }

    private void destroyPorts() {
        if (level instanceof ServerLevel serverLevel && federationDomainNodeId != null) {
            FederationDomainRegistryAccess.removeNodeIfPresent(serverLevel, federationDomainNodeId);
            FederationBindingRefresh.request(serverLevel);
        }
        initialized = false;
        federationDomainDirty = true;
        ports.values().forEach(CableFacePort::destroy);
    }

    /**
     * The neighbour mask the flow renderer draws, read from the level at most once per game tick: the renderer asks
     * every frame, and six block lookups per cable per frame add up across a base.
     */
    public int flowMask() {
        var gameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (level != null && flowMaskTick != gameTime) {
            flowMask = CableVisualConnections.mask(level, worldPosition);
            flowMaskTick = gameTime;
        }
        return flowMask;
    }
}
