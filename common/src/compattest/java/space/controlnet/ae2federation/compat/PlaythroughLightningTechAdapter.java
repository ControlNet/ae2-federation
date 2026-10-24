package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * AE2 Lightning Tech's grid recipe machines: the Overload Processing Factory, the Lightning Assembly Chamber and the
 * Lightning Simulation Chamber. Each recipe also needs FE and lightning. The machine joins its input subnet and, as on a
 * player's network, draws both from that network's storage: lightning at completion, and FE (Applied Flux's ME resource)
 * into its buffer as it works. Two ME Chests beside the subnet hold them, with a lightning cell and an FE cell. The
 * processing pattern carries the recipe's exact FE and lightning with its item and fluid inputs; the Storage Bus, filtered
 * to those item and fluid inputs, inserts them into the machine.
 */
final class PlaythroughLightningTechAdapter implements PlaythroughMachineAdapter {
    private static final String PACKAGE = "com.moakiee.ae2lt.";
    /** Holds the lightning of queued runs; a run needing more is rejected. */
    private static final String LIGHTNING_CELL = "ae2lt:lightning_storage_component_v";
    private static final long LIGHTNING_CAPACITY = 65536;
    private static final String FE_CELL = "appflux:fe_16k_cell";
    /** AbstractGridRecipeMachineLogic spends at least four ticks on a recipe. */
    private static final int MIN_TICKS = 4;
    private final String id;
    private final String type;
    private final String block;
    private final String recipeClass;
    private final String service;
    private final String inventory;
    private final boolean fluids;

    private PlaythroughLightningTechAdapter(String id, String type, String block, String machine, String recipe,
                                            String inventory, boolean fluids) {
        this.id = id; this.type = type; this.block = block; this.fluids = fluids;
        recipeClass = PACKAGE + "machine." + machine + ".recipe." + recipe + "Recipe";
        service = recipeClass + "Service";
        this.inventory = PACKAGE + "machine." + machine + "." + inventory;
    }

    static PlaythroughLightningTechAdapter overloadProcessing() {
        return new PlaythroughLightningTechAdapter("overload-processing", "ae2lt:overload_processing",
                "ae2lt:overload_processing_factory", "overloadfactory", "OverloadProcessing", "OverloadProcessingFactoryInventory", true);
    }

    static PlaythroughLightningTechAdapter lightningAssembly() {
        return new PlaythroughLightningTechAdapter("lightning-assembly", "ae2lt:lightning_assembly",
                "ae2lt:lightning_assembly_chamber", "lightningassembly", "LightningAssembly", "LightningAssemblyChamberInventory", false);
    }

    static PlaythroughLightningTechAdapter lightningSimulation() {
        return new PlaythroughLightningTechAdapter("lightning-simulation", "ae2lt:lightning_simulation",
                "ae2lt:lightning_simulation_room", "lightningchamber", "LightningSimulation", "LightningSimulationChamberInventory", false);
    }

    public String id() { return id; }
    public String recipeType() { return type; }

    public Recipe read(GameTestHelper helper, RecipeHolder<?> holder, Function<Ingredient, ItemStack> choose,
                       Function<Ingredient, ItemStack> equipment) {
        var recipe = holder.value();
        if (!recipe.getClass().getName().equals(recipeClass)) throw new IllegalArgumentException("Not an AE2 Lightning Tech " + type + " recipe");
        var inputs = new ArrayList<GenericStack>();
        for (var input : (List<?>) PlaythroughAddonRecipes.call(recipe, fluids ? "itemInputs" : "inputs")) {
            var ingredient = (Ingredient) PlaythroughAddonRecipes.call(input, "ingredient");
            int count = (int) PlaythroughAddonRecipes.call(input, "count");
            if (count <= 0) throw new IllegalArgumentException("Nonpositive ingredient count " + count);
            inputs.add(new GenericStack(appeng.api.stacks.AEItemKey.of(choose.apply(ingredient)), count));
        }
        GenericStack fluid = null;
        ItemStack output;
        if (fluids) {
            if (!((FluidStack) PlaythroughAddonRecipes.call(recipe, "fluidResult")).isEmpty())
                throw new IllegalArgumentException("Overload processing fluid product needs its own return pipe");
            var results = (List<?>) PlaythroughAddonRecipes.call(recipe, "itemResults");
            output = results.isEmpty() ? ItemStack.EMPTY : (ItemStack) results.getFirst();
            int amount = (int) PlaythroughAddonRecipes.call(recipe, "inputFluidAmount");
            var alternatives = (List<?>) PlaythroughAddonRecipes.call(recipe, "fluidInputAlternatives");
            if (amount > 0) {
                var choice = alternatives.stream().map(FluidStack.class::cast).filter(stack -> !stack.isEmpty())
                        .sorted(java.util.Comparator.<FluidStack>comparingInt(stack -> PlaythroughAddonRecipes.WORLD_FLUIDS
                                .contains(AEFluidKey.of(stack)) ? 0 : 1))
                        .findFirst().orElseThrow(() -> new IllegalArgumentException("Fluid input has no concrete fluid"));
                fluid = new GenericStack(AEFluidKey.of(choice), amount);
                inputs.add(fluid);
            }
        } else {
            output = (ItemStack) PlaythroughAddonRecipes.call(recipe, "getResultStack");
        }
        if (output.isEmpty()) throw new IllegalArgumentException(type + " recipe has no item output");
        long energy = (long) PlaythroughAddonRecipes.call(recipe, "totalEnergy");
        int lightning = (int) PlaythroughAddonRecipes.call(recipe, "lightningCost");
        var tier = PlaythroughAddonRecipes.call(recipe, "lightningTier");
        if (lightning <= 0 || lightning > LIGHTNING_CAPACITY)
            throw new IllegalArgumentException("Lightning cost " + lightning + " outside the subnet cell's " + LIGHTNING_CAPACITY);
        var merged = new ArrayList<>(PlaythroughAddonRecipes.merged(inputs));
        var lightningKey = lightningKey(tier);
        selectionCheck(helper, holder, merged, fluid, lightningKey, lightning);
        merged.add(new GenericStack(AppliedFluxCompatGameTests.fluxKey(), energy));
        merged.add(new GenericStack(lightningKey, lightning));
        return new Recipe(id, holder.id().toString(), merged, List.of(GenericStack.fromItemStack(output)), Map.of(),
                (int) Math.max(MIN_TICKS, Math.ceilDiv(energy, maxEnergyPerTick())));
    }

    /** The machine's own lookup over its inventory with exactly this recipe's lightning available must pick it. */
    private void selectionCheck(GameTestHelper helper, RecipeHolder<?> holder, List<GenericStack> inputs, GenericStack fluid,
                                AEKey lightningKey, long lightning) {
        try {
            var loader = holder.value().getClass().getClassLoader();
            var inventoryType = Class.forName(inventory, true, loader);
            var handler = (IItemHandler) inventoryType.getConstructor(Runnable.class).newInstance((Runnable) () -> { });
            for (var input : inputs) {
                if (!(input.what() instanceof appeng.api.stacks.AEItemKey item)) continue;
                var remainder = item.toStack((int) input.amount());
                for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++)
                    if ((boolean) PlaythroughAddonRecipes.call(handler, "isInputSlot", slot)) remainder = handler.insertItem(slot, remainder, false);
                if (!remainder.isEmpty()) throw new IllegalArgumentException("Machine inventory cannot hold one run's " + input);
            }
            boolean extreme = lightningKey.getId().getPath().startsWith("extreme");
            long hv = extreme ? 0 : lightning, ehv = extreme ? lightning : 0;
            Object result = null;
            for (var method : Class.forName(service, true, loader).getMethods()) {
                if (!method.getName().equals("findFirstProcessable")) continue;
                result = fluids ? method.invoke(null, helper.getLevel(), handler,
                        fluid == null ? FluidStack.EMPTY : ((AEFluidKey) fluid.what()).toStack((int) fluid.amount()), FluidStack.EMPTY, hv, ehv)
                        : method.invoke(null, helper.getLevel(), handler, hv, ehv);
            }
            var candidate = (Optional<?>) result;
            if (candidate == null || candidate.isEmpty()) throw new IllegalArgumentException("Machine finds no recipe for one run's inputs");
            var selected = (RecipeHolder<?>) PlaythroughAddonRecipes.call(candidate.get(), "recipe");
            if (!selected.id().equals(holder.id()))
                throw new IllegalArgumentException("Machine selects another recipe for the concrete inputs: " + selected.id());
            boolean parallel = java.util.Arrays.stream(candidate.get().getClass().getMethods()).anyMatch(m -> m.getName().equals("parallel"));
            if (parallel && (int) PlaythroughAddonRecipes.call(candidate.get(), "parallel") != 1)
                throw new IllegalArgumentException("Machine runs one run's inputs in parallel");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("AE2 Lightning Tech's recipe lookup is unavailable", exception);
        }
    }

    private static AEKey lightningKey(Object tier) {
        try {
            return (AEKey) Class.forName(PACKAGE + "me.key.LightningKey", true, tier.getClass().getClassLoader())
                    .getMethod("of", tier.getClass()).invoke(null, tier);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("AE2 Lightning Tech's lightning resource is unavailable", exception);
        }
    }

    /** FE per tick with four Acceleration Cards: the factory's config, the chambers' fixed rates. */
    private long maxEnergyPerTick() {
        return switch (id) {
            case "overload-processing" -> config("overloadFactoryFePerTickFourSpeedCards");
            case "lightning-assembly" -> 500_000L;
            default -> 200_000L;
        };
    }

    private static long config(String name) {
        try {
            return (long) Class.forName(PACKAGE + "config.AE2LTCommonConfig").getMethod(name).invoke(null);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("AE2 Lightning Tech's config is unavailable", exception);
        }
    }

    /**
     * World fluids, as for the other addon machines. FE is the layout's power, supplied like its creative AE energy.
     * Lightning is a world resource too: AE2 Lightning Tech's Lightning Collector stores natural and artificial strikes
     * as extreme and high voltage lightning in ME storage, and its Tesla Coil makes high voltage from FE and dust.
     */
    public Set<AEKey> providedInputs(GameTestHelper helper, Recipe recipe) {
        var result = new java.util.HashSet<>(PlaythroughAddonRecipes.WORLD_FLUIDS);
        result.add(AppliedFluxCompatGameTests.fluxKey());
        recipe.inputs().stream().map(GenericStack::what)
                .filter(key -> key.getType().getId().toString().equals("ae2lt:lightning")).forEach(result::add);
        return result;
    }

    private static BlockPos lightningChest(BlockPos machine) { return machine.above().north(); }
    private static BlockPos energyChest(BlockPos machine) { return machine.above().south(); }

    public void place(PlaythroughMachines.Site site, Recipe recipe) {
        site.block(site.machine(), AddonCraftingScene.block(block));
        var machine = (IUpgradeableObject) site.helper().<BlockEntity>getBlockEntity(site.machine());
        if (!machine.getUpgrades().addItems(AEItems.SPEED_CARD.stack(4)).isEmpty())
            throw new IllegalStateException(block + " refused its four acceleration cards");
        // Both ME Chests join the subnet through the cable above the machine.
        chest(site, lightningChest(site.machine()), LIGHTNING_CELL);
        chest(site, energyChest(site.machine()), FE_CELL);
        // Only the chests take FE and lightning; the bus inserts the item and fluid inputs.
        var bus = site.storageBus(site.machine().relative(inputFace()), inputFace().getOpposite());
        int slot = 0;
        for (var input : recipe.inputs())
            if (input.what() instanceof appeng.api.stacks.AEItemKey || input.what() instanceof AEFluidKey)
                bus.getConfig().setStack(slot++, new GenericStack(input.what(), 1));
    }

    private static void chest(PlaythroughMachines.Site site, BlockPos position, String cell) {
        site.block(position, AEBlocks.ME_CHEST.block());
        var chest = site.helper().<appeng.blockentity.storage.MEChestBlockEntity>getBlockEntity(position);
        chest.setCell(new ItemStack(AddonCraftingScene.item(cell)));
        if (chest.getOriginalCellInventory(0) == null) throw new IllegalStateException("Storage cell refused: " + cell);
    }

    public String leftovers(GameTestHelper helper, BlockPos position, Recipe recipe) {
        var machine = helper.<BlockEntity>getBlockEntity(position);
        var items = (IItemHandler) PlaythroughAddonRecipes.call(machine, "getInventory");
        for (int slot = 0; slot < items.getSlots(); slot++)
            if (!items.getStackInSlot(slot).isEmpty()) return block + " slot " + slot + ": " + items.getStackInSlot(slot);
        if (fluids) {
            if (!((FluidStack) PlaythroughAddonRecipes.call(machine, "getInputFluid")).isEmpty()) return "Factory input tank not empty";
            if (!((FluidStack) PlaythroughAddonRecipes.call(machine, "getOutputFluid")).isEmpty()) return "Factory output tank not empty";
        }
        // Exact FE per run leaves the buffer empty.
        long stored = (long) PlaythroughAddonRecipes.call(machine, "getMachineStoredEnergy");
        if (stored != 0) return block + " holds " + stored + " FE";
        if ((boolean) PlaythroughAddonRecipes.call(machine, "hasLockedRecipe")) return block + " has a locked recipe";
        var stock = new appeng.api.stacks.KeyCounter();
        for (var chest : List.of(lightningChest(position), energyChest(position)))
            helper.<appeng.blockentity.storage.MEChestBlockEntity>getBlockEntity(chest).getOriginalCellInventory(0).getAvailableStacks(stock);
        return stock.isEmpty() ? "" : "Subnet cells hold " + stock;
    }
}
