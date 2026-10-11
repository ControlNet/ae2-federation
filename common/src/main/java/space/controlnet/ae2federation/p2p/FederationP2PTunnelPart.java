package space.controlnet.ae2federation.p2p;

import appeng.api.networking.IGridNodeListener;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.items.parts.PartModels;
import appeng.me.service.P2PService;
import appeng.parts.p2p.P2PModels;
import appeng.parts.p2p.P2PTunnelPart;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import appeng.api.networking.IGrid;
import net.minecraft.server.MinecraftServer;
import java.util.List;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationBindingRefresh;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainPortEvidence;
import space.controlnet.ae2federation.domain.FederationDomainPortId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.CableFacePort;
import space.controlnet.ae2federation.domain.port.FederationPort;

/**
 * A P2P tunnel that carries Federation cable: its front is a Federation port, and the input of a frequency is joined to
 * each of its outputs, as if a Federation cable ran between their fronts. Each tunnel is its own Federation Domain node
 * (its host block and side); the ME network carrying the tunnel is never a member, as the tunnel publishes no native
 * evidence. Like AE2's own tunnels it needs power and a channel, and the link holds only while the input is active:
 * outputs reach one another through the input.
 */
public final class FederationP2PTunnelPart extends P2PTunnelPart<FederationP2PTunnelPart> {
    public static final ResourceLocation FRONT_MODEL =
            ResourceLocation.fromNamespaceAndPath("ae2federation", "part/p2p_tunnel_federation");
    private static final P2PModels MODELS = new P2PModels(FRONT_MODEL);
    private static final String TUNNEL_PORT = "tunnel:";
    /** Server-thread event coalescing, scoped by the native Grid and frequency. Entries live for one queued drain. */
    private static final Map<MinecraftServer, Map<GroupKey, Set<FederationP2PTunnelPart>>> PENDING = new WeakHashMap<>();
    private record GroupKey(@Nullable IGrid grid, short frequency) {}
    private @Nullable GroupKey previousGroup;

    private @Nullable CableFacePort front;
    private @Nullable FederationDomainNodeId nodeId;

    public FederationP2PTunnelPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @PartModels
    public static List<IPartModel> getModels() {
        return MODELS.getModels();
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS.getModel(isPowered(), isActive());
    }

    @Override
    public void addToWorld() {
        super.addToWorld();
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        var port = port();
        nodeId = FederationDomainRegistryAccess.nodeId(serverLevel, port);
        front = new CableFacePort(port.ownerPosition(), port.outwardFace(), port.part(), this::scheduleRefresh);
        front.initialize(serverLevel);
        scheduleRefresh();
    }

    @Override
    public void removeFromWorld() {
        scheduleRefresh();
        super.removeFromWorld();
        if (front != null) {
            front.destroy();
            front = null;
        }
        if (getLevel() instanceof ServerLevel serverLevel && nodeId != null) {
            P2PRefreshDiagnostics.removed(nodeId);
            FederationDomainRegistryAccess.removeNodeIfPresent(serverLevel, nodeId);
            FederationBindingRefresh.request(serverLevel);
        }
        nodeId = null;
    }

