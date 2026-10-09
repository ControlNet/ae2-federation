package space.controlnet.ae2federation.compat;

import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.me.cluster.IAEMultiBlock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    /** The computation system's interface, at the end of the consumer's cable. */
    private static final BlockPos COMPUTATION = AddonCraftingScene.FIRST_CPU.south(3).east(2);
    /** The consumer's storage system, from its bottom north-west corner, three blocks south of the computation system. */
    private static final BlockPos CONSUMER_STORAGE = COMPUTATION.south(3).offset(0, -1, -1);
    private static final BlockPos CONSUMER_DRIVE = CONSUMER_STORAGE.offset(3, 1, 0);

    private NeoEcoCompatGameTests() {
    }

    /**
     * The block and item ids of one tier of the ECO systems, by the names of Neo ECO's own L4 blocks: {@link #L4}, or
     * an addon's tier built the same way, whose ids {@code renamed} gives.
     */
    record Tier(String namespace, Map<String, String> renamed) {
        static final Tier L4 = new Tier("neoecoae", Map.of());

        String id(String name) {
            return namespace + ":" + renamed.getOrDefault(name, name);
        }
    }

    /** Neo ECO's crafting system serves the remote craft: its pattern bus provides the pattern, its worker crafts it. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void craftingSystemCrafting(GameTestHelper helper) {
        helper.succeedWhen(crafting(helper, Tier.L4)::tick);
    }

    static AddonCraftingScene crafting(GameTestHelper helper, Tier tier) {
        var start = AddonCraftingScene.PROVIDER.offset(0, -1, -1);
        return AddonCraftingScene.structure(helper, tier.id("crafting_system_l4"), List.of("ae2:1k_crafting_storage"),
                test -> placeCraftingSystem(test, tier, start), start.offset(3, 2, 1));
    }

    /**
     * Neo ECO's computation system, with a flash array in one of its drives, is the consumer's only CPU and orders from
     * the provider network's crafting system.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void computationSystemOrdering(GameTestHelper helper) {
        helper.succeedWhen(computationOrdering(helper, Tier.L4)::tick);
    }

    static AddonCraftingScene computationOrdering(GameTestHelper helper, Tier tier) {
        var start = AddonCraftingScene.PROVIDER.offset(0, -1, -1);
        return AddonCraftingScene.structure(helper, tier.id("crafting_system_l4"), List.of(),
                test -> placeCraftingSystem(test, tier, start), start.offset(3, 2, 1))
                .consumerCpuStructure(test -> placeComputationSystem(test, tier), () -> fitComputationCell(helper, tier));
    }

    /**
     * The guide's district and its exercise: the consumer keeps its items in an ECO storage system and runs its jobs on
     * an ECO computation system, and orders from the provider network's ECO crafting system, which has no power of its
     * own. Breaking a casing of the crafting system then takes its recipes away from the consumer, and putting it back
     * returns them.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 1200)
    public static void ecoDistrictOrdering(GameTestHelper helper) {
        helper.succeedWhen(district(helper, Tier.L4)::tick);
    }

    static AddonCraftingScene district(GameTestHelper helper, Tier tier) {
        var start = AddonCraftingScene.PROVIDER.offset(0, -1, -1);
        return computationOrdering(helper, tier)
                .consumerStorageStructure(test -> placeConsumerStorageSystem(test, tier),
                        () -> fitCell(helper, tier, CONSUMER_DRIVE) ? mountedStorage(helper, CONSUMER_DRIVE) : null)
                .dismantlingAfterwards(start.offset(4, 1, 0));
    }

    /** Neo ECO's storage system, with a cell in one of its drives, is the provider network's storage the rule shares. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void storageSystemStorage(GameTestHelper helper) {
        helper.succeedWhen(storage(helper, Tier.L4)::tick);
    }

    static AddonStorageScene storage(GameTestHelper helper, Tier tier) {
        var start = AddonStorageScene.BESIDE_CHEST.offset(0, -1, -1);
        var drive = start.offset(3, 1, 0);
        return AddonStorageScene.structure(helper, tier.id("storage_system_l4"),
                test -> placeStorageSystem(test, tier, start), () -> fitCell(helper, tier, drive));
    }

    /**
     * The guide's storage-system example and its exercise: after the round trip, breaking the tail casing takes the
     * shared items away from the consumer, and putting it back returns them.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void storageSystemDismantled(GameTestHelper helper) {
        helper.succeedWhen(storageDismantled(helper, Tier.L4)::tick);
    }

    static AddonStorageScene storageDismantled(GameTestHelper helper, Tier tier) {
        return storage(helper, tier).dismantlingAfterwards(AddonStorageScene.BESIDE_CHEST.offset(4, 0, -1));
    }

    /**
     * From the west, at {@code start} (bottom, north): the interface side with the interface between its input and
     * output hatches behind, the controller, a casing column, the worker between parallel cores with the vent between
     * pattern buses behind, and the end casing.
     */
    static void placeCraftingSystem(GameTestHelper helper, Tier tier, BlockPos start) {
        for (int y = 0; y < 3; y++) {
            helper.setBlock(start.offset(0, y, 0), block(tier, "crafting_casing"));
            helper.setBlock(start.offset(1, y, 1), block(tier, "crafting_casing"));
            helper.setBlock(start.offset(2, y, 0), block(tier, "crafting_casing"));
            helper.setBlock(start.offset(2, y, 1), block(tier, "crafting_casing"));
            helper.setBlock(start.offset(4, y, 0), block(tier, "crafting_casing"));
            helper.setBlock(start.offset(4, y, 1), block(tier, "crafting_casing"));
        }
        helper.setBlock(start.offset(0, 2, 1), block(tier, "input_hatch"));
        helper.setBlock(start.offset(0, 1, 1), block(tier, "crafting_interface"));
        helper.setBlock(start.offset(0, 0, 1), block(tier, "output_hatch"));
        helper.setBlock(start.offset(1, 2, 0), block(tier, "crafting_casing"));
        helper.setBlock(start.offset(1, 0, 0), block(tier, "crafting_casing"));
        helper.setBlock(start.offset(3, 2, 0), facing(tier, "crafting_parallel_core_l4", Direction.NORTH));
        helper.setBlock(start.offset(3, 1, 0), facing(tier, "crafting_worker", Direction.NORTH));
        helper.setBlock(start.offset(3, 0, 0), facing(tier, "crafting_parallel_core_l4", Direction.NORTH));
        helper.setBlock(start.offset(3, 2, 1), facing(tier, "crafting_pattern_bus", Direction.SOUTH));
        helper.setBlock(start.offset(3, 1, 1), facing(tier, "crafting_vent", Direction.SOUTH));
        helper.setBlock(start.offset(3, 0, 1), facing(tier, "crafting_pattern_bus", Direction.SOUTH));
        helper.setBlock(start.offset(1, 1, 0), facing(tier, "crafting_system_l4", Direction.NORTH));
    }

    /**
     * From the west, at {@code start} (bottom, north): the interface side with the interface behind, the controller, a
     * casing transition, the drives with the vent between energy cells behind, and the tail casing.
     */
    static void placeStorageSystem(GameTestHelper helper, Tier tier, BlockPos start) {
        for (int y = 0; y < 3; y++) {
            helper.setBlock(start.offset(0, y, 0), block(tier, "storage_casing"));
            helper.setBlock(start.offset(1, y, 1), block(tier, "storage_casing"));
            helper.setBlock(start.offset(2, y, 0), block(tier, "storage_casing"));
            helper.setBlock(start.offset(2, y, 1), block(tier, "storage_casing"));
            helper.setBlock(start.offset(3, y, 0), facing(tier, "eco_drive", Direction.NORTH));
            helper.setBlock(start.offset(4, y, 0), block(tier, "storage_casing"));
            helper.setBlock(start.offset(4, y, 1), block(tier, "storage_casing"));
        }
        helper.setBlock(start.offset(0, 2, 1), block(tier, "storage_casing"));
        helper.setBlock(start.offset(0, 1, 1), block(tier, "storage_interface"));
        helper.setBlock(start.offset(0, 0, 1), block(tier, "storage_casing"));
        helper.setBlock(start.offset(1, 2, 0), block(tier, "storage_casing"));
        helper.setBlock(start.offset(1, 0, 0), block(tier, "storage_casing"));
        helper.setBlock(start.offset(3, 2, 1), facing(tier, "energy_cell_l4", Direction.SOUTH));
        helper.setBlock(start.offset(3, 1, 1), facing(tier, "storage_vent", Direction.SOUTH));
        helper.setBlock(start.offset(3, 0, 1), facing(tier, "energy_cell_l4", Direction.SOUTH));
        helper.setBlock(start.offset(1, 1, 0), facing(tier, "storage_system_l4", Direction.NORTH));
    }

    /**
     * The consumer's cable runs from west of its first cable south and east to the computation system's interface,
     * which is on the system's west end, behind its controller; from there, as {@link #placeCraftingSystem}: the
     * controller, a casing column, the transmitter between drives with the threading core between parallel cores
     * behind, and the cooling controller at the end.
     */
    static void placeComputationSystem(GameTestHelper helper, Tier tier) {
        var cable = AddonCraftingScene.FIRST_CPU;
        consumerCable(helper, cable);
        for (int step = 1; step <= 3; step++) consumerCable(helper, cable.south(step));
        consumerCable(helper, cable.south(3).east());
        var start = COMPUTATION.offset(0, -1, -1);
        for (int y = 0; y < 3; y++) {
            helper.setBlock(start.offset(0, y, 0), block(tier, "computation_casing"));
            helper.setBlock(start.offset(1, y, 1), block(tier, "computation_casing"));
            helper.setBlock(start.offset(2, y, 0), block(tier, "computation_casing"));
            helper.setBlock(start.offset(2, y, 1), block(tier, "computation_casing"));
        }
        helper.setBlock(start.offset(0, 2, 1), block(tier, "computation_casing"));
        helper.setBlock(start.offset(0, 0, 1), block(tier, "computation_casing"));
        helper.setBlock(start.offset(1, 2, 0), block(tier, "computation_casing"));
        helper.setBlock(start.offset(1, 0, 0), block(tier, "computation_casing"));
        helper.setBlock(start.offset(3, 2, 0), facing(tier, "computation_drive", Direction.NORTH));
        helper.setBlock(start.offset(3, 1, 0), facing(tier, "computation_transmitter", Direction.NORTH));
        helper.setBlock(start.offset(3, 0, 0), facing(tier, "computation_drive", Direction.NORTH));
        helper.setBlock(start.offset(3, 2, 1), facing(tier, "computation_parallel_core_l4", Direction.SOUTH));
        helper.setBlock(start.offset(3, 1, 1), facing(tier, "computation_threading_core_l4", Direction.SOUTH));
        helper.setBlock(start.offset(3, 0, 1), facing(tier, "computation_parallel_core_l4", Direction.SOUTH));
        helper.setBlock(start.offset(4, 2, 0), block(tier, "computation_casing"));
        helper.setBlock(start.offset(4, 1, 0), facing(tier, "computation_cooling_controller_l4", Direction.EAST));
        helper.setBlock(start.offset(4, 0, 0), block(tier, "computation_casing"));
        for (int y = 0; y < 3; y++) helper.setBlock(start.offset(4, y, 1), block(tier, "computation_casing"));
        helper.setBlock(COMPUTATION, block(tier, "computation_interface"));
        helper.setBlock(start.offset(1, 1, 0), facing(tier, "computation_system_l4", Direction.NORTH));
    }

    /**
     * Past the computation system's cable, the consumer's cable runs on south and east to an ECO storage system laid
     * out as in {@link #placeStorageSystem}, its interface on its west end.
     */
    static void placeConsumerStorageSystem(GameTestHelper helper, Tier tier) {
        var cable = AddonCraftingScene.FIRST_CPU;
        for (int step = 4; step <= 6; step++) consumerCable(helper, cable.south(step));
        consumerCable(helper, cable.south(6).east());
        placeStorageSystem(helper, tier, CONSUMER_STORAGE);
    }

    /** The storage an ECO drive mounts into its network, as AE2 asks it for: the drive's cell inventory. */
    static MEStorage mountedStorage(GameTestHelper helper, BlockPos drive) {
        var mounted = new ArrayList<MEStorage>();
        ((IStorageProvider) helper.getLevel().getBlockEntity(helper.absolutePos(drive)))
                .mountInventories((inventory, priority) -> mounted.add(inventory));
        return mounted.isEmpty() ? null : mounted.getFirst();
    }

    private static void consumerCable(GameTestHelper helper, BlockPos position) {
        helper.assertTrue(PartHelper.setPart(helper.getLevel(), helper.absolutePos(position), null, null,
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)) != null, "A cable must go at " + position);
    }

    /** Once the computation system has formed, puts a flash array into its upper drive as its player does. */
    static boolean fitComputationCell(GameTestHelper helper, Tier tier) {
        return fitCell(helper, COMPUTATION.offset(3, 1, -1), tier.id("eco_computation_cell_l4"));
    }

    /** Once the storage system has formed, puts an ECO item cell into the drive as its player does. */
    static boolean fitCell(GameTestHelper helper, Tier tier, BlockPos drive) {
        return fitCell(helper, drive, tier.id("eco_item_storage_cell_16m"));
    }

    private static boolean fitCell(GameTestHelper helper, BlockPos drive, String cell) {
        var entity = helper.getLevel().getBlockEntity(helper.absolutePos(drive));
        if (!(entity instanceof IAEMultiBlock<?> part) || part.getCluster() == null) return false;
        try {
            // An empty drive holds no stack at all.
            var held = (ItemStack) entity.getClass().getMethod("getCellStack").invoke(entity);
            if (held == null || held.isEmpty()) {
                entity.getClass().getMethod("setCellStack", ItemStack.class)
                        .invoke(entity, new ItemStack(AddonCraftingScene.item(cell)));
            }
            held = (ItemStack) entity.getClass().getMethod("getCellStack").invoke(entity);
            return held != null && !held.isEmpty();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("The ECO drive at " + drive + " cannot take a cell", exception);
        }
    }

    private static BlockState block(Tier tier, String name) {
        return AddonCraftingScene.block(tier.id(name)).defaultBlockState();
    }

    /** Some of its blocks have a facing property of their own, so it is found by name. */
    private static BlockState facing(Tier tier, String name, Direction direction) {
        var state = block(tier, name);
        for (var property : state.getProperties()) {
            if (property.getName().equals("facing") && property instanceof DirectionProperty facing) {
                return state.setValue(facing, direction);
            }
        }
        throw new IllegalStateException(tier.id(name) + " has no facing");
    }
}
