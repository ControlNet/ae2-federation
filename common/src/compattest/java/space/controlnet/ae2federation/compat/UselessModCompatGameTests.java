package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternContainer;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyOperation;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.RuleMode;

/** Federation features with UselessMod's own blocks. */
@PrefixGameTestTemplate(false)
public final class UselessModCompatGameTests {
    private static final String FURNACE = "useless_mod:advanced_alloy_furnace_block";

    private UselessModCompatGameTests() {
    }

    /**
     * The provider network's pattern sits in an Advanced Alloy Furnace, which is its own crafting provider and crafts
     * the pattern itself, with no Molecular Assembler; UselessMod also wraps every push of the consumer's CPU.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void alloyFurnaceCrafting(GameTestHelper helper) {
        var furnace = AddonCraftingScene.block(FURNACE);
        var scene = AddonCraftingScene.structure(helper, FURNACE,
                List.of("ae2:1k_crafting_storage"),
                place -> place.setBlock(AddonCraftingScene.PROVIDER, furnace), AddonCraftingScene.PROVIDER,
                entity -> entity != null && entity.getBlockState().is(furnace));
        helper.succeedWhen(scene::tick);
    }

    /**
     * The guide's example: network B is the furnace alone, with no storage and no power of its own, and network A
     * orders from it. Taking the pattern out of the furnace takes the recipe away from A; putting it back brings it
     * back, and A orders again.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void alloyFurnaceWorkshop(GameTestHelper helper) {
        var furnace = AddonCraftingScene.block(FURNACE);
        var taken = new ItemStack[] {ItemStack.EMPTY};
        var scene = AddonCraftingScene.structure(helper, FURNACE,
                List.of("ae2:1k_crafting_storage"),
                place -> place.setBlock(AddonCraftingScene.PROVIDER, furnace), AddonCraftingScene.PROVIDER,
                entity -> entity != null && entity.getBlockState().is(furnace))
                .providerWithoutStorage()
                .reorderingAfterwards(
                        change -> {
                            var patterns = patterns(change);
                            for (int slot = 0; slot < patterns.size() && taken[0].isEmpty(); slot++) {
                                taken[0] = patterns.extractItem(slot, 1, false);
                            }
                            change.assertFalse(taken[0].isEmpty(), "The furnace must give its pattern back");
                        },
                        restore -> restore.assertTrue(patterns(restore).addItems(taken[0]).isEmpty(),
                                "The furnace must take its pattern back"),
                        (order, cpu, provider) -> {
                        });
        helper.succeedWhen(scene::tick);
    }

    /**
     * A cross-domain chain, once a guide example: the furnace on a workshop network, which also keeps a chest of its
     * own stock; a trading post with nothing but cable, which uses the workshop's Crafting with re-export and its
     * Storage without; and an orderer two Bridges from the workshop, with the CPU, the storage and the only energy
     * cell. The orderer shares no domain and has no rule with the workshop, yet its own CPU orders the furnace's
     * sticks, which come back to its storage; the workshop's stock stays out of its sight. Stepping the post's
     * Crafting back to plain Enabled takes the recipe from the orderer but not from the post; re-export brings it
     * back, and the orderer orders again.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 1200)
    public static void alloyFurnaceTradingPost(GameTestHelper helper) {
        helper.succeedWhen(new TradingPost(helper)::run);
    }

    /** The trading-post chain's stages; see {@link #alloyFurnaceTradingPost}. */
    private static final class TradingPost {
        private static final AEItemKey PLANKS = AEItemKey.of(Items.OAK_PLANKS);
        private static final AEItemKey STICKS = AEItemKey.of(Items.STICK);
        private static final AEItemKey COBBLESTONE = AEItemKey.of(Items.COBBLESTONE);
        private static final BlockPos ORDERER_CHEST = new BlockPos(10, BridgeChainFixture.Y, 3);
        private static final BlockPos FURNACE_POS = new BlockPos(6, BridgeChainFixture.Y, 3);
        private static final BlockPos WORKSHOP_CHEST = new BlockPos(6, BridgeChainFixture.Y, 5);
        private final GameTestHelper helper;
        private final BridgeChainFixture chain;
        private final IActionSource source = IActionSource.empty();
        private int stage;
        private int orders;
        private Future<ICraftingPlan> plan;

