package space.controlnet.ae2federation.test.crafting;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.api.networking.GridHelper;
import appeng.core.definitions.AEBlocks;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
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
    private static final BlockPos DEFAULT_BASE = new BlockPos(5, 3, 5);

    private final BlockPos base;

    private final GameTestHelper helper;
    private final PolicyBridgeFixtures bridge;
    private final boolean withCpu;
    private final CraftingNativeSourceFixture nativeSource;
    private boolean bridgePlaced;
    private PolicyKey key;

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
        nativeSource = new CraftingNativeSourceFixture(helper, base, withCpu, withForbiddenPattern);
        bridge = new PolicyBridgeFixtures(helper, base, withProviderFluidChest);
        bridge.installStorageCells();
    }

    public boolean ready() {
        return readiness().isEmpty();
    }

    /** The first readiness condition that does not hold yet, or empty once both networks are bridged and settled. */
    public String readiness() {
        if (!bridge.networksSettled()) {
            return "settling:" + bridge.settlementDiagnostics();
        }
        if (!nativeSource.advanceInitialPlacement(bridge.outerNetwork())) {
            return "placing-source";
        }
        if (!bridgePlaced) {
            bridge.placeFirstBridge();
            bridgePlaced = true;
            return "placing-bridge";
        }
        if (!bridge.firstBridgeReady()) {
            bridge.refreshFirstBridge();
            return "bridge";
        }
        if (!nativeSource.initialReady(bridge.outerGrid())) {
            return "source";
        }
        if (space.controlnet.ae2federation.domain.FederationDomainRegistryAccess.confirmedNetworkId(bridge.mainGrid()).isEmpty()
                || space.controlnet.ae2federation.domain.FederationDomainRegistryAccess.confirmedNetworkId(bridge.outerGrid()).isEmpty()) {
            return "confirming";
        }
        return "";
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

    public PolicyRevision delete(PolicyRevision expected) {
        return accepted(PolicyService.get(helper.getLevel()).delete(new PolicyDelete(key(), expected)));
    }

    public PolicyKey reverseKey() {
        return new PolicyKey(key().providerNetworkId(), key().consumerNetworkId(), PolicyCapability.CRAFTING);
    }

    public appeng.api.networking.IGridNode providerNode() {
        return nativeSource.providerNode();
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
