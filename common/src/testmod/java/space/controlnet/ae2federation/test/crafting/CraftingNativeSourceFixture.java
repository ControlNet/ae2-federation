package space.controlnet.ae2federation.test.crafting;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.orientation.BlockOrientation;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.identity.NetworkIdentityService;

final class CraftingNativeSourceFixture {
    private static final BlockPos BASE = new BlockPos(5, 3, 5);
    private static final BlockPos PROVIDER = BASE.east(2).north();
    private static final BlockPos ASSEMBLER = PROVIDER.east();
    private static final BlockPos CPU = BASE.north(3);

    private final GameTestHelper helper;
    private final boolean withCpu;
    private final boolean withForbiddenPattern;
    private int placementStage;
    private boolean patternInstalled;
    private boolean replacementStarted;

    CraftingNativeSourceFixture(GameTestHelper helper, boolean withCpu, boolean withForbiddenPattern) {
        this.helper = helper;
        this.withCpu = withCpu;
        this.withForbiddenPattern = withForbiddenPattern;
    }

    boolean advanceInitialPlacement(NetworkId networkId) {
        if (placementStage == 0) {
            helper.setBlock(PROVIDER, AEBlocks.PATTERN_PROVIDER.block());
            helper.setBlock(ASSEMBLER, AEBlocks.MOLECULAR_ASSEMBLER.block());
            provider().getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
            assembler().getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
            BlockOrientation.EAST_UP.setOn(helper.getLevel(), helper.absolutePos(PROVIDER));
            placementStage = 1;
            return false;
        }
        if (placementStage == 1) {
            if (withCpu) {
                helper.setBlock(CPU, AEBlocks.CRAFTING_STORAGE_1K.block());
                cpu().getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
            }
            placementStage = 2;
            return false;
        }
        return true;
    }

    boolean initialReady(IGrid sourceGrid) {
        if (provider().getMainNode().getGrid() != sourceGrid
                || withCpu && sourceGrid.getCraftingService().getCpus().isEmpty()) {
            return false;
        }
        if (!patternInstalled) {
            installPattern();
        }
        return sourceGrid.getCraftingService().isCraftable(outputKey())
                && (!withForbiddenPattern || sourceGrid.getCraftingService().isCraftable(forbiddenOutputKey()));
    }

    void removeProvider() {
        helper.setBlock(PROVIDER, Blocks.AIR);
    }

    void removeCpu() {
        helper.setBlock(CPU, Blocks.AIR);
    }

    void beginReplacement(NetworkId networkId) {
        helper.setBlock(PROVIDER, AEBlocks.PATTERN_PROVIDER.block());
        provider().getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
        BlockOrientation.EAST_UP.setOn(helper.getLevel(), helper.absolutePos(PROVIDER));
        replacementStarted = true;
        patternInstalled = false;
    }

    boolean replacementReady(IGrid sourceGrid) {
        if (!replacementStarted || provider().getMainNode().getGrid() != sourceGrid) {
            return false;
        }
        if (!patternInstalled) {
            installPattern();
            return false;
        }
        return sourceGrid.getCraftingService().isCraftable(outputKey());
    }

    PatternProviderBlockEntity provider() {
        return helper.getBlockEntity(PROVIDER);
    }

    appeng.api.networking.IGridNode providerNode() {
        return provider().getMainNode().getNode();
    }

    UUID providerNodeId(IGrid sourceGrid) {
        return sourceGrid.getService(NetworkIdentityService.class).lineage(providerNode()).nodeId();
    }

    int placementStage() {
        return placementStage;
    }

    boolean patternInstalled() {
        return patternInstalled;
    }

    private void installPattern() {
        provider().getLogic().getPatternInv().addItems(stickPattern());
        if (withForbiddenPattern) {
            provider().getLogic().getPatternInv().addItems(craftingTablePattern());
        }
        provider().getLogic().updatePatterns();
        patternInstalled = true;
    }

    private MolecularAssemblerBlockEntity assembler() {
        return helper.getBlockEntity(ASSEMBLER);
    }

    private CraftingBlockEntity cpu() {
        return helper.getBlockEntity(CPU);
    }

    private ItemStack stickPattern() {
        var items = NonNullList.withSize(9, ItemStack.EMPTY);
        items.set(0, new ItemStack(Items.OAK_PLANKS));
        items.set(3, new ItemStack(Items.OAK_PLANKS));
        var input = CraftingInput.of(3, 3, items);
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input,
                helper.getLevel()).orElseThrow();
        return PatternDetailsHelper.encodeCraftingPattern(recipe, items.toArray(ItemStack[]::new),
                recipe.value().assemble(input, helper.getLevel().registryAccess()), false, false);
    }

    private ItemStack craftingTablePattern() {
        var items = NonNullList.withSize(9, ItemStack.EMPTY);
        items.set(0, new ItemStack(Items.OAK_PLANKS));
        items.set(1, new ItemStack(Items.OAK_PLANKS));
        items.set(3, new ItemStack(Items.OAK_PLANKS));
        items.set(4, new ItemStack(Items.OAK_PLANKS));
        var input = CraftingInput.of(3, 3, items);
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input,
                helper.getLevel()).orElseThrow();
        return PatternDetailsHelper.encodeCraftingPattern(recipe, items.toArray(ItemStack[]::new),
                recipe.value().assemble(input, helper.getLevel().registryAccess()), false, false);
    }

    private static AEItemKey outputKey() {
        return AEItemKey.of(Items.STICK);
    }

    private static AEItemKey forbiddenOutputKey() {
        return AEItemKey.of(Items.CRAFTING_TABLE);
    }
}
