package space.controlnet.ae2federation.fabric.port;

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
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.energy.DirectionalEnergySource;

public final class HubFacePort {
    private static final IGridNodeListener<HubFacePort> NODE_LISTENER = (owner, node) -> owner.invalidate();

    private final BlockPos hubPosition;
    private final Direction face;
    private final FederationPort hubFabricPort;
    private final Runnable topologyInvalidator;
    private final IManagedGridNode boundaryNode;
    private final DirectionalEnergySource energySource;
    private HubPortBinding binding = HubPortBinding.Disconnected.INSTANCE;
    private BlockCapabilityCache<FederationPort, Direction> federationCache;
    private ServerLevel level;
    private boolean dirty = true;
    private boolean nodeLoaded;

    public HubFacePort(BlockPos hubPosition, Direction face, FederationPort hubFabricPort,
            Runnable topologyInvalidator) {
        this.hubPosition = hubPosition.immutable();
        this.face = face;
        this.hubFabricPort = hubFabricPort;
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
        var neighborPosition = hubPosition.relative(face);
        var neighbor = serverLevel.isLoaded(neighborPosition)
                ? GridHelper.getExposedNode(serverLevel, neighborPosition, face.getOpposite())
                : null;
        if (!nodeLoaded && neighbor != null) {
            FabricRegistryAccess.confirmedNetworkId(neighbor.getGrid()).ifPresent(networkId -> boundaryNode.loadFromNBT(
                    NetworkIdentityNodeSeed.managedNode("face_" + face.getSerializedName(), networkId)));
        }
        boundaryNode.create(serverLevel, hubPosition);
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
        binding = HubPortBinding.Disconnected.INSTANCE;
        dirty = true;
        topologyInvalidator.run();
    }

    public void destroy() {
        binding = HubPortBinding.Disconnected.INSTANCE;
        dirty = false;
        boundaryNode.destroy();
    }

    public HubPortBinding binding() {
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

    private HubPortBinding resolve() {
        var neighborPosition = hubPosition.relative(face);
        if (level == null || !level.isLoaded(neighborPosition) || federationCache == null) {
            return HubPortBinding.Disconnected.INSTANCE;
        }
        var node = boundaryNode.getNode();
        if (node == null) {
            return HubPortBinding.Disconnected.INSTANCE;
        }
        var nativeAttachment = NativeAttachmentResolver.resolve(level, hubPosition, face, node);
        var federationPort = federationCache.getCapability();
        var validFederationPort = federationPort != null
                && federationPort.ownerPosition().equals(neighborPosition)
                && federationPort.outwardFace() == face.getOpposite()
                && hubFabricPort.connectsTo(federationPort);
        if (nativeAttachment.isPresent() == validFederationPort) {
            return HubPortBinding.Disconnected.INSTANCE;
        }
        return nativeAttachment.<HubPortBinding>map(HubPortBinding.Native::new)
                .orElseGet(() -> new HubPortBinding.Federation(federationPort));
    }
}
