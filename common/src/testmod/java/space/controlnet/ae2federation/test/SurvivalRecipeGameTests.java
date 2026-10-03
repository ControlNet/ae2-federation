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
        var processor = MaterialRegistration.FEDERATION_LOGIC_PROCESSOR.get().getDefaultInstance();
        var cable = RouterRegistration.FEDERATION_CABLE_ITEM.get().getDefaultInstance();
        var assertions = 0;

        // Shapeless inputs in arbitrary cells.
        assertions += crafts(helper, "bridge", BridgeRegistration.BRIDGE.get().getDefaultInstance(), 1,
                grid(AEParts.QUARTZ_FIBER.stack(), null, null, null, processor, null, null, null, AEParts.STORAGE_BUS.stack()));
        assertions += crafts(helper, "pattern_provider", ProcessingRegistration.PROVIDER_ITEM.get().getDefaultInstance(), 1,
                grid(null, processor, null, null, null, null, AEBlocks.PATTERN_PROVIDER.stack(), null, null));
        assertions += crafts(helper, "processing_endpoint", ProcessingRegistration.ENDPOINT_ITEM.get().getDefaultInstance(), 1,
                grid(AEBlocks.INTERFACE.stack(), processor, null, null, null, null, null, null, null));
        assertions += crafts(helper, "router", RouterRegistration.ROUTER_ITEM.get().getDefaultInstance(), 4,
                grid(cable, AEParts.IMPORT_BUS.stack(), cable,
                        AEParts.STORAGE_BUS.stack(), AEItems.LOGIC_PROCESSOR.stack(), AEBlocks.INTERFACE.stack(),
                        cable, AEParts.EXPORT_BUS.stack(), cable));
        // Any glass cable colour, mixed within one craft.
        var white = AEParts.GLASS_CABLE.stack(AEColor.WHITE);
        var fluix = AEParts.GLASS_CABLE.stack(AEColor.TRANSPARENT);
        var red = AEParts.GLASS_CABLE.stack(AEColor.RED);
        assertions += crafts(helper, "cable", cable, 16, grid(fluix, fluix, white, fluix, processor, red, fluix, fluix, fluix));

        // The native processor in the cable ring's centre is not the Federation one.
        var plain = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,
                grid(fluix, fluix, fluix, fluix, AEItems.LOGIC_PROCESSOR.stack(), fluix, fluix, fluix, fluix), level);
        helper.assertFalse(plain.map(holder -> holder.id().getNamespace().equals("ae2federation")).orElse(false),
                "A native Logic Processor must not make Federation Cables");
        assertions++;

        // Inscriber: dust on top (or flipped to the bottom), the native processor in the middle, both spent.
        var dust = AEItems.FLUIX_DUST.stack();
        var logic = AEItems.LOGIC_PROCESSOR.stack();
        for (var plates : List.of(List.of(dust, ItemStack.EMPTY), List.of(ItemStack.EMPTY, dust))) {
            var recipe = InscriberRecipes.findRecipe(level, logic, plates.get(0), plates.get(1), false);
            helper.assertTrue(recipe != null && ItemStack.isSameItem(recipe.getResultItem(), processor)
                    && recipe.getResultItem().getCount() == 1, "The Inscriber must press a Federation Logic Processor");
            helper.assertTrue(recipe.getProcessType() == InscriberProcessType.PRESS, "Pressing must spend the dust");
            assertions += 2;
        }
        helper.assertTrue(InscriberRecipes.findRecipe(level, logic, ItemStack.EMPTY, ItemStack.EMPTY, false) == null,
                "A Logic Processor alone must not be pressed into anything");
        assertions++;

        for (var name : List.of("bridge", "pattern_provider", "processing_endpoint", "router", "cable")) {
            var id = ResourceLocation.fromNamespaceAndPath("ae2federation", "recipes/misc/" + name);
            helper.assertTrue(level.getServer().getAdvancements().get(id) != null, "Missing recipe-book unlock " + id);
            assertions++;
        }
        PolicyEvidence.write("survivalrecipes", assertions, Map.of("craftingRecipes", "5", "inscriberRecipes", "1",
                "routerBatch", "4", "cableBatch", "16", "recipeBookUnlocks", "5"));
        helper.succeed();
    }

    /** A real Inscriber, fed through its item handler as a pipe would, presses two processors and spends every input. */
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
                helper.assertTrue(insert(handler, AEItems.LOGIC_PROCESSOR.stack(2)).isEmpty(),
                        "Automation must accept Logic Processors");
                helper.assertTrue(insert(handler, AEItems.FLUIX_DUST.stack(2)).isEmpty(),
                        "Automation must accept the Fluix Dust beside the processors");
                fed[0] = true;
            }
            var inventory = inscriber.getInternalInventory();
            var processor = MaterialRegistration.FEDERATION_LOGIC_PROCESSOR.get();
            var made = 0;
            var leftovers = 0;
            for (var slot = 0; slot < inventory.size(); slot++) {
                var stack = inventory.getStackInSlot(slot);
                if (stack.is(processor)) made += stack.getCount();
                else leftovers += stack.getCount();
            }
            helper.assertValueEqual(made, 2, "Waiting for two Federation Logic Processors");
            helper.assertValueEqual(leftovers, 0, "Both processors and both dusts must be spent");
            PolicyEvidence.write("survivalinscriber", 4, Map.of("pressed", "2", "inputsLeft", "0",
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
