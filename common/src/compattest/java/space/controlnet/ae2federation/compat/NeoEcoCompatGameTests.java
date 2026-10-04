package space.controlnet.ae2federation.compat;

import appeng.me.cluster.IAEMultiBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Federation with Neo ECO AE Extension's multiblocks, each the smallest its controller accepts: 5 long, 3 high and 2
 * deep, with the controller facing north and its interface on the west, towards the provider network.
 */
@PrefixGameTestTemplate(false)
public final class NeoEcoCompatGameTests {
    private NeoEcoCompatGameTests() {
    }

    /** Neo ECO's crafting system serves the remote craft: its pattern bus provides the pattern, its worker crafts it. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void craftingSystemCrafting(GameTestHelper helper) {
        var start = AddonCraftingScene.PROVIDER.offset(0, -1, -1);
        var scene = AddonCraftingScene.structure(helper, "neoecoae:crafting_system_l4", List.of("ae2:1k_crafting_storage"),
                test -> placeCraftingSystem(test, start), start.offset(3, 2, 1));
        helper.succeedWhen(scene::tick);
    }

    /** Neo ECO's storage system, with a cell in one of its drives, is the provider network's storage the rule shares. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void storageSystemStorage(GameTestHelper helper) {
        var start = AddonStorageScene.BESIDE_CHEST.offset(0, -1, -1);
        var drive = start.offset(3, 1, 0);
        var scene = AddonStorageScene.structure(helper, "neoecoae:storage_system_l4",
                test -> placeStorageSystem(test, start), () -> fitCell(helper, drive));
        helper.succeedWhen(scene::tick);
    }

    /**
     * The guide's storage-system example and its exercise: after the round trip, breaking the tail casing takes the
     * shared items away from the consumer, and putting it back returns them.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void storageSystemDismantled(GameTestHelper helper) {
        var start = AddonStorageScene.BESIDE_CHEST.offset(0, -1, -1);
        var drive = start.offset(3, 1, 0);
        var scene = AddonStorageScene.structure(helper, "neoecoae:storage_system_l4",
                test -> placeStorageSystem(test, start), () -> fitCell(helper, drive))
                .dismantlingAfterwards(start.offset(4, 1, 0));
        helper.succeedWhen(scene::tick);
    }

    /**
     * From the west, at {@code start} (bottom, north): the interface side with the interface between its input and
     * output hatches behind, the controller, a casing column, the worker between parallel cores with the vent between
     * pattern buses behind, and the end casing.
     */
    private static void placeCraftingSystem(GameTestHelper helper, BlockPos start) {
        for (int y = 0; y < 3; y++) {
            helper.setBlock(start.offset(0, y, 0), block("crafting_casing"));
            helper.setBlock(start.offset(1, y, 1), block("crafting_casing"));
            helper.setBlock(start.offset(2, y, 0), block("crafting_casing"));
            helper.setBlock(start.offset(2, y, 1), block("crafting_casing"));
            helper.setBlock(start.offset(4, y, 0), block("crafting_casing"));
            helper.setBlock(start.offset(4, y, 1), block("crafting_casing"));
        }
        helper.setBlock(start.offset(0, 2, 1), block("input_hatch"));
        helper.setBlock(start.offset(0, 1, 1), block("crafting_interface"));
        helper.setBlock(start.offset(0, 0, 1), block("output_hatch"));
        helper.setBlock(start.offset(1, 2, 0), block("crafting_casing"));
        helper.setBlock(start.offset(1, 0, 0), block("crafting_casing"));
        helper.setBlock(start.offset(3, 2, 0), facing("crafting_parallel_core_l4", Direction.NORTH));
        helper.setBlock(start.offset(3, 1, 0), facing("crafting_worker", Direction.NORTH));
        helper.setBlock(start.offset(3, 0, 0), facing("crafting_parallel_core_l4", Direction.NORTH));
        helper.setBlock(start.offset(3, 2, 1), facing("crafting_pattern_bus", Direction.SOUTH));
        helper.setBlock(start.offset(3, 1, 1), facing("crafting_vent", Direction.SOUTH));
        helper.setBlock(start.offset(3, 0, 1), facing("crafting_pattern_bus", Direction.SOUTH));
        helper.setBlock(start.offset(1, 1, 0), facing("crafting_system_l4", Direction.NORTH));
    }

    /**
     * From the west, at {@code start} (bottom, north): the interface side with the interface behind, the controller, a
     * casing transition, the drives with the vent between energy cells behind, and the tail casing.
     */
    private static void placeStorageSystem(GameTestHelper helper, BlockPos start) {
        for (int y = 0; y < 3; y++) {
            helper.setBlock(start.offset(0, y, 0), block("storage_casing"));
            helper.setBlock(start.offset(1, y, 1), block("storage_casing"));
            helper.setBlock(start.offset(2, y, 0), block("storage_casing"));
            helper.setBlock(start.offset(2, y, 1), block("storage_casing"));
            helper.setBlock(start.offset(3, y, 0), facing("eco_drive", Direction.NORTH));
            helper.setBlock(start.offset(4, y, 0), block("storage_casing"));
            helper.setBlock(start.offset(4, y, 1), block("storage_casing"));
        }
        helper.setBlock(start.offset(0, 2, 1), block("storage_casing"));
        helper.setBlock(start.offset(0, 1, 1), block("storage_interface"));
        helper.setBlock(start.offset(0, 0, 1), block("storage_casing"));
        helper.setBlock(start.offset(1, 2, 0), block("storage_casing"));
        helper.setBlock(start.offset(1, 0, 0), block("storage_casing"));
        helper.setBlock(start.offset(3, 2, 1), facing("energy_cell_l4", Direction.SOUTH));
        helper.setBlock(start.offset(3, 1, 1), facing("storage_vent", Direction.SOUTH));
        helper.setBlock(start.offset(3, 0, 1), facing("energy_cell_l4", Direction.SOUTH));
        helper.setBlock(start.offset(1, 1, 0), facing("storage_system_l4", Direction.NORTH));
    }

    /** Once the storage system has formed, puts an ECO item cell into the drive as its player does. */
    private static boolean fitCell(GameTestHelper helper, BlockPos drive) {
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(drive));
        if (!(entity instanceof IAEMultiBlock<?> part) || part.getCluster() == null) return false;
        try {
            // An empty drive holds no stack at all.
            var held = (ItemStack) entity.getClass().getMethod("getCellStack").invoke(entity);
            if (held == null || held.isEmpty()) {
                entity.getClass().getMethod("setCellStack", ItemStack.class)
                        .invoke(entity, new ItemStack(AddonCraftingScene.item("neoecoae:eco_item_storage_cell_16m")));
            }
            held = (ItemStack) entity.getClass().getMethod("getCellStack").invoke(entity);
            return held != null && !held.isEmpty();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The ECO drive cannot take a cell", exception);
        }
    }

    private static BlockState block(String name) {
        return AddonCraftingScene.block("neoecoae:" + name).defaultBlockState();
    }

    /** Some of its blocks have a facing property of their own, so it is found by name. */
    private static BlockState facing(String name, Direction direction) {
        var state = block(name);
        for (var property : state.getProperties()) {
            if (property.getName().equals("facing") && property instanceof DirectionProperty facing) {
                return state.setValue(facing, direction);
            }
        }
        throw new IllegalStateException("neoecoae:" + name + " has no facing");
    }
}
