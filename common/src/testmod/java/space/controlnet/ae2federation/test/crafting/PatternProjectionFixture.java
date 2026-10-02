package space.controlnet.ae2federation.test.crafting;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.world.BlockEntityReload;

/**
 * Two networks joined by a Bridge: the consumer (the Bridge's main side) has a CPU and an ME chest; the provider (its
 * outer side) has an ME chest and no CPU, with two pattern providers. One holds a sticks crafting pattern beside a
 * Molecular Assembler. The other holds a processing pattern (cobblestone to stone) and faces a plain chest, a machine
 * the test runs by hand: it takes the inputs from the chest and puts the outputs into the provider network itself, as
 * an import bus or a second provider would, so late, partial and surplus returns can be tested.
 */
public final class PatternProjectionFixture implements AutoCloseable {
    private static final BlockPos BASE = new BlockPos(5, 3, 5);

    private final GameTestHelper helper;
    private final PolicyBridgeFixtures bridge;
    private final CraftingNativeSourceFixture assembler;
    private final BlockPos consumerCpuPos = BASE.west();
    private final BlockPos processingProviderPos = BASE.east().north(2);
    private final BlockPos machinePos = BASE.east().north(3);
    private boolean bridgePlaced;
    private boolean consumerCpuPlaced;
    private boolean processingPlaced;
    private boolean processingPatternInstalled;
    private Future<ICraftingPlan> planFuture;
    private ICraftingPlan plan;

    public PatternProjectionFixture(GameTestHelper helper) {
        this.helper = helper;
        assembler = new CraftingNativeSourceFixture(helper, BASE, false, false);
        bridge = new PolicyBridgeFixtures(helper, BASE);
        bridge.installStorageCells();
    }

