package space.controlnet.ae2federation.test.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.crafting.binding.CraftingBindingService;
import space.controlnet.ae2federation.crafting.binding.CraftingCapabilityBinding;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyDelete;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;

public final class CraftingBindingFixture implements AutoCloseable {
    private static final BlockPos BASE = new BlockPos(5, 3, 5);

    private final GameTestHelper helper;
    private final PolicyBridgeFixtures bridge;
    private final boolean withCpu;
    private final CraftingNativeSourceFixture nativeSource;
    private boolean bridgePlaced;
    private PolicyKey key;
    private Future<ICraftingPlan> planFuture;
    private ICraftingPlan plan;

    public CraftingBindingFixture(GameTestHelper helper, boolean withCpu) {
        this(helper, withCpu, false);
    }

    public CraftingBindingFixture(GameTestHelper helper, boolean withCpu, boolean withForbiddenPattern) {
        this.helper = helper;
        this.withCpu = withCpu;
        nativeSource = new CraftingNativeSourceFixture(helper, withCpu, withForbiddenPattern);
        bridge = new PolicyBridgeFixtures(helper, BASE);
        bridge.installStorageCells();
    }

    public boolean ready() {
        if (!bridge.networksSettled()) {
            return false;
        }
        if (!nativeSource.advanceInitialPlacement(bridge.outerNetwork())) {
            return false;
        }
        if (!bridgePlaced) {
            bridge.placeFirstBridge();
            bridgePlaced = true;
            return false;
        }
        if (!bridge.firstBridgeReady()) {
            bridge.refreshFirstBridge();
            return false;
        }
        return nativeSource.initialReady(bridge.outerGrid())
                && space.controlnet.ae2federation.fabric.FabricRegistryAccess.confirmedNetworkId(bridge.mainGrid()).isPresent()
                && space.controlnet.ae2federation.fabric.FabricRegistryAccess.confirmedNetworkId(bridge.outerGrid()).isPresent();
    }

    public String readinessState() {
        var mainGrid = bridge.mainGrid();
        var outerGrid = bridge.outerGrid();
        var mainSettlement = mainGrid.getService(
                space.controlnet.ae2federation.identity.NetworkIdentityService.class).settlement();
        var outerSettlement = outerGrid.getService(
                space.controlnet.ae2federation.identity.NetworkIdentityService.class).settlement();
        var networksSettled = bridge.networksSettled();
        var firstBridgeReady = bridgePlaced && bridge.firstBridgeReady();
        var providerOnSource = nativeSource.placementStage() > 0 && networksSettled
                && provider().getMainNode().getGrid() == bridge.outerGrid();
        var cpuCount = networksSettled ? sourceService().getCpus().size() : -1;
        var craftable = networksSettled && sourceService().isCraftable(outputKey());
        return "networksSettled=" + networksSettled + ",sameGrid=" + (mainGrid == outerGrid)
                + ",mainIdentity=" + mainSettlement.status() + ",outerIdentity=" + outerSettlement.status()
                + ",craftingPlacementStage=" + nativeSource.placementStage() + ",bridgePlaced=" + bridgePlaced
                + ",firstBridgeReady=" + firstBridgeReady + ",providerOnSource=" + providerOnSource
                + ",cpuCount=" + cpuCount + ",patternInstalled=" + nativeSource.patternInstalled()
                + ",craftable=" + craftable;
    }

    public PolicyKey key() {
        if (key == null) {
            key = new PolicyKey(bridge.mainNetwork(), bridge.outerNetwork(), PolicyCapability.CRAFTING);
        }
        return key;
    }

    public PolicyRevision enable() {
        return accepted(PolicyService.get(helper.getLevel()).edit(new PolicyEdit(key(), PolicyRevision.NONE,
                PolicyRule.enabled(java.util.Set.of(PolicyOperation.REQUEST)))));
    }

    public PolicyRevision setEnabled(PolicyRevision expected, boolean enabled) {
        return accepted(PolicyService.get(helper.getLevel()).edit(new PolicyEdit(key(), expected,
                PolicyRule.enabled(java.util.Set.of(PolicyOperation.REQUEST)).withEnabled(enabled))));
    }

    public PolicyMutationResult staleEdit(PolicyRevision expected) {
        return PolicyService.get(helper.getLevel()).edit(new PolicyEdit(key(), expected,
                PolicyRule.enabled(java.util.Set.of(PolicyOperation.REQUEST))));
    }

    public PolicyRevision delete(PolicyRevision expected) {
        return accepted(PolicyService.get(helper.getLevel()).delete(new PolicyDelete(key(), expected)));
    }

    public CraftingBindingService bindings() {
        return CraftingBindingService.get(helper.getLevel());
    }

