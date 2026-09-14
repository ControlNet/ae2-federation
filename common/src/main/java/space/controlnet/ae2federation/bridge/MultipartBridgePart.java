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
        super.addToWorld();
        outerNode.create(getLevel(), getBlockEntity().getBlockPos());
        refresh();
    }

    @Override
    public void removeFromWorld() {
        if (removed) {
            return;
        }
        removed = true;
        status = BridgeStatus.invalid(BridgeOperationalReason.REMOVED);
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
        super.readFromNBT(data, registries);
        refresh();
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
        return !isClientSide() && status.reason() != BridgeOperationalReason.REMOVED;
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
            status = BridgeStatus.invalid(removed ? BridgeOperationalReason.REMOVED
                    : BridgeOperationalReason.MISSING_OUTER_ATTACHMENT);
            return;
        }
        var main = getMainNode().getNode();
        var outer = outerNode.getNode();
        if (main == null) {
            status = BridgeStatus.invalid(BridgeOperationalReason.MISSING_MAIN_ATTACHMENT);
            return;
        }
        if (outer == null) {
            status = BridgeStatus.invalid(BridgeOperationalReason.MISSING_OUTER_ATTACHMENT);
            return;
        }
        if (main.getGrid() == null || main.getConnections().isEmpty()) {
            status = BridgeStatus.invalid(BridgeOperationalReason.MISSING_MAIN_ATTACHMENT);
            return;
        }
        var position = getBlockEntity().getBlockPos();
        var outerAttachment = NativeAttachmentResolver.resolve(getLevel(), position, getSide(), outer).orElse(null);
        if (outerAttachment == null) {
            status = BridgeStatus.invalid(getLevel().getBlockState(position.relative(getSide())).isAir()
                    ? BridgeOperationalReason.MISSING_OUTER_ATTACHMENT
                    : BridgeOperationalReason.FEDERATION_CABLE_UNSUPPORTED);
            return;
        }
        status = BridgeTopology.classify(main, outerAttachment);
    }
}
