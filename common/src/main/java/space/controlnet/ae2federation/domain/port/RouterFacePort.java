package space.controlnet.ae2federation.domain.port;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.ae2.NativeAttachmentResolver;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.IdentityNeutralNodeOwner;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.energy.FederationEnergyConnection;

public final class RouterFacePort implements IdentityNeutralNodeOwner {
    /**
     * A newly placed native neighbor (e.g. a cable bus) creates its node on a later tick than the block update that
     * dirtied this face, so the face must also re-resolve when the in-world connection or Grid actually changes. Those
     * events fire inside AE2 Grid propagation, so they only mark the face dirty; the Router tick resolves and publishes.
     * A saved-data change (a lineage minted for this neutral node, a new owner) leaves the attachment as it is, so it
     * too only asks for a resolve instead of dropping the face and splitting the domain for a tick.
     */
    private static final IGridNodeListener<RouterFacePort> NODE_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(RouterFacePort owner, IGridNode node) {
            owner.dirty = true;
        }

        @Override
        public void onInWorldConnectionChanged(RouterFacePort owner, IGridNode node) {
            owner.dirty = true;
        }

        @Override
        public void onGridChanged(RouterFacePort owner, IGridNode node) {
            owner.dirty = true;
        }
    };

    private final BlockPos routerPosition;
    private final Direction face;
    private final FederationPort routerFederationDomainPort;
    private final IManagedGridNode boundaryNode;
    private final FederationEnergyConnection energyConnection;
    private final space.controlnet.ae2federation.crafting.remote.RemoteCraftingRequester craftingRequester;
    private RouterPortBinding binding = RouterPortBinding.Disconnected.INSTANCE;
    private BlockCapabilityCache<FederationPort, Direction> federationCache;
    private ServerLevel level;
    private boolean dirty = true;
    private boolean nodeLoaded;

    /** @param changed marks the Router for saving, for the Crafting jobs this face's Grid runs for consumers */
    public RouterFacePort(BlockPos routerPosition, Direction face, FederationPort routerFederationDomainPort,
            Runnable changed) {
        this.routerPosition = routerPosition.immutable();
        this.face = face;
        this.routerFederationDomainPort = routerFederationDomainPort;
        energyConnection = new FederationEnergyConnection();
        craftingRequester = new space.controlnet.ae2federation.crafting.remote.RemoteCraftingRequester(
                "ae2federation_crafting_face_" + face.getSerializedName(), this::node, changed);
        this.boundaryNode = GridHelper.createManagedNode(this, NODE_LISTENER)
                .setTagName("face_" + face.getSerializedName())
                .setInWorldNode(true)
                .setIdlePowerUsage(0.0)
                .setFlags(GridFlags.CANNOT_CARRY)
                .setExposedOnSides(EnumSet.of(face))
                .addService(appeng.me.energy.IEnergyOverlayGridConnection.class, energyConnection)
                .addService(appeng.api.networking.crafting.ICraftingRequester.class, craftingRequester);
        energyConnection.bind(this, boundaryNode);
    }

    public void initialize(ServerLevel serverLevel) {
        level = serverLevel;
        var neighborPosition = routerPosition.relative(face);
        var neighbor = serverLevel.isLoaded(neighborPosition)
                ? GridHelper.getExposedNode(serverLevel, neighborPosition, face.getOpposite())
                : null;
        if (!nodeLoaded && neighbor != null) {
            FederationDomainRegistryAccess.confirmedNetworkId(neighbor.getGrid()).ifPresent(networkId -> boundaryNode.loadFromNBT(
                    NetworkIdentityNodeSeed.managedNode("face_" + face.getSerializedName(), networkId)));
        }
        boundaryNode.create(serverLevel, routerPosition);
        federationCache = BlockCapabilityCache.create(FederationPortCapability.BLOCK, serverLevel, neighborPosition,
                face.getOpposite(), () -> boundaryNode.isReady(), this::recheck);
        binding = RouterPortBinding.Disconnected.INSTANCE;
        dirty = true;
    }

    public boolean tick() {
        if (!dirty) {
            return false;
        }
        dirty = false;
        var resolved = resolve();
        var changed = !resolved.equals(binding);
        binding = resolved;
        return changed;
    }

    /**
     * The neighbouring block changed: resolve the face now and report whether its binding differs, so the Router
     * republishes only a real change. A removed native neighbour has already destroyed its node and so resolves as
     * disconnected at once; a new one connects later and reaches the face through the node listener. The face also
     * resolves again on the next tick.
     */
    public boolean revalidate() {
        var resolved = resolve();
        var changed = !resolved.equals(binding);
        binding = resolved;
        dirty = true;
        return changed;
    }

    /**
     * Some capability of the neighbour changed, such as an Endpoint's item handlers when it is claimed: resolve the
     * binding again on the next tick, which reports a change only when it differs. Dropping the binding here would
     * republish the whole domain although the link is the same; a changed neighbour block reaches {@link #revalidate()}.
     */
    private void recheck() {
        dirty = true;
    }

    public void destroy() {
        binding = RouterPortBinding.Disconnected.INSTANCE;
        dirty = false;
        boundaryNode.destroy();
    }

    public RouterPortBinding binding() {
        return binding;
    }

    @Nullable
    public IGridNode node() {
        return boundaryNode.getNode();
    }

    public IManagedGridNode managedNode() {
        return boundaryNode;
    }

    public void loadFromNBT(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        nodeLoaded = tag.contains("face_" + face.getSerializedName());
        craftingRequester.readFromNBT(tag, registries);
        boundaryNode.loadFromNBT(tag);
    }

    public void saveToNBT(net.minecraft.nbt.CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        boundaryNode.saveToNBT(tag);
        craftingRequester.writeToNBT(tag, registries);
    }

    private RouterPortBinding resolve() {
        var neighborPosition = routerPosition.relative(face);
        if (level == null || !level.isLoaded(neighborPosition) || federationCache == null) {
            return RouterPortBinding.Disconnected.INSTANCE;
        }
        var node = boundaryNode.getNode();
        if (node == null) {
            return RouterPortBinding.Disconnected.INSTANCE;
        }
        var nativeAttachment = NativeAttachmentResolver.resolve(level, routerPosition, face, node);
        var federationPort = federationCache.getCapability();
        var validFederationPort = federationPort != null
                && federationPort.ownerPosition().equals(neighborPosition)
                && federationPort.outwardFace() == face.getOpposite()
                && routerFederationDomainPort.connectsTo(federationPort);
        if (nativeAttachment.isPresent() == validFederationPort) {
            return RouterPortBinding.Disconnected.INSTANCE;
        }
        return nativeAttachment.<RouterPortBinding>map(RouterPortBinding.Native::new)
                .orElseGet(() -> new RouterPortBinding.Federation(federationPort));
    }
}
