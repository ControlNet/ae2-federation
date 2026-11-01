package space.controlnet.ae2federation.router;

import appeng.api.networking.GridHelper;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.bundle.BundleBeam;
import space.controlnet.ae2federation.bundle.BundleContacts;
import space.controlnet.ae2federation.bundle.BundleLookFrame;
import space.controlnet.ae2federation.bundle.BundleSegment;
import space.controlnet.ae2federation.bundle.BundleSet;
import space.controlnet.ae2federation.bundle.BundledCables;
import space.controlnet.ae2federation.client.CableBeamStyle;
import space.controlnet.ae2federation.domain.CableCapacity;
import space.controlnet.ae2federation.domain.CableCapacityCounter;
import space.controlnet.ae2federation.domain.CableCapacityLimit;
import space.controlnet.ae2federation.domain.FederationBindingRefresh;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeKind;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainPortEvidence;
import space.controlnet.ae2federation.domain.FederationDomainPortId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.CableFacePort;

public final class FederationCableBlockEntity extends BlockEntity {
    /** Ticks between two capacity counts of one cable while its domain keeps changing, as when a player builds. */
    private static final int CAPACITY_CHECK_TICKS = 10;
    private static final String CAPACITY_UNITS = "CapacityUnits";
    private static final String CAPACITY = "Capacity";
    private static final String CAPACITY_ENFORCED = "CapacityEnforced";
    private static final String UNPOWERED = "Unpowered";
    private static final String BUNDLE_MIN = "BundleMin";
    private static final String BUNDLE_MAX = "BundleMax";
    private static final String BUNDLE_AXIS = "BundleAxis";

    private final Map<Direction, CableFacePort> ports = new EnumMap<>(Direction.class);
    private boolean initialized;
    /** Server only: what the beam shows must be synced, the capacity count and the unpowered state each. */
    private boolean capacityDue;
    private boolean powerDue;
    private boolean federationDomainDirty = true;
    private @Nullable FederationDomainNodeId federationDomainNodeId;
    /** Set when the chunk unloads, so the removal that follows is not taken for a broken block. */
    private boolean unloading;
    /** Client only: the flow renderer's neighbour mask, a 2x2 bundle's middle beam, and their game tick. */
    private int flowMask;
    private @Nullable BundleBeam bundleBeam;
    private long flowMaskTick = Long.MIN_VALUE;
    /**
     * The capacity count the flow renderer draws: computed on the server, sent to clients when it changes. Derived, so
     * never saved. A capacity of 0 means none has been counted yet.
     */
    private int capacityUnits;
    private int capacity;
    private boolean capacityEnforced;
    /** Server only: the evidence revision last counted, and the earliest tick to count again. */
    private long capacityRevision = Long.MIN_VALUE;
    private long nextCapacityCheck;
    /** Whether this cable's domain is unpowered, as the server last checked it; its beam then shows red and thin. */
    private boolean unpowered;
    /**
     * The bundle segment holding this cable, null for an ordinary cable: on the server, as its registry last said; on a
     * client, as the server sent it. The model, outline and beam read it through {@link BundleView}.
     */
    private @Nullable BundleSegment bundleSegment;
    /** Client only: the load the flow renderer last drew and when, so a new count eases in. */
    private double beamLoad = Double.NaN;
    private long beamNanos;

    public FederationCableBlockEntity(BlockPos position, BlockState state) {
        super(RouterRegistration.FEDERATION_CABLE_BLOCK_ENTITY.get(), position, state);
        for (var face : Direction.values()) {
            ports.put(face, new CableFacePort(position, face, "", this::wake,
                    () -> !(level instanceof ServerLevel serverLevel) || BundleContacts.open(serverLevel, position, face)));
        }
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        unloading = false;
        GridHelper.onFirstTick(this, FederationCableBlockEntity::initialize);
    }

    /** The cable has face or evidence work: it runs in the next binding tick ({@link FederationCableTicks}). */
    private void wake() {
        if (initialized && level instanceof ServerLevel serverLevel) FederationCableTicks.wake(serverLevel, this);
    }

    /** What the beam shows may have changed: sync both once the domains settle. */
    void requestDisplaySync() {
        if (!initialized || !(level instanceof ServerLevel serverLevel)) return;
        capacityDue = true;
        powerDue = true;
        FederationCableTicks.syncDisplay(serverLevel, this);
    }

    /**
     * Resolves the faces that must, and publishes the evidence when a peer changed or it is dirty. Returns whether work
     * is left for the next tick.
     */
    /** Whether this cable is initialized in {@code serverLevel} and not removed, so it may work there. */
    boolean live(ServerLevel serverLevel) {
        return initialized && !isRemoved() && level == serverLevel;
    }

