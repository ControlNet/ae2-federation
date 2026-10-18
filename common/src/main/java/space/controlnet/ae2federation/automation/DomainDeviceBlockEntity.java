package space.controlnet.ae2federation.automation;

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
import net.minecraft.world.level.block.entity.BlockEntityType;
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
 * A block of the automation system that is a device node of its Federation domain, as an Endpoint is: it links ME
 * Federation Cable on the faces {@link #links} allows, and has a short ID drawn by the server and kept in a server-wide
 * index; the block stores the ID to claim it back on load, and a broken block gives it up.
 */
public abstract class DomainDeviceBlockEntity extends AEBaseBlockEntity {
    private final Map<Direction, CableFacePort> ports = new EnumMap<>(Direction.class);
    private @Nullable String id;
    private @Nullable FederationDomainNodeId nodeId;
    private boolean dirty = true;
    /** Set when the chunk unloads, so the removal that follows is not taken for a broken block. */
    private boolean unloading;

    protected DomainDeviceBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) {
        super(type, position, state);
    }

    /** The node kind the block publishes. */
    protected abstract FederationDomainNodeKind kind();

    /** Claims the stored ID, or draws a new one, in the server-wide index. */
    protected abstract String claim(ServerLevel level, @Nullable String stored);

    /** The block was broken: frees its ID. */
    protected abstract void release(ServerLevel level, String id);

    /** Whether the face links ME Federation Cable. */
    protected boolean links(Direction face) {
        return true;
    }

    /** The ID once the server has claimed it, such as {@code interface-k7m2qx}. */
    public @Nullable String id() {
        return id;
    }

    public @Nullable FederationPort federationPort(@Nullable Direction face) {
        return face != null && links(face) ? new FederationPort(worldPosition, face) : null;
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
        var claimed = claim(serverLevel, id);
        if (!claimed.equals(id)) {
            id = claimed;
            setChanged();
        }
        nodeId = FederationDomainRegistryAccess.nodeId(serverLevel, worldPosition);
        openPorts();
    }

    /** Opens a cable port on every linking face; again when the faces that link change. */
    protected void openPorts() {
        if (!(level instanceof ServerLevel serverLevel) || nodeId == null) {
            return;
        }
        ports.values().forEach(CableFacePort::destroy);
        ports.clear();
        for (var face : Direction.values()) {
            if (links(face)) {
                var port = new CableFacePort(worldPosition, face);
                port.initialize(serverLevel);
                port.revalidate();
                ports.put(face, port);
            }
        }
        publish();
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, DomainDeviceBlockEntity device) {
        var changed = false;
        for (var port : device.ports.values()) {
            changed |= port.tick();
        }
        if (changed || device.dirty) {
            device.publish();
        }
    }

    public void neighborChanged(BlockPos neighborPosition) {
        ports.forEach((face, port) -> {
            if (worldPosition.relative(face).equals(neighborPosition) && port.revalidate()) {
                publish();
            }
        });
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
                kind(), evidence))) {
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
            release(serverLevel, id);
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