    public CraftingCapabilityBinding binding() {
        return bindings().capability(key()).orElseThrow();
    }

    public void addDuplicateBridge() {
        bridge.placeSecondBridge();
    }

    public boolean duplicateBridgeReady() {
        if (!bridge.secondBridgeReady()) {
            bridge.refreshSecondBridge();
            return false;
        }
        return true;
    }

    public void removeBridges() {
        bridge.removeFirstBridge();
        if (bridge.secondBridgeReady()) {
            bridge.removeSecondBridge();
        }
    }

    public void removeProvider() {
        nativeSource.removeProvider();
        bindings().reconcileAll();
    }

    public void removeCpu() {
        nativeSource.removeCpu();
    }

    public void beginProviderReplacement() {
        nativeSource.beginReplacement(key().providerNetworkId());
    }

    public boolean replacementReady() {
        if (space.controlnet.ae2federation.fabric.FabricRegistryAccess.confirmedNetworkId(bridge.outerGrid())
                .filter(key().providerNetworkId()::equals).isEmpty()) {
            return false;
        }
        bridge.refreshFirstBridge();
        if (!nativeSource.replacementReady(bridge.outerGrid())) {
            return false;
        }
        bindings().reconcileAll();
        return sourceService().isCraftable(outputKey()) && bindings().capability(key()).isPresent();
    }

    public java.util.UUID providerNodeId() {
        return nativeSource.providerNodeId(providerGrid());
    }

    public appeng.api.networking.IGridNode providerNode() {
        return nativeSource.providerNode();
    }

    public long commonFabricCount() {
        var registry = space.controlnet.ae2federation.fabric.FabricRegistryAccess.get(helper.getLevel());
        var providerFabrics = registry.fabricsFor(key().providerNetworkId());
        return registry.fabricsFor(key().consumerNetworkId()).stream().filter(providerFabrics::contains).count();
    }

    public long topologyRevision() {
        return space.controlnet.ae2federation.fabric.FabricRegistryAccess.get(helper.getLevel())
                .snapshot().topologyRevision();
    }

    public net.minecraft.server.level.ServerLevel level() {
        return helper.getLevel();
    }

    public void insertMaterials(long amount) {
        helper.assertValueEqual(sourceStorage().insert(inputKey(), amount, Actionable.MODULATE, IActionSource.empty()),
                amount, "Native source storage must accept materials");
    }

    public void begin(long amount) {
        var node = sourceChest().getMainNode().getNode();
        ICraftingSimulationRequester requester = new ICraftingSimulationRequester() {
            @Override
            public IActionSource getActionSource() {
                return IActionSource.empty();
            }

            @Override
            public appeng.api.networking.IGridNode getGridNode() {
                return node;
            }
        };
        planFuture = binding().nativeService().orElseThrow().beginCraftingCalculation(helper.getLevel(), requester,
                outputKey(), amount, CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    public boolean planReady() {
        if (plan != null) {
            return true;
        }
        if (planFuture == null || !planFuture.isDone()) {
            return false;
        }
        try {
            plan = planFuture.get();
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Native crafting calculation interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Native crafting calculation failed", exception);
        }
    }

    public boolean submit() {
        return binding().nativeService().orElseThrow().submitJob(plan, null, null, true, IActionSource.empty()).successful();
    }

    public long outputAmount() {
        return sourceStorage().extract(outputKey(), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    public long materialAmount() {
        return sourceStorage().extract(inputKey(), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    public appeng.api.storage.MEStorage sourcePhysicalStorage() {
        return java.util.Objects.requireNonNull(sourceChest().getOriginalCellInventory(0));
    }

    public appeng.api.networking.crafting.ICraftingService sourceService() {
        return bridge.outerGrid().getCraftingService();
    }

    public PatternProviderBlockEntity provider() {
        return nativeSource.provider();
    }

    public appeng.api.networking.IGrid consumerGrid() {
        return bridge.mainGrid();
    }

    public appeng.api.networking.IGrid providerGrid() {
        return bridge.outerGrid();
    }

    private appeng.api.storage.MEStorage sourceStorage() {
        return bridge.outerGrid().getStorageService().getInventory();
    }

    private MEChestBlockEntity sourceChest() {
        return bridge.providerChest();
    }

    private static PolicyRevision accepted(PolicyMutationResult result) {
        return ((PolicyMutationResult.Accepted) result).revision();
    }

    private static AEItemKey inputKey() {
        return AEItemKey.of(Items.OAK_PLANKS);
    }

    private static AEItemKey outputKey() {
        return AEItemKey.of(Items.STICK);
    }

    @Override
    public void close() {
        bridge.close();
    }
}
