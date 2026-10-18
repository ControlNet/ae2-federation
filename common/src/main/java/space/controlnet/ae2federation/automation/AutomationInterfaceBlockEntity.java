package space.controlnet.ae2federation.automation;

import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.blockentity.AEBaseBlockEntity;
import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationBindingRefresh;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;
import space.controlnet.ae2federation.domain.FederationDomainPortEvidence;
import space.controlnet.ae2federation.domain.FederationDomainPortId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.CableFacePort;
import space.controlnet.ae2federation.domain.port.FederationPort;

/**
 * ME Federation Automation Interface: a place in the world where programs act, on the block in front of it. Its other
 * five faces link ME Federation Cable, through which it is a device node of its domain, as an Endpoint is. Its ID is
 * drawn by the server and kept in {@link AutomationInterfaces}; the block itself stores it to claim it back on load.
 */
public final class AutomationInterfaceBlockEntity extends AEBaseBlockEntity {
    private final Map<Direction, CableFacePort> ports = new EnumMap<>(Direction.class);
    private @Nullable String id;
    private @Nullable FederationDomainNodeId nodeId;
    private boolean dirty = true;
    /** Set when the chunk unloads, so the removal that follows is not taken for a broken block. */
    private boolean unloading;

    public AutomationInterfaceBlockEntity(BlockPos position, BlockState state) {
        super(AutomationRegistration.INTERFACE_BLOCK_ENTITY.get(), position, state);
    }

    /** The interface's ID once the server has claimed it, such as {@code interface-k7m2qx}. */
    public @Nullable String id() {
        return id;
    }

    public Direction front() {
        return getOrientation().getSide(RelativeSide.FRONT);
    }

    /** Federation ports on every face but the front, which acts on the world. */
    public @Nullable FederationPort federationPort(@Nullable Direction face) {
        return face != null && face != front() ? new FederationPort(worldPosition, face) : null;
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        unloading = false;
        scheduleInit();
    }

    @Override
    public void onReady() {
        super.onReady();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        var claimed = AutomationInterfaces.get(serverLevel).claim(id, serverLevel, worldPosition);
        if (!claimed.equals(id)) {
            id = claimed;
            setChanged();
        }
        nodeId = FederationDomainRegistryAccess.nodeId(serverLevel, worldPosition);
        openPorts(serverLevel);
    }

    private void openPorts(ServerLevel serverLevel) {
        ports.values().forEach(CableFacePort::destroy);
        ports.clear();
        for (var face : Direction.values()) {
            if (face != front()) {
                var port = new CableFacePort(worldPosition, face);
                port.initialize(serverLevel);
                port.revalidate();
                ports.put(face, port);
            }
        }
        publish();
    }

    public static void serverTick(Level level, BlockPos position, BlockState state,
            AutomationInterfaceBlockEntity automationInterface) {
        var changed = false;
        for (var port : automationInterface.ports.values()) {
            changed |= port.tick();
        }
        if (changed || automationInterface.dirty) {
            automationInterface.publish();
        }
    }

    public void neighborChanged(BlockPos neighborPosition) {
        ports.forEach((face, port) -> {
            if (worldPosition.relative(face).equals(neighborPosition) && port.revalidate()) {
                publish();
            }
        });
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        if (level instanceof ServerLevel serverLevel && nodeId != null) {
            // The front moved: the face that acted now links, and the new front no longer does.
            openPorts(serverLevel);
        }
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
        }
    }

    private void publish() {
        if (!(level instanceof ServerLevel serverLevel) || nodeId == null) {
            return;
        }
        var evidence = new TreeMap<String, FederationDomainPortEvidence>();
        ports.forEach((face, port) -> {
            var peer = port.peer();
            if (peer != null) {
                evidence.put(face.getSerializedName(), new FederationDomainPortEvidence.Federation(
                        new FederationDomainPortId(FederationDomainRegistryAccess.nodeId(serverLevel, peer),
                                peer.outwardFace().getSerializedName())));
            }
        });
        if (FederationDomainRegistryAccess.get(serverLevel).upsertNode(new FederationDomainNodeEvidence(nodeId,
                FederationDomainNodeKind.AUTOMATION_INTERFACE, evidence))) {
            FederationBindingRefresh.request(serverLevel);
        }
        dirty = false;
    }

    private void closePorts() {
        ports.values().forEach(CableFacePort::destroy);
        ports.clear();
        if (level instanceof ServerLevel serverLevel && nodeId != null) {
            FederationDomainRegistryAccess.removeNodeIfPresent(serverLevel, nodeId, !unloading);
            FederationBindingRefresh.request(serverLevel);
        }
        nodeId = null;
        dirty = true;
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        closePorts();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        var broken = !unloading;
        closePorts();
        if (broken && id != null && level instanceof ServerLevel serverLevel
                && !serverLevel.getBlockState(worldPosition).is(getBlockState().getBlock())) {
            // Broken, not unloaded or replaced by itself: the ID is free again.
            AutomationInterfaces.get(serverLevel).release(id);
        }
        super.setRemoved();
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        id = tag.contains("automationId") ? tag.getString("automationId") : null;
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (id != null) {
            tag.putString("automationId", id);
        }
    }
}
