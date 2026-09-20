package space.controlnet.ae2federation.hub;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IInWorldGridNodeHost;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.fabric.port.FederationPort;
import space.controlnet.ae2federation.fabric.port.HubFacePort;
import space.controlnet.ae2federation.fabric.port.HubPortBinding;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.fabric.FabricInvalidationReason;
import space.controlnet.ae2federation.fabric.FabricNodeEvidence;
import space.controlnet.ae2federation.fabric.FabricNodeId;
import space.controlnet.ae2federation.fabric.FabricPortEvidence;
import space.controlnet.ae2federation.fabric.FabricPortId;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;

public final class HubBlockEntity extends BlockEntity implements IInWorldGridNodeHost {
    private final Map<Direction, FederationPort> fabricPorts = new EnumMap<>(Direction.class);
    private final Map<Direction, HubFacePort> facePorts = new EnumMap<>(Direction.class);
    private boolean initialized;
    private boolean fabricDirty = true;
    private @Nullable FabricNodeId fabricNodeId;

    public HubBlockEntity(BlockPos position, BlockState state) {
        super(HubRegistration.HUB_BLOCK_ENTITY.get(), position, state);
        for (var face : Direction.values()) {
            var fabricPort = new FederationPort(position, face);
            fabricPorts.put(face, fabricPort);
            facePorts.put(face, new HubFacePort(position, face, fabricPort, this::invalidateFabricTopology));
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, HubBlockEntity::initialize);
    }

    private void initialize() {
        if (initialized || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        initialized = true;
        fabricNodeId = FabricRegistryAccess.nodeId(serverLevel, worldPosition);
        facePorts.values().forEach(port -> port.initialize(serverLevel));
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, HubBlockEntity hub) {
        var changed = hub.facePorts.values().stream().map(HubFacePort::tick).reduce(false, Boolean::logicalOr);
        if (changed || hub.fabricDirty) {
            hub.publishFabricTopology();
        }
    }

    public void neighborChanged(BlockPos neighborPosition) {
        for (var face : Direction.values()) {
            if (worldPosition.relative(face).equals(neighborPosition)) {
                facePorts.get(face).invalidate();
                return;
            }
        }
    }

    @Nullable
    public FederationPort fabricPort(@Nullable Direction face) {
        return face == null ? null : fabricPorts.get(face);
    }

    public HubPortBinding binding(Direction face) {
        return facePorts.get(face).binding();
    }

    @Nullable
    public IGridNode boundaryNode(Direction face) {
        return facePorts.get(face).node();
    }

    @Nullable
    @Override
    public IGridNode getGridNode(Direction face) {
        return boundaryNode(face);
    }

    public Map<Direction, HubPortBinding> bindings() {
        var snapshot = new EnumMap<Direction, HubPortBinding>(Direction.class);
        facePorts.forEach((face, port) -> snapshot.put(face, port.binding()));
        return Collections.unmodifiableMap(snapshot);
    }

    public Map<IGrid, List<Direction>> nativeFacesByGrid() {
        var mutable = new IdentityHashMap<IGrid, ArrayList<Direction>>();
        facePorts.forEach((face, port) -> {
            if (port.binding() instanceof HubPortBinding.Native nativeBinding) {
                mutable.computeIfAbsent(nativeBinding.attachment().grid(), ignored -> new ArrayList<>()).add(face);
            }
        });
        var snapshot = new IdentityHashMap<IGrid, List<Direction>>();
        mutable.forEach((grid, faces) -> snapshot.put(grid, List.copyOf(faces)));
        return Collections.unmodifiableMap(snapshot);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        facePorts.values().forEach(port -> port.loadFromNBT(tag));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        facePorts.values().forEach(port -> port.managedNode().saveToNBT(tag));
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

    private void destroyPorts() {
        if (level instanceof ServerLevel serverLevel && fabricNodeId != null) {
            FabricRegistryAccess.removeNodeIfPresent(serverLevel, fabricNodeId);
            StorageMountService.reconcileIfPresent(serverLevel);
            CraftingBindingService.reconcileIfPresent(serverLevel);
        }
        initialized = false;
        fabricDirty = true;
        facePorts.values().forEach(HubFacePort::destroy);
    }

    private void invalidateFabricTopology() {
        fabricDirty = true;
        if (level instanceof ServerLevel serverLevel && fabricNodeId != null) {
            FabricRegistryAccess.invalidateNodeIfPresent(serverLevel, fabricNodeId,
                    FabricInvalidationReason.TOPOLOGY_CHANGED);
            StorageMountService.reconcileIfPresent(serverLevel);
            CraftingBindingService.reconcileIfPresent(serverLevel);
        }
    }

    private void publishFabricTopology() {
        if (!(level instanceof ServerLevel serverLevel) || fabricNodeId == null) {
            return;
        }
        var evidence = new java.util.TreeMap<String, FabricPortEvidence>();
        for (var face : Direction.values()) {
            var portId = new FabricPortId(fabricNodeId, face.getSerializedName());
            var binding = facePorts.get(face).binding();
            if (binding instanceof HubPortBinding.Native nativeBinding) {
                evidence.put(portId.port(), FabricRegistryAccess.nativeEvidence(nativeBinding.attachment().grid(), portId));
            } else if (binding instanceof HubPortBinding.Federation federationBinding) {
                var remoteNode = FabricRegistryAccess.nodeId(serverLevel, federationBinding.port().ownerPosition());
                evidence.put(portId.port(), new FabricPortEvidence.Federation(
                        new FabricPortId(remoteNode, federationBinding.port().outwardFace().getSerializedName())));
            }
        }
        FabricRegistryAccess.get(serverLevel).upsertNode(new FabricNodeEvidence(fabricNodeId, evidence));
        StorageMountService.get(serverLevel).observeFabricMembers(nativeFacesByGrid().keySet());
        CraftingBindingService.get(serverLevel).observeFabricMembers(nativeFacesByGrid().keySet());
        fabricDirty = false;
    }
}
