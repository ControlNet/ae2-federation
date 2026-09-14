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

public final class HubBlockEntity extends BlockEntity implements IInWorldGridNodeHost {
    private final Map<Direction, FederationPort> fabricPorts = new EnumMap<>(Direction.class);
    private final Map<Direction, HubFacePort> facePorts = new EnumMap<>(Direction.class);
    private boolean initialized;

    public HubBlockEntity(BlockPos position, BlockState state) {
        super(HubRegistration.HUB_BLOCK_ENTITY.get(), position, state);
        for (var face : Direction.values()) {
            var fabricPort = new FederationPort(position, face);
            fabricPorts.put(face, fabricPort);
            facePorts.put(face, new HubFacePort(position, face, fabricPort));
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
        facePorts.values().forEach(port -> port.initialize(serverLevel));
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, HubBlockEntity hub) {
        hub.facePorts.values().forEach(HubFacePort::tick);
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
        facePorts.values().forEach(port -> port.managedNode().loadFromNBT(tag));
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
        initialized = false;
        facePorts.values().forEach(HubFacePort::destroy);
    }
}
