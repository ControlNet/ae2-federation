package space.controlnet.ae2federation.compat;

import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Federation with Neo ECO Prototype's L1 tier: its storage, crafting and computation systems, built as Neo ECO's own
 * ({@link NeoEcoCompatGameTests}) from its own blocks, its 27-slot pattern provider, its processor assembler and its
 * superconductive interface. Its Trinity system is left out: the addon marks it as not implemented yet.
 */
@PrefixGameTestTemplate(false)
public final class NeoEcoPrototypeCompatGameTests {
    private static final String PATTERN_PROVIDER = "neoecoprototype:simplify_pattern_provider";

    /** Neo ECO's L4 names mapped to Neo ECO Prototype's L1 blocks and cells. */
    static final NeoEcoCompatGameTests.Tier L1 = new NeoEcoCompatGameTests.Tier("neoecoprototype", Map.ofEntries(
            Map.entry("crafting_casing", "simplify_crafting_casing"),
            Map.entry("input_hatch", "simplify_fluid_input_hatch"),
            Map.entry("output_hatch", "simplify_fluid_output_hatch"),
            Map.entry("crafting_interface", "simplify_crafting_interface"),
            Map.entry("crafting_parallel_core_l4", "simplify_crafting_parallel_core"),
            Map.entry("crafting_worker", "simplify_crafting_worker"),
            Map.entry("crafting_pattern_bus", "simplify_crafting_pattern_bus"),
            Map.entry("crafting_vent", "simplify_crafting_vent"),
            Map.entry("crafting_system_l4", "simplify_crafting_system"),
            Map.entry("storage_casing", "simplify_storage_casing"),
            Map.entry("eco_drive", "simplify_drive"),
            Map.entry("storage_interface", "simplify_storage_interface"),
            Map.entry("energy_cell_l4", "simplify_energy_cell"),
            Map.entry("storage_vent", "simplify_storage_vent"),
            Map.entry("storage_system_l4", "simplify_storage_controller"),
            Map.entry("computation_casing", "simplify_computation_casing"),
            Map.entry("computation_drive", "simplify_computation_drive"),
            Map.entry("computation_transmitter", "simplify_computation_transmitter"),
            Map.entry("computation_parallel_core_l4", "simplify_computation_parallel_core"),
            Map.entry("computation_threading_core_l4", "simplify_computation_threading_core"),
            Map.entry("computation_cooling_controller_l4", "simplify_computation_cooling_controller"),
            Map.entry("computation_interface", "simplify_computation_interface"),
            Map.entry("computation_system_l4", "simplify_computation_system"),
            // An L1 drive mounts only L1 cells unless the server config names others.
            Map.entry("eco_item_storage_cell_16m", "simplify_item_storage_cell_1m"),
            Map.entry("eco_computation_cell_l4", "simplify_computation_cell_1m")));

    private NeoEcoPrototypeCompatGameTests() {
    }

    /** The F1 crafting system serves the remote craft: its pattern bus provides the pattern, its worker crafts it. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void l1CraftingSystemCrafting(GameTestHelper helper) {
        helper.succeedWhen(NeoEcoCompatGameTests.crafting(helper, L1)::tick);
    }

    /** The C1 computation system, with a flash array in a drive, is the consumer's only CPU. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void l1ComputationSystemOrdering(GameTestHelper helper) {
        helper.succeedWhen(NeoEcoCompatGameTests.computationOrdering(helper, L1)::tick);
    }

    /**
     * The guide's ECO district at L1: the consumer stores in an L1 storage system and runs its jobs on a C1 computation
     * system, and orders from an F1 crafting system on a provider with no power of its own, whose recipes leave and
     * return when a casing is broken and put back.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 1200)
    public static void l1DistrictOrdering(GameTestHelper helper) {
        helper.succeedWhen(NeoEcoCompatGameTests.district(helper, L1)::tick);
    }

    /** The L1 storage system, with an L1 item matrix in a drive, is the provider network's storage the rule shares. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void l1StorageSystemStorage(GameTestHelper helper) {
        helper.succeedWhen(NeoEcoCompatGameTests.storage(helper, L1)::tick);
    }

    /** As {@link #l1StorageSystemStorage}; breaking the tail casing then takes the shared items away until it is back. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void l1StorageSystemDismantled(GameTestHelper helper) {
        helper.succeedWhen(NeoEcoCompatGameTests.storageDismantled(helper, L1)::tick);
    }

    /** The L1 Pattern Provider runs the remote request's processing pattern in a machine. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void l1PatternProviderProcessing(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, PATTERN_PROVIDER, "minecraft:chest",
                List.of("ae2:1k_crafting_storage"), true);
        helper.succeedWhen(scene::tick);
    }

    /** The L1 Pattern Provider serves the remote craft into the L1 Processor Assembler, which crafts it. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void l1ProcessorAssemblerCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, PATTERN_PROVIDER, "neoecoprototype:simplify_stonecutting_assembler",
                List.of("ae2:1k_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }

    /** The L1 Pattern Provider runs an Endpoint in Local mode, as AE2's own does. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalL1PatternProvider(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace())
                .throughLocalProvider(PATTERN_PROVIDER);
        helper.succeedWhen(scene::tick);
    }

    /** As {@link #endpointLocalL1PatternProvider}, with the L1 Cable Pattern Provider on a cable. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void endpointLocalL1CablePatternProvider(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace())
                .throughLocalProviderPart("neoecoprototype:cable_pattern_provider");
        helper.succeedWhen(scene::tick);
    }

    /**
     * The Energized Superconductive Interface, a passive generator, is the only power source: on a plant network with no
     * energy cell anywhere, it powers a workshop and a district through ME power. The district orders sticks from the
     * workshop's pattern provider and assembler. Switching off ME power for the workshop then darkens it, and sticks
     * leave the district's craftables.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "scale_36_empty", timeoutTicks = 1200)
    public static void superconductiveInterfacePowerPlant(GameTestHelper helper) {
        var scene = new RouterCraftingScene(helper, new BlockPos(18, 1, 18));
        var generator = scene.start(Direction.NORTH).above();
        scene.member("plant", Direction.NORTH, (test, start, outward) -> placeGenerator(test, start), generator,
                        entity -> true)
                .provider("workshop", Direction.EAST, RouterCraftingScene::placeAssemblyProvider,
                        scene.start(Direction.EAST), RouterCraftingScene::patternProvider,
                        RouterCraftingScene.sticksPattern(helper))
                .consumer("district", Direction.SOUTH, RouterCraftingScene::placeCpu, RouterCraftingScene::formed,
                        RouterCraftingScene.STICKS, 8, RouterCraftingScene.PLANKS, 4, "workshop")
                .poweredBy("plant")
                .withoutEnergyCell()
                .thenSwitchingOffPower("workshop", RouterCraftingScene.STICKS, "district");
        helper.succeedWhen(scene::tick);
    }

    /** An ME cable at {@code start} with the superconductive interface on top. */
    private static void placeGenerator(GameTestHelper helper, BlockPos start) {
        helper.assertTrue(PartHelper.setPart(helper.getLevel(), helper.absolutePos(start), null, null,
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)) != null, "A cable must carry the interface");
        helper.setBlock(start.above(), AddonCraftingScene.block("neoecoprototype:superconductive_interface"));
    }
}
