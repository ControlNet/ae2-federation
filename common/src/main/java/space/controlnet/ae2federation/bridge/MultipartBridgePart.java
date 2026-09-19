package space.controlnet.ae2federation.bridge;

import appeng.api.networking.GridFlags;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.items.parts.PartModels;
import appeng.parts.AEBasePart;
import appeng.parts.PartModel;
import java.util.EnumSet;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.ae2.NativeAttachment;
import space.controlnet.ae2federation.ae2.NativeAttachmentResolver;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.fabric.FabricNodeId;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.client.menu.FabricPolicyMenu;
import space.controlnet.ae2federation.fabric.FabricSourceId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.storage.mount.StorageMountService;

public final class MultipartBridgePart extends AEBasePart {
    @PartModels
    public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            "ae2federation", "part/multipart_bridge");
    private static final IPartModel MODELS = new PartModel(MODEL);
    private static final IGridNodeListener<MultipartBridgePart> NODE_LISTENER = (owner, node) -> owner.refresh();

    private final IManagedGridNode outerNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setTagName("outer")
            .setInWorldNode(true)
            .setIdlePowerUsage(0.0)
            .setFlags(GridFlags.CANNOT_CARRY);
    private BridgeStatus status = BridgeStatus.invalid(BridgeOperationalReason.MISSING_MAIN_ATTACHMENT);
    private boolean removed;
    private @Nullable FabricSourceId fabricSource;
    private boolean mainNodeLoaded;
    private boolean outerNodeLoaded;

    public MultipartBridgePart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(0.0).setFlags(GridFlags.CANNOT_CARRY);
    }

    @Override
    public void setPartHostInfo(Direction side, IPartHost host, net.minecraft.world.level.block.entity.BlockEntity blockEntity) {
        super.setPartHostInfo(side, host, blockEntity);
        outerNode.setExposedOnSides(EnumSet.of(side));
    }

    @Override
    public void addToWorld() {
        removed = false;
        seedBoundaryNodes();
        super.addToWorld();
        outerNode.create(getLevel(), getBlockEntity().getBlockPos());
        if (getLevel() instanceof ServerLevel serverLevel) {
            var nodeId = FabricRegistryAccess.nodeId(serverLevel, getBlockEntity().getBlockPos());
            fabricSource = new FabricSourceId("bridge:" + nodeId + ":" + getSide().getSerializedName());
        }
        refresh();
    }

    @Override
    public void removeFromWorld() {
        if (removed) {
            return;
        }
        removed = true;
        setStatus(BridgeStatus.invalid(BridgeOperationalReason.REMOVED));
        outerNode.destroy();
        super.removeFromWorld();
    }

    @Override
    public void onNeighborChanged(net.minecraft.world.level.BlockGetter level, BlockPos pos, BlockPos neighbor) {
        refresh();
    }

    @Override
    public void onUpdateShape(Direction side) {
        refresh();
    }

    @Override
    public void readFromNBT(net.minecraft.nbt.CompoundTag data,
            net.minecraft.core.HolderLookup.Provider registries) {
        mainNodeLoaded = data.contains("gn");
        outerNodeLoaded = data.contains("outer");
        super.readFromNBT(data, registries);
        outerNode.loadFromNBT(data);
        refresh();
    }

    @Override
    public void writeToNBT(net.minecraft.nbt.CompoundTag data,
            net.minecraft.core.HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        outerNode.saveToNBT(data);
    }

    @Override
    public IGridNode getExternalFacingNode() {
        return outerNode.getNode();
    }

    @Override
    public float getCableConnectionLength(AECableType cable) {
        return 5;
    }

    @Override
    public void getBoxes(IPartCollisionHelper helper) {
        helper.addBox(6, 6, 8, 10, 10, 16);
    }

    @Override
    public IPartModel getStaticModels() {
        return MODELS;
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (status.reason() == BridgeOperationalReason.REMOVED) {
            return false;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            return FabricPolicyMenu.openBridge(serverPlayer, rightClickContext());
        }
        return true;
    }

    public BridgeOperationalReason operationalReason() {
        return status.reason();
    }

    public Optional<BridgeMembershipCandidate> membershipCandidate() {
        return status.membershipCandidate();
    }

    public BridgeRightClickContext rightClickContext() {
        var position = getBlockEntity().getBlockPos();
        return new BridgeRightClickContext(position, getSide(), status.reason(),
                status.membershipCandidate().map(BridgeMembershipCandidate::mainGrid).orElse(null),
                status.membershipCandidate().map(BridgeMembershipCandidate::outerGrid).orElse(null));
    }

    private void refresh() {
        if (removed || getBlockEntity() == null || getLevel() == null || getSide() == null) {
            setStatus(BridgeStatus.invalid(removed ? BridgeOperationalReason.REMOVED
                    : BridgeOperationalReason.MISSING_OUTER_ATTACHMENT));
            return;
        }
        var main = getMainNode().getNode();
        var outer = outerNode.getNode();
        if (main == null) {
            setStatus(BridgeStatus.invalid(BridgeOperationalReason.MISSING_MAIN_ATTACHMENT));
            return;
        }
        if (outer == null) {
            setStatus(BridgeStatus.invalid(BridgeOperationalReason.MISSING_OUTER_ATTACHMENT));
            return;
        }
        if (main.getGrid() == null || main.getConnections().isEmpty()) {
            setStatus(BridgeStatus.invalid(BridgeOperationalReason.MISSING_MAIN_ATTACHMENT));
            return;
        }
        var position = getBlockEntity().getBlockPos();
        var outerAttachment = NativeAttachmentResolver.resolve(getLevel(), position, getSide(), outer).orElse(null);
        if (outerAttachment == null) {
            setStatus(BridgeStatus.invalid(getLevel().getBlockState(position.relative(getSide())).isAir()
                    ? BridgeOperationalReason.MISSING_OUTER_ATTACHMENT
                    : BridgeOperationalReason.FEDERATION_CABLE_UNSUPPORTED));
            return;
        }
        setStatus(BridgeTopology.classify(main, outerAttachment));
    }

    private void seedBoundaryNodes() {
        if (!(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        var center = getHost().getPart(null);
        if (!mainNodeLoaded && center != null && center.getGridNode() != null) {
            FabricRegistryAccess.confirmedNetworkId(center.getGridNode().getGrid()).ifPresent(networkId ->
                    getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("gn", networkId)));
        }
        var outerPosition = getBlockEntity().getBlockPos().relative(getSide());
        var neighbor = serverLevel.isLoaded(outerPosition)
                ? GridHelper.getExposedNode(serverLevel, outerPosition, getSide().getOpposite())
                : null;
        if (!outerNodeLoaded && neighbor != null) {
            FabricRegistryAccess.confirmedNetworkId(neighbor.getGrid()).ifPresent(networkId ->
                    outerNode.loadFromNBT(NetworkIdentityNodeSeed.managedNode("outer", networkId)));
        }
    }

    private void setStatus(BridgeStatus nextStatus) {
        status = nextStatus;
        if (!(getLevel() instanceof ServerLevel serverLevel) || fabricSource == null) {
            return;
        }
        var candidate = nextStatus.membershipCandidate().orElse(null);
        if (candidate == null) {
            FabricRegistryAccess.invalidateDirectBridgeIfPresent(serverLevel, fabricSource);
            StorageMountService.reconcileIfPresent(serverLevel);
            return;
        }
        var mainId = FabricRegistryAccess.confirmedNetworkId(candidate.mainGrid());
        var outerId = FabricRegistryAccess.confirmedNetworkId(candidate.outerGrid());
        if (mainId.isEmpty() || outerId.isEmpty()) {
            FabricRegistryAccess.invalidateDirectBridgeIfPresent(serverLevel, fabricSource);
            StorageMountService.reconcileIfPresent(serverLevel);
            return;
        }
        FabricRegistryAccess.get(serverLevel).upsertDirectBridge(fabricSource, mainId.get(), outerId.get());
        StorageMountService.get(serverLevel).observeConnectedGrids(candidate.mainGrid(), candidate.outerGrid());
    }
}
