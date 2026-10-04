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
    private final BlockPos providerPos;
    private final BlockPos assemblerPos;
    private final BlockPos cpuPos;

    private final GameTestHelper helper;
    private final boolean withCpu;
    private final boolean withForbiddenPattern;
    private int placementStage;
    private boolean patternInstalled;
    private boolean replacementStarted;

    CraftingNativeSourceFixture(GameTestHelper helper, boolean withCpu, boolean withForbiddenPattern) {
        this(helper, new BlockPos(5, 3, 5), withCpu, withForbiddenPattern);
    }

    /** @param base the Bridge's cable position; the provider, assembler and CPU are placed relative to it */
    CraftingNativeSourceFixture(GameTestHelper helper, BlockPos base, boolean withCpu, boolean withForbiddenPattern) {
        this.helper = helper;
        providerPos = base.east(2).north();
        assemblerPos = providerPos.east();
        cpuPos = base.north(3);
        this.withCpu = withCpu;
        this.withForbiddenPattern = withForbiddenPattern;
    }

    boolean advanceInitialPlacement(NetworkId networkId) {
        if (placementStage == 0) {
            helper.setBlock(providerPos, AEBlocks.PATTERN_PROVIDER.block());
            helper.setBlock(assemblerPos, AEBlocks.MOLECULAR_ASSEMBLER.block());
            provider().getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
            assembler().getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
            BlockOrientation.EAST_UP.setOn(helper.getLevel(), helper.absolutePos(providerPos));
            placementStage = 1;
            return false;
        }
        if (placementStage == 1) {
            if (withCpu) {
                helper.setBlock(cpuPos, AEBlocks.CRAFTING_STORAGE_1K.block());
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
        helper.setBlock(providerPos, Blocks.AIR);
    }

    void removeCpu() {
        helper.setBlock(cpuPos, Blocks.AIR);
    }

    void beginReplacement(NetworkId networkId) {
        helper.setBlock(providerPos, AEBlocks.PATTERN_PROVIDER.block());
        provider().getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
        BlockOrientation.EAST_UP.setOn(helper.getLevel(), helper.absolutePos(providerPos));
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
        return helper.getBlockEntity(providerPos);
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
        return helper.getBlockEntity(assemblerPos);
    }

    void placeCpu(NetworkId networkId) {
        helper.setBlock(cpuPos, AEBlocks.CRAFTING_STORAGE_1K.block());
        cpu().getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", networkId));
    }

    BlockPos cpuPosition() {
        return cpuPos;
    }

    private CraftingBlockEntity cpu() {
        return helper.getBlockEntity(cpuPos);
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