        TradingPost(GameTestHelper helper) {
            this.helper = helper;
            chain = new BridgeChainFixture(helper, List.of(
                    new BridgeChainFixture.Network("orderer", 9, 2, 5, AEColor.PURPLE),
                    new BridgeChainFixture.Network("post", 8, 1, 6, AEColor.LIGHT_BLUE),
                    new BridgeChainFixture.Network("workshop", 7, 2, 5, AEColor.GREEN)),
                    List.of(new BridgeChainFixture.Link("orderer", "post", 2),
                            new BridgeChainFixture.Link("post", "workshop", 5)));
            // East of the orderer's cable, touching nothing else: its energy cell, the only one, its chest and its CPU.
            helper.setBlock(ORDERER_CHEST.north(), AEBlocks.CREATIVE_ENERGY_CELL.block());
            helper.setBlock(ORDERER_CHEST, AEBlocks.ME_CHEST.block());
            helper.<MEChestBlockEntity>getBlockEntity(ORDERER_CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
            helper.setBlock(ORDERER_CHEST.south(), AEBlocks.CRAFTING_STORAGE_1K.block());
            // West of the workshop's cable: the furnace and the workshop's own chest, apart from each other.
            helper.setBlock(FURNACE_POS, AddonCraftingScene.block(FURNACE));
            helper.setBlock(WORKSHOP_CHEST, AEBlocks.ME_CHEST.block());
            helper.<MEChestBlockEntity>getBlockEntity(WORKSHOP_CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
        }

        void run() {
            switch (stage) {
                case 0 -> {
                    helper.assertTrue(chain.built(), "Waiting for the networks and Bridges: " + chain.readiness());
                    helper.assertTrue(chain.powered(), "Waiting for the ME power rules to power every network");
                    helper.assertTrue(gridAt(FURNACE_POS) == chain.grid("workshop"), "The furnace must join the workshop");
                    helper.assertFalse(chain.shareDomain("orderer", "workshop"),
                            "The orderer must share no domain with the workshop");
                    helper.assertTrue(patterns(helper, FURNACE_POS).addItems(stickPattern()).isEmpty(),
                            "The furnace must take the stick pattern");
                    helper.assertValueEqual(chest(WORKSHOP_CHEST).insert(COBBLESTONE, 3, Actionable.MODULATE, source), 3L,
                            "The workshop's chest must take its stock");
                    stage = 1;
                    helper.fail("Put the pattern into the furnace");
                }
                case 1 -> {
                    helper.assertTrue(crafts("workshop"), "Waiting for the workshop to offer the furnace's recipe");
                    chain.rule("post", "workshop", PolicyCapability.STORAGE, PolicyRule.storageDefaults());
                    chain.rule("post", "workshop", PolicyCapability.CRAFTING, crafting(RuleMode.REEXPORT));
                    chain.rule("orderer", "post", PolicyCapability.STORAGE, PolicyRule.storageDefaults());
                    chain.rule("orderer", "post", PolicyCapability.CRAFTING, crafting(RuleMode.ENABLED));
                    stage = 2;
                    helper.fail("Set the rules");
                }
                case 2 -> {
                    helper.assertTrue(crafts("orderer"), "Waiting for the furnace's recipe on the orderer");
                    helper.assertTrue(crafts("post"), "The post offers the furnace's recipe too");
                    helper.assertValueEqual(seen("post", COBBLESTONE), 3L, "The post sees the workshop's stock");
                    helper.assertValueEqual(seen("orderer", COBBLESTONE), 0L,
                            "Without Storage re-export, the orderer must not see the workshop's stock");
                    order();
                }
                case 3 -> submit();
                case 4 -> {
                    finished(1);
                    // The exercise: the post's Crafting steps back from re-export to plain Enabled.
                    chain.rule("post", "workshop", PolicyCapability.CRAFTING, crafting(RuleMode.ENABLED));
                    stage = 5;
                    helper.fail("Stepped the post's Crafting back to Enabled");
                }
                case 5 -> {
                    helper.assertFalse(crafts("orderer"), "Waiting for the recipe to leave the orderer");
                    helper.assertTrue(crafts("post"), "The post keeps the recipe under its own rule");
                    chain.rule("post", "workshop", PolicyCapability.CRAFTING, crafting(RuleMode.REEXPORT));
                    stage = 6;
                    helper.fail("Stepped the post's Crafting to re-export again");
                }
                case 6 -> {
                    helper.assertTrue(crafts("orderer"), "Waiting for the recipe to return to the orderer");
                    order();
                }
                default -> {
                    finished(2);
                    chain.close();
                }
            }
        }

        /** Stores two planks in the orderer's chest and plans four sticks on the orderer's own crafting service. */
        private void order() {
            helper.assertValueEqual(chest(ORDERER_CHEST).insert(PLANKS, 2, Actionable.MODULATE, source), 2L,
                    "The orderer's chest must take the planks");
            var node = helper.<MEChestBlockEntity>getBlockEntity(ORDERER_CHEST).getMainNode().getNode();
            plan = chain.grid("orderer").getCraftingService().beginCraftingCalculation(helper.getLevel(),
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
            stage = 3;
            helper.fail("Planning the order");
        }

        private void submit() {
            helper.assertTrue(plan.isDone(), "Waiting for the orderer's plan");
            ICraftingPlan planned;
            try {
                planned = plan.get();
            } catch (InterruptedException | ExecutionException exception) {
                throw new IllegalStateException("The orderer's plan failed", exception);
            }
            helper.assertFalse(planned.simulation(), "The orderer's planks must be enough: " + planned.missingItems());
            helper.assertTrue(chain.grid("orderer").getCraftingService().submitJob(planned, null, null, true, source)
                    .successful(), "The orderer's own CPU must take the job");
            orders++;
            stage = orders == 1 ? 4 : 7;
            helper.fail("Submitted order " + orders);
        }

        /** After {@code count} orders: every stick came back to the orderer's chest, and nothing is owed. */
        private void finished(int count) {
            helper.assertTrue(chain.grid("orderer").getCraftingService().getCpus().stream().noneMatch(cpu -> cpu.isBusy()),
                    "Waiting for the orderer's job to finish");
            helper.assertValueEqual(held(chest(ORDERER_CHEST), STICKS), 4L * count,
                    "Every stick must come back to the orderer's chest");
            helper.assertValueEqual(held(chest(ORDERER_CHEST), PLANKS), 0L, "The orderer's planks were used");
            helper.assertValueEqual(CraftingReturnLedger.get(helper.getLevel()).owed(chain.network("workshop"),
                    chain.network("orderer"), STICKS), 0L, "Nothing stays owed to the orderer");
        }

        private boolean crafts(String network) {
            return chain.grid(network).getCraftingService().isCraftable(STICKS);
        }

        private long seen(String network, AEItemKey what) {
            return chain.grid(network).getStorageService().getInventory().getAvailableStacks().get(what);
        }

        private MEStorage chest(BlockPos position) {
            return helper.<MEChestBlockEntity>getBlockEntity(position).getOriginalCellInventory(0);
        }

        private static long held(MEStorage storage, AEItemKey what) {
            return storage.extract(what, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
        }

        private static PolicyRule crafting(RuleMode mode) {
            return PolicyRule.enabled(Set.of(PolicyOperation.REQUEST)).withMode(mode);
        }

        private IGrid gridAt(BlockPos position) {
            var host = GridHelper.getNodeHost(helper.getLevel(), helper.absolutePos(position));
            if (host == null) return null;
            for (var side : Direction.values()) {
                var node = host.getGridNode(side);
                if (node != null) return node.getGrid();
            }
            return null;
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

    private static InternalInventory patterns(GameTestHelper helper) {
        return patterns(helper, AddonCraftingScene.PROVIDER);
    }

    private static InternalInventory patterns(GameTestHelper helper, BlockPos position) {
        return ((PatternContainer) helper.getLevel().getBlockEntity(helper.absolutePos(position)))
                .getTerminalPatternInventory();
    }
}
