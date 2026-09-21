package space.controlnet.ae2federation.processing.endpoint;

import appeng.api.networking.IGridNode;
import appeng.api.storage.MEStorage;
import appeng.helpers.patternprovider.PatternProviderLogic;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;
import space.controlnet.ae2federation.ae2.processing.endpoint.EndpointCapabilityComposition;
import space.controlnet.ae2federation.ae2.processing.endpoint.EndpointMode;
import space.controlnet.ae2federation.ae2.processing.endpoint.NativeLocalProvider;
import space.controlnet.ae2federation.processing.claim.ClaimEpoch;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.claim.EndpointClaimAuthority;
import space.controlnet.ae2federation.processing.provider.AuthorizedNativeTarget;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;

public final class EndpointRuntime {
    private final ServerLevel level;
    private final BlockPos position;
    private final Direction federationFace;
    private final EndpointClaimAuthority claims;
    private final EndpointCapabilityComposition composition;
    private EndpointModeGeneration mode;
    private EndpointItemReturnContext itemReturn;
    private EndpointFluidReturnContext fluidReturn;

    public EndpointRuntime(ServerLevel level, BlockPos position, IGridNode subnetNode, Direction federationFace,
            EndpointClaimAuthority claims) {
        this(level, position, subnetNode, federationFace, claims, 0);
    }

    public EndpointRuntime(ServerLevel level, BlockPos position, IGridNode subnetNode, Direction federationFace,
            EndpointClaimAuthority claims, long generation) {
        this.level = Objects.requireNonNull(level);
        this.position = Objects.requireNonNull(position).immutable();
        this.federationFace = Objects.requireNonNull(federationFace);
        this.claims = Objects.requireNonNull(claims);
        composition = new EndpointCapabilityComposition(level, position, subnetNode, federationFace, face -> {
            if (level.getBlockEntity(this.position) instanceof EndpointBlockEntity endpoint
                    && endpoint.getMainNode().getNode() == subnetNode && subnetNode.getGrid() != null) {
                return subnetNode.getGrid().getStorageService().getInventory();
            }
            return level.getCapability(appeng.api.AECapabilities.ME_STORAGE, this.position, face);
        }, generation);
    }

    public boolean activateLocal(List<NativeLocalProvider> advisoryCandidates) {
        Objects.requireNonNull(advisoryCandidates);
        if (composition.mode() != EndpointMode.LOCAL) {
            composition.setMode(EndpointMode.LOCAL);
        } else {
            composition.resetLocal();
        }
        clearCurrentReturns();
        var accepted = advisoryCandidates.isEmpty()
                ? composition.discoverLocal()
                : composition.claimLocal(advisoryCandidates);
        if (!accepted) {
            mode = null;
            invalidateCapabilities();
            return false;
        }
        var provider = composition.localProvider().orElseThrow();
        var local = new EndpointModeGeneration.Local(composition.generation(), provider);
        installReturnOwner(new EndpointReturnOwner(local, provider.logic(), provider.logic().getReturnInv(),
                Optional.empty()));
        mode = local;
        invalidateCapabilities();
        return true;
    }

    public boolean activateFederated() {
        if (!(claims.state() instanceof ClaimState.Owned owned)) {
            return false;
        }
        if (composition.mode() != EndpointMode.FEDERATED) {
            composition.setMode(EndpointMode.FEDERATED);
        } else {
            composition.resetFederated();
        }
        clearCurrentReturns();
        mode = new EndpointModeGeneration.Federated(composition.generation(), claims.endpoint(), owned.epoch(),
                owned.ownerIdentity());
        invalidateCapabilities();
        return true;
    }

    public Optional<EndpointModeGeneration.Federated> federatedMode(ProviderIdentity provider, ClaimEpoch claimEpoch) {
        if (mode instanceof EndpointModeGeneration.Federated federated
                && federated.endpoint().equals(claims.endpoint())
                && federated.claimEpoch().equals(claimEpoch)
                && federated.owner().provider().equals(provider)
                && claims.state() instanceof ClaimState.Owned owned
                && owned.epoch().equals(claimEpoch)
                && owned.ownerIdentity().provider().equals(provider)) {
            return Optional.of(federated);
        }
        return Optional.empty();
    }

    public boolean bindFederatedReturn(AuthorizedNativeTarget target) {
        Objects.requireNonNull(target);
        var logic = target.provenance().logic();
        if (!target.level().equals(level) || !target.position().equals(position)
                || !target.endpoint().equals(claims.endpoint())
                || !(mode instanceof EndpointModeGeneration.Federated federated)
                || !target.mode().equals(federated)
                || federatedMode(target.provider(), target.claimEpoch()).isEmpty()) {
            return false;
        }
        var lane = target.laneIdentity();
        if (itemReturn != null) {
            return itemReturn.owner().lane().filter(lane::equals).isPresent()
                    && itemReturn.owner().logic() == logic
                    && itemReturn.owner().mode().equals(federated);
        }
        installReturnOwner(new EndpointReturnOwner(federated, logic, logic.getReturnInv(), Optional.of(lane)));
        invalidateCapabilities();
        return true;
    }

    public Optional<MEStorage> inputStorage(Direction face) {
        if (mode instanceof EndpointModeGeneration.Local) {
            return composition.targetStorage(face, composition.openLocalInput());
        }
        if (mode instanceof EndpointModeGeneration.Federated) {
            return composition.targetStorage(face, composition.openFederatedInput());
        }
        return Optional.empty();
    }

    public Optional<IGridNode> node(Direction face) {
        return composition.faceNode(face);
    }

    public Optional<IItemHandler> itemReturn(Direction face) {
        return logisticsFaces().contains(face) && itemReturn != null
                ? Optional.of(itemReturn.capability())
                : Optional.empty();
    }

    public Optional<IFluidHandler> fluidReturn(Direction face) {
        return logisticsFaces().contains(face) && fluidReturn != null
                ? Optional.of(fluidReturn.capability())
                : Optional.empty();
    }

    public Optional<EndpointModeGeneration> mode() {
        return Optional.ofNullable(mode);
    }

    public EndpointMode configuredMode() {
        return composition.mode();
    }

    public long generation() {
        return composition.generation();
    }

    public Optional<EndpointItemReturnContext> itemReturnContext() {
        return Optional.ofNullable(itemReturn);
    }

    public Optional<EndpointFluidReturnContext> fluidReturnContext() {
        return Optional.ofNullable(fluidReturn);
    }

    public EnumSet<Direction> logisticsFaces() {
        return EnumSet.complementOf(EnumSet.of(federationFace));
    }

    public Direction federationFace() {
        return federationFace;
    }

    private void installReturnOwner(EndpointReturnOwner owner) {
        itemReturn = new EndpointItemReturnContext(level, owner);
        fluidReturn = new EndpointFluidReturnContext(level, owner);
    }

    private void clearCurrentReturns() {
        itemReturn = null;
        fluidReturn = null;
    }

    private void invalidateCapabilities() {
        level.invalidateCapabilities(position);
    }
}
