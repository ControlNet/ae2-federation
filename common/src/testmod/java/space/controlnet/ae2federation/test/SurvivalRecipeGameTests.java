package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.util.AEColor;
import appeng.blockentity.misc.InscriberBlockEntity;
import appeng.blockentity.misc.InscriberRecipes;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.recipes.handlers.InscriberProcessType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.bridge.BridgeRegistration;
import space.controlnet.ae2federation.material.MaterialRegistration;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

/** The survival recipes as the game loads them: crafting-table matches, the Inscriber press, recipe-book unlocks. */
@PrefixGameTestTemplate(false)
public final class SurvivalRecipeGameTests {
    private SurvivalRecipeGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 100)
    public static void survivalRecipes(GameTestHelper helper) {
        var level = helper.getLevel();
        var circuit = MaterialRegistration.PRINTED_NEXUS_CIRCUIT.get().getDefaultInstance();
        var processor = MaterialRegistration.NEXUS_PROCESSOR.get().getDefaultInstance();
        var core = MaterialRegistration.NEXUS_CORE.get().getDefaultInstance();
        var cable = RouterRegistration.FEDERATION_CABLE_ITEM.get().getDefaultInstance();
        var assertions = 0;

        // Like AE2's Formation Core: Fluix Crystal, Ender Dust and the processor in a row, in any row of the grid.
        var redstone = new ItemStack(Items.REDSTONE);
        var ender = AEItems.ENDER_DUST.stack();
        var crystal = AEItems.FLUIX_CRYSTAL.stack();
        assertions += crafts(helper, "nexus_core", core, 2, grid(crystal, ender, processor, null, null, null,
                null, null, null));
        assertions += crafts(helper, "nexus_core", core, 2, grid(null, null, null, null, null, null,
                crystal, ender, processor));
        // Shaped recipes also match mirrored, as AE2's Formation Core does.
        assertions += crafts(helper, "nexus_core", core, 2, grid(processor, ender, crystal, null, null, null,
                null, null, null));
        // A native Logic Processor in place of the Nexus one, or a missing input, make no core.
        for (var input : List.of(grid(crystal, ender, AEItems.LOGIC_PROCESSOR.stack(), null, null, null, null, null, null),
                grid(crystal, null, processor, null, null, null, null, null, null))) {
            helper.assertFalse(level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level)
                    .map(holder -> holder.id().getPath().equals("nexus_core")).orElse(false),
                    "Only Fluix Crystal, Ender Dust and a Nexus Processor in a row make Nexus Cores");
            assertions++;
        }

        // Shapeless inputs in arbitrary cells.
        assertions += crafts(helper, "bridge", BridgeRegistration.BRIDGE.get().getDefaultInstance(), 1,
                grid(AEParts.QUARTZ_FIBER.stack(), null, null, null, core, null, null, null, AEParts.STORAGE_BUS.stack()));
        assertions += crafts(helper, "pattern_provider", ProcessingRegistration.PROVIDER_ITEM.get().getDefaultInstance(), 1,
                grid(null, core, null, null, null, null, AEBlocks.PATTERN_PROVIDER.stack(), null, null));
        assertions += crafts(helper, "processing_endpoint", ProcessingRegistration.ENDPOINT_ITEM.get().getDefaultInstance(), 1,
                grid(AEBlocks.INTERFACE.stack(), core, null, null, null, null, null, null, null));
        assertions += crafts(helper, "switch", RouterRegistration.SWITCH_ITEM.get().getDefaultInstance(), 1,
                grid(cable, AEParts.QUARTZ_FIBER.stack(), cable,
                        AEParts.STORAGE_BUS.stack(), core, AEBlocks.INTERFACE.stack(),
                        cable, AEParts.QUARTZ_FIBER.stack(), cable));
        // The Switch's centre takes the Nexus Core, not AE2's Formation Core.
        var nativeSwitch = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                grid(cable, AEParts.QUARTZ_FIBER.stack(), cable,
                        AEParts.STORAGE_BUS.stack(), AEItems.FORMATION_CORE.stack(), AEBlocks.INTERFACE.stack(),
                        cable, AEParts.QUARTZ_FIBER.stack(), cable), level);
        helper.assertFalse(nativeSwitch.isPresent(), "A Formation Core must not make Switches");
        assertions++;
        var fluixCrystal = AEItems.FLUIX_CRYSTAL.stack();
        assertions += crafts(helper, "router", RouterRegistration.ROUTER_ITEM.get().getDefaultInstance(), 1,
                grid(cable, fluixCrystal, cable, fluixCrystal, core, fluixCrystal, cable, fluixCrystal, cable));
        // Any glass cable colour, mixed within one craft.
        var white = AEParts.GLASS_CABLE.stack(AEColor.WHITE);
        var fluix = AEParts.GLASS_CABLE.stack(AEColor.TRANSPARENT);
        var red = AEParts.GLASS_CABLE.stack(AEColor.RED);
        assertions += crafts(helper, "cable", cable, 8, grid(fluix, fluix, white, fluix, core, red, fluix, fluix, fluix));

        // AE2's Formation Core in the cable ring's centre is not the Nexus one.
        var plain = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                grid(fluix, fluix, fluix, fluix, AEItems.FORMATION_CORE.stack(), fluix, fluix, fluix, fluix), level);
        helper.assertFalse(plain.map(holder -> holder.id().getNamespace().equals("ae2federation")).orElse(false),
                "A Formation Core must not make Federation Cables");
        assertions++;

        // Inscriber, like Advanced AE's Quantum Press: the Engineering and Logic Presses (either way round) around an
        // Ender Pearl, all spent.
        var pearl = new ItemStack(Items.ENDER_PEARL);
        var press = MaterialRegistration.NEXUS_PROCESSOR_PRESS.get().getDefaultInstance();
        var engineering = AEItems.ENGINEERING_PROCESSOR_PRESS.stack();
        var logic = AEItems.LOGIC_PROCESSOR_PRESS.stack();
        for (var plates : List.of(List.of(engineering, logic), List.of(logic, engineering))) {
            var made = InscriberRecipes.findRecipe(level, pearl, plates.get(0), plates.get(1), false);
            helper.assertTrue(made != null && ItemStack.isSameItem(made.getResultItem(), press)
                    && made.getResultItem().getCount() == 1, "The Inscriber must press a Nexus Press");
            helper.assertTrue(made.getProcessType() == InscriberProcessType.PRESS, "Making the press must spend both presses");
            assertions += 2;
        }
        // Like every AE2 press, it copies itself onto a Block of Iron and stays.
        var copy = InscriberRecipes.findRecipe(level, new ItemStack(Items.IRON_BLOCK), press, ItemStack.EMPTY, false);
        helper.assertTrue(copy != null && ItemStack.isSameItem(copy.getResultItem(), press)
                && copy.getResultItem().getCount() == 1, "A Block of Iron under the Nexus Press must copy it");
        helper.assertTrue(copy.getProcessType() == InscriberProcessType.INSCRIBE, "Copying must keep the press");
        assertions += 2;

        // Inscriber, like AE2's printed circuits: an Ender Pearl under the Nexus Press, which is kept.
        var printed = InscriberRecipes.findRecipe(level, pearl, press, ItemStack.EMPTY, false);
        helper.assertTrue(printed != null && ItemStack.isSameItem(printed.getResultItem(), circuit)
                && printed.getResultItem().getCount() == 1, "The Inscriber must print a Nexus circuit");
        helper.assertTrue(printed.getProcessType() == InscriberProcessType.INSCRIBE, "Printing must keep the press");
        // The Logic Press alone no longer prints it.
        var underLogic = InscriberRecipes.findRecipe(level, pearl, logic, ItemStack.EMPTY, false);
        helper.assertFalse(underLogic != null && ItemStack.isSameItem(underLogic.getResultItem(), circuit),
                "The Logic Press must not print a Nexus circuit");
        assertions++;
        // Without the press the pearl still grinds into AE2's Ender Dust.
        var ground = InscriberRecipes.findRecipe(level, pearl, ItemStack.EMPTY, ItemStack.EMPTY, false);
        helper.assertTrue(ground != null && ItemStack.isSameItem(ground.getResultItem(), ender),
                "An Ender Pearl alone must still grind into Ender Dust");
        assertions += 3;

        // Inscriber, like AE2's processors: the circuit on top and Printed Silicon at the bottom (or flipped),
        // redstone in the middle, all spent.
        var silicon = AEItems.SILICON_PRINT.stack();
        for (var plates : List.of(List.of(circuit, silicon), List.of(silicon, circuit))) {
            var recipe = InscriberRecipes.findRecipe(level, redstone, plates.get(0), plates.get(1), false);
            helper.assertTrue(recipe != null && ItemStack.isSameItem(recipe.getResultItem(), processor)
                    && recipe.getResultItem().getCount() == 1, "The Inscriber must press a Nexus Processor");
            helper.assertTrue(recipe.getProcessType() == InscriberProcessType.PRESS, "Pressing must spend the plates");
            assertions += 2;
        }
        for (var plates : List.of(List.of(circuit, ItemStack.EMPTY), List.of(ItemStack.EMPTY, silicon))) {
            var recipe = InscriberRecipes.findRecipe(level, redstone, plates.get(0), plates.get(1), false);
            helper.assertFalse(recipe != null && ItemStack.isSameItem(recipe.getResultItem(), processor),
                    "A Nexus Processor needs the circuit, the redstone and the silicon together");
            assertions++;
        }

        for (var name : List.of("nexus_core", "bridge", "pattern_provider", "processing_endpoint", "router", "switch",
                "cable")) {
            var id = ResourceLocation.fromNamespaceAndPath("ae2federation", "recipes/misc/" + name);
            helper.assertTrue(level.getServer().getAdvancements().get(id) != null, "Missing recipe-book unlock " + id);
            assertions++;
        }
        PolicyEvidence.write("survivalrecipes", assertions, Map.of("craftingRecipes", "7", "inscriberRecipes", "4",
                "coreBatch", "2", "routerBatch", "1", "switchBatch", "1", "cableBatch", "8",
                "recipeBookUnlocks", "7"));
        helper.succeed();
    }

    /** A real Inscriber, fed through one side as a pipe would, presses two processors and spends every input. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 600)
    public static void survivalInscriber(GameTestHelper helper) {
        var cellPos = new BlockPos(1, 1, 1);
        var inscriberPos = cellPos.above();
        helper.setBlock(cellPos, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(inscriberPos, AEBlocks.INSCRIBER.block());
        var level = helper.getLevel();
        var absolute = helper.absolutePos(inscriberPos);
        var fed = new boolean[1];
        helper.succeedWhen(() -> {
            var inscriber = (InscriberBlockEntity) helper.getBlockEntity(inscriberPos);
            // Power the machine directly too, so the test does not hang on the cell's grid joining.
            inscriber.injectAEPower(1_600, Actionable.MODULATE);
            if (!fed[0]) {
                var handler = level.getCapability(Capabilities.ItemHandler.BLOCK, absolute, Direction.NORTH);
                helper.assertTrue(handler != null, "Waiting for the Inscriber's item handler");
                helper.assertTrue(insert(handler, new ItemStack(MaterialRegistration.PRINTED_NEXUS_CIRCUIT.get(), 2)).isEmpty(),
                        "Automation must accept Nexus circuits");
                helper.assertTrue(insert(handler, new ItemStack(Items.REDSTONE, 2)).isEmpty(),
                        "Automation must accept the redstone beside the circuits");
                helper.assertTrue(insert(handler, AEItems.SILICON_PRINT.stack(2)).isEmpty(),
                        "Automation must accept the Printed Silicon beside the circuits");
                fed[0] = true;
            }
            var inventory = inscriber.getInternalInventory();
            var processor = MaterialRegistration.NEXUS_PROCESSOR.get();
            var made = 0;
            var leftovers = 0;
            for (var slot = 0; slot < inventory.size(); slot++) {
                var stack = inventory.getStackInSlot(slot);
                if (stack.is(processor)) made += stack.getCount();
                else leftovers += stack.getCount();
            }
            helper.assertValueEqual(made, 2, "Waiting for two Nexus Processors");
            helper.assertValueEqual(leftovers, 0, "Both circuits, both redstone and both silicon prints must be spent");
            PolicyEvidence.write("survivalinscriber", 5, Map.of("pressed", "2", "inputsLeft", "0",
                    "fedThrough", "itemHandler"));
        });
    }

    private static ItemStack insert(net.neoforged.neoforge.items.IItemHandler handler, ItemStack stack) {
        var remaining = stack;
        for (var slot = 0; slot < handler.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = handler.insertItem(slot, remaining, false);
        }
        return remaining;
    }

    private static int crafts(GameTestHelper helper, String name, ItemStack expected, int count, CraftingInput input) {
        var level = helper.getLevel();
        var holder = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        helper.assertTrue(holder.isPresent(), "No recipe matched the " + name + " inputs");
        helper.assertValueEqual(holder.get().id(), ResourceLocation.fromNamespaceAndPath("ae2federation", name),
                "Another recipe took the " + name + " inputs");
        var result = holder.get().value().assemble(input, level.registryAccess());
        helper.assertTrue(ItemStack.isSameItem(result, expected), "Wrong " + name + " result: " + result);
        helper.assertValueEqual(result.getCount(), count, "Wrong " + name + " count");
        return 4;
    }

    private static CraftingInput grid(ItemStack... cells) {
        var items = new ArrayList<ItemStack>(Arrays.stream(cells)
                .map(cell -> cell == null ? ItemStack.EMPTY : cell.copy()).toList());
        return CraftingInput.of(3, 3, items);
    }
}
