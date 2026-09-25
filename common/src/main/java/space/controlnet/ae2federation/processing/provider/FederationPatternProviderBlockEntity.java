package space.controlnet.ae2federation.processing.provider;

import appeng.api.config.Setting;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.IConfigManager;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.ae2.processing.NativeLaneDispatchListener;
import space.controlnet.ae2federation.ae2.processing.NativeProviderLane;
import space.controlnet.ae2federation.ae2.processing.NativeProviderOwnerLogic;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.energy.EnergyBindingService;
import space.controlnet.ae2federation.domain.FederationDomainInvalidationReason;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainPortEvidence;
import space.controlnet.ae2federation.domain.FederationDomainPortId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.FederationPort;
import space.controlnet.ae2federation.domain.port.FederationPortCapability;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.ClaimRequest;
import space.controlnet.ae2federation.processing.claim.ClaimResult;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.claim.EndpointId;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;
import space.controlnet.ae2federation.processing.claim.EndpointInstanceEpoch;
import space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity;
import space.controlnet.ae2federation.processing.claim.NativeTargetDomainRegistry;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.storage.mount.StorageMountService;

/**
 * ME Federation Pattern Provider. The front face is the Federation port; the other five faces expose one native ME
 * node. The physical Pattern slots and settings belong to one native {@link NativeProviderOwnerLogic}, so AE2's own
 * Pattern Provider menu, Pattern Access Terminal and drop/persistence rules apply to them. Each mapped Endpoint gets
 * one native {@link NativeProviderLane}, published to AE2 as its own crafting provider; AE2's planner and CPUs choose
 * among Lanes, and each Lane keeps its own native Blocking, lock, send remainder and return state.
 */
