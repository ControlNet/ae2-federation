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
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.energy.DirectionalEnergySource;

public final class RouterFacePort {
    private static final IGridNodeListener<RouterFacePort> NODE_LISTENER = (owner, node) -> owner.invalidate();

    private final BlockPos routerPosition;
    private final Direction face;
    private final FederationPort routerFederationDomainPort;
    private final Runnable topologyInvalidator;
    private final IManagedGridNode boundaryNode;
    private final DirectionalEnergySource energySource;
    private RouterPortBinding binding = RouterPortBinding.Disconnected.INSTANCE;
    private BlockCapabilityCache<FederationPort, Direction> federationCache;
    private ServerLevel level;
    private boolean dirty = true;
    private boolean nodeLoaded;

    public RouterFacePort(BlockPos routerPosition, Direction face, FederationPort routerFederationDomainPort,
            Runnable topologyInvalidator) {
        this.routerPosition = routerPosition.immutable();
        this.face = face;
        this.routerFederationDomainPort = routerFederationDomainPort;
        this.topologyInvalidator = topologyInvalidator;
        energySource = new DirectionalEnergySource();
        this.boundaryNode = GridHelper.createManagedNode(this, NODE_LISTENER)
                .setTagName("face_" + face.getSerializedName())
                .setInWorldNode(true)
                .setIdlePowerUsage(0.0)
                .setFlags(GridFlags.CANNOT_CARRY)
                .setExposedOnSides(EnumSet.of(face))
                .addService(appeng.api.networking.energy.IAEPowerStorage.class, energySource);
        energySource.bind(boundaryNode);
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
                face.getOpposite(), () -> boundaryNode.isReady(), this::invalidate);
        invalidate();
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

    public void invalidate() {
        binding = RouterPortBinding.Disconnected.INSTANCE;
        dirty = true;
        topologyInvalidator.run();
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

    public void loadFromNBT(net.minecraft.nbt.CompoundTag tag) {
        nodeLoaded = tag.contains("face_" + face.getSerializedName());
        boundaryNode.loadFromNBT(tag);
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