    boolean work(ServerLevel serverLevel) {
        if (!live(serverLevel)) return false;
        var changed = false;
        for (var port : ports.values()) changed |= port.tick();
        if (changed || federationDomainDirty) publishFederationDomainTopology();
        if (federationDomainDirty) return true;
        for (var port : ports.values()) {
            if (port.dirty()) return true;
        }
        return false;
    }

    /** Syncs what the beam shows, once the domains settled. Returns whether some of it must wait for a later tick. */
    boolean syncDisplay(ServerLevel serverLevel) {
        if (!initialized || isRemoved() || level != serverLevel) return false;
        if (capacityDue) capacityDue = !syncCapacity(serverLevel);
        if (powerDue) powerDue = !syncPower(serverLevel);
        return capacityDue || powerDue;
    }

    /** Looks again whether the cable's domain is unpowered and tells clients of a change. */
    private boolean syncPower(ServerLevel serverLevel) {
        var registry = FederationDomainRegistryAccess.get(serverLevel);
        if (federationDomainNodeId == null) return true;
        if (registry.pending()) return false;
        boolean stopped = registry.federationDomainOf(federationDomainNodeId)
                .filter(domain -> registry.unpowered(domain.federationDomainId())).isPresent();
        if (stopped == unpowered) return true;
        unpowered = stopped;
        serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        return true;
    }

    /**
     * Counts this cable again once its domain or the limit changed, and tells clients when the count differs. Returns
     * whether the count is current; false while it must wait for its throttle or a pending recompute.
     */
    private boolean syncCapacity(ServerLevel serverLevel) {
        long revision = FederationDomainRegistryAccess.get(serverLevel).evidenceRevision();
        int factor = capacityFactor(serverLevel);
        int limit = CableCapacityLimit.capacity()
                * (factor == FederationDomainNodeEvidence.UNLIMITED ? CableBeamStyle.UNLIMITED_SCALE : factor);
        boolean enforced = CableCapacityLimit.current(factor).isPresent();
        long now = serverLevel.getGameTime();
        if (revision == capacityRevision && limit == capacity && enforced == capacityEnforced) return true;
        if (now < nextCapacityCheck || FederationDomainRegistryAccess.get(serverLevel).pending()) return false;
        capacityRevision = revision;
        nextCapacityCheck = now + CAPACITY_CHECK_TICKS;
        int units = CableCapacity.demand(serverLevel, worldPosition).map(CableCapacityCounter.Demand::units).orElse(0);
        if (units == capacityUnits && limit == capacity && enforced == capacityEnforced) return true;
        capacityUnits = units;
        capacity = limit;
        capacityEnforced = enforced;
        serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        return true;
    }

    /** How many cables' capacity this cable carries: its bundle's multiple, or 1 when it is not bundled. */
    public static int capacityFactor(ServerLevel level, BlockPos position) {
        return BundledCables.get(level).at(position)
                .map(membership -> membership.bundle().shape().multiplier().orElse(FederationDomainNodeEvidence.UNLIMITED))
                .orElse(1);
    }

    private int capacityFactor(ServerLevel level) {
        return capacityFactor(level, worldPosition);
    }

