package space.controlnet.ae2federation.router;

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
import space.controlnet.ae2federation.domain.FederationBindingRefresh;
import space.controlnet.ae2federation.domain.port.FederationPort;
import space.controlnet.ae2federation.domain.port.RouterFacePort;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainPortEvidence;
import space.controlnet.ae2federation.domain.FederationDomainPortId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;

public final class RouterBlockEntity extends BlockEntity implements IInWorldGridNodeHost {
    private final Map<Direction, FederationPort> federationDomainPorts = new EnumMap<>(Direction.class);
    private final Map<Direction, RouterFacePort> facePorts = new EnumMap<>(Direction.class);
    private boolean initialized;
    private boolean federationDomainDirty = true;
    private @Nullable FederationDomainNodeId federationDomainNodeId;
    private @Nullable FederationDomainNodeEvidence publishedEvidence;

    public RouterBlockEntity(BlockPos position, BlockState state) {
        super(RouterBlock.blockEntityType(state), position, state);
        for (var face : Direction.values()) {
            var federationDomainPort = new FederationPort(position, face);
            federationDomainPorts.put(face, federationDomainPort);
            facePorts.put(face, new RouterFacePort(position, face, federationDomainPort));
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        GridHelper.onFirstTick(this, RouterBlockEntity::initialize);
    }

    private void initialize() {
        if (initialized || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        initialized = true;
        federationDomainNodeId = FederationDomainRegistryAccess.nodeId(serverLevel, worldPosition);
        facePorts.values().forEach(port -> port.initialize(serverLevel));
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, RouterBlockEntity router) {
        var changed = router.facePorts.values().stream().map(RouterFacePort::tick).reduce(false, Boolean::logicalOr);
        if (changed || router.federationDomainDirty) {
            router.publishFederationDomainTopology();
        } else if (level.getGameTime() % 20 == 0) {
            // A native network settles its identity without an event on this Router's faces.
            router.publishIfEvidenceChanged();
        }
    }

    public void neighborChanged(BlockPos neighborPosition) {
        for (var face : Direction.values()) {
            if (worldPosition.relative(face).equals(neighborPosition)) {
                if (facePorts.get(face).revalidate()) {
                    publishFederationDomainTopology();
                }
                return;
            }
        }
    }

    @Nullable
    public FederationPort federationDomainPort(@Nullable Direction face) {
        return face == null ? null : federationDomainPorts.get(face);
    }

    public RouterPortBinding binding(Direction face) {
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

    public Map<Direction, RouterPortBinding> bindings() {
        var snapshot = new EnumMap<Direction, RouterPortBinding>(Direction.class);
        facePorts.forEach((face, port) -> snapshot.put(face, port.binding()));
        return Collections.unmodifiableMap(snapshot);
    }

    public Map<IGrid, List<Direction>> nativeFacesByGrid() {
        var mutable = new IdentityHashMap<IGrid, ArrayList<Direction>>();
        facePorts.forEach((face, port) -> {
            if (port.binding() instanceof RouterPortBinding.Native nativeBinding) {
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
        facePorts.values().forEach(port -> port.loadFromNBT(tag, registries));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        facePorts.values().forEach(port -> port.saveToNBT(tag, registries));
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
        if (level instanceof ServerLevel serverLevel && federationDomainNodeId != null) {
            FederationDomainRegistryAccess.removeNodeIfPresent(serverLevel, federationDomainNodeId);
            FederationBindingRefresh.request(serverLevel);
        }
        initialized = false;
        federationDomainDirty = true;
        publishedEvidence = null;
        facePorts.values().forEach(RouterFacePort::destroy);
    }

    private void publishIfEvidenceChanged() {
        if (level instanceof ServerLevel serverLevel && federationDomainNodeId != null
                && !evidence(serverLevel).equals(publishedEvidence)) {
            publishFederationDomainTopology();
        }
    }

    private void publishFederationDomainTopology() {
        if (!(level instanceof ServerLevel serverLevel) || federationDomainNodeId == null) {
            return;
        }
        publishedEvidence = evidence(serverLevel);
        FederationDomainRegistryAccess.get(serverLevel).upsertNode(publishedEvidence);
        // The Grids are read when the level reconciles, so a merge later in the tick registers the surviving Grid.
        FederationBindingRefresh.request(serverLevel, () -> nativeFacesByGrid().keySet());
        federationDomainDirty = false;
    }

    private FederationDomainNodeEvidence evidence(ServerLevel serverLevel) {
        var evidence = new java.util.TreeMap<String, FederationDomainPortEvidence>();
        for (var face : Direction.values()) {
            var portId = new FederationDomainPortId(federationDomainNodeId, face.getSerializedName());
            var binding = facePorts.get(face).binding();
            if (binding instanceof RouterPortBinding.Native nativeBinding) {
                evidence.put(portId.port(), FederationDomainRegistryAccess.nativeEvidence(nativeBinding.attachment().grid(), portId));
            } else if (binding instanceof RouterPortBinding.Federation federationBinding) {
                var remoteNode = FederationDomainRegistryAccess.nodeId(serverLevel, federationBinding.port());
                evidence.put(portId.port(), new FederationDomainPortEvidence.Federation(
                        new FederationDomainPortId(remoteNode, federationBinding.port().outwardFace().getSerializedName())));
            }
        }
        return new FederationDomainNodeEvidence(federationDomainNodeId, evidence);
    }
}
