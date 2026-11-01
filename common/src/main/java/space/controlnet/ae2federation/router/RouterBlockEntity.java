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
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationBindingRefresh;
import space.controlnet.ae2federation.domain.port.FederationPort;
import space.controlnet.ae2federation.domain.port.RouterFacePort;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainPortEvidence;
import space.controlnet.ae2federation.domain.FederationDomainPortId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;

/**
 * A Router's or Switch's block: one node of the domain, linking through each face. A Beam Converter, Beam Former or
 * Quantum Cube is one too, linking the fewer faces its block names ({@link CableLinkedBlock}).
 */
public class RouterBlockEntity extends BlockEntity implements IInWorldGridNodeHost {
    /** Ticks between two looks of each Switch face without a node at its neighbour, missed events aside. */
    private static final int VERIFY_TICKS = 100;
    private final Map<Direction, FederationPort> federationDomainPorts = new EnumMap<>(Direction.class);
    private final Map<Direction, RouterFacePort> facePorts = new EnumMap<>(Direction.class);
    private boolean initialized;
    private boolean federationDomainDirty = true;
    private @Nullable FederationDomainNodeId federationDomainNodeId;
    private @Nullable FederationDomainNodeEvidence publishedEvidence;
    /** Set when the chunk unloads, so the removal that follows is not taken for a broken block. */
    private boolean unloading;
    /**
     * A Router or Switch, which runs on {@link RouterTicks}; the blocks built on this one (Beam Converter, Beam
     * Former, Quantum Cube) tick themselves through {@link #serverTick}.
     */
    private final boolean scheduled;
    /** Compare the evidence with what was published at the next work, though no face changed. */
    private boolean evidenceDue;
    private long works;

    public RouterBlockEntity(BlockPos position, BlockState state) {
        this(RouterBlock.blockEntityType(state), position, state, RouterBlock.attachesNetworks(state), true);
    }