public final class FederationPatternProviderBlockEntity extends AENetworkedBlockEntity
        implements PatternProviderLogicHost, ProviderMappingController {
    public static final int PATTERN_SLOTS = 9;
    /** Side of an Endpoint through which Lanes reach its Subnet; any non-Federation Endpoint face is equivalent. */
    public static final Direction ENDPOINT_ACCESS_SIDE = EndpointBlockEntity.FEDERATION_FACE.getOpposite();
    private static final String SCHEMA_TAG = "federationProviderSchema";
    private static final int SCHEMA = 1;
    private static final String IDENTITY_TAG = "providerIdentity";
    private static final String OWNER_TAG = "ownerLogic";
    private static final String LANES_TAG = "laneBindings";
    private static final String PROVIDER_TAG = "mappedProvider";
    private static final String NATIVE_PORT = "native";
    private static final int MAINTENANCE_INTERVAL = 20;
    private static final IGridNodeListener<FederationPatternProviderBlockEntity> NODE_LISTENER =
            new IGridNodeListener<>() {
                @Override
                public void onSaveChanges(FederationPatternProviderBlockEntity owner, IGridNode node) {
                    owner.saveChanges();
                }

                @Override
                public void onStateChanged(FederationPatternProviderBlockEntity owner, IGridNode node, State state) {
                    owner.onMainNodeStateChanged(state);
                }

                @Override
                public void onGridChanged(FederationPatternProviderBlockEntity owner, IGridNode node) {
                    owner.onGridChanged();
                }
            };

    private final NativeProviderOwnerLogic owner;
    private final MappedPatternProvider provider;
    private final List<LaneBinding> lanes = new ArrayList<>();
    private final NativeTargetDomainRegistry domains = new NativeTargetDomainRegistry();
    private final LaneConfigManager configManager;
    private ProviderIdentity identity = ProviderIdentity.create();
    private @Nullable ProviderRuntime runtime;
    private @Nullable BlockCapabilityCache<FederationPort, Direction> federationCache;
    private @Nullable FederationDomainNodeId federationDomainNodeId;
    private boolean federationDomainDirty = true;
    private boolean unloading;
    private boolean pendingRotation;
    private int maintenanceTicks;

    public FederationPatternProviderBlockEntity(BlockPos position, BlockState state) {
        this(ProcessingRegistration.PROVIDER_BLOCK_ENTITY.get(), position, state);
    }

    public FederationPatternProviderBlockEntity(BlockEntityType<?> type, BlockPos position, BlockState state) {
        super(type, position, state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL);
        owner = new NativeProviderOwnerLogic(getMainNode(), this, PATTERN_SLOTS);
        provider = new MappedPatternProvider(getMainNode(), this, owner.getPatternInv());
        owner.onPatternSlotChanged(provider::refreshPatternSlot);
        configManager = new LaneConfigManager(owner.getConfigManager(), provider::nativeLanes);
    }

    @Override
    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, NODE_LISTENER);
    }

    // ---- orientation and faces

    public Direction federationFace() {
        return getOrientation().getSide(RelativeSide.FRONT);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.complementOf(EnumSet.of(orientation.getSide(RelativeSide.FRONT)));
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        if (runtime != null) {
            runtime.rotate(new ProviderOrientation(ProviderNodeWiring.face(federationFace())));
            pendingRotation = true;
        }
        rebuildFederationCache();
        invalidateFederationDomainTopology();
    }

    /** The Federation port capability exists only on the front face. */
    public @Nullable FederationPort federationPort(@Nullable Direction face) {
        return face != null && face == federationFace() ? new FederationPort(worldPosition, face) : null;
    }

    // ---- lifecycle

    @Override
    public void onReady() {
        super.onReady();
        if (!(level instanceof ServerLevel serverLevel) || runtime != null) {
            return;
        }
        runtime = new ProviderRuntime(serverLevel, getMainNode(), provider, identity,
                new ProviderOrientation(ProviderNodeWiring.face(federationFace())), this::laneRequest, domains);
        for (int laneIndex = 0; laneIndex < lanes.size(); laneIndex++) {
            runtime.bindLane(laneIndex, lanes.get(laneIndex).revision);
        }
        runtime.settle();
        ProviderObservationRegistry.attachController(provider, this);
        if (getMainNode().getGrid() != null && !provider.registered()) {
            provider.register();
        }
        federationDomainNodeId = FederationDomainRegistryAccess.nodeId(serverLevel, worldPosition);
        rebuildFederationCache();
        invalidateFederationDomainTopology();
    }

    public static void serverTick(Level level, BlockPos position, BlockState state,
            FederationPatternProviderBlockEntity provider) {
        provider.tickServer();
    }

    private void tickServer() {
        if (runtime == null) {
            return;
        }
        if (pendingRotation) {
            // Settled one tick after the orientation change, after the native node re-exposed its sides.
            runtime.settle();
            pendingRotation = false;
        }
        if (federationDomainDirty) {
            publishFederationDomainTopology();
        }
        if (++maintenanceTicks >= MAINTENANCE_INTERVAL) {
            maintenanceTicks = 0;
            releaseDrainedLanes();
            if (level instanceof ServerLevel serverLevel) {
                bindReturns(serverLevel);
            }
        }
    }

    private void onGridChanged() {
        provider.rebindGrid();
        if (runtime != null && getMainNode().getGrid() != null && !provider.registered()) {
            provider.register();
        }
        invalidateFederationDomainTopology();
    }

    @Override
    public void onMainNodeStateChanged(IGridNodeListener.State reason) {
        provider.onMainNodeStateChanged();
        if (reason == IGridNodeListener.State.GRID_BOOT) {
            invalidateFederationDomainTopology();
        }
    }

    @Override
    public void onChunkUnloaded() {
        unloading = true;
        shutdown(false);
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        shutdown(!unloading);
        super.setRemoved();
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        unloading = false;
    }

    private void shutdown(boolean removedFromWorld) {
        if (level instanceof ServerLevel serverLevel && runtime != null) {
            detachReturns(serverLevel);
        }
        if (removedFromWorld && level instanceof ServerLevel serverLevel) {
            releaseAllClaims(serverLevel);
        }
        if (runtime != null) {
            provider.close();
            runtime = null;
        }
        if (level instanceof ServerLevel serverLevel && federationDomainNodeId != null) {
            FederationDomainRegistryAccess.removeNodeIfPresent(serverLevel, federationDomainNodeId);
            reconcileServices(serverLevel);
        }
        federationCache = null;
        federationDomainDirty = true;
    }

    // ---- PatternProviderLogicHost: the owner logic is the native face of the Provider

    @Override
    public PatternProviderLogic getLogic() {
        return owner;
    }

    @Override
    public EnumSet<Direction> getTargets() {
        return EnumSet.of(federationFace());
    }

    @Override
    public IConfigManager getConfigManager() {
        return configManager;
    }

    @Override
    public int getPriority() {
        return owner.getPriority();
    }

    @Override
    public void setPriority(int priority) {
        owner.setPriority(priority);
        provider.setPriority(priority);
        saveChanges();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(ProcessingRegistration.PROVIDER_ITEM.get());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return ProcessingRegistration.PROVIDER_ITEM.get().getDefaultInstance();
    }

    public MappedPatternProvider mappedProvider() {
        return provider;
    }

    public ProviderIdentity providerIdentity() {
        return identity;
    }

    public Optional<ProviderRuntime> runtime() {
        return Optional.ofNullable(runtime);
    }

    public int laneCount() {
        return lanes.size();
    }

    public NativeProviderLane lane(int laneIndex) {
        return provider.nativeLane(laneIndex);
    }

    /** Lane index serving {@code endpoint}, if the Endpoint is mapped or still draining native work. */
    public Optional<Integer> laneFor(EndpointIdentity endpoint) {
        for (int index = 0; index < lanes.size(); index++) {
            if (endpoint.equals(lanes.get(index).endpoint)) {
                return Optional.of(index);
            }
        }
        return Optional.empty();
    }

    // ---- mapping

    @Override
    public String toggleEndpoint(PatternSlotHandle handle, EndpointTargetBinding endpoint) {
        if (!(level instanceof ServerLevel serverLevel) || runtime == null) {
            return "rejected-provider-offline";
        }
        var slot = handle.slot();
        if (slot < 0 || slot >= PATTERN_SLOTS) {
            return "rejected-invalid-selection";
        }
        var endpointIdentity = endpoint.endpointIdentity();
        var existing = laneFor(endpointIdentity);
        var assigned = new TreeSet<>(provider.lanesForSlot(slot));
        if (existing.isPresent() && assigned.contains(existing.get())) {
            assigned.remove(existing.get());
            if (!provider.replaceMapping(handle, assigned)) {
                return "rejected-stale-slot";
            }
            if (provider.slotsForLane(existing.get()).isEmpty()) {
                lanes.get(existing.get()).releasePending = true;
                releaseDrainedLanes();
            }
            saveChanges();
            return "accepted-" + slot + "-" + handle.generation();
        }
        if (provider.mappingHandle(slot).generation() != handle.generation()) {
            return "rejected-stale-slot";
        }
        int laneIndex;
        if (existing.isPresent()) {
            laneIndex = existing.get();
            lanes.get(laneIndex).releasePending = false;
        } else {
            var claimed = claim(serverLevel, endpoint);
            if (claimed.isEmpty()) {
                return "rejected-" + endpoint.lastClaimResultCode().toLowerCase(java.util.Locale.ROOT);
            }
            laneIndex = allocateLane(endpoint, claimed.orElseThrow());
        }
        assigned.add(laneIndex);
        if (!provider.replaceMapping(handle, assigned)) {
            return "rejected-stale-slot";
        }
        saveChanges();
        return "accepted-" + slot + "-" + handle.generation();
    }

    @Override
    public Set<EndpointIdentity> endpointsForSlot(int slot) {
        var result = new java.util.LinkedHashSet<EndpointIdentity>();
        for (var laneIndex : provider.lanesForSlot(slot)) {
            var endpoint = lanes.get(laneIndex).endpoint;
            if (endpoint != null) {
                result.add(endpoint);
            }
        }
        return result;
    }

    @Override
    public Optional<EndpointIdentity> laneEndpoint(int laneIndex) {
        return laneIndex < 0 || laneIndex >= lanes.size() ? Optional.empty()
                : Optional.ofNullable(lanes.get(laneIndex).endpoint);
    }

    @Override
    public boolean retained(EndpointIdentity endpoint) {
        return laneFor(endpoint).filter(index -> lanes.get(index).releasePending).isPresent();
    }

    @Override
    public String releaseEndpoint(EndpointIdentity endpoint) {
        if (!(level instanceof ServerLevel serverLevel) || runtime == null) {
            return "rejected-provider-offline";
        }
        var laneIndex = laneFor(endpoint);
        if (laneIndex.isEmpty()) {
            return "rejected-not-retained";
        }
        var index = laneIndex.get();
        var binding = lanes.get(index);
        if (!binding.releasePending || !provider.slotsForLane(index).isEmpty()) {
            return "rejected-still-mapped";
        }
        var lane = provider.nativeLane(index);
        if (lane.hasPendingSend()) {
            // Native send remainder is only delivered to this Endpoint; releasing now would strand it in the Lane.
            return "rejected-pending-send";
        }
        if (!serverLevel.isLoaded(binding.position)
                || !(serverLevel.getBlockEntity(binding.position) instanceof EndpointBlockEntity entity)
                || !entity.endpointIdentity().equals(binding.endpoint)) {
            return "rejected-endpoint-unavailable";
        }
        entity.releaseClaim(new EndpointOwnerIdentity(identity), binding.epoch);
        // The result can no longer return to this Lane, so its native lock is reset as on a Lock mode change.
        lane.resetCraftingLock();
        binding.retire();
        runtime.bindLane(index, binding.revision);
        saveChanges();
        return "released-" + index;
    }

    private Optional<ClaimEpoch> claim(ServerLevel serverLevel, EndpointTargetBinding endpoint) {
        var position = endpoint.runtime().position();
        if (!(serverLevel.getBlockEntity(position) instanceof EndpointBlockEntity entity)
                || !entity.endpointIdentity().equals(endpoint.endpointIdentity())) {
            return Optional.empty();
        }
        var result = entity.claim(new ClaimRequest(entity.endpointIdentity(), entity.claimState().epoch(),
                new EndpointOwnerIdentity(identity)));
        if (result instanceof ClaimResult.Rejected || !entity.activateFederated()) {
            return Optional.empty();
        }
        return Optional.of(entity.claimState().epoch());
    }

    private int allocateLane(EndpointTargetBinding endpoint, ClaimEpoch epoch) {
        var position = endpoint.runtime().position();
        for (int index = 0; index < lanes.size(); index++) {
            var binding = lanes.get(index);
            if (binding.endpoint == null && laneIdle(index)) {
                // Reuse a drained Lane; its new revision rejects returns authorized for the previous Endpoint.
                binding.bind(endpoint.endpointIdentity(), position, epoch);
                provider.nativeLane(index).resetCraftingLock();
                runtime.bindLane(index, binding.revision);
                return index;
            }
        }
        var binding = new LaneBinding();
        binding.bind(endpoint.endpointIdentity(), position, epoch);
        lanes.add(binding);
        var laneIndex = provider.addLane(new LaneHost(lanes.size() - 1));
        configManager.applyTo(provider.nativeLane(laneIndex));
        provider.nativeLane(laneIndex).setPriority(owner.getPriority());
        runtime.bindLane(laneIndex, binding.revision);
        return laneIndex;
    }

    private boolean laneIdle(int laneIndex) {
        var lane = provider.nativeLane(laneIndex);
        return !lane.hasPendingSend() && lane.getReturnInv().isEmpty();
    }

    /**
     * Releases the Claim of an unmapped Lane only when it provably has no work in the machine: it never sent a Pattern
     * under its current binding and its native send list and return buffer are empty. Empty native buffers do not prove
     * that the machine finished work already sent, and no native state does, so a Lane that sent work keeps its Claim
     * and return path after unmapping until it is mapped again, explicitly released or its Provider is removed.
     */
    private void releaseDrainedLanes() {
        if (!(level instanceof ServerLevel serverLevel) || runtime == null) {
            return;
        }
        for (int index = 0; index < lanes.size(); index++) {
            var binding = lanes.get(index);
            if (!binding.releasePending || binding.endpoint == null || binding.dispatched || !laneIdle(index)
                    || !provider.slotsForLane(index).isEmpty()) {
                continue;
            }
            if (!serverLevel.isLoaded(binding.position)) {
                continue;
            }
            if (serverLevel.getBlockEntity(binding.position) instanceof EndpointBlockEntity entity
                    && entity.endpointIdentity().equals(binding.endpoint)) {
                entity.releaseClaim(new EndpointOwnerIdentity(identity), binding.epoch);
            }
            binding.retire();
            runtime.bindLane(index, binding.revision);
            saveChanges();
        }
    }

    private void detachReturns(ServerLevel serverLevel) {
        for (int index = 0; index < lanes.size(); index++) {
            var binding = lanes.get(index);
            if (binding.endpoint == null) {
                continue;
            }
            var endpoint = EndpointTargetBinding.findEndpoint(serverLevel, binding.position);
            if (endpoint != null) {
                endpoint.runtime().detachReturn(provider.nativeLane(index));
            }
        }
    }

    /**
     * Restores the return path of each bound Lane whose loaded Endpoint does not return to it, through the same
     * authorization as a native push (Provider, Endpoint identity, Claim epoch, Lane identity and Policy). This covers
     * a reloaded Provider (new Lane objects) and a reloaded Endpoint (new runtime without return context) even while
     * the native lock prevents the push that would otherwise rebind it. Lanes whose Endpoint is unloaded, replaced or
     * unauthorized stay detached and are retried here; the check reads only this Provider's own Lanes.
     */
    private void bindReturns(ServerLevel serverLevel) {
        for (int index = 0; index < lanes.size(); index++) {
            var binding = lanes.get(index);
            if (binding.endpoint == null) {
                continue;
            }
            var endpoint = EndpointTargetBinding.findEndpoint(serverLevel, binding.position);
            var lane = provider.nativeLane(index);
            if (endpoint != null && endpoint.endpointIdentity().equals(binding.endpoint)
                    && !endpoint.runtime().returnOwnedBy(lane)) {
                space.controlnet.ae2federation.ae2.processing.FederationPatternProviderTargetCache.find(lane);
            }
        }
    }

    private void releaseAllClaims(ServerLevel serverLevel) {
        var ownerIdentity = new EndpointOwnerIdentity(identity);
        var unreleased = false;
        for (var binding : lanes) {
            if (binding.endpoint == null) {
                continue;
            }
            if (serverLevel.isLoaded(binding.position)
                    && serverLevel.getBlockEntity(binding.position) instanceof EndpointBlockEntity entity
                    && entity.endpointIdentity().equals(binding.endpoint)) {
                entity.releaseClaim(ownerIdentity, binding.epoch);
            } else {
                unreleased = true;
            }
        }
        if (unreleased) {
            RetiredProviderRegistry.get(serverLevel).retire(identity.id());
        }
    }

    private @Nullable ProviderTargetRequest laneRequest(int laneIndex) {
        if (laneIndex < 0 || laneIndex >= lanes.size()) {
            return null;
        }
        var binding = lanes.get(laneIndex);
        if (binding.endpoint == null) {
            return null;
        }
        return new ProviderTargetRequest(identity, binding.endpoint, binding.epoch, binding.position,
                ENDPOINT_ACCESS_SIDE, true);
    }

    // ---- Domain (Federation Domain) port on the front face

    private void rebuildFederationCache() {
        if (!(level instanceof ServerLevel serverLevel) || runtime == null) {
            federationCache = null;
            return;
        }
        var face = federationFace();
        federationCache = BlockCapabilityCache.create(FederationPortCapability.BLOCK, serverLevel,
                worldPosition.relative(face), face.getOpposite(), () -> !isRemoved(), this::invalidateFederationDomainTopology);
    }

    public void neighborChanged(BlockPos neighborPosition) {
        owner.updateRedstoneState();
        provider.updateRedstoneState();
        if (worldPosition.relative(federationFace()).equals(neighborPosition)) {
            invalidateFederationDomainTopology();
        }
    }

    private void invalidateFederationDomainTopology() {
        federationDomainDirty = true;
        if (level instanceof ServerLevel serverLevel && federationDomainNodeId != null) {
            FederationDomainRegistryAccess.invalidateNodeIfPresent(serverLevel, federationDomainNodeId,
                    FederationDomainInvalidationReason.TOPOLOGY_CHANGED);
        }
    }

    private void publishFederationDomainTopology() {
        if (!(level instanceof ServerLevel serverLevel) || federationDomainNodeId == null) {
            return;
        }
        federationDomainDirty = false;
        var evidence = new java.util.TreeMap<String, FederationDomainPortEvidence>();
        var peer = federationPeer(serverLevel);
        var grid = getMainNode().getGrid();
        if (peer != null && grid != null) {
            var remoteNode = FederationDomainRegistryAccess.nodeId(serverLevel, peer.ownerPosition());
            // Port ids are face names so the Cable's reciprocal evidence (which names this face) matches.
            evidence.put(federationFace().getSerializedName(), new FederationDomainPortEvidence.Federation(
                    new FederationDomainPortId(remoteNode, peer.outwardFace().getSerializedName())));
            evidence.put(NATIVE_PORT, FederationDomainRegistryAccess.nativeEvidence(grid, new FederationDomainPortId(federationDomainNodeId,
                    NATIVE_PORT)));
        }
        FederationDomainRegistryAccess.get(serverLevel).upsertNode(new FederationDomainNodeEvidence(federationDomainNodeId, evidence));
        if (grid != null) {
            StorageMountService.get(serverLevel).observeFederationDomainMembers(List.of(grid));
            CraftingBindingService.get(serverLevel).observeFederationDomainMembers(List.of(grid));
            EnergyBindingService.get(serverLevel).observeFederationDomainMembers(List.of(grid));
        }
    }

    private @Nullable FederationPort federationPeer(ServerLevel serverLevel) {
        var face = federationFace();
        var neighbor = worldPosition.relative(face);
        if (federationCache == null || !serverLevel.isLoaded(neighbor)) {
            return null;
        }
        var port = federationCache.getCapability();
        var own = new FederationPort(worldPosition, face);
        return port != null && own.connectsTo(port) ? port : null;
    }

    private static void reconcileServices(ServerLevel serverLevel) {
        StorageMountService.reconcileIfPresent(serverLevel);
        CraftingBindingService.reconcileIfPresent(serverLevel);
        EnergyBindingService.reconcileIfPresent(serverLevel);
    }

    // ---- drops and persistence

    @Override
    public void addAdditionalDrops(Level level, BlockPos pos, List<ItemStack> drops) {
        super.addAdditionalDrops(level, pos, drops);
        // The owner logic is the single drop owner of the Pattern slots; Lanes own no Pattern slots, only their
        // native send remainder and return buffer, which AE2's own addDrops emits.
        owner.addDrops(drops);
        provider.addDrops(drops);
    }

    @Override
    public void clearContent() {
        super.clearContent();
        owner.clearContent();
        provider.nativeLanes().forEach(PatternProviderLogic::clearContent);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt(SCHEMA_TAG, SCHEMA);
        var identityTag = new CompoundTag();
        ProviderIdentityCodec.save(identityTag, identity);
        tag.put(IDENTITY_TAG, identityTag);
        var ownerTag = new CompoundTag();
        owner.writeToNBT(ownerTag, registries);
        tag.put(OWNER_TAG, ownerTag);
        var laneList = new ListTag();
        lanes.forEach(binding -> laneList.add(binding.save()));
        tag.put(LANES_TAG, laneList);
        var providerTag = new CompoundTag();
        provider.writeToNBT(providerTag, registries);
        tag.put(PROVIDER_TAG, providerTag);
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        if (!tag.contains(SCHEMA_TAG, Tag.TAG_INT)) {
            return;
        }
        if (tag.getInt(SCHEMA_TAG) != SCHEMA) {
            throw new IllegalArgumentException("Unsupported Federation Pattern Provider schema");
        }
        identity = ProviderIdentityCodec.load(tag.getCompound(IDENTITY_TAG));
        owner.readFromNBT(tag.getCompound(OWNER_TAG), registries);
        var laneList = tag.getList(LANES_TAG, Tag.TAG_COMPOUND);
        if (lanes.isEmpty()) {
            for (var raw : laneList) {
                lanes.add(LaneBinding.load((CompoundTag) raw));
                provider.addLane(new LaneHost(lanes.size() - 1));
            }
        } else if (lanes.size() != laneList.size()) {
            throw new IllegalStateException("Federation Pattern Provider Lanes cannot be reloaded in place");
        }
        provider.readFromNBT(tag.getCompound(PROVIDER_TAG), registries);
    }

    // ---- nested types

    /** Native host of one Lane: same block entity, Federation face as its only target side. */
    private final class LaneHost implements PatternProviderLogicHost, NativeLaneDispatchListener {
        private final int laneIndex;

        private LaneHost(int laneIndex) {
            this.laneIndex = laneIndex;
        }

        @Override
        public PatternProviderLogic getLogic() {
            return provider.nativeLane(laneIndex);
        }

        @Override
        public void onLaneDispatched() {
            var binding = lanes.get(laneIndex);
            if (binding.endpoint != null && !binding.dispatched) {
                binding.dispatched = true;
                FederationPatternProviderBlockEntity.this.saveChanges();
            }
        }

        @Override
        public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity() {
            return FederationPatternProviderBlockEntity.this;
        }

        @Override
        public EnumSet<Direction> getTargets() {
            return EnumSet.of(federationFace());
        }

        @Override
        public void saveChanges() {
            FederationPatternProviderBlockEntity.this.saveChanges();
        }

        @Override
        public AEItemKey getTerminalIcon() {
            return FederationPatternProviderBlockEntity.this.getTerminalIcon();
        }

        @Override
        public ItemStack getMainMenuIcon() {
            return FederationPatternProviderBlockEntity.this.getMainMenuIcon();
        }
    }

    private static final class LaneBinding {
        private @Nullable EndpointIdentity endpoint;
        private BlockPos position = BlockPos.ZERO;
        private ClaimEpoch epoch = ClaimEpoch.NONE;
        private long revision;
        private boolean releasePending;
        /** Set once the Lane sent a Pattern under this binding; work may then be inside the machine. */
        private boolean dispatched;

        private void bind(EndpointIdentity endpoint, BlockPos position, ClaimEpoch epoch) {
            this.endpoint = endpoint;
            this.position = position.immutable();
            this.epoch = epoch;
            revision = Math.incrementExact(revision);
            releasePending = false;
            dispatched = false;
        }

        private void retire() {
            endpoint = null;
            epoch = ClaimEpoch.NONE;
            revision = Math.incrementExact(revision);
            releasePending = false;
            dispatched = false;
        }

        private CompoundTag save() {
            var tag = new CompoundTag();
            tag.putLong("revision", revision);
            tag.putBoolean("releasePending", releasePending);
            tag.putBoolean("dispatched", dispatched);
            if (endpoint != null) {
                tag.putUUID("endpoint", endpoint.id().value());
                tag.putLong("endpointEpoch", endpoint.instanceEpoch().value());
                tag.put("position", NbtUtils.writeBlockPos(position));
                tag.putLong("claimEpoch", epoch.value());
            }
            return tag;
        }

        private static LaneBinding load(CompoundTag tag) {
            var binding = new LaneBinding();
            binding.revision = Math.max(1, tag.getLong("revision"));
            binding.releasePending = tag.getBoolean("releasePending");
            if (tag.hasUUID("endpoint")) {
                binding.endpoint = new EndpointIdentity(new EndpointId(tag.getUUID("endpoint")),
                        new EndpointInstanceEpoch(tag.getLong("endpointEpoch")));
                binding.position = NbtUtils.readBlockPos(tag, "position").orElse(BlockPos.ZERO);
                binding.epoch = new ClaimEpoch(tag.getLong("claimEpoch"));
                // Saves from before this flag existed cannot prove the Lane never sent work, so they keep the Claim.
                binding.dispatched = !tag.contains("dispatched") || tag.getBoolean("dispatched");
            }
            return binding;
        }
    }

    /**
     * Config manager exposed to AE2's menu and memory card: it reads and writes the owner's native settings and writes
     * every change through to each Lane's own native config manager, so each Lane applies Blocking and Lock Crafting
     * with native semantics (including the native lock reset on a Lock mode change).
     */
    static final class LaneConfigManager implements IConfigManager {
        private final IConfigManager ownerSettings;
        private final java.util.function.Supplier<List<NativeProviderLane>> lanes;

        LaneConfigManager(IConfigManager ownerSettings, java.util.function.Supplier<List<NativeProviderLane>> lanes) {
            this.ownerSettings = ownerSettings;
            this.lanes = lanes;
        }

        void applyTo(NativeProviderLane lane) {
            for (var setting : ownerSettings.getSettings()) {
                copy(setting, lane.getConfigManager());
            }
        }

        @Override
        public Set<Setting<?>> getSettings() {
            return ownerSettings.getSettings();
        }

        @Override
        public <T extends Enum<T>> T getSetting(Setting<T> setting) {
            return ownerSettings.getSetting(setting);
        }

        @Override
        public <T extends Enum<T>> void putSetting(Setting<T> setting, T newValue) {
            ownerSettings.putSetting(setting, newValue);
            for (var lane : lanes.get()) {
                lane.getConfigManager().putSetting(setting, newValue);
            }
        }

        @Override
        public void writeToNBT(CompoundTag destination, HolderLookup.Provider registries) {
            ownerSettings.writeToNBT(destination, registries);
        }

        @Override
        public boolean readFromNBT(CompoundTag source, HolderLookup.Provider registries) {
            var changed = ownerSettings.readFromNBT(source, registries);
            lanes.get().forEach(this::applyTo);
            return changed;
        }

        @Override
        public boolean importSettings(Map<String, String> settings) {
            var changed = ownerSettings.importSettings(settings);
            lanes.get().forEach(this::applyTo);
            return changed;
        }

        @Override
        public Map<String, String> exportSettings() {
            return ownerSettings.exportSettings();
        }

        @SuppressWarnings({ "unchecked", "rawtypes" })
        private void copy(Setting setting, IConfigManager target) {
            var value = ownerSettings.getSetting(setting);
            if (target.hasSetting(setting) && target.getSetting(setting) != value) {
                target.putSetting(setting, value);
            }
        }
    }
}
