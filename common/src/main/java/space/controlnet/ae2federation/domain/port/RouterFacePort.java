package space.controlnet.ae2federation.domain.port;

import appeng.api.AECapabilities;
import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import java.util.EnumSet;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.capabilities.BlockCapabilityCache;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.ae2.NativeAttachmentResolver;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.IdentityNeutralNodeOwner;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.energy.FederationEnergyConnection;

/**
 * One face of a Router or a Switch. A Switch's face attaches an ME network through a boundary node or links to a
 * Federation port; a Router's face has no node and only links to a Federation port. A face of a block that is not part
 * of a formed body binds nothing.
 * <p>
 * A Switch face has its node only while an AE2 block it may attach is beside it: a node is a Grid of its own until it
 * connects, and AE2 ticks every Grid, so a thousand Switches with five empty faces each would tick five thousand idle
 * Grids. A face toward a Federation port has no node either, so two Routers face to face never join a native Grid.
 */
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
            owner.resolveLater();
        }

        @Override
        public void onInWorldConnectionChanged(RouterFacePort owner, IGridNode node) {
            owner.resolveLater();
        }

        @Override
        public void onGridChanged(RouterFacePort owner, IGridNode node) {
            owner.resolveLater();
        }
    };

    private final BlockPos routerPosition;
    private final Direction face;
    private final FederationPort routerFederationDomainPort;
    private final boolean attachesNetworks;
    /** Built anew each time the face needs it: AE2's managed node cannot be created again once destroyed. */
    private @Nullable IManagedGridNode boundaryNode;
    /** The node's data as saved, until its node is created; dropped when the face loses its neighbour. */
    private @Nullable CompoundTag savedNode;
    private final BooleanSupplier linking;
    /** Asks the block this face belongs to for a tick, in which the face resolves. */
    private final Runnable wake;
    private RouterPortBinding binding = RouterPortBinding.Disconnected.INSTANCE;
    private BlockCapabilityCache<FederationPort, Direction> federationCache;
    /** Tells the face when the neighbour's AE2 node host comes or goes, including when its chunk loads or unloads. */
    private @Nullable BlockCapabilityCache<IInWorldGridNodeHost, @Nullable Void> nodeHostCache;
    private ServerLevel level;
    private boolean dirty = true;
    /** Look at the neighbour without the caches on the next resolve, in case an event that changed it was missed. */
    private boolean verify;
    private boolean alive;

    public RouterFacePort(BlockPos routerPosition, Direction face, FederationPort routerFederationDomainPort,
            boolean attachesNetworks, BooleanSupplier linking, Runnable wake) {
        this.routerPosition = routerPosition.immutable();
        this.linking = linking;
        this.wake = wake;
        this.face = face;
        this.routerFederationDomainPort = routerFederationDomainPort;
        this.attachesNetworks = attachesNetworks;
    }

    public void initialize(ServerLevel serverLevel) {
        level = serverLevel;
        var neighborPosition = routerPosition.relative(face);
        alive = true;
        binding = RouterPortBinding.Disconnected.INSTANCE;
        resolveLater();
        federationCache = BlockCapabilityCache.create(FederationPortCapability.BLOCK, serverLevel, neighborPosition,
                face.getOpposite(), () -> alive, this::recheck);
        if (attachesNetworks) {
            nodeHostCache = BlockCapabilityCache.create(AECapabilities.IN_WORLD_GRID_NODE_HOST, serverLevel,
                    neighborPosition, null, () -> alive, this::recheck);
            syncNode();
        }
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
        resolveLater();
        return changed;
    }

    /**
     * Some capability of the neighbour changed, such as an Endpoint's item handlers when it is claimed: resolve the
     * binding again on the next tick, which reports a change only when it differs. Dropping the binding here would
     * republish the whole domain although the link is the same; a changed neighbour block reaches {@link #revalidate()}.
     */
    private void recheck() {
        resolveLater();
    }

    /** Whether the block may link changed, as when its body forms or breaks: resolve again on the next tick. */
    public void markDirty() {
        resolveLater();
    }

    /** Whether the face waits to resolve again. */
    public boolean dirty() {
        return dirty;
    }

    private void resolveLater() {
        dirty = true;
        wake.run();
    }

    /**
     * A Switch face without a node looks at its neighbour again on the next tick, without its caches. Something asked
     * for the face's node, as an AE2 node does of every block beside it when it connects, or the Switch's slow sweep
     * runs: either way an AE2 block placed by a path that told no one still gets its face node.
     */
    public void verifyIfNodeless() {
        if (attachesNetworks && alive && boundaryNode == null) {
            verify = true;
            resolveLater();
        }
    }

    public void destroy() {
        binding = RouterPortBinding.Disconnected.INSTANCE;
        dirty = false;
        alive = false;
        if (boundaryNode != null) {
            boundaryNode.destroy();
            boundaryNode = null;
        }
    }

    public RouterPortBinding binding() {
        return binding;
    }

    /** The Router or Switch block this face belongs to. */
    public BlockPos routerPosition() {
        return routerPosition;
    }

    /** The face's boundary node; always null on a Router, which attaches no ME network. */
    @Nullable
    public IGridNode node() {
        return boundaryNode == null ? null : boundaryNode.getNode();
    }

    public void loadFromNBT(CompoundTag tag, HolderLookup.Provider registries) {
        if (!attachesNetworks) {
            return;
        }
        savedNode = null;
        if (tag.contains(tagName())) {
            savedNode = new CompoundTag();
            savedNode.put(tagName(), tag.getCompound(tagName()).copy());
        }
        if (boundaryNode != null) {
            boundaryNode.loadFromNBT(tag);
        }
    }

    public void saveToNBT(CompoundTag tag, HolderLookup.Provider registries) {
        if (boundaryNode != null) {
            boundaryNode.saveToNBT(tag);
        } else if (savedNode != null) {
            tag.merge(savedNode.copy());
        }
    }

    private String tagName() {
        return "face_" + face.getSerializedName();
    }

    /**
     * Creates the face's node when an AE2 block it may attach is beside it, and destroys it once none is. Both caches
     * are read every time, even with the neighbour unloaded, as a cache tells of a change only after a read.
     */
    private void syncNode() {
        if (!attachesNetworks || !alive || nodeHostCache == null) {
            return;
        }
        var host = nodeHostCache.getCapability();
        var federationPort = federationCache.getCapability();
        var neighborPosition = routerPosition.relative(face);
        var loaded = level.isLoaded(neighborPosition);
        if (verify) {
            verify = false;
            if (loaded) {
                host = GridHelper.getNodeHost(level, neighborPosition);
                federationPort = level.getCapability(FederationPortCapability.BLOCK, neighborPosition, face.getOpposite());
            }
        }
        var wanted = host != null && federationPort == null && loaded;
        if (wanted && boundaryNode == null) {
            createNode();
        } else if (!wanted && boundaryNode != null) {
            boundaryNode.destroy();
            boundaryNode = null;
            savedNode = null;
        }
    }

    private void createNode() {
        var energyConnection = new FederationEnergyConnection();
        var node = GridHelper.createManagedNode(this, NODE_LISTENER)
                .setTagName(tagName())
                .setInWorldNode(true)
                .setIdlePowerUsage(0.0)
                .setFlags(GridFlags.CANNOT_CARRY)
                .setExposedOnSides(EnumSet.of(face))
                .addService(appeng.me.energy.IEnergyOverlayGridConnection.class, energyConnection);
        energyConnection.bind(this, node);
        if (savedNode != null) {
            node.loadFromNBT(savedNode);
            savedNode = null;
        } else {
            var neighborPosition = routerPosition.relative(face);
            var neighbor = level.isLoaded(neighborPosition)
                    ? GridHelper.getExposedNode(level, neighborPosition, face.getOpposite())
                    : null;
            if (neighbor != null) {
                FederationDomainRegistryAccess.confirmedNetworkId(neighbor.getGrid()).ifPresent(networkId ->
                        node.loadFromNBT(NetworkIdentityNodeSeed.managedNode(tagName(), networkId)));
            }
        }
        boundaryNode = node;
        node.create(level, routerPosition);
    }

    private RouterPortBinding resolve() {
        syncNode();
        var link = resolveLink();
        return linking.getAsBoolean() ? link : RouterPortBinding.Disconnected.INSTANCE;
    }

    private RouterPortBinding resolveLink() {
        var neighborPosition = routerPosition.relative(face);
        if (level == null || !level.isLoaded(neighborPosition) || federationCache == null) {
            return RouterPortBinding.Disconnected.INSTANCE;
        }
        var federationPort = federationCache.getCapability();
        if (reciprocal(federationPort)) {
            return new RouterPortBinding.Federation(federationPort);
        }
        // A face toward any Federation port has no node, so only a face beside an AE2 block gets this far.
        var node = boundaryNode == null ? null : boundaryNode.getNode();
        if (node == null) {
            return RouterPortBinding.Disconnected.INSTANCE;
        }
        return NativeAttachmentResolver.resolve(level, routerPosition, face, node)
                .<RouterPortBinding>map(RouterPortBinding.Native::new)
                .orElse(RouterPortBinding.Disconnected.INSTANCE);
    }

    private boolean reciprocal(@Nullable FederationPort port) {
        return port != null && port.ownerPosition().equals(routerPosition.relative(face))
                && port.outwardFace() == face.getOpposite() && routerFederationDomainPort.connectsTo(port);
    }
}