    protected RouterBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state, boolean attachesNetworks) {
        this(type, position, state, attachesNetworks, false);
    }

    private RouterBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state, boolean attachesNetworks,
            boolean scheduled) {
        super(type, position, state);
        this.scheduled = scheduled;
        for (var face : Direction.values()) {
            var federationDomainPort = new FederationPort(position, face);
            federationDomainPorts.put(face, federationDomainPort);
            facePorts.put(face, new RouterFacePort(position, face, federationDomainPort, attachesNetworks,
                    () -> links(face), this::wake));
        }
    }

    /** Whether {@code face} links now, as the block says ({@link CableLinkedBlock}). */
    protected final boolean links(Direction face) {
        var state = getBlockState();
        return state.getBlock() instanceof CableLinkedBlock linked && linked.linksCable(state, face);
    }

    /** What the block publishes itself as. */
    protected FederationDomainNodeKind kind() {
        return getType() == RouterRegistration.SWITCH_BLOCK_ENTITY.get() ? FederationDomainNodeKind.SWITCH
                : FederationDomainNodeKind.ROUTER;
    }

    /** Links to nodes that are not beside this block, named on both ends, such as a Beam Former's beam. */
    protected Map<String, FederationDomainPortEvidence> remotePorts() {
        return Map.of();
    }

    /** How many cables' capacity this node carries: one, unless the block's shape says otherwise. */
    protected int capacityFactor() {
        return 1;
    }

    /** The body formed, broke or reshaped: the faces link again, or stop linking, from the next tick. */
    @Override
    public void setBlockState(BlockState state) {
        var previous = getBlockState();
        var wasFormed = RouterBlock.formed(previous);
        super.setBlockState(state);
        if ((wasFormed != RouterBlock.formed(state) || RouterBlock.connections(previous) != RouterBlock.connections(state)
                || turned(previous, state)) && level instanceof ServerLevel) {
            facePorts.values().forEach(RouterFacePort::markDirty);
            federationDomainDirty = true;
            wake();
        }
        if (previous != state && level instanceof ServerLevel serverLevel) {
            // A bundled cable links only to a whole body: the cables beside it, and this block's own caches of them,
            // judge the contact again. A body changes without neighbour updates, so nothing else would tell them.
            serverLevel.invalidateCapabilities(worldPosition);
            for (var face : Direction.values()) serverLevel.invalidateCapabilities(worldPosition.relative(face));
        }
    }

    /** A Beam Converter turned end for end: its front, the face it links through, moved. */
    private static boolean turned(BlockState previous, BlockState state) {
        var facing = net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING;
        return previous.hasProperty(facing) && state.hasProperty(facing) && previous.getValue(facing) != state.getValue(facing);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        unloading = false;
        GridHelper.onFirstTick(this, RouterBlockEntity::initialize);
    }

    private void initialize() {
        if (initialized || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        initialized = true;
        federationDomainNodeId = FederationDomainRegistryAccess.nodeId(serverLevel, worldPosition);
        if (scheduled) RouterTicks.register(serverLevel, this);
        facePorts.values().forEach(port -> port.initialize(serverLevel));
        wake();
        // A placed or loaded block checks the body it belongs to; one check settles every member asking this tick.
        DeviceStructureUpdater.request(serverLevel, worldPosition);
    }

    public static void serverTick(Level level, BlockPos position, BlockState state, RouterBlockEntity router) {
        // A slow sweep, spread over the Switches by position: a face without a node looks at its neighbour again.
        if (Math.floorMod(level.getGameTime() + position.hashCode(), VERIFY_TICKS) == 0) {
            router.facePorts.values().forEach(RouterFacePort::verifyIfNodeless);
        }
        var changed = router.facePorts.values().stream().map(RouterFacePort::tick).reduce(false, Boolean::logicalOr);
        if (changed || router.federationDomainDirty) {
            router.publishFederationDomainTopology();
        } else if (level.getGameTime() % 20 == 0) {
            // A native network settles its identity without an event on this Router's faces.
            router.publishIfEvidenceChanged();
        }
    }

    /** Asks {@link RouterTicks} for this Router's work at its level's next tick end. */
    private void wake() {
        if (scheduled && initialized && !isRemoved() && level instanceof ServerLevel serverLevel) {
            RouterTicks.wake(serverLevel, this);
        }
    }

    /**
     * Resolves the faces that wait for it and publishes what changed, or compares the evidence when asked to. Returns
     * whether work is left for the next tick.
     */
    boolean work(ServerLevel serverLevel) {
        if (!live(serverLevel)) return false;
        works++;
        var changed = false;
        for (var port : facePorts.values()) changed |= port.tick();
        if (changed || federationDomainDirty) {
            publishFederationDomainTopology();
        } else if (evidenceDue) {
            publishIfEvidenceChanged();
        }
        evidenceDue = false;
        if (federationDomainDirty) return true;
        for (var port : facePorts.values()) {
            if (port.dirty()) return true;
        }
        return false;
    }

    /** Whether this Router is initialized in {@code serverLevel} and not removed, so it may work there. */
    boolean live(ServerLevel serverLevel) {
        return initialized && !isRemoved() && level == serverLevel;
    }

    /** How many times this Router's scheduled work ran: an idle Router's count stays put, as tests check. */
    public long works() {
        return works;
    }

    /** The identities changed: compare the evidence with what was published at the next work. */
    void compareEvidence() {
        evidenceDue = true;
        wake();
    }

    /**
     * This Router's turn: every face resolves again and reads its neighbour's Federation port without its cache, and a
     * Switch face without a node looks at its AE2 neighbour without its caches.
     */
    void sweep() {
        facePorts.values().forEach(RouterFacePort::sweep);
        compareEvidence();
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

    /**
     * AE2 asks with a null side for a node not tied to a face; the nodes here all sit on faces. A Switch face asked
     * while it has no node looks at its neighbour again on the next tick: an AE2 node asks every block beside it when
     * it connects, so an AE2 block that appeared without telling this Switch still gets a face to connect to.
     */
    @Nullable
    @Override
    public IGridNode getGridNode(@Nullable Direction face) {
        if (face == null) return null;
        var node = boundaryNode(face);
        if (node == null) facePorts.get(face).verifyIfNodeless();
        return node;
    }

    public Map<Direction, RouterPortBinding> bindings() {
        var snapshot = new EnumMap<Direction, RouterPortBinding>(Direction.class);
        facePorts.forEach((face, port) -> snapshot.put(face, port.binding()));
        return Collections.unmodifiableMap(snapshot);
    }

    /** The settled networks this block's faces attach, as last published to the domain registry. */
    public java.util.Set<space.controlnet.ae2federation.identity.NetworkId> attachedNetworks() {
        var networks = new java.util.HashSet<space.controlnet.ae2federation.identity.NetworkId>();
        if (publishedEvidence != null) {
            publishedEvidence.ports().values().forEach(port -> {
                if (port instanceof FederationDomainPortEvidence.Native attached) networks.add(attached.networkId());
            });
        }
        return networks;
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
        unloading = true;
        destroyPorts();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        destroyPorts();
        super.setRemoved();
    }

    private void destroyPorts() {
        if (scheduled && level instanceof ServerLevel serverLevel) RouterTicks.forget(serverLevel, this);
        if (level instanceof ServerLevel serverLevel && federationDomainNodeId != null) {
            FederationDomainRegistryAccess.removeNodeIfPresent(serverLevel, federationDomainNodeId, !unloading);
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

    protected void publishFederationDomainTopology() {
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
        evidence.putAll(remotePorts());
        return new FederationDomainNodeEvidence(federationDomainNodeId, kind(), evidence, capacityFactor());
    }
}
