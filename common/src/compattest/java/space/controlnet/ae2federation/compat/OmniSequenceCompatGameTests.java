package space.controlnet.ae2federation.compat;

import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Federation features with OmniSequence's own blocks, which batch crafting jobs. */
@PrefixGameTestTemplate(false)
public final class OmniSequenceCompatGameTests {
    private static final String NEXUS = "molecularmanipulator:transfinite_compute_nexus";

    private OmniSequenceCompatGameTests() {
    }

    /** The consumer's only CPU is a Transfinite Compute Nexus, whose virtual CPUs drive the provider's pattern. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void nexusCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "ae2:molecular_assembler", List.of(NEXUS));
        helper.succeedWhen(scene::tick);
    }

    /** The Transfinite Compute Nexus' job is cancelled after the push. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void nexusCancel(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "minecraft:chest", List.of(NEXUS), true)
                .cancellingAfterPush();
        helper.succeedWhen(scene::tick);
    }

    /** A Transfinite Compute Nexus is the Federation Pattern Provider network's CPU for a job through an Endpoint. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 900)
    public static void nexusEndpoint(GameTestHelper helper) {
        var scene = new EndpointMachineScene(helper, new CoreCompatGameTests.Furnace(), NEXUS);
        helper.succeedWhen(scene::tick);
    }

    /**
     * The provider network's pattern sits in a Molecular Sequence Rewrite Array, which crafts inside itself in batches
     * instead of pushing to an assembler.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void molecularManipulatorCrafting(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "molecularmanipulator:molecular_manipulator", "minecraft:air",
                List.of("ae2:1k_crafting_storage"));
        helper.succeedWhen(scene::tick);
    }

    /**
     * The guide's OmniSequence example: the provider network is a formed Matter Fabrication Well, its controller on the
     * network's cable, with a Fabrication Pattern Assembly in a front service bay holding a processing pattern for one
     * of the well's recipes that needs no research. The consumer orders calcite; the well makes it and the assembly
     * returns it to its network, from where it reaches the consumer. As the guide's exercise has its player do, breaking
     * one casing of the well takes the recipe away from the consumer, and putting it back returns it.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "matter_fabrication_well", timeoutTicks = 2400)
    public static void matterFabricationWell(GameTestHelper helper) {
        var well = new MatterFabricationWell(helper);
        var scene = AddonCraftingScene.processingStructure(helper, MatterFabricationWell.ASSEMBLY,
                List.of("ae2:1k_crafting_storage"), new Calcite(), test -> well.place(), MatterFabricationWell.BAY,
                entity -> well.ready()).dismantlingAfterwards(MatterFabricationWell.CASING);
        helper.succeedWhen(scene::tick);
    }

    /** The Bridge is removed while the Transfinite Compute Nexus runs a job. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void nexusDisconnected(GameTestHelper helper) {
        var scene = new AddonCraftingScene(helper, "ae2:pattern_provider", "minecraft:chest", List.of(NEXUS), true)
                .disconnectingAfterPush();
        helper.succeedWhen(scene::tick);
    }


    /** The well's calcite recipe, which needs no research: four quartz and four bone meal make eight calcite. */
    private record Calcite() implements AddonCraftingScene.Machine {
        @Override
        public String blockId() {
            return MatterFabricationWell.ASSEMBLY;
        }

        @Override
        public Map<AEItemKey, Long> inputs() {
            var inputs = new LinkedHashMap<AEItemKey, Long>();
            inputs.put(AEItemKey.of(Items.QUARTZ), 4L);
            inputs.put(AEItemKey.of(Items.BONE_MEAL), 4L);
            return inputs;
        }

        @Override
        public AEKey output() {
            return AEItemKey.of(Items.CALCITE);
        }

        @Override
        public long outputPerInput() {
            return 8;
        }

        /** The assembly returns what the well made to its network by itself. */
        @Override
        public void collect(GameTestHelper helper, BlockPos position, MEStorage network) {
        }
    }

