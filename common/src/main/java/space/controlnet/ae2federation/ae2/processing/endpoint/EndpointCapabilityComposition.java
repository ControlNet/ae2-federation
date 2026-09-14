package space.controlnet.ae2federation.ae2.processing.endpoint;

import appeng.api.AECapabilities;
import appeng.api.behaviors.GenericInternalInventory;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.externalstorage.GenericStackFluidStorage;
import appeng.helpers.externalstorage.GenericStackItemStorage;
import appeng.helpers.patternprovider.PatternProviderReturnInventory;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.items.IItemHandler;

public final class EndpointCapabilityComposition {
    private final ServerLevel level;
    private final BlockPos endpointPosition;
    private final IGridNode subnetNode;
    private final Direction federationFace;
    private final EnumSet<Direction> subnetFaces;
    private EndpointMode mode = EndpointMode.LOCAL;
    private NativeLocalProvider localProvider;
    private long generation;

    public EndpointCapabilityComposition(ServerLevel level, BlockPos endpointPosition, IGridNode subnetNode,
            Direction federationFace) {
        this.level = level;
        this.endpointPosition = endpointPosition.immutable();
        this.subnetNode = subnetNode;
        this.federationFace = federationFace;
        subnetFaces = EnumSet.complementOf(EnumSet.of(federationFace));
    }

    public EndpointMode mode() {
        return mode;
    }

    public IGridNode subnetNode() {
        return subnetNode;
    }

    public EnumSet<Direction> subnetFaces() {
        return EnumSet.copyOf(subnetFaces);
    }

    public boolean claimLocal(List<NativeLocalProvider> candidates) {
        if (mode != EndpointMode.LOCAL || localProvider != null || candidates.size() != 1) {
            return false;
        }
        var candidate = candidates.getFirst();
        var targetStorage = level.getCapability(AECapabilities.ME_STORAGE, endpointPosition,
                candidate.targetSide().getOpposite());
        if (!candidate.providerPosition().relative(candidate.targetSide()).equals(endpointPosition)
                || !(level.getBlockEntity(candidate.providerPosition()) instanceof PatternProviderBlockEntity provider)
                || provider.getLogic() != candidate.logic()
                || GridHelper.getExposedNode(level, endpointPosition, candidate.targetSide().getOpposite()) != subnetNode
                || targetStorage != subnetNode.getGrid().getStorageService().getInventory()
                || candidate.sourceNode().getGrid() == subnetNode.getGrid()
                || candidate.sourceNode().getInWorldConnections().containsKey(candidate.targetSide())) {
            return false;
        }
        localProvider = candidate;
        generation++;
        return true;
    }

    public void setMode(EndpointMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            localProvider = null;
            generation++;
        }
    }

    public EndpointCallContext openLocalInput() {
        return new EndpointCallContext(EndpointCallContext.Purpose.LOCAL_INPUT, generation);
    }

    public EndpointCallContext openFederatedInput() {
        return new EndpointCallContext(EndpointCallContext.Purpose.FEDERATED_INPUT, generation);
    }

    public EndpointCallContext openReturnInsert() {
        return new EndpointCallContext(EndpointCallContext.Purpose.RETURN_INSERT, generation);
    }

    public Optional<MEStorage> targetStorage(Direction face, EndpointCallContext context) {
        var faceStorage = faceStorage(face);
        if (faceStorage.isEmpty() || !valid(face, context)
                || context.purpose() == EndpointCallContext.Purpose.RETURN_INSERT) {
            return Optional.empty();
        }
        if (mode == EndpointMode.LOCAL
                && context.purpose() == EndpointCallContext.Purpose.LOCAL_INPUT
                && localProvider != null) {
            return faceStorage;
        }
        if (mode == EndpointMode.FEDERATED
                && context.purpose() == EndpointCallContext.Purpose.FEDERATED_INPUT) {
            return faceStorage;
        }
        return Optional.empty();
    }

    public Optional<GenericInternalInventory> returnInventory(Direction face, EndpointCallContext context) {
        if (faceStorage(face).isEmpty() || !valid(face, context)
                || context.purpose() != EndpointCallContext.Purpose.RETURN_INSERT
                || mode != EndpointMode.LOCAL || localProvider == null) {
            return Optional.empty();
        }
        return Optional.of(localProvider.returnInventory());
    }

    public Optional<IItemHandler> itemReturnCapability(Direction face, EndpointCallContext context) {
        return returnInventory(face, context).map(GenericStackItemStorage::new);
    }

    public Optional<IFluidHandler> fluidReturnCapability(Direction face, EndpointCallContext context) {
        return returnInventory(face, context).map(GenericStackFluidStorage::new);
    }

    public Optional<PatternProviderReturnInventory> nativeReturnInventory() {
        if (localProvider == null) {
            return Optional.empty();
        }
        return Optional.of(localProvider.logic().getReturnInv());
    }

    public Direction federationFace() {
        return federationFace;
    }

    public Optional<IGridNode> faceNode(Direction face) {
        if (!subnetFaces.contains(face)) {
            return Optional.empty();
        }
        var exposed = GridHelper.getExposedNode(level, endpointPosition, face);
        return exposed == subnetNode ? Optional.of(exposed) : Optional.empty();
    }

    public Optional<MEStorage> faceStorage(Direction face) {
        if (faceNode(face).isEmpty()) {
            return Optional.empty();
        }
        var storage = level.getCapability(AECapabilities.ME_STORAGE, endpointPosition, face);
        return storage == subnetNode.getGrid().getStorageService().getInventory()
                ? Optional.of(storage)
                : Optional.empty();
    }

    private boolean valid(Direction face, EndpointCallContext context) {
        return subnetFaces.contains(face) && context.generation() == generation;
    }
}