    /** The bundle this cable belongs to changed: its faces, its evidence and its capacity count are judged again. */
    public void bundleChanged() {
        if (level instanceof ServerLevel serverLevel) bundleSegment = registeredSegment(serverLevel);
        ports.values().forEach(CableFacePort::revalidate);
        federationDomainDirty = true;
        capacityRevision = Long.MIN_VALUE;
        nextCapacityCheck = 0;
        wake();
        requestDisplaySync();
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public void neighborChanged(BlockPos neighborPosition) {
        for (var face : Direction.values()) {
            if (worldPosition.relative(face).equals(neighborPosition)) {
                if (ports.get(face).revalidate()) {
                    publishFederationDomainTopology();
                }
                // The face resolves again next tick even when the peer looks the same now.
                wake();
                return;
            }
        }
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

    private void initialize() {
        if (initialized || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        initialized = true;
        bundleSegment = registeredSegment(serverLevel);
        federationDomainNodeId = FederationDomainRegistryAccess.nodeId(serverLevel, worldPosition);
        ports.values().forEach(port -> port.initialize(serverLevel));
        FederationCableTicks.register(serverLevel, this);
        wake();
        requestDisplaySync();
    }

    private @Nullable BundleSegment registeredSegment(ServerLevel serverLevel) {
        return BundledCables.get(serverLevel).at(worldPosition).map(BundleSet.Membership::segment).orElse(null);
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
                new FederationDomainNodeEvidence(federationDomainNodeId, FederationDomainNodeKind.CABLE, evidence,
                        capacityFactor(serverLevel)))) {
            FederationBindingRefresh.request(serverLevel);
        }
        federationDomainDirty = false;
    }

    private void destroyPorts() {
        if (level instanceof ServerLevel serverLevel) FederationCableTicks.forget(serverLevel, this);
        if (level instanceof ServerLevel serverLevel && federationDomainNodeId != null) {
            FederationDomainRegistryAccess.removeNodeIfPresent(serverLevel, federationDomainNodeId, !unloading);
            FederationBindingRefresh.request(serverLevel);
        }
        initialized = false;
        federationDomainDirty = true;
        ports.values().forEach(CableFacePort::destroy);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putInt(CAPACITY_UNITS, capacityUnits);
        tag.putInt(CAPACITY, capacity);
        tag.putBoolean(CAPACITY_ENFORCED, capacityEnforced);
        tag.putBoolean(UNPOWERED, unpowered);
        if (level instanceof ServerLevel serverLevel) {
            BundledCables.get(serverLevel).at(worldPosition).ifPresent(membership -> {
                tag.putLong(BUNDLE_MIN, membership.segment().min().asLong());
                tag.putLong(BUNDLE_MAX, membership.segment().max().asLong());
                tag.putString(BUNDLE_AXIS, membership.segment().axis().getSerializedName());
            });
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (!tag.contains(CAPACITY)) return;
        capacityUnits = tag.getInt(CAPACITY_UNITS);
        capacity = tag.getInt(CAPACITY);
        capacityEnforced = tag.getBoolean(CAPACITY_ENFORCED);
        unpowered = tag.getBoolean(UNPOWERED);
        var axis = Direction.Axis.byName(tag.getString(BUNDLE_AXIS));
        var previous = bundleSegment;
        bundleSegment = tag.contains(BUNDLE_MIN) && axis != null
                ? new BundleSegment(BlockPos.of(tag.getLong(BUNDLE_MIN)), BlockPos.of(tag.getLong(BUNDLE_MAX)), axis)
                : null;
        // A new segment changes this cable's shell and its neighbours': mesh the sections around it again.
        if (level != null && level.isClientSide() && !java.util.Objects.equals(previous, bundleSegment)) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    /** The bundle segment holding this cable, or null when it is an ordinary cable. */
    public @Nullable BundleSegment bundleSegment() {
        return bundleSegment;
    }

    /** The load of this cable's capacity count, as {@link CableBeamStyle#loadOf} reads it. */
    /** Whether the cable's domain is unpowered, as the server last sent it. */
    public boolean unpowered() {
        return unpowered;
    }

    public double capacityLoad() {
        return CableBeamStyle.loadOf(capacityUnits, capacity, capacityEnforced);
    }

    /** Client only: eases the drawn load towards {@code target}, frame-rate independent, in drawn steps. */
    public double beamLoad(double target, long nanos) {
        beamLoad = Double.isNaN(beamLoad) ? target
                : CableBeamStyle.smooth(beamLoad, target, Math.max(0, nanos - beamNanos) / 1e9);
        beamNanos = nanos;
        return CableBeamStyle.drawn(beamLoad, target);
    }

    /**
     * The neighbour mask the flow renderer draws, read from the level at most once per game tick: the renderer asks
     * every frame, and six block lookups per cable per frame add up across a base.
     */
    public int flowMask() {
        refreshFlow();
        return flowMask;
    }

    /** Where this cell draws its 2x2 or 3x3 bundle's middle beam, or null when it draws its own; read like {@link #flowMask}. */
    public @Nullable BundleBeam bundleBeam() {
        refreshFlow();
        return bundleBeam;
    }

    private void refreshFlow() {
        var gameTime = level == null ? Long.MIN_VALUE : level.getGameTime();
        if (level == null || flowMaskTick == gameTime) return;
        flowMaskTick = gameTime;
        // Most cells of a 2x2 or 3x3 bundle draw no beam, which their own segment tells: no need to read the
        // neighbours every tick for that. A bundled cell's mask is never drawn.
        var own = bundleSegment == null ? null : BundleBeam.offCorner(worldPosition, bundleSegment);
        if (own != null) {
            flowMask = 0;
            bundleBeam = own;
            return;
        }
        // The tick's looks are shared: a layer's beam reads the other layers of a long device contact.
        var frame = BundleLookFrame.of(level);
        var look = frame.look(worldPosition);
        flowMask = look.isPresent() ? look.get().flowMask()
                : CableVisualConnections.maskOf(CableVisualConnections.connections(level, worldPosition));
        var segment = bundleSegment;
        bundleBeam = look.isEmpty() || segment == null ? null : BundleBeam.of(worldPosition, segment, look.get(),
                layers -> frame.look(worldPosition.relative(segment.axis(), layers))).orElse(null);
    }
}