    /**
     * A Matter Fabrication Well in the {@code matter_fabrication_well} template, beside the provider network of
     * {@link AddonCraftingScene}. Its controller faces north, so the structure runs 20 blocks to each side of it, 5 in
     * front, 35 behind, 2 below and 24 above. A player presses the controller's Build button, which places each block
     * of the mod's own blueprint; here the test places the same blocks from that blueprint, as no player can join a
     * test server that other mods send their configuration to. The player then places the assembly in a front service
     * bay once the well has formed.
     */
    private static final class MatterFabricationWell {
        static final String ASSEMBLY = "molecularmanipulator:matter_fabrication_pattern_assembly";
        private static final BlockPos CONTROLLER = new BlockPos(25, 3, 13);
        /** The front service bay two blocks right of the controller and four in front: blueprint (2, 2, -19). */
        static final BlockPos BAY = CONTROLLER.offset(2, 0, -4);
        /** A casing of the base, under the front steps: blueprint (0, 0, -18). */
        static final BlockPos CASING = CONTROLLER.offset(0, -2, -3);

        private final GameTestHelper helper;

        MatterFabricationWell(GameTestHelper helper) {
            this.helper = helper;
        }

        /**
         * Runs the provider network's cable from its end along the front of the well and into the service channel in
         * front of the controller, and builds the well around where the controller goes.
         */
        void place() {
            var start = AddonCraftingScene.PROVIDER_CABLE.east();
            for (int x = start.getX(); x <= CONTROLLER.getX(); x++) {
                cable(new BlockPos(x, start.getY(), start.getZ()));
            }
            for (int z = start.getZ() + 1; z < CONTROLLER.getZ(); z++) {
                cable(new BlockPos(CONTROLLER.getX(), start.getY(), z));
            }
            build();
        }

        /** What the controller's Build places: every blueprint block but the controller, at its place for the facing. */
        private void build() {
            try {
                var structure = Class.forName("com.atir.molecularmanipulator.blockentity.MatterFabricationStructure");
                var part = Class.forName("com.atir.molecularmanipulator.blockentity.MatterFabricationStructure$Part");
                var type = Class.forName("com.atir.molecularmanipulator.blockentity.MatterFabricationStructure$PartType");
                var worldPos = structure.getMethod("worldPos", BlockPos.class, Direction.class, part);
                var partState = structure.getMethod("partState", type);
                var isController = structure.getMethod("isController", part);
                var controller = helper.absolutePos(CONTROLLER);
                for (var each : (List<?>) structure.getMethod("parts").invoke(null)) {
                    if ((Boolean) isController.invoke(null, each)) continue;
                    var state = (net.minecraft.world.level.block.state.BlockState) partState.invoke(null,
                            part.getMethod("type").invoke(each));
                    helper.getLevel().setBlock((BlockPos) worldPos.invoke(null, controller, Direction.NORTH, each), state, 3);
                }
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("The Matter Fabrication Well's blueprint cannot be read", exception);
            }
        }

        /**
         * True once the well has formed and the assembly placed in its bay works with it. The controller goes in only
         * once the cable has joined the provider network, as a player places it at the end of a working cable.
         */
        boolean ready() {
            var controller = AddonCraftingScene.block("molecularmanipulator:matter_fabrication_controller");
            if (!helper.getBlockState(CONTROLLER).is(controller)) {
                helper.setBlock(CONTROLLER, controller.defaultBlockState()
                        .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
                return false;
            }
            if (!(Boolean) invoke(controller(), "isStructureFormed")) return false;
            if (!helper.getBlockState(BAY).is(AddonCraftingScene.block(ASSEMBLY))) {
                helper.setBlock(BAY, AddonCraftingScene.block(ASSEMBLY));
                return false;
            }
            return (Boolean) invoke(helper.getLevel().getBlockEntity(helper.absolutePos(BAY)), "isOperational");
        }

        private BlockEntity controller() {
            return helper.getLevel().getBlockEntity(helper.absolutePos(CONTROLLER));
        }

        private void cable(BlockPos position) {
            helper.assertTrue(PartHelper.setPart(helper.getLevel(), helper.absolutePos(position), null, null,
                    AEParts.GLASS_CABLE.item(AEColor.BLUE)) != null, "A cable must go at " + position);
        }

        private static Object invoke(Object target, String name) {
            try {
                return target.getClass().getMethod(name).invoke(target);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException(target.getClass().getName() + " has no usable " + name, exception);
            }
        }
    }
}
