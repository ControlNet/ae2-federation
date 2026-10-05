package space.controlnet.ae2federation.compat;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with AE2 Lightning Tech's own blocks. */
@PrefixGameTestTemplate(false)
public final class LightningTechCompatGameTests {
    private static final String MENTAL_MATH_UNIT = "ae2lt:pigmee_mentalmath_unit";
    private static final String OVERLOADED_PROVIDER = "ae2lt:overloaded_pattern_provider";

    private LightningTechCompatGameTests() {
    }

    /** AE2 Lightning Tech's Overloaded Pattern Provider serves the remote craft. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void overloadedPatternProviderCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2lt:overloaded_pattern_provider", "ae2:molecular_assembler", List.of("ae2:1k_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }

    /**
     * The guide's Lightning Tech example and its exercise: after the job, the provider network's AE2 Pattern Provider is
     * upgraded in place with an Overloaded Pattern Provider Upgrade, as its player does. It keeps its pattern, and the
     * consumer's next order runs through the Overloaded Pattern Provider.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void overloadedUpgradeInPlace(GameTestHelper helper) {
        var provider = AddonCraftingScene.PROVIDER;
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "ae2:molecular_assembler",
                List.of("ae2:1k_crafting_storage")).reorderingAfterwards(test -> {
                    var player = test.makeMockPlayer(GameType.SURVIVAL);
                    var stack = new ItemStack(AddonCraftingScene.item("ae2lt:overloaded_pattern_provider_upgrade"));
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    var absolute = test.absolutePos(provider);
                    var hit = new BlockHitResult(Vec3.atCenterOf(absolute).relative(Direction.UP, 0.5), Direction.UP,
                            absolute, false);
                    var result = stack.onItemUseFirst(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
                    test.assertTrue(result.consumesAction(), "The upgrade must take the AE2 Pattern Provider: " + result);
                }, (order, cpu, network) -> helper.assertTrue(helper.getBlockState(provider)
                        .is(AddonCraftingScene.block(order == 1 ? "ae2:pattern_provider" : OVERLOADED_PROVIDER)),
                        "Order " + order + " must run through " + helper.getBlockState(provider)));
        helper.succeedWhen(scene::tick);
    }

    /** The same provider runs the remote request's processing pattern in a machine. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void overloadedPatternProviderProcessing(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2lt:overloaded_pattern_provider", "minecraft:chest",
                List.of("ae2:1k_crafting_storage"), true);
        helper.succeedWhen(scene::tick);
    }

    /** The Pigmee Pattern Provider, which has its own provider logic instead of AE2's, serves the remote craft. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void pigmeePatternProviderCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2lt:pigmee_pattern_provider", "ae2:molecular_assembler",
                List.of("ae2:1k_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }

    /** The consumer's only CPU is a Pigmee Mental Math Unit, a one-block CPU that Thunderbolt runs, not AE2. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void mentalMathUnitCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "ae2:molecular_assembler",
                List.of(MENTAL_MATH_UNIT));
        helper.succeedWhen(scene::tick);
    }

    /** The Pigmee Mental Math Unit's job is cancelled after the push. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void mentalMathUnitCancel(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "minecraft:chest",
                List.of(MENTAL_MATH_UNIT), true).cancellingAfterPush();
        helper.succeedWhen(scene::tick);
    }

    /** A Pigmee Mental Math Unit is the Federation Pattern Provider network's CPU for a job through an Endpoint. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void mentalMathUnitEndpoint(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace(), MENTAL_MATH_UNIT);
        helper.succeedWhen(scene::tick);
    }

    /**
     * The Pigmee provider is its own crafting provider with no AE2 logic, and the Overloaded one extends AE2's: both run
     * the Endpoint in Local mode.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalPigmeePatternProvider(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace())
                .throughLocalProvider("ae2lt:pigmee_pattern_provider");
        helper.succeedWhen(scene::tick);
    }

    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalOverloadedPatternProvider(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace())
                .throughLocalProvider("ae2lt:overloaded_pattern_provider");
        helper.succeedWhen(scene::tick);
    }


    /** The Bridge is removed while the Pigmee Mental Math Unit runs a job through the Pigmee provider. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void mentalMathUnitDisconnected(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2lt:pigmee_pattern_provider", "minecraft:chest",
                List.of(MENTAL_MATH_UNIT), true).disconnectingAfterPush();
        helper.succeedWhen(scene::tick);
    }

    /**
     * The guide's Tianshu district: a network whose only CPU is a formed Tianshu Supercomputer orders sticks from a
     * foundry network whose only crafter is a formed Tianshu Matter Warping Matrix and which has no storage, the two on
     * one Router, each built as AE2 Lightning Tech's own guide shows it. The Supercomputer runs the job, the Matrix
     * crafts it and its results come back to the district. The exercise breaks one block of the Matrix's casing: sticks leave the district's
     * craftables until it is put back.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "matter_fabrication_well", timeoutTicks = 1600)
    public static void tianshuOrdersFromMatrix(GameTestHelper helper) {
        var center = new BlockPos(23, 2, 14);
        var scene = new RouterCraftingScene(helper, center);
        // The Tianshu file's controller is at (6, 0, 3) and its Port at (3, 0, 3), over the last cable.
        var controller = scene.start(Direction.SOUTH).relative(Direction.SOUTH, 3).above().offset(3, 0, 0);
        // The Matrix file's Port is at (6, 5, 3) on its east face; turned to face south, it ends the cable's column.
        var port = scene.start(Direction.NORTH).above(MATRIX_PORT_HEIGHT).north();
        var matrix = port.offset(3, -MATRIX_PORT_HEIGHT, -6);
        scene.provider("foundry", Direction.NORTH, (test, start, outward) -> placeMatrix(test, start, matrix),
                        port, entity -> matrixFormed(helper, entity), RouterCraftingScene.sticksPattern(helper))
                .installingPatternsWith(LightningTechCompatGameTests::installInMatrix)
                .withoutStorage()
                .consumer("district", Direction.SOUTH, LightningTechCompatGameTests::placeTianshu,
                        entity -> formed(helper, controller), RouterCraftingScene.STICKS, 8, RouterCraftingScene.PLANKS, 4,
                        "foundry")
                .poweredBy("district")
                .thenRemoving(matrix.offset(-3, 0, 0), RouterCraftingScene.STICKS, "district");
        helper.succeedWhen(scene::tick);
    }

    private static final int MATRIX_PORT_HEIGHT = 5;

    /**
     * Cables from {@code start} up to beside a Tianshu Matter Warping Matrix's Port, and the Matrix itself at {@code
     * origin}, block for block as AE2 Lightning Tech's guide scene has it but turned so its Port faces south.
     */
    private static void placeMatrix(GameTestHelper helper, BlockPos start, BlockPos origin) {
        for (int up = 0; up <= MATRIX_PORT_HEIGHT; up++) {
            helper.assertTrue(appeng.api.parts.PartHelper.setPart(helper.getLevel(),
                    helper.absolutePos(start.above(up)), null, null,
                    appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT)) != null,
                    "A cable must lead up to the Matrix's Port");
        }
        placeAssembly(helper, "matter_warping_matrix.snbt", origin, Rotation.CLOCKWISE_90);
    }

    /**
     * Cables from {@code start} along {@code outward} to under the middle of a Tianshu Supercomputer's bottom face, where
     * its Port sits, and the Supercomputer itself, block for block as AE2 Lightning Tech's guide scene has it.
     */
    private static void placeTianshu(GameTestHelper helper, BlockPos start, Direction outward) {
        for (int along = 0; along <= 3; along++) {
            helper.assertTrue(appeng.api.parts.PartHelper.setPart(helper.getLevel(),
                    helper.absolutePos(start.relative(outward, along)), null, null,
                    appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT)) != null,
                    "A cable must go under the Supercomputer");
        }
        placeAssembly(helper, "tianshu_supercomputer.snbt", start.relative(outward, 3).above().offset(-3, 0, -3),
                Rotation.NONE);
    }

    /**
     * One of AE2 Lightning Tech's guide scenes, read from the mod's own file, with its {@code (0, 0, 0)} at {@code
     * origin} and turned by {@code rotation} about it. Its blocks go in unformed and its controller last, as a player
     * builds it.
     */
    private static void placeAssembly(GameTestHelper helper, String file, BlockPos origin, Rotation rotation) {
        String text;
        try {
            text = java.nio.file.Files.readString(net.neoforged.fml.ModList.get().getModFileById("ae2lt").getFile()
                    .findResource("assets", "ae2lt", "ae2guide", "assets", "assemblies", file));
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("AE2 Lightning Tech's scene " + file + " is unreadable", exception);
        }
        var matcher = java.util.regex.Pattern.compile("pos: \\[(\\d+), (\\d+), (\\d+)\\], state: \"([^\"]+)\"")
                .matcher(text);
        BlockPos controller = null;
        net.minecraft.world.level.block.state.BlockState controllerState = null;
        while (matcher.find()) {
            var position = origin.offset(new BlockPos(Integer.parseInt(matcher.group(1)),
                    Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3))).rotate(rotation));
            var state = unformed(helper, matcher.group(4)).rotate(rotation);
            if (matcher.group(4).contains("controller")) {
                controller = position;
                controllerState = state;
            } else {
                helper.setBlock(position, state);
            }
        }
        helper.assertTrue(controller != null, "The scene " + file + " has no controller");
        helper.setBlock(controller, controllerState);
    }

    /** Whether the Matrix Port {@code entity} belongs to a formed Matrix; until then, what its controller reports. */
    private static boolean matrixFormed(GameTestHelper helper, BlockEntity entity) {
        helper.assertTrue(entity != null, "No Matrix Port");
        try {
            if ((boolean) entity.getClass().getMethod("isFormed").invoke(entity)) return true;
            var controller = entity.getClass().getMethod("getController").invoke(entity);
            helper.fail("Waiting for the Matter Warping Matrix to form"
                    + (controller == null ? "" : ": issue " + controller.getClass().getMethod("getPrimaryIssueOrdinal")
                            .invoke(controller)));
            return false;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The Matrix Port cannot be read", exception);
        }
    }

    /** Puts {@code pattern} in the first free slot of the Matrix's Pattern Storages, through its Port. */
    private static boolean installInMatrix(BlockEntity port, ItemStack pattern) {
        try {
            var slots = (net.neoforged.neoforge.items.IItemHandler) port.getClass().getMethod("getPatternItemHandler")
                    .invoke(port);
            for (int slot = 0; slot < slots.getSlots(); slot++) {
                if (slots.insertItem(slot, pattern.copy(), false).isEmpty()) return true;
            }
            return false;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The Matrix Port cannot be read", exception);
        }
    }

    /** A block state as a guide scene writes it, {@code id{property:value,...}}, with its {@code formed} off. */
    private static net.minecraft.world.level.block.state.BlockState unformed(GameTestHelper helper, String text) {
        // The scene's form; the command form the parser reads is id[property=value,...].
        var command = text.contains("{") ? text.replace('{', '[').replace('}', ']').replace(':', '=')
                .replaceFirst("=", ":") : text;
        try {
            var state = net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(
                    helper.getLevel().holderLookup(net.minecraft.core.registries.Registries.BLOCK), command, false)
                    .blockState();
            for (var property : state.getProperties()) {
                if (property.getName().equals("formed")
                        && property instanceof net.minecraft.world.level.block.state.properties.BooleanProperty flag) {
                    state = state.setValue(flag, false);
                }
            }
            return state;
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            throw new IllegalStateException("Unreadable block state " + text, exception);
        }
    }

    /** Whether the Tianshu controller at {@code position} has formed its structure; until then, what it reports. */
    private static boolean formed(GameTestHelper helper, BlockPos position) {
        var controller = helper.getLevel().getBlockEntity(helper.absolutePos(position));
        helper.assertTrue(controller != null, "No Tianshu controller at " + position);
        try {
            var formed = (boolean) controller.getClass().getMethod("isFormed").invoke(controller);
            helper.assertTrue(formed, "Waiting for the Tianshu Supercomputer to form: "
                    + controller.getClass().getMethod("issueText").invoke(controller));
            return true;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The Tianshu controller cannot be read", exception);
        }
    }
}
