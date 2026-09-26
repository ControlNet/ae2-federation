package space.controlnet.ae2federation.processing.endpoint;

import appeng.api.orientation.BlockOrientation;
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
import space.controlnet.ae2federation.ae2.processing.endpoint.EndpointMode;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.ClaimRequest;
import space.controlnet.ae2federation.processing.claim.EndpointOwnerIdentity;
import space.controlnet.ae2federation.processing.claim.ClaimResult;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.claim.ClaimStateCodec;
import space.controlnet.ae2federation.processing.claim.EndpointClaimAuthority;
import space.controlnet.ae2federation.processing.claim.EndpointIdentity;

public final class EndpointBlockEntity extends AENetworkedBlockEntity {
    public static final Direction FEDERATION_FACE = Direction.EAST;
    private static final String CLAIM_TAG = "endpointClaim";
    private static final String MODE_TAG = "endpointMode";
    private static final String GENERATION_TAG = "endpointModeGeneration";
    private EndpointClaimAuthority claims = new EndpointClaimAuthority(EndpointIdentity.create());
    private EndpointMode configuredMode = EndpointMode.LOCAL;
    private long generation;
    private @Nullable EndpointTargetBinding binding;

    public EndpointBlockEntity(BlockPos position, BlockState state) {
        super(space.controlnet.ae2federation.processing.ProcessingRegistration.ENDPOINT_BLOCK_ENTITY.get(),
                position, state);
    }

    @Override
    public Set<Direction> getGridConnectableSides(BlockOrientation orientation) {
        return EnumSet.complementOf(EnumSet.of(FEDERATION_FACE));
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
        binding = new EndpointTargetBinding(serverLevel, worldPosition, FEDERATION_FACE, claims, node, configuredMode,
                generation);
        snapshotRuntime();
        setChanged();
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
        super.onChunkUnloaded();
    }

    @Override
    public void setRemoved() {
        closeBinding();
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
