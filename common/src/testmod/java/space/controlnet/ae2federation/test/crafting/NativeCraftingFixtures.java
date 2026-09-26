package space.controlnet.ae2federation.test.crafting;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;

public final class NativeCraftingFixtures implements AutoCloseable {
    private static final BlockPos CHEST_POS = new BlockPos(1, 2, 1);
    private static final BlockPos ENERGY_POS = CHEST_POS.west();
    private static final BlockPos CPU_POS = CHEST_POS.east();
    private static final BlockPos PROVIDER_POS = CHEST_POS.south();
    private static final BlockPos ASSEMBLER_POS = PROVIDER_POS.south();
    private static final BlockPos REQUESTER_POS = CHEST_POS.above();
    private final GameTestHelper helper;
    private final boolean withCpu;
    private Future<ICraftingPlan> planFuture;
    private ICraftingPlan plan;
    private ICraftingSubmitResult submitResult;
    private ICraftingLink submittedLink;
    private IActionSource calculationSource;
    private NativeCraftingRequester requester;
    private final Set<String> observedCpuJobIds = new TreeSet<>();
    private boolean initialized;

    public NativeCraftingFixtures(GameTestHelper helper, boolean withCpu) {
        this.helper = helper;
        this.withCpu = withCpu;
        helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(CHEST_POS, AEBlocks.ME_CHEST.block());
        if (withCpu) {
            helper.setBlock(CPU_POS, AEBlocks.CRAFTING_STORAGE_1K.block());
        }
        helper.setBlock(PROVIDER_POS, AEBlocks.PATTERN_PROVIDER.block());
        helper.setBlock(ASSEMBLER_POS, AEBlocks.MOLECULAR_ASSEMBLER.block());
        var cell = AEItems.ITEM_CELL_1K.stack();
        helper.assertTrue(StorageCells.getCellInventory(cell, null) != null, "Native item cell must resolve");
        chest().setCell(cell);
    }

    public boolean ready() {
        var node = chest().getMainNode().getNode();
        if (node == null || !node.hasGridBooted() || !node.isActive()) {
            return false;
        }
        if (withCpu && (cpu().getCluster() == null || service().getCpus().isEmpty())) {
            return false;
        }
        if (!initialized) {
            provider().getLogic().getPatternInv().addItems(stickPattern());
            provider().getLogic().updatePatterns();
            initialized = true;
        }
        return service().isCraftable(outputKey()) && provider().getLogic().getAvailablePatterns().size() == 1;
    }

    public void insertMaterials(long amount) {
        helper.assertValueEqual(gridInventory().insert(inputKey(), amount, Actionable.MODULATE,
                IActionSource.empty()), amount, "Native chest must accept fixture materials");
    }

    public long materialAmount() {
        return gridInventory().extract(inputKey(), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    public long outputAmount() {
        return gridInventory().extract(outputKey(), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    public void begin(CalculationStrategy strategy, long amount, IActionSource source) {
        if (planFuture == null) {
            calculationSource = source;
            var gridNode = chest().getMainNode().getNode();
            var simulationRequester = new ICraftingSimulationRequester() {
                @Override
                public IActionSource getActionSource() {
                    return source;
                }

                @Override
                public appeng.api.networking.IGridNode getGridNode() {
                    return gridNode;
                }
            };
            planFuture = service().beginCraftingCalculation(helper.getLevel(), simulationRequester, outputKey(),
                    amount, strategy);
        }
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

    public ICraftingSubmitResult submit(NativeCraftingRequester target) {
        if (submitResult == null) {
            var result = service().submitJob(plan, target, null, true,
                    target == null ? calculationSource : target.actionSource());
            submitResult = result;
            submittedLink = result.link();
            service().getCpus().stream()
                    .filter(cpu -> cpu.isBusy())
                    .map(CraftingCPUCluster.class::cast)
                    .map(cpu -> cpu.craftingLogic.getLastLink())
                    .filter(link -> link != null)
                    .map(link -> link.getCraftingID().toString())
                    .forEach(observedCpuJobIds::add);
        }
        return submitResult;
    }

    public int uniqueNativeJobCount() { return observedCpuJobIds.size(); }

    public String nativeJobIds() { return String.join(",", observedCpuJobIds); }

    public ICraftingLink submittedLink() { return submittedLink; }

    public ICraftingPlan plan() { return plan; }

    public boolean assemblerReceivedInput() {
        var inventory = assembler().getInternalInventory();
        for (int slot = 0; slot < 9; slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    public boolean cpuBusy() { return service().getCpus().stream().anyMatch(cpu -> cpu.isBusy()); }

    public void suspendCpu() {
        service().getCpus().stream()
                .filter(cpu -> cpu.isBusy())
                .map(CraftingCPUCluster.class::cast)
                .forEach(cpu -> cpu.craftingLogic.setJobSuspended(true));
    }

    public long cpuMaterialAmount() {
        return service().getCpus().stream()
                .filter(cpu -> cpu.isBusy())
                .map(CraftingCPUCluster.class::cast)
                .mapToLong(cpu -> cpu.craftingLogic.getStored(inputKey()))
                .sum();
    }

    public int cpuCount() { return service().getCpus().size(); }

    public NativeCraftingRequester createRequester() {
        requester = new NativeCraftingRequester(helper.getLevel(), helper.absolutePos(REQUESTER_POS),
                chest().getInventory());
        requester.connect(chest().getMainNode().getNode());
        return requester;
    }

    public NativeCraftingRequester replaceRequester(CompoundTag linkData) {
        requester = new NativeCraftingRequester(helper.getLevel(), helper.absolutePos(REQUESTER_POS),
                chest().getInventory(), null, linkData);
        requester.connect(chest().getMainNode().getNode());
        return requester;
    }

    public appeng.api.networking.crafting.ICraftingService service() {
        return chest().getMainNode().getGrid().getCraftingService();
    }

    private appeng.api.storage.MEStorage gridInventory() {
        return chest().getMainNode().getGrid().getStorageService().getInventory();
    }

    public MEChestBlockEntity chest() { return helper.getBlockEntity(CHEST_POS); }

    public PatternProviderBlockEntity provider() { return helper.getBlockEntity(PROVIDER_POS); }

    public MolecularAssemblerBlockEntity assembler() { return helper.getBlockEntity(ASSEMBLER_POS); }

    private CraftingBlockEntity cpu() { return helper.getBlockEntity(CPU_POS); }

    private ItemStack stickPattern() {
        var items = NonNullList.withSize(9, ItemStack.EMPTY);
        items.set(0, new ItemStack(Items.OAK_PLANKS));
        items.set(3, new ItemStack(Items.OAK_PLANKS));
        var input = CraftingInput.of(3, 3, items);
        var recipe = helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input,
                helper.getLevel()).orElseThrow();
        var ingredients = new ItemStack[9];
        for (int slot = 0; slot < ingredients.length; slot++) {
            ingredients[slot] = items.get(slot);
        }
        return PatternDetailsHelper.encodeCraftingPattern(recipe, ingredients,
                recipe.value().assemble(input, helper.getLevel().registryAccess()), false, false);
    }

    public static AEItemKey inputKey() { return AEItemKey.of(Items.OAK_PLANKS); }

    public static AEItemKey outputKey() { return AEItemKey.of(Items.STICK); }

    @Override
    public void close() {
        if (requester != null) {
            requester.close();
        }
    }
}
