package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
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
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.policy.RuleMode;
import space.controlnet.ae2federation.storage.mount.StorageMountService;

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
     * The guide's former storage-system example and its exercise: after the round trip, breaking the tail casing takes
     * the shared items away from the consumer, and putting it back returns them.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void storageSystemDismantled(GameTestHelper helper) {
        helper.succeedWhen(storageDismantled(helper, Tier.L4)::tick);
    }

    static AddonStorageScene storageDismantled(GameTestHelper helper, Tier tier) {
        return storage(helper, tier).dismantlingAfterwards(AddonStorageScene.BESIDE_CHEST.offset(4, 0, -1));
    }

    /**
     * A regional warehouse, once a guide example: an ECO storage system on the warehouse network, with the only energy
     * cell; a hub network that uses the warehouse's storage with re-export; and two districts, each with no storage and
     * no power of its own and a Bridge of its own to the hub. A district is two Bridges from the warehouse, shares no
     * domain and has no rule with it, yet sees, takes and stores into it. Stepping the hub's rule back to
     * plain Enabled takes the warehouse away from both districts but not from the hub; re-export brings it back.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 1200)
    public static void ecoWarehouseChain(GameTestHelper helper) {
        helper.succeedWhen(new Warehouse(helper, Tier.L4)::run);
    }

    /** The warehouse chain's stages; see {@link #ecoWarehouseChain}. */
    static final class Warehouse {
        private static final appeng.api.stacks.AEItemKey IRON =
                appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.IRON_INGOT);
        /** The storage system's bottom north-west corner; its interface touches the warehouse's cable at (9, 4, 3). */
        private static final BlockPos STORAGE = new BlockPos(10, 3, 2);
        private static final BlockPos DRIVE = STORAGE.offset(3, 1, 0);
        private final GameTestHelper helper;
        private final Tier tier;
        private final BridgeChainFixture chain;
        private final appeng.api.networking.security.IActionSource source =
                appeng.api.networking.security.IActionSource.empty();
        private int stage;

        Warehouse(GameTestHelper helper, Tier tier) {
            this.helper = helper;
            this.tier = tier;
            chain = new BridgeChainFixture(helper, List.of(
                    new BridgeChainFixture.Network("district1", 7, 1, 3, AEColor.PURPLE),
                    new BridgeChainFixture.Network("district2", 7, 7, 9, AEColor.YELLOW),
                    new BridgeChainFixture.Network("hub", 8, 1, 9, AEColor.LIGHT_BLUE),
                    new BridgeChainFixture.Network("warehouse", 9, 3, 7, AEColor.GREEN)),
                    List.of(new BridgeChainFixture.Link("district1", "hub", 2),
                            new BridgeChainFixture.Link("hub", "warehouse", 5),
                            new BridgeChainFixture.Link("district2", "hub", 8)));
            // The warehouse's energy cell is the only power source; it touches no other network's cable.
            helper.setBlock(chain.cable("warehouse", 7).below(),
                    appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
            placeStorageSystem(helper, tier, STORAGE);
        }

        void run() {
            switch (stage) {
                case 0 -> {
                    helper.assertTrue(chain.built(), "Waiting for the networks and Bridges: " + chain.readiness());
                    helper.assertTrue(chain.powered(), "Waiting for the ME power rules to power every network");
                    helper.assertTrue(fitCell(helper, tier, DRIVE) && mountedStorage(helper, DRIVE) != null,
                            "Waiting for the ECO storage system to form and take its cell");
                    helper.assertFalse(chain.shareDomain("district1", "warehouse"),
                            "A district must share no domain with the warehouse");
                    chain.rule("hub", "warehouse", PolicyCapability.STORAGE, reexport());
                    chain.rule("district1", "hub", PolicyCapability.STORAGE, PolicyRule.storageDefaults());
                    chain.rule("district2", "hub", PolicyCapability.STORAGE, PolicyRule.storageDefaults());
                    helper.assertValueEqual(inventory("warehouse").insert(IRON, 9, Actionable.MODULATE, source), 9L,
                            "The warehouse's storage system must store the iron");
                    stage = 1;
                    helper.fail("Set the rules and stocked the warehouse");
                }
                case 1 -> {
                    helper.assertValueEqual(seen("district1"), 9L, "Waiting for district 1 to see the warehouse: "
                            + mountStatus("district1"));
                    helper.assertValueEqual(seen("district2"), 9L, "Waiting for district 2 to see the warehouse: "
                            + mountStatus("district2"));
                    helper.assertTrue(PolicyService.get(helper.getLevel())
                            .configured(chain.key("district1", "warehouse", PolicyCapability.STORAGE)).isEmpty(),
                            "No rule joins a district to the warehouse");
                    helper.assertValueEqual(inventory("district1").extract(IRON, 4, Actionable.MODULATE, source), 4L,
                            "District 1 must take iron out of the warehouse");
                    helper.assertValueEqual(stored(), 5L, "What district 1 took must leave the storage system");
                    helper.assertValueEqual(inventory("district2").insert(IRON, 4, Actionable.MODULATE, source), 4L,
                            "District 2, with no storage of its own, must store into the warehouse");
                    helper.assertValueEqual(stored(), 9L, "What district 2 stored must reach the storage system");
                    // The exercise: the hub's rule steps back from re-export to plain Enabled.
                    chain.rule("hub", "warehouse", PolicyCapability.STORAGE, PolicyRule.storageDefaults());
                    stage = 2;
                    helper.fail("Stepped the hub's rule back to Enabled");
                }
                case 2 -> {
                    helper.assertValueEqual(seen("district1") + seen("district2"), 0L,
                            "Waiting for the warehouse to leave both districts");
                    helper.assertValueEqual(seen("hub"), 9L, "The hub keeps the warehouse under its own rule");
                    chain.rule("hub", "warehouse", PolicyCapability.STORAGE, reexport());
                    stage = 3;
                    helper.fail("Stepped the hub's rule to re-export again");
                }
                default -> {
                    helper.assertValueEqual(seen("district1"), 9L, "Waiting for the warehouse to return to district 1");
                    helper.assertValueEqual(seen("district2"), 9L, "Waiting for the warehouse to return to district 2");
                    chain.close();
                }
            }
        }

        private static PolicyRule reexport() {
            return PolicyRule.storageDefaults().withMode(RuleMode.REEXPORT);
        }

        private MEStorage inventory(String network) {
            return chain.grid(network).getStorageService().getInventory();
        }

        private long seen(String network) {
            return inventory(network).getAvailableStacks().get(IRON);
        }

        /** What the ECO drive's own cell holds, so nothing is counted twice through a mount. */
        private long stored() {
            return mountedStorage(helper, DRIVE).extract(IRON, Long.MAX_VALUE, Actionable.SIMULATE, source);
        }

        private String mountStatus(String district) {
            return "hub " + StorageMountService.status(helper.getLevel(),
                    chain.key("hub", "warehouse", PolicyCapability.STORAGE)) + ", district "
                    + StorageMountService.status(helper.getLevel(), chain.key(district, "hub", PolicyCapability.STORAGE));
        }
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