    /** Advances the placement one step at a time; true once both networks and every provider are ready. */
    public boolean ready() {
        if (!bridge.networksSettled() || !assembler.advanceInitialPlacement(bridge.outerNetwork())) return false;
        if (!processingPlaced) {
            // It pushes only north, into the machine, and joins the provider network through its other sides.
            helper.setBlock(processingProviderPos, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState().setValue(
                    appeng.block.crafting.PatternProviderBlock.PUSH_DIRECTION, appeng.block.crafting.PushDirection.NORTH));
            helper.setBlock(machinePos, Blocks.CHEST);
            processingProvider().getMainNode().loadFromNBT(
                    NetworkIdentityNodeSeed.managedNode("proxy", bridge.outerNetwork()));
            processingPlaced = true;
            return false;
        }
        if (!consumerCpuPlaced) {
            helper.setBlock(consumerCpuPos, AEBlocks.CRAFTING_STORAGE_1K.block());
            helper.<CraftingBlockEntity>getBlockEntity(consumerCpuPos).getMainNode()
                    .loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", bridge.mainNetwork()));
            consumerCpuPlaced = true;
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
        if (processingProvider().getMainNode().getGrid() != providerGrid()) return false;
        if (!processingPatternInstalled) {
            processingProvider().getLogic().getPatternInv().addItems(stonePattern());
            processingProvider().getLogic().updatePatterns();
            processingPatternInstalled = true;
        }
        return assembler.initialReady(providerGrid()) && providerGrid().getCraftingService().isCraftable(stone())
                && !consumerService().getCpus().isEmpty()
                && FederationDomainRegistryAccess.confirmedNetworkId(consumerGrid()).isPresent()
                && FederationDomainRegistryAccess.confirmedNetworkId(providerGrid()).isPresent();
    }

    public PolicyKey crafting() {
        return new PolicyKey(bridge.mainNetwork(), bridge.outerNetwork(), PolicyCapability.CRAFTING);
    }

    public PolicyKey storage() {
        return new PolicyKey(bridge.mainNetwork(), bridge.outerNetwork(), PolicyCapability.STORAGE);
    }

    /** The consumer uses the provider's crafting, with the storage rule crafting needs, in one edit. */
    public void enableRules() {
        var policies = PolicyService.get(helper.getLevel());
        accepted(policies.editAll(List.of(
                new PolicyEdit(storage(), policies.revision(storage()), PolicyRule.storageDefaults()),
                new PolicyEdit(crafting(), policies.revision(crafting()),
                        PolicyRule.enabled(Set.of(PolicyOperation.REQUEST))))));
    }

    /** Sets one rule's mode directly through the policy service, as an API caller can without the pair editor. */
    public void setRule(PolicyKey key, RuleMode mode) {
        var policies = PolicyService.get(helper.getLevel());
        var rule = policies.configured(key).map(record -> record.rule()).orElseGet(() -> key.capability()
                == PolicyCapability.STORAGE ? PolicyRule.storageDefaults()
                : PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)));
        accepted(policies.edit(new PolicyEdit(key, policies.revision(key), rule.withMode(mode))));
    }

    public int projections() {
        return CraftingProjectionService.get(helper.getLevel()).projectionCount(crafting());
    }

    public java.util.Optional<CraftingProjectionService.Status> status() {
        return CraftingProjectionService.status(helper.getLevel(), crafting());
    }

    public boolean providerRouted() {
        return CraftingProjectionService.get(helper.getLevel()).routes(providerGrid());
    }

    public long owed(AEKey key) {
        return CraftingReturnLedger.get(helper.getLevel()).owed(bridge.outerNetwork(), bridge.mainNetwork(), key);
    }

    /** Plans {@code amount} of {@code what} on the consumer's own crafting service, as its ME Terminal does. */
    public void begin(AEKey what, long amount) {
        var node = bridge.consumerChest().getMainNode().getNode();
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
        planFuture = consumerService().beginCraftingCalculation(helper.getLevel(), requester, what, amount,
                CalculationStrategy.REPORT_MISSING_ITEMS);
    }

    public boolean planReady() {
        if (plan != null) return true;
        if (planFuture == null || !planFuture.isDone()) return false;
        try {
            plan = planFuture.get();
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Crafting calculation interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Crafting calculation failed", exception);
        }
    }

    public ICraftingPlan plan() {
        return plan;
    }

    public boolean submit() {
        return consumerService().submitJob(plan, null, null, true, IActionSource.empty()).successful();
    }

    public long busyConsumerCpus() {
        return consumerService().getCpus().stream().filter(cpu -> cpu.isBusy()).count();
    }

    public void cancelConsumerJob() {
        consumerService().getCpus().stream().filter(cpu -> cpu.isBusy()).map(CraftingCPUCluster.class::cast)
                .forEach(CraftingCPUCluster::cancelJob);
    }

    /** What the hand-run machine holds: the inputs the processing provider pushed into it. */
    public long inMachine(net.minecraft.world.item.Item item) {
        var chest = helper.<ChestBlockEntity>getBlockEntity(machinePos);
        long count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            if (chest.getItem(slot).is(item)) count += chest.getItem(slot).getCount();
        }
        return count;
    }

    /** Runs the machine: empties it, and puts {@code outputs} stone into the provider network as its output. */
    public long runMachine(long outputs) {
        var chest = helper.<ChestBlockEntity>getBlockEntity(machinePos);
        chest.clearContent();
        return providerGrid().getStorageService().getInventory().insert(stone(), outputs, Actionable.MODULATE,
                IActionSource.empty());
    }

    public void putInConsumer(AEKey what, long amount) {
        helper.assertValueEqual(consumerChest().insert(what, amount, Actionable.MODULATE, IActionSource.empty()),
                amount, "The consumer's chest must take the materials");
    }

    public void putInProvider(AEKey what, long amount) {
        helper.assertValueEqual(providerChest().insert(what, amount, Actionable.MODULATE, IActionSource.empty()),
                amount, "The provider's chest must take the materials");
    }

    /** What the consumer's own ME chest holds. */
    public MEStorage consumerChest() {
        return Objects.requireNonNull(bridge.consumerChest().getOriginalCellInventory(0));
    }

    /** What the provider's own ME chest holds. */
    public MEStorage providerChest() {
        return Objects.requireNonNull(bridge.providerChest().getOriginalCellInventory(0));
    }

    public long held(MEStorage storage, AEKey what) {
        return storage.extract(what, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    public ICraftingService consumerService() {
        return consumerGrid().getCraftingService();
    }

    public IGrid consumerGrid() {
        return bridge.mainGrid();
    }

    public IGrid providerGrid() {
        return bridge.outerGrid();
    }

    /**
     * Unloads and reloads every block of both networks in one tick, as a server restart does, so AE2 builds both Grids
     * anew from the saved data, the consumer's running job included.
     */
    public void reloadAll() {
        BlockEntityReload.reload(helper, BASE.offset(-1, -1, -3), BASE.offset(3, 1, 2));
        bridge.refreshFirstBridgePart();
    }

    /** True once both networks stand again after {@link #reloadAll} and the Bridge links them. */
    public boolean reloaded() {
        if (!bridge.networksSettled()) return false;
        if (!bridge.firstBridgeReady()) {
            bridge.refreshFirstBridge();
            return false;
        }
        return processingProvider().getMainNode().getGrid() == providerGrid()
                && consumerService().getCpus().stream().anyMatch(cpu -> cpu.isBusy());
    }

    public static AEItemKey planks() {
        return AEItemKey.of(Items.OAK_PLANKS);
    }

    public static AEItemKey sticks() {
        return AEItemKey.of(Items.STICK);
    }

    public static AEItemKey cobblestone() {
        return AEItemKey.of(Items.COBBLESTONE);
    }

    public static AEItemKey stone() {
        return AEItemKey.of(Items.STONE);
    }

    private PatternProviderBlockEntity processingProvider() {
        return helper.getBlockEntity(processingProviderPos);
    }

    private static ItemStack stonePattern() {
        return PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(cobblestone(), 1)),
                List.of(new GenericStack(stone(), 1)));
    }

    private static void accepted(PolicyMutationResult result) {
        if (!(result instanceof PolicyMutationResult.Accepted)) {
            throw new IllegalStateException("Rule edit refused: " + result);
        }
    }

    @Override
    public void close() {
        bridge.close();
    }
}
