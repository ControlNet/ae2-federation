package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.mojang.logging.LogUtils;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Spikes for the CPU route (see .omo/knowledges/remote-crafting-cpu-route-plan-2026-10-26.md): what AE2 does when one
 * real provider object is registered on a second Grid. Two plain AE2 networks, P and C, share no cable and no
 * Federation device; P's Pattern Provider logic is added to C's crafting service as a global provider. Each stage logs
 * what it observes as an {@code AE2F_SPIKE} line; the test only fails when a stage cannot run.
 */
@PrefixGameTestTemplate(false)
public final class CpuRouteSpikeGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();

    private CpuRouteSpikeGameTests() {
    }

    /** An AE2 Pattern Provider registered on its own Grid and on a second one. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 1500)
    public static void dualRegisteredPatternProvider(GameTestHelper helper) {
        helper.succeedWhen(new DualRegistration(helper)::run);
    }

    private static final class DualRegistration {
        private static final int Y = BridgeChainFixture.Y;
        private static final AEItemKey PLANKS = AEItemKey.of(Items.OAK_PLANKS);
        private static final AEItemKey STICKS = AEItemKey.of(Items.STICK);
        // P: energy with a CPU south of it, provider with an assembler south of it, chest. C, four blocks further south:
        // energy, chest, CPU.
        private static final BlockPos P_ENERGY = new BlockPos(1, Y, 1);
        private static final BlockPos P_CPU = new BlockPos(1, Y, 2);
        private static final BlockPos PROVIDER = new BlockPos(2, Y, 1);
        private static final BlockPos ASSEMBLER = new BlockPos(2, Y, 2);
        private static final BlockPos P_CHEST = new BlockPos(3, Y, 1);
        private static final BlockPos C_ENERGY = new BlockPos(1, Y, 6);
        private static final BlockPos C_CHEST = new BlockPos(2, Y, 6);
        private static final BlockPos C_CPU = new BlockPos(3, Y, 6);

        private final GameTestHelper helper;
        private final IActionSource source = IActionSource.empty();
        private PatternProviderLogic logic;
        private IGrid provider;
        private IGrid consumer;
        private ItemStack pattern = ItemStack.EMPTY;
        private Future<ICraftingPlan> consumerPlan;
        private Future<ICraftingPlan> providerPlan;
        private int stage;
        private long since;
        private long firstOutput = -1;

        DualRegistration(GameTestHelper helper) {
            this.helper = helper;
            helper.setBlock(P_ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
            helper.setBlock(P_CPU, AEBlocks.CRAFTING_STORAGE_1K.block());
            helper.setBlock(PROVIDER, AEBlocks.PATTERN_PROVIDER.block());
            helper.setBlock(ASSEMBLER, AEBlocks.MOLECULAR_ASSEMBLER.block());
            helper.setBlock(P_CHEST, AEBlocks.ME_CHEST.block());
            helper.<MEChestBlockEntity>getBlockEntity(P_CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
            helper.setBlock(C_ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
            helper.setBlock(C_CHEST, AEBlocks.ME_CHEST.block());
            helper.<MEChestBlockEntity>getBlockEntity(C_CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
            helper.setBlock(C_CPU, AEBlocks.CRAFTING_STORAGE_1K.block());
        }

        void run() {
            switch (stage) {
                case 0 -> {
                    provider = grid(PROVIDER);
                    consumer = grid(C_CHEST);
                    helper.assertTrue(provider != null && consumer != null, "Waiting for both Grids");
                    helper.assertTrue(provider != consumer, "P and C must be two Grids");
                    helper.assertTrue(provider.getEnergyService().isNetworkPowered()
                            && consumer.getEnergyService().isNetworkPowered(), "Waiting for power");
                    helper.assertFalse(provider.getCraftingService().getCpus().isEmpty()
                            || consumer.getCraftingService().getCpus().isEmpty(), "Waiting for both CPUs");
                    logic = helper.<PatternProviderBlockEntity>getBlockEntity(PROVIDER).getLogic();
                    pattern = stickPattern();
                    helper.assertTrue(logic.getPatternInv().addItems(pattern.copy()).isEmpty(),
                            "The provider must take the pattern");
                    next(1, "Put the pattern into P's provider");
                }
                case 1 -> {
                    helper.assertTrue(provider.getCraftingService().isCraftable(STICKS), "Waiting for P's pattern");
                    consumer.getCraftingService().addGlobalCraftingProvider(logic);
                    next(2, "Registered P's provider logic on C");
                }
                case 2 -> {
                    if (!consumer.getCraftingService().isCraftable(STICKS)) {
                        helper.assertTrue(age() < 100, "C never offered the pattern after addGlobalCraftingProvider");
                        helper.fail("Waiting for C to offer the pattern");
                    }
                    finding("registration", "C offers the pattern " + age() + " ticks after addGlobalCraftingProvider");
                    consumerPlan = order(consumer, C_CHEST);
                    next(3, "Ordered on C");
                }
                case 3 -> {
                    submit(consumer, consumerPlan);
                    next(4, "Submitted on C");
                }
                case 4 -> {
                    long pSticks = held(chest(P_CHEST), STICKS);
                    long cSticks = held(chest(C_CHEST), STICKS);
                    // Until the sticks show up somewhere and then 40 more ticks, or 200 ticks at most.
                    if (pSticks + cSticks > 0 && firstOutput < 0) firstOutput = helper.getTick();
                    if (age() < 200 && (firstOutput < 0 || helper.getTick() - firstOutput < 40)) {
                        helper.fail("Waiting for the outputs");
                    }
                    finding("return", "after " + age() + " ticks: sticks in P's chest " + pSticks
                            + ", sticks in C's chest " + cSticks + ", C's CPU busy " + busy(consumer)
                            + ", provider busy " + logic.isBusy());
                    cancelJobs(consumer);
                    for (int slot = 0; slot < logic.getPatternInv().size(); slot++) {
                        logic.getPatternInv().extractItem(slot, 64, false);
                    }
                    next(5, "Took the pattern out of P's provider");
                }
                case 5 -> {
                    helper.assertFalse(provider.getCraftingService().isCraftable(STICKS),
                            "Waiting for P to drop the pattern");
                    helper.assertTrue(age() >= 20, "Giving C 20 ticks to drop the pattern by itself");
                    finding("update", "20 ticks after the pattern left P's provider, C still offers it: "
                            + consumer.getCraftingService().isCraftable(STICKS));
                    consumer.getCraftingService().refreshGlobalCraftingProvider(logic);
                    next(6, "Refreshed the global provider on C");
                }
                case 6 -> {
                    helper.assertTrue(age() >= 2, "Waiting for C's refresh");
                    finding("update", "after refreshGlobalCraftingProvider, C offers it: "
                            + consumer.getCraftingService().isCraftable(STICKS));
                    helper.assertTrue(logic.getPatternInv().addItems(pattern.copy()).isEmpty(),
                            "The provider must take the pattern back");
                    consumer.getCraftingService().refreshGlobalCraftingProvider(logic);
                    next(7, "Put the pattern back and refreshed C");
                }
                case 7 -> {
                    helper.assertTrue(consumer.getCraftingService().isCraftable(STICKS)
                            && provider.getCraftingService().isCraftable(STICKS), "Waiting for both to offer it again");
                    // The first job's sticks went to P's chest; P's order must craft, not take them.
                    chest(P_CHEST).extract(STICKS, Long.MAX_VALUE, Actionable.MODULATE, source);
                    providerPlan = order(provider, P_CHEST);
                    consumerPlan = order(consumer, C_CHEST);
                    next(8, "Ordered on P and on C");
                }
                case 8 -> {
                    submit(provider, providerPlan);
                    submit(consumer, consumerPlan);
                    next(9, "Submitted on P and on C in the same tick");
                }
                case 9 -> {
                    helper.assertTrue(age() >= 150, "Giving both jobs 150 ticks");
                    finding("concurrent", "150 ticks after both orders: P's CPU busy " + busy(provider)
                            + ", C's CPU busy " + busy(consumer) + ", sticks in P's chest "
                            + held(chest(P_CHEST), STICKS) + ", sticks in C's chest "
                            + held(chest(C_CHEST), STICKS));
                    cancelJobs(provider);
                    cancelJobs(consumer);
                    next(10, "Cancelled both jobs");
                }
                case 10 -> {
                    helper.assertTrue(age() >= 2, "Settling the cancelled jobs");
                    helper.destroyBlock(PROVIDER);
                    next(11, "Broke P's provider");
                }
                case 11 -> {
                    helper.assertTrue(age() >= 20, "Giving C 20 ticks after the provider broke");
                    finding("removal", "20 ticks after the provider broke, C still offers it: "
                            + consumer.getCraftingService().isCraftable(STICKS));
                    if (!consumer.getCraftingService().isCraftable(STICKS)) {
                        consumer.getCraftingService().removeGlobalCraftingProvider(logic);
                        next(15, "C dropped the broken provider by itself");
                    }
                    consumerPlan = order(consumer, C_CHEST);
                    next(12, "Ordered on C from the broken provider");
                }
                case 12 -> {
                    submit(consumer, consumerPlan);
                    next(13, "Submitted on C");
                }
                case 13 -> {
                    helper.assertTrue(age() >= 60, "Giving C's CPU 60 ticks with the broken provider");
                    finding("removal", "60 ticks after ordering from the broken provider: C's CPU busy "
                            + busy(consumer));
                    long before = held(chest(C_CHEST), PLANKS);
                    cancelJobs(consumer);
                    firstOutput = before;
                    next(14, "Cancelled C's job");
                }
                case 14 -> {
                    // The CPU takes its inputs from storage when the job starts; a cancelled job gives back the unpushed.
                    helper.assertTrue(age() >= 2, "Waiting for the cancelled job's inputs");
                    long back = held(chest(C_CHEST), PLANKS) - firstOutput;
                    finding("removal", "cancelling gave C's chest back " + back + " of the job's 2 planks");
                    consumer.getCraftingService().removeGlobalCraftingProvider(logic);
                    next(15, "Removed the global provider");
                }
                default -> helper.assertTrue(age() >= 2, "Settling");
            }
        }

        private void next(int stage, String message) {
            this.stage = stage;
            since = helper.getTick();
            helper.fail(message);
        }

        private long age() {
            return helper.getTick() - since;
        }

        private void finding(String topic, String text) {
            LOGGER.info("AE2F_SPIKE dualRegisteredPatternProvider {}: {}", topic, text);
        }

        /** Stores two planks in {@code chest} and plans one stick craft on {@code grid}'s own crafting service. */
        private Future<ICraftingPlan> order(IGrid grid, BlockPos chest) {
            helper.assertValueEqual(chest(chest).insert(PLANKS, 2, Actionable.MODULATE, source), 2L,
                    "The chest must take the planks");
            var node = helper.<MEChestBlockEntity>getBlockEntity(chest).getMainNode().getNode();
            return grid.getCraftingService().beginCraftingCalculation(helper.getLevel(),
                    new ICraftingSimulationRequester() {
                        @Override
                        public IActionSource getActionSource() {
                            return source;
                        }

                        @Override
                        public IGridNode getGridNode() {
                            return node;
                        }
                    }, STICKS, 4, CalculationStrategy.REPORT_MISSING_ITEMS);
        }

        private void submit(IGrid grid, Future<ICraftingPlan> plan) {
            helper.assertTrue(plan.isDone(), "Waiting for the plan");
            ICraftingPlan planned;
            try {
                planned = plan.get();
            } catch (InterruptedException | ExecutionException exception) {
                throw new IllegalStateException("The plan failed", exception);
            }
            helper.assertFalse(planned.simulation(), "The planks must be enough: " + planned.missingItems());
            var result = grid.getCraftingService().submitJob(planned, null, null, true, source);
            helper.assertTrue(result.successful(), "The CPU must take the job: " + result.errorCode());
        }

        private static boolean busy(IGrid grid) {
            return grid.getCraftingService().getCpus().stream().anyMatch(cpu -> cpu.isBusy());
        }

        private static void cancelJobs(IGrid grid) {
            for (var cpu : grid.getCraftingService().getCpus()) {
                if (cpu.isBusy()) cpu.cancelJob();
            }
        }

        private IGrid grid(BlockPos position) {
            var entity = helper.getLevel().getBlockEntity(helper.absolutePos(position));
            var node = entity instanceof PatternProviderBlockEntity providerEntity
                    ? providerEntity.getMainNode().getNode()
                    : ((MEChestBlockEntity) entity).getMainNode().getNode();
            return node == null ? null : node.getGrid();
        }

        private MEStorage chest(BlockPos position) {
            return helper.<MEChestBlockEntity>getBlockEntity(position).getOriginalCellInventory(0);
        }

        private static long held(MEStorage storage, AEItemKey what) {
            return storage.extract(what, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
        }

        /** Two planks above each other make four sticks, as a player encodes it. */
        private ItemStack stickPattern() {
            var items = NonNullList.withSize(9, ItemStack.EMPTY);
            items.set(0, new ItemStack(Items.OAK_PLANKS));
            items.set(3, new ItemStack(Items.OAK_PLANKS));
            var input = CraftingInput.of(3, 3, items);
            var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                    .orElseThrow();
            return PatternDetailsHelper.encodeCraftingPattern(recipe, items.toArray(ItemStack[]::new),
                    recipe.value().assemble(input, helper.getLevel().registryAccess()), false, false);
        }
    }
}
