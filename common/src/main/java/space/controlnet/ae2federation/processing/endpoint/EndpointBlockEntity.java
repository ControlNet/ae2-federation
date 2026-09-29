package space.controlnet.ae2federation.processing.endpoint;

import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.RelativeSide;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import java.util.EnumSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.domain.FederationDomainInvalidationReason;
import space.controlnet.ae2federation.domain.FederationDomainNodeEvidence;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainPortEvidence;
import space.controlnet.ae2federation.domain.FederationDomainPortId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.port.CableFacePort;
import space.controlnet.ae2federation.domain.port.FederationPort;
import space.controlnet.ae2federation.energy.EnergyBindingService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.ae2.processing.endpoint.EndpointMode;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.ClaimRequest;
import space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity;
import space.controlnet.ae2federation.processing.claim.ClaimResult;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.claim.ClaimStateCodec;
import space.controlnet.ae2federation.processing.claim.EndpointClaimAuthority;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;

/**
 * The Endpoint's FRONT is its Federation face: it joins a Federation Domain through a Federation Cable, a Router or a
 * Federation Pattern Provider's front, and joins no ME Grid. The other five faces carry its subnet, which stays outside
 * the domain: every Federation Provider of the domain may send processing work into it.
 */
public final class EndpointBlockEntity extends AENetworkedBlockEntity {
    private static final String CLAIM_TAG = "endpointClaim";
    private static final String MODE_TAG = "endpointMode";
    private static final String GENERATION_TAG = "endpointModeGeneration";
    private EndpointClaimAuthority claims = new EndpointClaimAuthority(EndpointIdentity.create());
    private EndpointMode configuredMode = EndpointMode.LOCAL;
    private long generation;
    private @Nullable EndpointTargetBinding binding;
    private @Nullable FederationDomainNodeId federationDomainNodeId;
    private @Nullable CableFacePort federationPort;
    private boolean federationDomainDirty = true;

    public EndpointBlockEntity(BlockPos position, BlockState state) {
        super(space.controlnet.ae2federation.processing.ProcessingRegistration.ENDPOINT_BLOCK_ENTITY.get(),
                position, state);
    }

    public Direction federationFace() {
        return getOrientation().getSide(RelativeSide.FRONT);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.complementOf(EnumSet.of(orientation.getSide(RelativeSide.FRONT)));
    }

    /** The Federation port capability exists only on the front face. */
    public @Nullable FederationPort federationPort(@Nullable Direction face) {
        return face != null && face == federationFace() ? new FederationPort(worldPosition, face) : null;
    }

    @Override
    protected void onOrientationChanged(BlockOrientation orientation) {
        super.onOrientationChanged(orientation);
        if (level instanceof ServerLevel serverLevel && binding != null) {
            // The binding and the Federation port are both built for one face; rebuild them for the new front.
            closeBinding();
            bind(serverLevel);
            openFederationPort(serverLevel);
        }
        if (level != null) {
            level.invalidateCapabilities(worldPosition);
        }
    }

    public static void serverTick(net.minecraft.world.level.Level level, BlockPos position, BlockState state,
            EndpointBlockEntity endpoint) {
        var changed = endpoint.federationPort != null && endpoint.federationPort.tick();
        if (changed || endpoint.federationDomainDirty) {
            endpoint.publishFederationDomainTopology();
        }
    }

    public void neighborChanged(BlockPos neighborPosition) {
        if (federationPort != null && worldPosition.relative(federationFace()).equals(neighborPosition)) {
            federationPort.invalidate();
        }
        refreshLocal();
    }

    @Override
    public void onReady() {
        super.onReady();
        if (!(level instanceof ServerLevel serverLevel) || binding != null) {
            return;
        }
        var node = getMainNode().getNode();
        if (node == null) {
            return;
        }
        claims.withOnline(true);
        if (claims.state() instanceof ClaimState.Owned owned && space.controlnet.ae2federation.processing.provider
                .RetiredProviderRegistry.get(serverLevel).isRetired(owned.ownerIdentity().provider().id())) {
            // The owning Provider was removed while this Endpoint was unloaded.
            claims.release(owned.ownerIdentity(), owned.epoch());
        }
        bind(serverLevel);
        federationDomainNodeId = FederationDomainRegistryAccess.nodeId(serverLevel, worldPosition);
        openFederationPort(serverLevel);
    }

    private void bind(ServerLevel serverLevel) {
        var node = getMainNode().getNode();
        if (node == null) {
            return;
        }
        claims.withOnline(true);
        binding = new EndpointTargetBinding(serverLevel, worldPosition, federationFace(), claims, node, configuredMode,
                generation);
        snapshotRuntime();
        setChanged();
    }

    // ---- Federation Domain node on the front face

    private void openFederationPort(ServerLevel serverLevel) {
        if (federationPort != null) {
            federationPort.destroy();
        }
        federationPort = new CableFacePort(worldPosition, federationFace(), this::invalidateFederationDomainTopology);
        federationPort.initialize(serverLevel);
    }

