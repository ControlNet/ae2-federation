package space.controlnet.ae2federation.automation;

import java.util.EnumMap;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
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
import space.controlnet.ae2federation.router.CableLinkedBlock;

/**
 * What makes a block entity a device node of its Federation domain, as an Endpoint is: it links ME Federation Cable on
 * the faces its block names ({@link CableLinkedBlock}), and has a short ID drawn by the server and kept in a server-wide
 * index; the block stores the ID to claim it back on load, and a broken block gives it up. Held by the block entity,
 * which forwards its lifecycle here, so a device can extend another mod's block entity.
 */
public final class DomainDevice {
    /** The block entity that holds the device, and what differs between devices. */
    public interface Host {
        /** The node kind the block publishes. */
        FederationDomainNodeKind kind();

        /**
         * Claims the stored ID, or draws a new one, in the server-wide index; null for a node without an ID of its own,
         * such as a computation system's interface.
         */
        @Nullable String claim(ServerLevel level, @Nullable String stored);

        /** The block was broken: frees its ID. */
        void release(ServerLevel level, String id);

        /** Links to nodes that are not beside the block, named on both ends, as a Router's remote ports are. */
        default Map<String, FederationDomainPortEvidence> remotePorts() {
            return Map.of();
        }
    }

    private final BlockEntity entity;
    private final Host host;
    private final Map<Direction, CableFacePort> ports = new EnumMap<>(Direction.class);
    private @Nullable String id;
    private @Nullable FederationDomainNodeId nodeId;
    private boolean dirty = true;
    /** Set when the chunk unloads, so the removal that follows is not taken for a broken block. */
    private boolean unloading;

    public DomainDevice(BlockEntity entity, Host host) {
        this.entity = entity;
        this.host = host;
    }

    /** Whether the face links ME Federation Cable, as the block says. */
    public boolean links(Direction face) {
        var state = entity.getBlockState();
        return state.getBlock() instanceof CableLinkedBlock linked && linked.linksCable(state, face);
    }

    /** The ID once the server has claimed it, such as {@code interface-k7m2qx}. */
    public @Nullable String id() {
        return id;
    }

    public @Nullable FederationPort federationPort(@Nullable Direction face) {
        return face != null && links(face) ? new FederationPort(entity.getBlockPos(), face) : null;
    }

    /** The block entity is back in the world after a removal. */
    public void clearRemoved() {
        unloading = false;
    }

    /** The block entity is ready: claims the ID and opens the ports. */
    public void onReady() {
        if (!(entity.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        var claimed = host.claim(serverLevel, id);
        if (!java.util.Objects.equals(claimed, id)) {
            id = claimed;
            entity.setChanged();
        }
        nodeId = FederationDomainRegistryAccess.nodeId(serverLevel, entity.getBlockPos());
        openPorts();
    }

    /** Opens a cable port on every linking face; again when the faces that link change. */
    public void openPorts() {
        if (!(entity.getLevel() instanceof ServerLevel serverLevel) || nodeId == null) {
            return;
        }
        ports.values().forEach(CableFacePort::destroy);
        ports.clear();
        for (var face : Direction.values()) {
            if (links(face)) {
                var port = new CableFacePort(entity.getBlockPos(), face);
                port.initialize(serverLevel);
                port.revalidate();
                ports.put(face, port);
            }
        }
        publish();
    }

    /** The host's remote ports changed: publishes the node again on the next tick. */
    public void remotePortsChanged() {
        dirty = true;
    }

    /** A server tick: publishes the node again when a port's peer changed. */
    public void tick() {
        var changed = false;
        for (var port : ports.values()) {
            changed |= port.tick();
        }
        if (changed || dirty) {
            publish();
        }
    }

    public void neighborChanged(BlockPos neighborPosition) {
        ports.forEach((face, port) -> {
            if (entity.getBlockPos().relative(face).equals(neighborPosition) && port.revalidate()) {
                publish();
            }
        });
    }

    private void publish() {
        if (!(entity.getLevel() instanceof ServerLevel serverLevel) || nodeId == null) {
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
        evidence.putAll(host.remotePorts());
        if (FederationDomainRegistryAccess.get(serverLevel).upsertNode(new FederationDomainNodeEvidence(nodeId,
                host.kind(), evidence))) {
            FederationBindingRefresh.request(serverLevel);
        }
        dirty = false;
    }

    private void closePorts() {
        ports.values().forEach(CableFacePort::destroy);
        ports.clear();
        if (entity.getLevel() instanceof ServerLevel serverLevel && nodeId != null) {
            FederationDomainRegistryAccess.removeNodeIfPresent(serverLevel, nodeId, !unloading);
            FederationBindingRefresh.request(serverLevel);
        }
        nodeId = null;
        dirty = true;
    }

    public void onChunkUnloaded() {
        unloading = true;
        closePorts();
    }

    public void setRemoved() {
        var broken = !unloading;
        closePorts();
        if (broken && id != null && entity.getLevel() instanceof ServerLevel serverLevel
                && !serverLevel.getBlockState(entity.getBlockPos()).is(entity.getBlockState().getBlock())) {
            // Broken, not unloaded or replaced by itself: the ID is free again.
            host.release(serverLevel, id);
        }
    }

    public void load(CompoundTag tag) {
        id = tag.contains("automationId") ? tag.getString("automationId") : null;
    }

    public void save(CompoundTag tag) {
        if (id != null) {
            tag.putString("automationId", id);
        }
    }
}
