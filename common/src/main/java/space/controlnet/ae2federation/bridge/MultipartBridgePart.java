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
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu;
import space.controlnet.ae2federation.domain.FederationDomainSourceId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.energy.DirectionalEnergySource;
import space.controlnet.ae2federation.energy.EnergyBindingService;

public final class MultipartBridgePart extends AEBasePart {
    @PartModels
    public static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath(
            "ae2federation", "part/bridge");
    private static final IPartModel MODELS = new PartModel(MODEL);
    private static final IGridNodeListener<MultipartBridgePart> NODE_LISTENER = new IGridNodeListener<>() {
        @Override
        public void onSaveChanges(MultipartBridgePart owner, IGridNode node) {
            owner.refresh();
        }

        @Override
        public void onStateChanged(MultipartBridgePart owner, IGridNode node, State state) {
            owner.refreshIfChanged();
        }

        @Override
        public void onGridChanged(MultipartBridgePart owner, IGridNode node) {
            owner.refreshIfChanged();
        }
    };

    private final DirectionalEnergySource mainEnergySource = new DirectionalEnergySource();
    private final DirectionalEnergySource outerEnergySource = new DirectionalEnergySource();
    private final IManagedGridNode outerNode = GridHelper.createManagedNode(this, NODE_LISTENER)
            .setTagName("outer")
            .setInWorldNode(true)
            .setIdlePowerUsage(0.0)
            .setFlags(GridFlags.CANNOT_CARRY)
            .addService(appeng.api.networking.energy.IAEPowerStorage.class, outerEnergySource);
    private BridgeStatus status = BridgeStatus.invalid(BridgeOperationalReason.MISSING_MAIN_ATTACHMENT);
    private boolean removed;
    private @Nullable FederationDomainSourceId federationDomainSource;
    private boolean mainNodeLoaded;
    private boolean onlyIfChanged;
    private boolean refreshScheduled;
    private java.util.List<Object> lastPublished = java.util.List.of();
    private boolean outerNodeLoaded;

    public MultipartBridgePart(IPartItem<?> partItem) {
        super(partItem);
        getMainNode().setIdlePowerUsage(0.0).setFlags(GridFlags.CANNOT_CARRY)
                .addService(appeng.api.networking.energy.IAEPowerStorage.class, mainEnergySource);
        mainEnergySource.bind(getMainNode());
        outerEnergySource.bind(outerNode);
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
            var nodeId = FederationDomainRegistryAccess.nodeId(serverLevel, getBlockEntity().getBlockPos());
            federationDomainSource = new FederationDomainSourceId("bridge:" + nodeId + ":" + getSide().getSerializedName());
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

    /**
     * AE2 forms the main node's cable-bus connections after addToWorld, so a loaded Bridge must re-evaluate once the
     * native node reports its Grid state; otherwise it stays invalid until an unrelated neighbor update.
     */
    @Override
    protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        refreshIfChanged();
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
            return FederationDomainPolicyMenu.openBridge(serverPlayer, rightClickContext());
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

    /**
     * Native state notifications are frequent (power, channels, boot); republishing an unchanged Bridge would bump the
     * Domain topology revision and invalidate dependent snapshots, so these triggers publish only real changes.
     */
    private void refreshIfChanged() {
        // Node events also fire while neighboring chunks unload. Evaluating then could look up a block in a chunk that
        // is being unloaded and load it again (which never lets the server's shutdown unload loop drain), so the
        // refresh runs on the next server tick and never while the server is stopping.
        if (refreshScheduled || !(getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        var server = serverLevel.getServer();
        if (!server.isRunning()) {
            return;
        }
        refreshScheduled = true;
        server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 1, () -> {
            refreshScheduled = false;
            if (removed || !server.isRunning() || getBlockEntity() == null || getBlockEntity().isRemoved()) {
                return;
            }
            onlyIfChanged = true;
            try {
                refresh();
            } finally {
                onlyIfChanged = false;
            }
        }));
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
        if (!getLevel().isLoaded(position.relative(getSide()))) {
            // Never touch an unloaded (or unloading) neighbor: a block lookup there would load the chunk again,
            // which keeps the server's shutdown unload loop from ever draining.
            setStatus(BridgeStatus.invalid(BridgeOperationalReason.MISSING_OUTER_ATTACHMENT));
            return;
        }
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
            FederationDomainRegistryAccess.confirmedNetworkId(center.getGridNode().getGrid()).ifPresent(networkId ->
                    getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("gn", networkId)));
        }
        var outerPosition = getBlockEntity().getBlockPos().relative(getSide());
        var neighbor = serverLevel.isLoaded(outerPosition)
                ? GridHelper.getExposedNode(serverLevel, outerPosition, getSide().getOpposite())
                : null;
        if (!outerNodeLoaded && neighbor != null) {
            FederationDomainRegistryAccess.confirmedNetworkId(neighbor.getGrid()).ifPresent(networkId ->
                    outerNode.loadFromNBT(NetworkIdentityNodeSeed.managedNode("outer", networkId)));
        }
    }

    private void setStatus(BridgeStatus nextStatus) {
        var previous = status;
        status = nextStatus;
        if (!(getLevel() instanceof ServerLevel serverLevel) || federationDomainSource == null) {
            return;
        }
        var candidate = nextStatus.membershipCandidate().orElse(null);
        var published = java.util.List.of(nextStatus, candidate == null ? java.util.Optional.empty()
                : FederationDomainRegistryAccess.confirmedNetworkId(candidate.mainGrid()),
                candidate == null ? java.util.Optional.empty()
                        : FederationDomainRegistryAccess.confirmedNetworkId(candidate.outerGrid()));
        if (onlyIfChanged && previous.equals(nextStatus) && published.equals(lastPublished)) {
            return;
        }
        lastPublished = published;
        if (candidate == null) {
            FederationDomainRegistryAccess.invalidateDirectBridgeIfPresent(serverLevel, federationDomainSource);
            StorageMountService.reconcileIfPresent(serverLevel);
            CraftingBindingService.reconcileIfPresent(serverLevel);
            EnergyBindingService.reconcileIfPresent(serverLevel);
            return;
        }
        var mainId = FederationDomainRegistryAccess.confirmedNetworkId(candidate.mainGrid());
        var outerId = FederationDomainRegistryAccess.confirmedNetworkId(candidate.outerGrid());
        if (mainId.isEmpty() || outerId.isEmpty()) {
            FederationDomainRegistryAccess.invalidateDirectBridgeIfPresent(serverLevel, federationDomainSource);
            StorageMountService.reconcileIfPresent(serverLevel);
            CraftingBindingService.reconcileIfPresent(serverLevel);
            EnergyBindingService.reconcileIfPresent(serverLevel);
            return;
        }
        FederationDomainRegistryAccess.get(serverLevel).upsertDirectBridge(federationDomainSource, mainId.get(), outerId.get());
        StorageMountService.get(serverLevel).observeConnectedGrids(candidate.mainGrid(), candidate.outerGrid());
        CraftingBindingService.get(serverLevel).observeConnectedGrids(candidate.mainGrid(), candidate.outerGrid());
        EnergyBindingService.get(serverLevel).observeConnectedGrids(candidate.mainGrid(), candidate.outerGrid());
    }
}
