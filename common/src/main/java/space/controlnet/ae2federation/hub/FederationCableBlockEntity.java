package space.controlnet.ae2federation.hub;

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
import space.controlnet.ae2federation.fabric.FabricInvalidationReason;
import space.controlnet.ae2federation.fabric.FabricNodeEvidence;
import space.controlnet.ae2federation.fabric.FabricNodeId;
import space.controlnet.ae2federation.fabric.FabricPortEvidence;
import space.controlnet.ae2federation.fabric.FabricPortId;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.fabric.port.CableFacePort;

public final class FederationCableBlockEntity extends BlockEntity {
    private final Map<Direction, CableFacePort> ports = new EnumMap<>(Direction.class);
    private boolean initialized;
    private boolean fabricDirty = true;
    private @Nullable FabricNodeId fabricNodeId;

    public FederationCableBlockEntity(BlockPos position, BlockState state) {
        super(HubRegistration.FEDERATION_CABLE_BLOCK_ENTITY.get(), position, state);
        for (var face : Direction.values()) {
            ports.put(face, new CableFacePort(position, face, this::invalidateFabricTopology));
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, FederationCableBlockEntity::initialize);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, FederationCableBlockEntity cable) {
        var changed = cable.ports.values().stream().map(CableFacePort::tick).reduce(false, Boolean::logicalOr);
        if (changed || cable.fabricDirty) {
            cable.publishFabricTopology();
        }
    }

    public void neighborChanged(BlockPos neighborPosition) {
        for (var face : Direction.values()) {
            if (worldPosition.relative(face).equals(neighborPosition)) {
                ports.get(face).invalidate();
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
        fabricNodeId = FabricRegistryAccess.nodeId(serverLevel, worldPosition);
        ports.values().forEach(port -> port.initialize(serverLevel));
    }

    private void invalidateFabricTopology() {
        fabricDirty = true;
        if (level instanceof ServerLevel serverLevel && fabricNodeId != null) {
            FabricRegistryAccess.get(serverLevel).invalidateNode(fabricNodeId,
                    FabricInvalidationReason.TOPOLOGY_CHANGED);
        }
    }

    private void publishFabricTopology() {
        if (!(level instanceof ServerLevel serverLevel) || fabricNodeId == null) {
            return;
        }
        var evidence = new java.util.TreeMap<String, FabricPortEvidence>();
        ports.forEach((face, port) -> {
            var peer = port.peer();
            if (peer != null) {
                var remoteNode = FabricRegistryAccess.nodeId(serverLevel, peer.ownerPosition());
                evidence.put(face.getSerializedName(), new FabricPortEvidence.Federation(
                        new FabricPortId(remoteNode, peer.outwardFace().getSerializedName())));
            }
        });
        FabricRegistryAccess.get(serverLevel).upsertNode(new FabricNodeEvidence(fabricNodeId, evidence));
        fabricDirty = false;
    }

    private void destroyPorts() {
        if (level instanceof ServerLevel serverLevel && fabricNodeId != null) {
            FabricRegistryAccess.get(serverLevel).removeNode(fabricNodeId);
        }
        initialized = false;
        fabricDirty = true;
        ports.values().forEach(CableFacePort::destroy);
    }
}