    /**
     * The Federation port on this tunnel's front, for a Federation block in front of it; none on other sides. Like the
     * Federation cable's, it does not depend on the tunnel's state: a neighbour caches what it reads, and a host that
     * loads adds its parts to the world only on its first tick, after the neighbour may have read.
     */
    public @Nullable FederationPort federationPort(Direction side) {
        return side == getSide() ? port() : null;
    }

    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        scheduleRefresh();
    }

    @Override
    public void onTunnelNetworkChange() {
        scheduleRefresh();
    }

    @Override
    public void onTunnelConfigChange() {
        scheduleRefresh();
    }

    @Override
    public void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) {
        if (pos.relative(getSide()).equals(neighbor)) {
            scheduleRefresh();
        }
    }

    @Override
    public void onUpdateShape(Direction side) {
        if (side == getSide()) {
            scheduleRefresh();
        }
    }

    private FederationPort port() {
        return new FederationPort(getBlockEntity().getBlockPos(), getSide(), getSide().getSerializedName());
    }

    /**
     * Republishes every tunnel of this frequency on the next server tick. AE2 tells only some tunnels of a frequency
     * about a change (a channel change reaches only the tunnel itself), so each tunnel's links are rebuilt together.
     * Like the Bridge, never during a node event itself, when a neighbouring chunk may be unloading, and never while the
     * server stops.
     *
     * <p>A burst queues one drain per server and builds each affected frequency once. Both the previous and current
     * group are dirtied on a frequency change, including outputs left behind after the input moves.
     */
    private void scheduleRefresh() {
        if (!(getLevel() instanceof ServerLevel serverLevel)) return;
        P2PRefreshDiagnostics.requested();
        var server = serverLevel.getServer();
        if (!server.isRunning()) return;
        var pending = PENDING.get(server);
        if (pending == null) {
            pending = new LinkedHashMap<>();
            PENDING.put(server, pending);
            P2PRefreshDiagnostics.queued();
            server.tell(new TickTask(server.getTickCount() + 1, () -> drain(server)));
        }
        var current = new GroupKey(getMainNode().getGrid(), getFrequency());
        if (previousGroup != null) pending.computeIfAbsent(previousGroup, ignored -> new LinkedHashSet<>()).add(this);
        pending.computeIfAbsent(current, ignored -> new LinkedHashSet<>()).add(this);
        previousGroup = current;
    }

    private static void drain(MinecraftServer server) {
        var pending = PENDING.remove(server);
        if (pending == null) return;
        long started = System.nanoTime();
        if (server.isRunning()) {
            pending.forEach((key, changed) -> {
                var members = new LinkedHashSet<FederationP2PTunnelPart>();
                if (key.grid() != null && !key.grid().isEmpty() && key.frequency() != 0) {
                    var service = P2PService.get(key.grid());
                    if (service.getInput(key.frequency()) instanceof FederationP2PTunnelPart input) members.add(input);
                    service.getOutputs(key.frequency(), FederationP2PTunnelPart.class).forEach(members::add);
                }
                // Include disconnected/inactive members, but never publish a moved tunnel using its old group.
                for (var tunnel : changed) {
                    if (key.equals(new GroupKey(tunnel.getMainNode().getGrid(), tunnel.getFrequency()))) members.add(tunnel);
                }
                var group = List.copyOf(members);
                P2PRefreshDiagnostics.groupBuilt(group.size());
                for (var tunnel : group) tunnel.publish(group);
            });
        }
        P2PRefreshDiagnostics.ran(System.nanoTime() - started);
    }

    /**
     * Publishes this tunnel's node: its front port, and one port per tunnel of its frequency it is linked to. The peer
     * publishes the matching port, so a link holds only when both agree.
     */
    private void publish(List<FederationP2PTunnelPart> group) {
        if (nodeId == null || front == null || !(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        var evidence = new TreeMap<String, FederationDomainPortEvidence>();
        front.revalidate();
        var peer = front.peer();
        if (peer != null) {
            evidence.put(getSide().getSerializedName(), new FederationDomainPortEvidence.Federation(
                    new FederationDomainPortId(FederationDomainRegistryAccess.nodeId(serverLevel, peer),
                            peer.outwardFace().getSerializedName())));
        }
        int links = 0;
        for (var other : TunnelLinks.linked(this, isOutput() ? getInput() : this, group,
                FederationP2PTunnelPart::isActive)) {
            if (other.nodeId != null) {
                evidence.put(TUNNEL_PORT + other.nodeId, new FederationDomainPortEvidence.Federation(
                        new FederationDomainPortId(other.nodeId, TUNNEL_PORT + nodeId)));
                links++;
            }
        }
        boolean changed = FederationDomainRegistryAccess.get(serverLevel).upsertNode(
                new FederationDomainNodeEvidence(nodeId, evidence));
        P2PRefreshDiagnostics.published(nodeId, links, changed);
        if (changed) {
            FederationBindingRefresh.request(serverLevel);
        }
    }
}
