package space.controlnet.ae2federation.test.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.api.networking.GridHelper;
import appeng.core.definitions.AEBlocks;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
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
import space.controlnet.ae2federation.test.world.BlockEntityReload;

public final class CraftingBindingFixture implements AutoCloseable {
    private static final BlockPos DEFAULT_BASE = new BlockPos(5, 3, 5);

    private final BlockPos base;
    /** Beside the consumer's cable, so the CPU joins the consumer Grid as a placed block does, and again after a reload. */
    private final BlockPos consumerCpuPos;

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
        this(helper, withCpu, withForbiddenPattern, false);
    }

    public CraftingBindingFixture(GameTestHelper helper, boolean withCpu, boolean withForbiddenPattern,
            boolean withProviderFluidChest) {
        this(helper, DEFAULT_BASE, withCpu, withForbiddenPattern, withProviderFluidChest);
    }

    private CraftingBindingFixture(GameTestHelper helper, BlockPos base, boolean withCpu, boolean withForbiddenPattern,
            boolean withProviderFluidChest) {
        this.helper = helper;
        this.withCpu = withCpu;
        this.base = base;
        consumerCpuPos = base.west();
        nativeSource = new CraftingNativeSourceFixture(helper, base, withCpu, withForbiddenPattern);
        bridge = new PolicyBridgeFixtures(helper, base, withProviderFluidChest);
        bridge.installStorageCells();
    }

    /** Places the provider's CPU beside its chest, as a player adds one after the rule is already on. */
    public void placeProviderCpu() {
        nativeSource.placeCpu(key().providerNetworkId());
    }

    /** The provider's CPU, relative to the test. */
    public BlockPos providerCpuPos() {
        return nativeSource.cpuPosition();
    }

    /** The consumer's CPU, relative to the test. */
    public BlockPos consumerCpuPos() {
        return consumerCpuPos;
    }

    /** The provider's ME chest, relative to the test. */
    public BlockPos providerChestPos() {
        return base.north(2);
    }

    /** The consumer's ME chest, relative to the test. */
    public BlockPos consumerChestPos() {
        return base.south();
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
                && space.controlnet.ae2federation.domain.FederationDomainRegistryAccess.confirmedNetworkId(bridge.mainGrid()).isPresent()
                && space.controlnet.ae2federation.domain.FederationDomainRegistryAccess.confirmedNetworkId(bridge.outerGrid()).isPresent();
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
        var policies = PolicyService.get(helper.getLevel());
        return accepted(policies.edit(new PolicyEdit(key(), policies.revision(key()),
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

    public void restoreBridge() {
        bridge.placeFirstBridge();
        bindings().reconcileAll();
    }

    public boolean restoredBridgeReady() {
        if (!bridge.firstBridgeReady()) {
            bridge.refreshFirstBridge();
            return false;
        }
        bindings().observeConnectedGrids(consumerGrid(), providerGrid());
        return bindings().capability(key()).isPresent();
    }

    public PolicyKey reverseKey() {
        return new PolicyKey(key().providerNetworkId(), key().consumerNetworkId(), PolicyCapability.CRAFTING);
    }

    public PolicyRevision enableReverse() {
        return accepted(PolicyService.get(helper.getLevel()).edit(new PolicyEdit(reverseKey(), PolicyRevision.NONE,
                PolicyRule.enabled(java.util.Set.of(PolicyOperation.REQUEST)))));
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
        if (space.controlnet.ae2federation.domain.FederationDomainRegistryAccess.confirmedNetworkId(bridge.outerGrid())
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

    public long commonFederationDomainCount() {
        var registry = space.controlnet.ae2federation.domain.FederationDomainRegistryAccess.get(helper.getLevel());
        var providerFederationDomains = registry.federationdomainsFor(key().providerNetworkId());
        return registry.federationdomainsFor(key().consumerNetworkId()).stream().filter(providerFederationDomains::contains).count();
    }

    public long topologyRevision() {
        return space.controlnet.ae2federation.domain.FederationDomainRegistryAccess.get(helper.getLevel())
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

    public long physicalOutputAmount() {
        return sourcePhysicalStorage().extract(outputKey(), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    public long materialAmount() {
        return sourceStorage().extract(inputKey(), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    public long physicalMaterialAmount() {
        return sourcePhysicalStorage().extract(inputKey(), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    public void suspendCpu() {
        sourceService().getCpus().stream().filter(cpu -> cpu.isBusy()).map(CraftingCPUCluster.class::cast)
                .forEach(cpu -> cpu.craftingLogic.setJobSuspended(true));
    }

    public void resumeCpu() {
        sourceService().getCpus().stream().filter(cpu -> cpu.isBusy()).map(CraftingCPUCluster.class::cast)
                .forEach(cpu -> cpu.craftingLogic.setJobSuspended(false));
    }

    public long busyCpuCount() {
        return sourceService().getCpus().stream().filter(cpu -> cpu.isBusy()).count();
    }

    public void addNativeCpu(BlockPos position) {
        helper.setBlock(position, AEBlocks.CRAFTING_STORAGE_1K.block());
        var cpu = helper.<CraftingBlockEntity>getBlockEntity(position);
        cpu.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", key().providerNetworkId()));
        connectNativeCpu(position);
    }

    public boolean connectNativeCpu(BlockPos position) {
        var cpu = helper.<CraftingBlockEntity>getBlockEntity(position);
        var cpuNode = cpu.getMainNode().getNode();
        var sourceNode = sourceChest().getMainNode().getNode();
        if (cpuNode != null && sourceNode != null && cpuNode.getGrid() != sourceNode.getGrid()) {
            GridHelper.createConnection(cpuNode, sourceNode);
            return false;
        }
        return cpuNode != null && sourceNode != null && cpuNode.getGrid() == sourceNode.getGrid();
    }

    /** A crafting CPU on the consumer Grid, west of its cable, so the consumer can run its own native jobs. */
    public void addConsumerCpu() {
        helper.setBlock(consumerCpuPos, AEBlocks.CRAFTING_STORAGE_1K.block());
        helper.<CraftingBlockEntity>getBlockEntity(consumerCpuPos).getMainNode()
                .loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", key().consumerNetworkId()));
    }

    public boolean consumerCpuReady() {
        var cpuNode = helper.<CraftingBlockEntity>getBlockEntity(consumerCpuPos).getMainNode().getNode();
        var chestNode = consumerChest().getMainNode().getNode();
        if (cpuNode == null || chestNode == null) {
            return false;
        }
        return cpuNode.getGrid() == chestNode.getGrid() && !consumerService().getCpus().isEmpty();
    }

    public appeng.api.networking.crafting.ICraftingService consumerService() {
        return consumerGrid().getCraftingService();
    }

    /** Plans {@code amount} of the output on the consumer's own crafting service, as its ME Terminal does. */
    public void beginOnConsumer(long amount) {
        var node = consumerChest().getMainNode().getNode();
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
        plan = null;
        planFuture = consumerService().beginCraftingCalculation(helper.getLevel(), requester, outputKey(), amount,
                CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    public ICraftingPlan plan() {
        return plan;
    }

    public boolean submitOnConsumer() {
        return consumerService().submitJob(plan, null, null, true, IActionSource.empty()).successful();
    }

    public long consumerPhysicalOutputAmount() {
        return java.util.Objects.requireNonNull(consumerChest().getOriginalCellInventory(0))
                .extract(outputKey(), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    /** Reloads the Bridge's cable bus from its saved data in one tick; see {@link PolicyBridgeFixtures}. */
    public net.minecraft.nbt.CompoundTag reloadBridgeHost() {
        return bridge.reloadFirstBridgeHost();
    }

    /** Every block of both networks lies between these two corners, relative to the test. */
    private BlockPos regionMin() {
        return base.offset(-1, -1, -3);
    }

    private BlockPos regionMax() {
        return base.offset(3, 1, 2);
    }

    /** The chunks both networks occupy, so a test can keep them loaded across a server restart. */
    public java.util.Set<net.minecraft.world.level.ChunkPos> chunks() {
        var chunks = new java.util.LinkedHashSet<net.minecraft.world.level.ChunkPos>();
        for (var position : BlockPos.betweenClosed(helper.absolutePos(regionMin()), helper.absolutePos(regionMax()))) {
            chunks.add(new net.minecraft.world.level.ChunkPos(position));
        }
        return chunks;
    }

    /**
     * Unloads and reloads every block of both networks in one tick, as a server restart does, so AE2 builds both Grids
     * anew from the saved data; see {@link BlockEntityReload}.
     *
     * @return each reloaded block entity's saved data
     */
    public java.util.Map<BlockPos, net.minecraft.nbt.CompoundTag> reloadAll() {
        // Every block the fixture places.
        var saved = BlockEntityReload.reload(helper, regionMin(), regionMax());
        bridge.refreshFirstBridgePart();
        return saved;
    }

    /** Cancels the consumer's running job, as a player does from its CPU's status screen. */
    public void cancelConsumerJob() {
        consumerService().getCpus().stream().filter(cpu -> cpu.isBusy()).map(CraftingCPUCluster.class::cast)
                .forEach(CraftingCPUCluster::cancelJob);
    }

    public long busyConsumerCpuCount() {
        return consumerService().getCpus().stream().filter(cpu -> cpu.isBusy()).count();
    }

    public appeng.api.storage.MEStorage sourcePhysicalStorage() {
        return java.util.Objects.requireNonNull(sourceChest().getOriginalCellInventory(0));
    }

    public appeng.api.storage.MEStorage sourceFluidStorage() {
        return java.util.Objects.requireNonNull(bridge.providerFluidChest().getOriginalCellInventory(0));
    }

    public appeng.api.storage.MEStorage sourceStorage() {
        return bridge.outerGrid().getStorageService().getInventory();
    }

    public MEChestBlockEntity consumerChest() {
        return bridge.consumerChest();
    }

    public appeng.api.networking.crafting.ICraftingService sourceService() {
        return bridge.outerGrid().getCraftingService();
    }

    public PatternProviderBlockEntity provider() {
        return nativeSource.provider();
    }

    /**
     * Published Crafting bindings between this fixture's two networks, in either direction. Other tests' networks may
     * still stand in the shared level, so a level-wide count says nothing about this fixture.
     */
    public long ownBindingCount() {
        var networks = java.util.Set.of(key().consumerNetworkId(), key().providerNetworkId());
        return space.controlnet.ae2federation.crafting.binding.CraftingBindingService
                .publishedBindingsIfPresent(helper.getLevel()).stream()
                .map(binding -> binding.relationship().key())
                .filter(bound -> networks.contains(bound.consumerNetworkId())
                        && networks.contains(bound.providerNetworkId()))
                .count();
    }

    public appeng.api.networking.IGrid consumerGrid() {
        return bridge.mainGrid();
    }

    public appeng.api.networking.IGrid providerGrid() {
        return bridge.outerGrid();
    }

    public MEChestBlockEntity sourceChest() {
        return bridge.providerChest();
    }

    private static PolicyRevision accepted(PolicyMutationResult result) {
        return ((PolicyMutationResult.Accepted) result).revision();
    }

    public static AEItemKey inputKey() {
        return AEItemKey.of(Items.OAK_PLANKS);
    }

    public static AEItemKey outputKey() {
        return AEItemKey.of(Items.STICK);
    }

    @Override
    public void close() {
        bridge.close();
    }
}
