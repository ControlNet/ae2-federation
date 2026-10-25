package space.controlnet.ae2federation.compat;

import static space.controlnet.ae2federation.compat.NeoEcoComputingCompatGameTests.CABLE;
import static space.controlnet.ae2federation.compat.NeoEcoComputingCompatGameTests.INTERFACE;
import static space.controlnet.ae2federation.compat.NeoEcoComputingCompatGameTests.communicationInterface;
import static space.controlnet.ae2federation.compat.NeoEcoComputingCompatGameTests.deleteProgram;
import static space.controlnet.ae2federation.compat.NeoEcoComputingCompatGameTests.formed;
import static space.controlnet.ae2federation.compat.NeoEcoComputingCompatGameTests.log;
import static space.controlnet.ae2federation.compat.NeoEcoComputingCompatGameTests.placeFederationComputationSystem;
import static space.controlnet.ae2federation.compat.NeoEcoComputingCompatGameTests.placeLinkedFederationComputationSystem;
import static space.controlnet.ae2federation.compat.NeoEcoComputingCompatGameTests.sharesDomain;
import static space.controlnet.ae2federation.compat.NeoEcoComputingCompatGameTests.sideFace;
import static space.controlnet.ae2federation.compat.NeoEcoComputingCompatGameTests.writeProgram;
import static space.controlnet.ae2federation.compat.NeoEcoPrototypeCompatGameTests.L1;

import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.automation.ComputingHost;
import space.controlnet.ae2federation.automation.ProgramScheduler;

/**
 * ECO - N1: the Federation computation system on Neo ECO Prototype's C1 structure, which registers only beside Neo ECO
 * Prototype 1.4.0 or later. Its own group, since the {@code neoecoprototype} group also runs where N1 does not exist.
 */
@PrefixGameTestTemplate(false)
public final class NeoEcoPrototypeComputingCompatGameTests {
    private static final String N1 = "eco_computation_system_l1";

    private NeoEcoPrototypeComputingCompatGameTests() {
    }