    private void invalidateFederationDomainTopology() {
        federationDomainDirty = true;
        if (level instanceof ServerLevel serverLevel && federationDomainNodeId != null) {
            FederationDomainRegistryAccess.invalidateNodeIfPresent(serverLevel, federationDomainNodeId,
                    FederationDomainInvalidationReason.TOPOLOGY_CHANGED);
            StorageMountService.topologyChangedIfPresent(serverLevel);
            CraftingBindingService.topologyChangedIfPresent(serverLevel);
            EnergyBindingService.reconcileIfPresent(serverLevel);
        }
    }

    /** Only the Federation link is published: the subnet on the other faces is no domain member. */
    private void publishFederationDomainTopology() {
        if (!(level instanceof ServerLevel serverLevel) || federationDomainNodeId == null) {
            return;
        }
        var evidence = new java.util.TreeMap<String, FederationDomainPortEvidence>();
        var peer = federationPort == null ? null : federationPort.peer();
        if (peer != null) {
            var remoteNode = FederationDomainRegistryAccess.nodeId(serverLevel, peer.ownerPosition());
            evidence.put(federationFace().getSerializedName(), new FederationDomainPortEvidence.Federation(
                    new FederationDomainPortId(remoteNode, peer.outwardFace().getSerializedName())));
        }
        FederationDomainRegistryAccess.get(serverLevel).upsertNode(new FederationDomainNodeEvidence(federationDomainNodeId,
                evidence));
        StorageMountService.topologyChangedIfPresent(serverLevel);
        CraftingBindingService.topologyChangedIfPresent(serverLevel);
        EnergyBindingService.reconcileIfPresent(serverLevel);
        federationDomainDirty = false;
    }

    private void closeFederationPort() {
        if (federationPort != null) {
            federationPort.destroy();
            federationPort = null;
        }
        if (level instanceof ServerLevel serverLevel && federationDomainNodeId != null) {
            FederationDomainRegistryAccess.removeNodeIfPresent(serverLevel, federationDomainNodeId);
            StorageMountService.topologyChangedIfPresent(serverLevel);
            CraftingBindingService.topologyChangedIfPresent(serverLevel);
            EnergyBindingService.reconcileIfPresent(serverLevel);
        }
        federationDomainDirty = true;
    }

    @Override
    public void loadTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadTag(tag, registries);
        if (!tag.contains(CLAIM_TAG, Tag.TAG_COMPOUND)) {
            return;
        }
        if (!tag.contains(MODE_TAG, Tag.TAG_STRING) || !tag.contains(GENERATION_TAG, Tag.TAG_LONG)
                || tag.getLong(GENERATION_TAG) < 0) {
            throw new IllegalArgumentException("Malformed Endpoint persistence");
        }
        var restored = ClaimStateCodec.load(tag.getCompound(CLAIM_TAG));
        claims = new EndpointClaimAuthority(restored.key().endpoint(), restored);
        configuredMode = EndpointMode.valueOf(tag.getString(MODE_TAG));
        generation = tag.getLong(GENERATION_TAG);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        snapshotRuntime();
        super.saveAdditional(tag, registries);
        tag.put(CLAIM_TAG, ClaimStateCodec.save(claims.state()));
        tag.putString(MODE_TAG, configuredMode.name());
        tag.putLong(GENERATION_TAG, generation);
    }

    public EndpointIdentity endpointIdentity() {
        return claims.endpoint();
    }

    public ClaimState claimState() {
        return claims.state();
    }

    public ClaimResult claim(ClaimRequest request) {
        var result = claims.compareAndSet(request);
        if (!(result instanceof ClaimResult.Rejected)) {
            setChanged();
        }
        return result;
    }

    /** Releases this Endpoint's Claim if {@code owner} still holds it at {@code epoch}; closes Federated input. */
    public boolean releaseClaim(EndpointOwnerIdentity owner, ClaimEpoch epoch) {
        if (!claims.release(owner, epoch)) {
            return false;
        }
        if (binding != null) {
            binding.runtime().closeFederated();
        }
        snapshotRuntime();
        setChanged();
        return true;
    }

    public boolean activateFederated() {
        if (!(claims.state() instanceof ClaimState.Owned)) {
            return false;
        }
        configuredMode = EndpointMode.FEDERATED;
        var activated = binding == null || binding.activateFederated();
        snapshotRuntime();
        setChanged();
        return activated;
    }

    public boolean activateLocal() {
        configuredMode = EndpointMode.LOCAL;
        var activated = binding != null && binding.activateLocal(java.util.List.of());
        snapshotRuntime();
        setChanged();
        return activated;
    }

    public void refreshLocal() {
        if (configuredMode == EndpointMode.LOCAL && binding != null) {
            binding.refreshLocal();
            snapshotRuntime();
            setChanged();
        }
    }

    public @Nullable EndpointTargetBinding binding() {
        return binding;
    }

    @Override
    public void onChunkUnloaded() {
        closeBinding();
        closeFederationPort();
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        closeBinding();
        closeFederationPort();
        super.setRemoved();
    }

    private void closeBinding() {
        if (binding != null) {
            snapshotRuntime();
            binding.close();
            binding = null;
        }
        claims.withOnline(false);
    }

    private void snapshotRuntime() {
        if (binding != null) {
            configuredMode = binding.runtime().configuredMode();
            generation = binding.runtime().generation();
        }
    }
}
