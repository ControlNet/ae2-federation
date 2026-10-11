package space.controlnet.ae2federation.compat;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.parts.AEBasePart;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.cells.ICellWorkbenchItem;
import appeng.api.upgrades.IUpgradeInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture;

/** Actual MEGACells global service; the addon itself generates patterns and executes decompression. */
final class MegaGlobalCraftingScene {
    static void run(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper);
        var ingot = AEItemKey.of(Items.IRON_INGOT);
        var block = AEItemKey.of(Items.IRON_BLOCK);
        var cable = new BlockPos(5, 3, 4);
        var module = new AEBasePart[1];
        int[] phase = {0};
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                helper.assertTrue(fixture.ready(), "Waiting for two native Grids and consumer CPU");
                var bulk = new ItemStack(AddonCraftingScene.item("megacells:bulk_item_cell"));
                ((ICellWorkbenchItem) bulk.getItem()).getConfigInventory(bulk).setStack(0, new GenericStack(block, 1));
                try {
                    var upgrades = (IUpgradeInventory) bulk.getItem().getClass()
                            .getMethod("getUpgrades", ItemStack.class).invoke(bulk.getItem(), bulk);
                    helper.assertTrue(upgrades.addItems(new ItemStack(AddonCraftingScene.item("megacells:compression_card"))).isEmpty(),
                            "The actual Bulk Cell accepts its native Compression Card");
                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(exception);
                }
                helper.<appeng.blockentity.storage.MEChestBlockEntity>getBlockEntity(new BlockPos(5, 3, 3)).setCell(bulk);
                module[0] = (AEBasePart) PartHelper.setPart(helper.getLevel(), helper.absolutePos(cable), Direction.WEST, null,
                        (IPartItem<?>) AddonCraftingScene.item("megacells:decompression_module"));
                helper.assertTrue(module[0] != null, "Install the actual Decompression Module on the provider Grid");
                fixture.setRule(fixture.crafting(), RuleMode.ENABLED);
                fixture.putInConsumer(block, 1);
                phase[0] = 1;
            }
            if (phase[0] == 1) {
                helper.assertTrue(fixture.providerGrid().getCraftingService().isCraftable(ingot),
                        "The addon publishes decompression through its real global registration");
                for (var node : fixture.providerGrid().getNodes()) {
                    var provider = node.getService(ICraftingProvider.class);
                    helper.assertFalse(provider != null && provider.getAvailablePatterns().stream().anyMatch(
                                    pattern -> pattern.getPrimaryOutput().what().equals(ingot)),
                            "No node service provides the iron pattern; this is the official global extension path");
                }
                helper.assertTrue(fixture.consumerService().isCraftable(ingot), "The global pattern crosses the Crafting-only relation");
                helper.assertValueEqual(fixture.held(fixture.consumerChest(), ingot), 0L, "The consumer starts without iron ingots");
                helper.assertValueEqual(fixture.held(fixture.providerChest(), block), 0L, "The factory has no iron to subsidize the result");
                fixture.begin(ingot, 9);
                phase[0] = 2;
            }
            if (phase[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for AE2's actual remote crafting calculation");
                helper.assertFalse(fixture.plan().simulation(), "The actual native plan has all input resources");
                helper.assertTrue(fixture.plan().patternTimes().keySet().stream().anyMatch(MegaGlobalCraftingScene::decompression),
                        "The planner selects the addon's generated decompression recipe, not just a craftable getter");
                helper.assertValueEqual(fixture.plan().patternTimes().size(), 1, "One generated recipe suffices for the order");
                var selected = fixture.plan().patternTimes().keySet().iterator().next();
                helper.assertValueEqual(fixture.consumerProviders(selected).size(), 1,
                        "The single native global provider is projected exactly once");
                helper.assertValueEqual(fixture.plan().usedItems().get(block), 1L, "The native plan consumes exactly one consumer iron block");
                helper.assertTrue(fixture.submit(), "The native consumer CPU accepts the plan");
                phase[0] = 3;
            }
            if (phase[0] == 3) {
                helper.assertValueEqual(fixture.busyConsumerCpus(), 0L, "The real module executes and the native CPU finishes");
                helper.assertValueEqual(fixture.held(fixture.consumerChest(), ingot), 9L, "All nine real outputs return without Storage sharing");
                helper.assertValueEqual(fixture.held(fixture.consumerChest(), block), 0L, "Exactly one input block was consumed");
                helper.assertValueEqual(fixture.held(fixture.providerChest(), ingot), 0L, "No duplicate output stays in the Bulk Cell");
                helper.assertValueEqual(fixture.owed(ingot), 0L, "Actual return settles the decompression debt");
                helper.assertTrue(module[0].getHost().removePart(module[0]), "Remove the actual module through the native part host");
                phase[0] = 4;
            }
            helper.assertFalse(fixture.consumerService().isCraftable(ingot), "Removing the module withdraws its remote generated pattern");
            fixture.close();
        });
    }

    private static boolean decompression(IPatternDetails pattern) {
        return pattern.getClass().getName().equals("gripe._90.megacells.misc.DecompressionPattern")
                && pattern.getPrimaryOutput().what().equals(AEItemKey.of(Items.IRON_INGOT));
    }
}