    /**
     * C1's structure with the N1 controller and a communication interface where C1's interface goes: it forms, joins
     * the domain of the cable against the interface and runs an installed program, as N4 does.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void n1LinksThroughItsInterfaceAndRunsPrograms(GameTestHelper helper) {
        var controller = placeLinkedFederationComputationSystem(helper, L1, N1);
        var file = "neoeco_n1_run";
        writeProgram(helper, file, """
                def ping() -> str:
                    return "ping"
                """);
        var scheduler = ProgramScheduler.get(helper.getLevel().getServer());
        var device = new String[1];
        var installation = new String[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(formed(helper.getBlockState(controller)), "N1 forms over C1's parts");
                    device[0] = helper.getBlockEntity(controller) instanceof ComputingHost host ? host.id() : null;
                    helper.assertTrue(device[0] != null, "its controller is a computing device with an ID");
                    helper.assertTrue(sharesDomain(helper, controller, CABLE),
                            "the cable at its interface joins it to the domain");
                })
                .thenExecute(() -> {
                    var capacity = ((ComputingHost) helper.getBlockEntity(controller)).capacity();
                    helper.assertTrue(capacity.compute() > 0 && capacity.installations() > 0,
                            "its L1 parallel and threading cores give it compute and installations: " + capacity);
                    installation[0] = scheduler.install(device[0], file, "ping", List.of()).id();
                    scheduler.trigger(installation[0]);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(log(scheduler, installation[0]),
                        List.of("started", "done: 'ping'"), "it ran once"))
                .thenExecute(() -> {
                    scheduler.uninstall(installation[0]);
                    deleteProgram(helper, file);
                })
                .thenSucceed();
    }

    /** One to four communication interfaces anywhere in its side face, as N4: none or a fifth keeps it from forming. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void n1TakesOneToFourInterfacesInItsSideFace(GameTestHelper helper) {
        var controller = placeFederationComputationSystem(helper, L1, "computation_casing", N1);
        var side = sideFace(controller);
        var casing = helper.getBlockState(side.get(4));
        helper.startSequence()
                .thenExecuteAfter(40, () -> helper.assertFalse(formed(helper.getBlockState(controller)),
                        "it does not form with casing all over its side face"))
                .thenExecute(() -> side.subList(0, 4).forEach(cell -> helper.setBlock(cell, communicationInterface())))
                .thenWaitUntil(() -> helper.assertTrue(formed(helper.getBlockState(controller)),
                        "it forms with four interfaces"))
                .thenExecute(() -> helper.setBlock(side.get(4), communicationInterface()))
                .thenWaitUntil(() -> helper.assertFalse(formed(helper.getBlockState(controller)),
                        "a fifth interface keeps it from forming"))
                .thenExecute(() -> helper.setBlock(side.get(4), casing))
                .thenWaitUntil(() -> helper.assertTrue(formed(helper.getBlockState(controller)),
                        "it forms again with four"))
                .thenSucceed();
    }

    /** Neo ECO Prototype's own interfaces, ME and GUI, keep N1 from forming: it has no way into an ME network. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void n1RefusesNeoEcoPrototypesInterfaces(GameTestHelper helper) {
        var controller = placeFederationComputationSystem(helper, L1, "computation_interface", N1);
        var network = BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("neoecoprototype",
                "simplify_computation_network_interface"));
        helper.startSequence()
                .thenExecuteAfter(40, () -> helper.assertFalse(formed(helper.getBlockState(controller)),
                        "C1's ME interface keeps it from forming"))
                .thenExecute(() -> helper.setBlock(INTERFACE, network))
                .thenExecuteAfter(40, () -> helper.assertFalse(formed(helper.getBlockState(controller)),
                        "C1's network interface keeps it from forming"))
                .thenExecute(() -> helper.setBlock(INTERFACE, communicationInterface()))
                .thenWaitUntil(() -> helper.assertTrue(formed(helper.getBlockState(controller)),
                        "it forms with a communication interface there"))
                .thenSucceed();
    }

    /**
     * N1's capacity is its L1 parts' at Neo ECO Prototype's default server config (the guide's numbers): 24 CU per
     * parallel core, two installations per threading core, 1,572,864 B/s per CE1 array. The energized parallel core
     * behind the controller adds 1,024 CU, the energized threading core in the first threading cell gives 16
     * installations, and an energized array 5,242,880 B/s.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void n1CountsItsL1AndEnergizedParts(GameTestHelper helper) {
        var controller = placeLinkedFederationComputationSystem(helper, L1, N1);
        var behind = controller.south();
        var firstThreading = controller.east(2).south();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(formed(helper.getBlockState(controller)), "N1 forms"))
                .thenExecute(() -> insertArray(helper, controller.east(2).above(), "simplify_computation_cell_1m"))
                .thenWaitUntil(() -> helper.assertValueEqual(capacity(helper, controller),
                        new ComputingHost.Capacity(48, 1_572_864, 2), "two parallel cores, one threading core, one array"))
                .thenExecute(() -> {
                    helper.setBlock(behind, block("energized_computation_core"));
                    helper.setBlock(firstThreading, block("energized_computation_threading_core").setValue(
                            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
                            net.minecraft.core.Direction.SOUTH));
                })
                .thenWaitUntil(() -> helper.assertValueEqual(capacity(helper, controller),
                        new ComputingHost.Capacity(48 + 1024, 1_572_864, 16), "with the energized cores"))
                .thenExecute(() -> insertArray(helper, controller.east(2).below(), "energized_computation_cell_4m"))
                .thenWaitUntil(() -> helper.assertValueEqual(capacity(helper, controller),
                        new ComputingHost.Capacity(48 + 1024, 1_572_864 + 5_242_880, 16), "with an energized array"))
                .thenSucceed();
    }

    /** The L1 drive takes an array from a player's hand only. */
    private static void insertArray(GameTestHelper helper, net.minecraft.core.BlockPos drive, String array) {
        var player = helper.makeMockPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(
                BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("neoecoprototype", array))));
        helper.useBlock(drive, player);
        helper.assertTrue(player.getMainHandItem().isEmpty(), "the drive took " + array);
    }

    private static ComputingHost.Capacity capacity(GameTestHelper helper, net.minecraft.core.BlockPos controller) {
        return helper.getBlockEntity(controller) instanceof ComputingHost host ? host.capacity()
                : ComputingHost.Capacity.NONE;
    }

    private static net.minecraft.world.level.block.state.BlockState block(String id) {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("neoecoprototype", id))
                .defaultBlockState();
    }

    /** N1 is crafted as C1 is, with a Nexus Processor in place of the Calculation Processor (user, 2026-10-23). */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke")
    public static void n1IsCraftedAsC1WithANexusProcessor(GameTestHelper helper) {
        var id = ResourceLocation.fromNamespaceAndPath("ae2federation", N1);
        var holder = helper.getLevel().getRecipeManager().byKey(id).orElse(null);
        helper.assertTrue(holder != null, id + " loads");
        var recipe = holder.value();
        helper.assertValueEqual(recipe.getType(), RecipeType.CRAFTING, id + " type");
        helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(recipe.getResultItem(
                helper.getLevel().registryAccess()).getItem()), id, id + " result");
        var nexus = BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2federation",
                "nexus_processor"));
        var centre = recipe.getIngredients().get(4);
        helper.assertTrue(centre.getItems().length == 1 && centre.getItems()[0].is(nexus),
                "a Nexus Processor in the centre");
        helper.succeed();
    }
}
