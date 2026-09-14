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

public final class HubFacePort {
    private static final IGridNodeListener<HubFacePort> NODE_LISTENER = (owner, node) -> owner.invalidate();

    private final BlockPos hubPosition;
    private final Direction face;
    private final FederationPort hubFabricPort;
    private final IManagedGridNode boundaryNode;
    private HubPortBinding binding = HubPortBinding.Disconnected.INSTANCE;
    private BlockCapabilityCache<FederationPort, Direction> federationCache;
    private ServerLevel level;
    private boolean dirty = true;

    public HubFacePort(BlockPos hubPosition, Direction face, FederationPort hubFabricPort) {
        this.hubPosition = hubPosition.immutable();
        this.face = face;
        this.hubFabricPort = hubFabricPort;
        this.boundaryNode = GridHelper.createManagedNode(this, NODE_LISTENER)
                .setTagName("face_" + face.getSerializedName())
                .setInWorldNode(true)
                .setIdlePowerUsage(0.0)
                .setFlags(GridFlags.CANNOT_CARRY)
                .setExposedOnSides(EnumSet.of(face));
    }

    public void initialize(ServerLevel serverLevel) {
        level = serverLevel;
        boundaryNode.create(serverLevel, hubPosition);
        var neighborPosition = hubPosition.relative(face);
        federationCache = BlockCapabilityCache.create(FederationPortCapability.BLOCK, serverLevel, neighborPosition,
                face.getOpposite(), () -> boundaryNode.isReady(), this::invalidate);
        invalidate();
    }

    public void tick() {
        if (!dirty) {
            return;
        }
        dirty = false;
        binding = resolve();
    }

    public void invalidate() {
        binding = HubPortBinding.Disconnected.INSTANCE;
        dirty = true;
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
