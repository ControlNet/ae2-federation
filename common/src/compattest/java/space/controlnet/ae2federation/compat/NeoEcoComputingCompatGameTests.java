package space.controlnet.ae2federation.compat;

import static space.controlnet.ae2federation.compat.NeoEcoCompatGameTests.block;
import static space.controlnet.ae2federation.compat.NeoEcoCompatGameTests.facing;

import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.core.definitions.AEParts;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.automation.ComputingDevices;
import space.controlnet.ae2federation.automation.ComputingHost;
import space.controlnet.ae2federation.automation.Installation;
import space.controlnet.ae2federation.automation.ProgramScheduler;
import space.controlnet.ae2federation.automation.ProgramStore;
import space.controlnet.ae2federation.compat.NeoEcoCompatGameTests.Tier;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.router.CableVisualConnections;

/**
 * The Federation computation system on Neo ECO's computation structure. Its own group, since it registers only with
 * Neo ECO 21.2.1 or later, and the profiles on older versions run the {@code neoecoae} group.
 */
@PrefixGameTestTemplate(false)
public final class NeoEcoComputingCompatGameTests {
    private NeoEcoComputingCompatGameTests() {
    }

    /**
     * The Federation computation system: Neo ECO's computation structure with the Federation controller and a casing
     * column where Neo ECO's ME interface would go. It forms, becomes a computing device of the domain of the Federation
     * Cable at its front, which reaches it with a dense arm, and ME cables against its parts stay off their AE2 grid.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 300)
    public static void federationComputationSystemLinksAtItsFront(GameTestHelper helper) {
        var controller = placeFederationComputationSystem(helper, "computation_casing", "eco_computation_system_l4");
        var front = controller.north();
        helper.setBlock(front, space.controlnet.ae2federation.router.RouterRegistration.FEDERATION_CABLE.get());
        // ME cables against its parts' outer faces, where an ME network would try to reach it.
        var probes = List.of(controller.east(2).above().north(), controller.east(2).north(),
                controller.east(3).north(), controller.above().north(), controller.west().north(),
                controller.east(4).above(2), controller.south(2));
        for (var probe : probes) {
            PartHelper.setPart(helper.getLevel(), helper.absolutePos(probe), null, null,
                    AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        }
        var level = helper.getLevel();
        helper.succeedWhen(() -> {
            var state = helper.getBlockState(controller);
            helper.assertTrue(formed(state), "the Federation computation system forms without an ME interface");
            helper.assertTrue(helper.getBlockEntity(controller) instanceof ComputingHost host && host.id() != null,
                    "its controller is a computing device with an ID");
            var node = FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(controller));
            var domain = FederationDomainRegistryAccess.get(level).federationDomainOf(
                    FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(front)));
            helper.assertTrue(domain.isPresent() && domain.get().nodes().contains(node),
                    "the cable at its front joins it to the domain");
            helper.assertValueEqual(CableVisualConnections.kind(CableVisualConnections.connections(level,
                    helper.absolutePos(front)), Direction.SOUTH), CableVisualConnections.DENSE,
                    "the cable reaches the front with a dense arm");
            var drive = helper.getBlockEntity(controller.east(2).above());
            var grid = drive instanceof AENetworkedBlockEntity networked ? networked.getMainNode().getGrid() : null;
            helper.assertTrue(grid != null, "its parts are on one grid");
            for (var probe : probes) {
                var cable = helper.getBlockEntity(probe) instanceof IInWorldGridNodeHost host
                        ? host.getGridNode(null) : null;
                helper.assertTrue(cable != null && cable.getGrid() != grid,
                        "an ME cable at " + probe + " stays off the grid of its parts");
            }
        });
    }

    /**
     * An L9 Federation computation system takes no network switch at its side: a switch pools clusters across systems
     * and would lend this one's capacity to a Neo ECO system on ME. The same structure forms with casing there.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void federationComputationSystemRefusesANetworkSwitch(GameTestHelper helper) {
        var controller = placeFederationComputationSystem(helper, "computation_casing", "eco_computation_system_l9");
        var side = controller.west();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(formed(helper.getBlockState(controller)),
                        "an L9 controller forms over L4 parts with casing at its side"))
                .thenExecute(() -> helper.setBlock(side, block(Tier.L4, "computation_network_switch")))
                .thenWaitUntil(() -> helper.assertFalse(formed(helper.getBlockState(controller)),
                        "a network switch at its side keeps it from forming"))
                .thenExecuteAfter(40, () -> helper.assertFalse(formed(helper.getBlockState(controller)),
                        "it stays unformed"))
                .thenSucceed();
    }

    /** With Neo ECO's ME interface in its interface cell, the Federation computation system does not form. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 300)
    public static void federationComputationSystemRefusesAnMeInterface(GameTestHelper helper) {
        var controller = placeFederationComputationSystem(helper, "computation_interface",
                "eco_computation_system_l4");
        helper.runAfterDelay(60, () -> {
            helper.assertFalse(formed(helper.getBlockState(controller)), "an ME interface keeps it from forming");
            helper.succeed();
        });
    }

    /**
     * Programs install and run on the Federation computation system as on any computing device. A broken part takes it
     * out of its domain while its installation stays, and it runs again once the part is back.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void federationComputationSystemRunsInstalledPrograms(GameTestHelper helper) {
        var controller = placeFederationComputationSystem(helper, "computation_casing", "eco_computation_system_l4");
        helper.setBlock(controller.north(), space.controlnet.ae2federation.router.RouterRegistration.FEDERATION_CABLE
                .get());
        var file = "neoeco_sched_run";
        writeProgram(helper, file, """
                def ping() -> str:
                    return "ping"
                """);
        var level = helper.getLevel();
        var scheduler = ProgramScheduler.get(level.getServer());
        var device = new String[1];
        var installation = new String[1];
        var casing = controller.above();
        var casingState = new BlockState[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    device[0] = helper.getBlockEntity(controller) instanceof ComputingHost host ? host.id() : null;
                    helper.assertTrue(device[0] != null && sharesDomain(helper, controller, controller.north()),
                            "waiting for the device to join the cable's domain");
                })
                .thenExecute(() -> {
                    installation[0] = scheduler.install(device[0], file, "ping", List.of()).id();
                    scheduler.trigger(installation[0]);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(log(scheduler, installation[0]),
                        List.of("started", "done: 'ping'"), "it ran once"))
                .thenExecute(() -> {
                    casingState[0] = helper.getBlockState(casing);
                    helper.destroyBlock(casing);
                })
                .thenWaitUntil(() -> helper.assertFalse(sharesDomain(helper, controller, controller.north()),
                        "a broken part takes it out of the cable's domain"))
                .thenExecute(() -> helper.assertTrue(ComputingDevices.get(level).installation(installation[0])
                        .isPresent(), "its installation stays"))
                .thenExecute(() -> helper.setBlock(casing, casingState[0]))
                .thenWaitUntil(() -> helper.assertTrue(sharesDomain(helper, controller, controller.north()),
                        "it rejoins the cable's domain once the part is back"))
                .thenExecute(() -> scheduler.trigger(installation[0]))
                .thenWaitUntil(() -> helper.assertValueEqual(log(scheduler, installation[0]),
                        List.of("started", "done: 'ping'", "started", "done: 'ping'"), "it ran again"))
                .thenExecute(() -> {
                    scheduler.uninstall(installation[0]);
                    deleteProgram(helper, file);
                })
                .thenSucceed();
    }

    /**
     * A redstone signal starts a Redstone-triggered installation, as on any computing device. Its structure covers every
     * face but the front, so the signal comes through a part: a lever on the casing above it.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void aLeverOnItsCasingStartsTheFederationComputationSystem(GameTestHelper helper) {
        var controller = placeFederationComputationSystem(helper, "computation_casing", "eco_computation_system_l4");
        helper.setBlock(controller.north(), space.controlnet.ae2federation.router.RouterRegistration.FEDERATION_CABLE
                .get());
        var lever = controller.above(2);
        helper.setBlock(lever, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE,
                AttachFace.FLOOR));
        var file = "neoeco_sched_redstone";
        writeProgram(helper, file, """
                def ping() -> str:
                    return "ping"
                """);
        var level = helper.getLevel();
        var scheduler = ProgramScheduler.get(level.getServer());
        var device = new String[1];
        var installation = new String[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    device[0] = helper.getBlockEntity(controller) instanceof ComputingHost host ? host.id() : null;
                    helper.assertTrue(device[0] != null && sharesDomain(helper, controller, controller.north()),
                            "waiting for the device to join the cable's domain");
                })
                .thenExecute(() -> {
                    installation[0] = scheduler.install(device[0], file, "ping", List.of()).id();
                    scheduler.setTrigger(installation[0], new Installation.Trigger.Redstone());
                    helper.pullLever(lever);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(log(scheduler, installation[0]),
                        List.of("started", "done: 'ping'"), "the lever turning on starts it once"))
                .thenExecuteAfter(10, () -> helper.assertValueEqual(log(scheduler, installation[0]).size(), 2,
                        "a signal that stays on does not start it again"))
                .thenExecute(() -> {
                    scheduler.uninstall(installation[0]);
                    deleteProgram(helper, file);
                })
                .thenSucceed();
    }

    /** The Federation computation systems are made on Neo ECO's Integrated Working Station, from Nexus Processors. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke")
    public static void federationComputationSystemsHaveWorkingStationRecipes(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        for (var tier : List.of("l4", "l6", "l9")) {
            var id = ResourceLocation.fromNamespaceAndPath("ae2federation", "eco_computation_system_" + tier);
            var recipe = recipes.byKey(id).orElse(null);
            helper.assertTrue(recipe != null, id + " loads");
            helper.assertValueEqual(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.value().getType()),
                    ResourceLocation.fromNamespaceAndPath("neoecoae", "integrated_working_station"), id + " type");
            helper.assertValueEqual(BuiltInRegistries.ITEM.getKey(recipe.value().getResultItem(
                    helper.getLevel().registryAccess()).getItem()), id, id + " result");
        }
        helper.succeed();
    }

    /** Breaking the controller stops its runs, drops its installations and frees its ID. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void breakingTheFederationComputationSystemDropsItsInstallations(GameTestHelper helper) {
        var controller = placeFederationComputationSystem(helper, "computation_casing", "eco_computation_system_l4");
        helper.setBlock(controller.north(), space.controlnet.ae2federation.router.RouterRegistration.FEDERATION_CABLE
                .get());
        var file = "neoeco_sched_broken";
        writeProgram(helper, file, """
                def slow() -> int:
                    mc.wait(100)
                    return 1
                """);
        var level = helper.getLevel();
        var scheduler = ProgramScheduler.get(level.getServer());
        var device = new String[1];
        var installation = new String[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    device[0] = helper.getBlockEntity(controller) instanceof ComputingHost host ? host.id() : null;
                    helper.assertTrue(device[0] != null && sharesDomain(helper, controller, controller.north()),
                            "waiting for the device to join the cable's domain");
                })
                .thenExecute(() -> {
                    installation[0] = scheduler.install(device[0], file, "slow", List.of()).id();
                    scheduler.trigger(installation[0]);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(scheduler.running(installation[0]), 1, "running"))
                .thenExecute(() -> helper.destroyBlock(controller))
                .thenExecuteAfter(2, () -> {
                    helper.assertValueEqual(scheduler.running(installation[0]), 0, "its run stopped");
                    helper.assertTrue(ComputingDevices.get(level).installation(installation[0]).isEmpty(),
                            "its installation went with it");
                    helper.assertTrue(ComputingDevices.get(level).entry(device[0]).isEmpty(), "its ID is free again");
                    deleteProgram(helper, file);
                })
                .thenSucceed();
    }

    /** Whether the blocks at {@code a} and {@code b} are nodes of one Federation domain. */
    private static boolean sharesDomain(GameTestHelper helper, BlockPos a, BlockPos b) {
        var level = helper.getLevel();
        var registry = FederationDomainRegistryAccess.get(level);
        var domain = registry.federationDomainOf(FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(b)));
        return domain.isPresent() && domain.get().nodes().contains(
                FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(a)));
    }

    private static List<String> log(ProgramScheduler scheduler, String installation) {
        return scheduler.log(installation).stream().map(ProgramScheduler.LogEntry::text).toList();
    }

    private static void writeProgram(GameTestHelper helper, String file, String text) {
        var store = ProgramStore.get(helper.getLevel().getServer());
        try {
            Files.writeString(store.folder().resolve(file + ".ae2f"), text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        store.reload();
    }

    private static void deleteProgram(GameTestHelper helper, String file) {
        var store = ProgramStore.get(helper.getLevel().getServer());
        try {
            Files.deleteIfExists(store.folder().resolve(file + ".ae2f"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        store.reload();
    }

    /**
     * Neo ECO's smallest computation structure, as {@link NeoEcoCompatGameTests#placeComputationSystem} lays it out without the consumer's
     * cable, around the Federation controller {@code controllerId} facing north, with {@code interfaceCell} where the
     * interface goes.
     * Returns the controller's position.
     */
    private static BlockPos placeFederationComputationSystem(GameTestHelper helper, String interfaceCell,
            String controllerId) {
        var tier = Tier.L4;
        var start = NeoEcoCompatGameTests.COMPUTATION.offset(0, -1, -1);
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
        helper.setBlock(NeoEcoCompatGameTests.COMPUTATION, block(tier, interfaceCell));
        var controller = start.offset(1, 1, 0);
        helper.setBlock(controller, facing(new Tier("ae2federation", Map.of()), controllerId, Direction.NORTH));
        return controller;
    }

    private static boolean formed(BlockState state) {
        for (var property : state.getProperties()) {
            if (property.getName().equals("formed")) return Boolean.TRUE.equals(state.getValue(property));
        }
        return false;
    }
}
