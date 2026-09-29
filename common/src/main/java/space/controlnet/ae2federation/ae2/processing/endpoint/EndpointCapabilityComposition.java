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
import java.util.function.Function;
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
    private final Function<Direction, MEStorage> storageLookup;
    private EndpointMode mode = EndpointMode.LOCAL;
    private NativeLocalProvider localProvider;
    private long generation;

    public EndpointCapabilityComposition(ServerLevel level, BlockPos endpointPosition, IGridNode subnetNode,
            Direction federationFace) {
        this(level, endpointPosition, subnetNode, federationFace,
                face -> level.getCapability(AECapabilities.ME_STORAGE, endpointPosition, face));
    }

    public EndpointCapabilityComposition(ServerLevel level, BlockPos endpointPosition, IGridNode subnetNode,
            Direction federationFace, Function<Direction, MEStorage> storageLookup) {
        this(level, endpointPosition, subnetNode, federationFace, storageLookup, 0);
    }

    public EndpointCapabilityComposition(ServerLevel level, BlockPos endpointPosition, IGridNode subnetNode,
            Direction federationFace, Function<Direction, MEStorage> storageLookup, long generation) {
        if (generation < 0) {
            throw new IllegalArgumentException("Endpoint mode generation must be non-negative");
        }
        this.level = level;
        this.endpointPosition = endpointPosition.immutable();
        this.subnetNode = subnetNode;
        this.federationFace = federationFace;
        this.storageLookup = storageLookup;
        this.generation = generation;
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
        var discovered = adjacentProviders();
        if (candidates.size() != 1 || discovered.size() != 1
                || candidates.getFirst().logic() != discovered.getFirst().logic()) {
            return false;
        }
        return claimDiscovered(discovered.getFirst());
    }

    public boolean discoverLocal() {
        var discovered = adjacentProviders();
        return discovered.size() == 1 && claimDiscovered(discovered.getFirst());
    }

    private boolean claimDiscovered(NativeLocalProvider candidate) {
        if (mode != EndpointMode.LOCAL || localProvider != null) {
            return false;
        }
        if (candidate.targetSide().getOpposite() != federationFace
                || !candidate.providerPosition().relative(candidate.targetSide()).equals(endpointPosition)
                || !(level.getBlockEntity(candidate.providerPosition()) instanceof PatternProviderBlockEntity provider)
                || provider.getLogic() != candidate.logic()
                || !provider.getTargets().contains(candidate.targetSide())
                || localInputStorage().isEmpty()
                || candidate.sourceNode().getGrid() == subnetNode.getGrid()
                || candidate.sourceNode().getInWorldConnections().containsKey(candidate.targetSide())) {
            return false;
        }
        localProvider = candidate;
        generation++;
        return true;
    }

    /**
     * The native AE2 Pattern Provider on the Federation face that pushes into it. It belongs to another network, so it
     * sits on the face that carries no subnet connection; a Provider on a subnet face would be part of the subnet.
     */
    private List<NativeLocalProvider> adjacentProviders() {
        var providerPosition = endpointPosition.relative(federationFace);
        if (!level.isLoaded(providerPosition)
                || !(level.getBlockEntity(providerPosition) instanceof PatternProviderBlockEntity provider)) {
            return List.of();
        }
        var targetSide = federationFace.getOpposite();
        var sourceNode = provider.getMainNode().getNode();
        return sourceNode != null && provider.getTargets().contains(targetSide)
                ? List.of(new NativeLocalProvider(provider.getLogic(), sourceNode, providerPosition, targetSide,
                        provider.getLogic().getReturnInv()))
                : List.of();
    }

    public void setMode(EndpointMode mode) {
        if (this.mode != mode) {
            this.mode = mode;
            localProvider = null;
            generation++;
        }
    }

    public void resetLocal() {
        if (mode == EndpointMode.LOCAL && localProvider != null) {
            localProvider = null;
            generation++;
        }
    }

    public void resetFederated() {
        if (mode == EndpointMode.FEDERATED) {
            generation++;
        }
    }

    public EndpointCallContext openLocalInput() {
        return new EndpointCallContext(EndpointCallContext.Purpose.LOCAL_INPUT, generation);
    }

    public EndpointCallContext openFederatedInput() {
        return new EndpointCallContext(EndpointCallContext.Purpose.FEDERATED_INPUT, generation);
    }

    public EndpointCallContext openItemReturn() {
        return new EndpointCallContext(EndpointCallContext.Purpose.ITEM_RETURN, generation);
    }

    public EndpointCallContext openFluidReturn() {
        return new EndpointCallContext(EndpointCallContext.Purpose.FLUID_RETURN, generation);
    }

    /**
     * Local input enters through the Federation face, where the native Provider sits; Federated input enters through a
     * subnet face. Return contexts never resolve input storage.
     */
    public Optional<MEStorage> targetStorage(Direction face, EndpointCallContext context) {
        if (context.generation() != generation) {
            return Optional.empty();
        }
        if (mode == EndpointMode.LOCAL && context.purpose() == EndpointCallContext.Purpose.LOCAL_INPUT
                && localProvider != null && face == federationFace) {
            return localInputStorage();
        }
        if (mode == EndpointMode.FEDERATED && context.purpose() == EndpointCallContext.Purpose.FEDERATED_INPUT
                && valid(face, context)) {
            return faceStorage(face);
        }
        return Optional.empty();
    }

    private Optional<MEStorage> localInputStorage() {
        var storage = storageLookup.apply(federationFace);
        return subnetNode.getGrid() != null && storage == subnetNode.getGrid().getStorageService().getInventory()
                ? Optional.of(storage)
                : Optional.empty();
    }

    public Optional<GenericInternalInventory> returnInventory(Direction face, EndpointCallContext context) {
        if (faceStorage(face).isEmpty() || !valid(face, context)
                || (context.purpose() != EndpointCallContext.Purpose.ITEM_RETURN
                && context.purpose() != EndpointCallContext.Purpose.FLUID_RETURN)
                || mode != EndpointMode.LOCAL || localProvider == null) {
            return Optional.empty();
        }
        return Optional.of(localProvider.returnInventory());
    }

    public Optional<IItemHandler> itemReturnCapability(Direction face, EndpointCallContext context) {
        return context.purpose() == EndpointCallContext.Purpose.ITEM_RETURN
                ? returnInventory(face, context).map(GenericStackItemStorage::new)
                : Optional.empty();
    }

    public Optional<IFluidHandler> fluidReturnCapability(Direction face, EndpointCallContext context) {
        return context.purpose() == EndpointCallContext.Purpose.FLUID_RETURN
                ? returnInventory(face, context).map(GenericStackFluidStorage::new)
                : Optional.empty();
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

    public long generation() {
        return generation;
    }

    public Optional<NativeLocalProvider> localProvider() {
        return Optional.ofNullable(localProvider);
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
        var storage = storageLookup.apply(face);
        return storage == subnetNode.getGrid().getStorageService().getInventory()
                ? Optional.of(storage)
                : Optional.empty();
    }

    private boolean valid(Direction face, EndpointCallContext context) {
        return subnetFaces.contains(face) && context.generation() == generation;
    }
}
