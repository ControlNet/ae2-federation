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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.automation.AutomationInterfaceBlockEntity;
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
     * The Federation computation system: Neo ECO's computation structure with the Federation controller and a
     * communication interface where Neo ECO's ME interface would go. It forms and becomes a computing device of the
     * domain of the Federation Cable against the interface, which reaches it with a dense arm. A cable at the
     * controller's front stays apart (user, 2026-10-20), and ME cables against its parts stay off their AE2 grid.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 300)
    public static void federationComputationSystemLinksThroughItsInterface(GameTestHelper helper) {
        var controller = placeLinkedFederationComputationSystem(helper, "eco_computation_system_l4");
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
            helper.assertTrue(sharesDomain(helper, controller, CABLE),
                    "the cable at its interface joins it to the domain");
            helper.assertTrue(sharesDomain(helper, INTERFACE, CABLE), "the interface is a node of that domain");
            helper.assertFalse(sharesDomain(helper, controller, front), "the cable at its front stays apart");
            helper.assertValueEqual(CableVisualConnections.kind(CableVisualConnections.connections(level,
                    helper.absolutePos(CABLE)), Direction.EAST), CableVisualConnections.DENSE,
                    "the cable reaches the interface with a dense arm");
            helper.assertValueEqual(CableVisualConnections.kind(CableVisualConnections.connections(level,
                    helper.absolutePos(front)), Direction.SOUTH), CableVisualConnections.NONE,
                    "the cable at its front has no arm toward it");
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

    /** It needs at least one interface (user, 2026-10-21): with casing all over its side face it does not form. */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 300)
    public static void federationComputationSystemNeedsAnInterface(GameTestHelper helper) {
        var controller = placeFederationComputationSystem(helper, "computation_casing", "eco_computation_system_l4");
        var casing = helper.getBlockState(INTERFACE);
        helper.startSequence()
                .thenExecuteAfter(40, () -> helper.assertFalse(formed(helper.getBlockState(controller)),
                        "it does not form with casing all over its side face"))
                .thenExecute(() -> helper.setBlock(INTERFACE, communicationInterface()))
                .thenWaitUntil(() -> helper.assertTrue(formed(helper.getBlockState(controller)),
                        "it forms with one interface"))
                .thenExecute(() -> helper.setBlock(INTERFACE, casing))
                .thenWaitUntil(() -> helper.assertFalse(formed(helper.getBlockState(controller)),
                        "it breaks up once its last interface is gone"))
                .thenSucceed();
    }

    /**
     * Up to four interfaces, anywhere in the side face: a fifth, or one in another face, keeps the structure from
     * forming (user, 2026-10-20).
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void federationComputationSystemTakesFourInterfacesInItsSideFace(GameTestHelper helper) {
        var controller = placeFederationComputationSystem(helper, "computation_casing", "eco_computation_system_l4");
        var side = sideFace(controller);
        for (var cell : side.subList(0, 4)) helper.setBlock(cell, communicationInterface());
        var casing = helper.getBlockState(side.get(4));
        var offSide = controller.east().south();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(formed(helper.getBlockState(controller)),
                        "it forms with four interfaces"))
                .thenExecute(() -> helper.setBlock(side.get(4), communicationInterface()))
                .thenWaitUntil(() -> helper.assertFalse(formed(helper.getBlockState(controller)),
                        "a fifth interface keeps it from forming"))
                .thenExecute(() -> helper.setBlock(side.get(4), casing))
                .thenWaitUntil(() -> helper.assertTrue(formed(helper.getBlockState(controller)),
                        "it forms again with four"))
                .thenExecute(() -> helper.setBlock(offSide, communicationInterface()))
                .thenWaitUntil(() -> helper.assertFalse(formed(helper.getBlockState(controller)),
                        "an interface outside the side face keeps it from forming"))
                .thenSucceed();
    }

    /**
     * A 2x2 bundled cable links only to four interfaces that fill a 2x2 square of the side face (user, 2026-10-20):
     * against three, it stays apart.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void aBundleLinksOnlyToAFilledSquareOfInterfaces(GameTestHelper helper) {
        var controller = placeFederationComputationSystem(helper, "computation_casing", "eco_computation_system_l4");
        var side = sideFace(controller);
        // The lower square: the bottom two rows of both columns.
        var square = List.of(side.get(0), side.get(1), side.get(3), side.get(4));
        for (var cell : square.subList(0, 3)) helper.setBlock(cell, communicationInterface());
        var near = side.get(0).west();
        var far = side.get(4).west(3);
        for (var cell : BlockPos.betweenClosed(near.west(2), side.get(4).west())) {
            helper.setBlock(cell.immutable(),
                    space.controlnet.ae2federation.router.RouterRegistration.FEDERATION_CABLE.get());
        }
        var level = helper.getLevel();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(formed(helper.getBlockState(controller)),
                        "it forms with three interfaces"))
                .thenExecute(() -> {
                    var bundles = space.controlnet.ae2federation.bundle.BundledCables.get(level);
                    var segment = new space.controlnet.ae2federation.bundle.BundleSegment(
                            helper.absolutePos(near.west(2)), helper.absolutePos(side.get(4).west()),
                            Direction.Axis.X);
                    helper.assertValueEqual(bundles.formable(level, segment), java.util.Optional.empty(),
                            "the cables bundle");
                    bundles.form(level, segment);
                })
                .thenExecuteAfter(20, () -> helper.assertFalse(sharesDomain(helper, controller, far),
                        "against three interfaces the bundle stays apart"))
                .thenExecute(() -> helper.setBlock(square.get(3), communicationInterface()))
                .thenWaitUntil(() -> {
                    helper.assertTrue(formed(helper.getBlockState(controller)), "it forms with the fourth");
                    for (var cell : square) {
                        helper.assertTrue(sharesDomain(helper, cell, far), "the bundle reaches interface " + cell);
                    }
                    helper.assertTrue(sharesDomain(helper, controller, far), "and through them the controller");
                })
                .thenSucceed();
    }

    /**
     * An L9 Federation computation system takes no network switch at its side: a switch pools clusters across systems
     * and would lend this one's capacity to a Neo ECO system on ME. The same structure forms with casing there.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void federationComputationSystemRefusesANetworkSwitch(GameTestHelper helper) {
        var controller = placeFederationComputationSystem(helper, "computation_casing", "eco_computation_system_l9");
        helper.setBlock(INTERFACE, communicationInterface());
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
        var controller = placeLinkedFederationComputationSystem(helper, "eco_computation_system_l4");
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
                    helper.assertTrue(device[0] != null && sharesDomain(helper, controller, CABLE),
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
                .thenWaitUntil(() -> helper.assertFalse(sharesDomain(helper, controller, CABLE),
                        "a broken part takes it out of the cable's domain"))
                .thenExecute(() -> helper.assertTrue(ComputingDevices.get(level).installation(installation[0])
                        .isPresent(), "its installation stays"))
                .thenExecute(() -> helper.setBlock(casing, casingState[0]))
                .thenWaitUntil(() -> helper.assertTrue(sharesDomain(helper, controller, CABLE),
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
        var controller = placeLinkedFederationComputationSystem(helper, "eco_computation_system_l4");
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
                    helper.assertTrue(device[0] != null && sharesDomain(helper, controller, CABLE),
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

    /**
     * Installations reserve the device's compute and bandwidth (user decisions 2026-10-20). Two L4 parallel cores give
     * 128 CU; drives without cells give no bandwidth, so anything that moves is refused until a cell goes in. Parallel
     * mode reserves four times the compute. A move's bytes follow its resource, named by an argument too.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 400)
    public static void federationComputationSystemReservesComputeAndBandwidth(GameTestHelper helper) {
        var controller = placeFederationComputationSystem(helper, "computation_casing", "eco_computation_system_l4");
        helper.setBlock(INTERFACE, communicationInterface());
        var file = "neoeco_budget";
        var statements = String.join("\n", java.util.Collections.nCopies(40, "    x = 1"));
        writeProgram(helper, file, """
                def ping() -> str:
                    return "ping"

                def busy() -> int:
                %s
                    return x

                def give(port: Interface) -> None:
                    mc.move(ae2f.select(port, 1), ae2f.select(port, 2), "minecraft:iron_ingot", 64)

                def drain(port: Interface) -> None:
                    chest = ae2f.select(port, 1)
                    for stack in mc.storage(chest):
                        ae2.import_from(chest, stack.resource, stack.amount)

                def stock(base: Network, farm: Network, res: Resource, n: int) -> None:
                    ae2.move(base, farm, res, n)
                """.formatted(statements));
        var scheduler = ProgramScheduler.get(helper.getLevel().getServer());
        var installed = new java.util.ArrayList<String>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(controller) instanceof ComputingHost host
                        && host.id() != null && host.capacity().equals(new ComputingHost.Capacity(128, 0, 4)),
                        "a formed L4 system with two parallel cores, one threading core and empty drives has 128 CU,"
                                + " no bandwidth and 4 installations"))
                .thenExecute(() -> {
                    var device = ((ComputingHost) helper.getBlockEntity(controller)).id();
                    installed.add(scheduler.install(device, file, "ping", List.of()).id());
                    var busy = scheduler.install(device, file, "busy", List.of()).id();
                    installed.add(busy);
                    helper.assertValueEqual(scheduler.cost(busy).compute(), 42L,
                            "its entry, forty assignments and its return");
                    refused(helper, () -> scheduler.setOverlap(busy, Installation.Overlap.PARALLEL), "CU");
                    refused(helper, () -> scheduler.install(device, file, "give", List.of("port")), "B/s");
                    refused(helper, () -> scheduler.install(device, file, "drain", List.of("port")),
                            "no bandwidth left");
                    insertCell(helper, controller);
                    helper.assertValueEqual(((ComputingHost) helper.getBlockEntity(controller)).capacity()
                            .bandwidth(), 1L << 26, "an L4 cell gives its bytes");
                    installed.add(scheduler.install(device, file, "give", List.of("port")).id());
                    installed.add(scheduler.install(device, file, "drain", List.of("port")).id());
                    refused(helper, () -> scheduler.install(device, file, "ping", List.of()), "4 installations");
                    scheduler.uninstall(installed.remove(0));
                    scheduler.uninstall(installed.remove(0));
                    // Fluids count in mB, 8,000 to a byte: 64,000 mB of water named by an argument is 8 bytes a tick.
                    var water = scheduler.install(device, file, "stock", List.of("base", "farm", "minecraft:water",
                            "64000")).id();
                    installed.add(water);
                    helper.assertValueEqual(scheduler.cost(water).bandwidth(), 160L, "64 buckets of water a tick");
                    var iron = scheduler.install(device, file, "stock", List.of("base", "farm",
                            "minecraft:iron_ingot", "64000")).id();
                    installed.add(iron);
                    helper.assertValueEqual(scheduler.cost(iron).bandwidth(), 160_000L, "64,000 iron a tick");
                    installed.forEach(scheduler::uninstall);
                    deleteProgram(helper, file);
                })
                .thenSucceed();
    }

    /**
     * While its fixed reservations do not fit, a device starts nothing and its runs wait, and it resumes once they fit
     * again (user decision 2026-10-20). Broken casing unforms it, which leaves it no capacity.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void federationComputationSystemPausesWhileItHoldsTooLittle(GameTestHelper helper) {
        var controller = placeFederationComputationSystem(helper, "computation_casing", "eco_computation_system_l4");
        helper.setBlock(INTERFACE, communicationInterface());
        var file = "neoeco_pause";
        writeProgram(helper, file, """
                def ping() -> str:
                    return "ping"
                """);
        var scheduler = ProgramScheduler.get(helper.getLevel().getServer());
        var installation = new String[1];
        var casing = controller.above();
        var casingState = new BlockState[1];
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(controller) instanceof ComputingHost host
                        && host.id() != null && host.capacity().compute() > 0, "waiting for it to form"))
                .thenExecute(() -> {
                    installation[0] = scheduler.install(((ComputingHost) helper.getBlockEntity(controller)).id(), file,
                            "ping", List.of()).id();
                    scheduler.setTrigger(installation[0], new Installation.Trigger.Schedule(10));
                })
                .thenWaitUntil(() -> helper.assertTrue(log(scheduler, installation[0]).contains("done: 'ping'"),
                        "it runs on its schedule"))
                .thenExecute(() -> {
                    casingState[0] = helper.getBlockState(casing);
                    helper.destroyBlock(casing);
                })
                .thenWaitUntil(() -> helper.assertTrue(log(scheduler, installation[0]).contains(
                        "paused: the device holds less than its installations reserve"), "an unformed device pauses"))
                .thenExecuteAfter(40, () -> {
                    var log = log(scheduler, installation[0]);
                    helper.assertValueEqual(log.get(log.size() - 1),
                            "paused: the device holds less than its installations reserve",
                            "a paused device starts nothing on its schedule");
                })
                .thenExecute(() -> helper.setBlock(casing, casingState[0]))
                .thenWaitUntil(() -> {
                    var log = log(scheduler, installation[0]);
                    var resumed = log.lastIndexOf("resumed");
                    helper.assertTrue(resumed >= 0 && log.subList(resumed, log.size()).contains("done: 'ping'"),
                            "it resumes and runs once it forms again");
                })
                .thenExecute(() -> {
                    scheduler.uninstall(installation[0]);
                    deleteProgram(helper, file);
                })
                .thenSucceed();
    }

    /**
     * A block event that happens while the device is paused starts its run once the device resumes. Taking out the
     * cell leaves no bandwidth for a fixed reservation, which pauses it; putting the cell back resumes it.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void aPausedDeviceKeepsItsEventsForLater(GameTestHelper helper) {
        var controller = placeLinkedFederationComputationSystem(helper, "eco_computation_system_l4");
        var port = CABLE.west();
        helper.setBlock(port, space.controlnet.ae2federation.automation.AutomationRegistration.INTERFACE.get()
                .defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        var target = port.north();
        var file = "neoeco_pause_event";
        writeProgram(helper, file, """
                def ping() -> str:
                    return "ping"

                def hold(port: Interface) -> None:
                    mc.move(ae2f.select(port, 1), ae2f.select(port, 2), "minecraft:iron_ingot", 8)
                """);
        var scheduler = ProgramScheduler.get(helper.getLevel().getServer());
        var installed = new java.util.ArrayList<String>();
        var watcher = new String[1];
        var paused = "paused: the device holds less than its installations reserve";
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(controller) instanceof ComputingHost host
                        && host.id() != null
                        && helper.getBlockEntity(port) instanceof AutomationInterfaceBlockEntity automationInterface
                        && automationInterface.id() != null && sharesDomain(helper, controller, port),
                        "waiting for it to form beside the interface"))
                .thenExecute(() -> insertCell(helper, controller))
                .thenWaitUntil(() -> helper.assertTrue(((ComputingHost) helper.getBlockEntity(controller)).capacity()
                        .bandwidth() > 0, "waiting for the cell's bandwidth"))
                .thenExecute(() -> {
                    var device = ((ComputingHost) helper.getBlockEntity(controller)).id();
                    var portId = ((AutomationInterfaceBlockEntity) helper.getBlockEntity(port)).id();
                    installed.add(scheduler.install(device, file, "hold", List.of(portId)).id());
                    watcher[0] = scheduler.install(device, file, "ping", List.of()).id();
                    installed.add(watcher[0]);
                    scheduler.setTrigger(watcher[0], new Installation.Trigger.Event("block_changed", List.of(portId)));
                    drive(helper, controller).extractItem(0, 1, false);
                })
                .thenWaitUntil(() -> helper.assertTrue(log(scheduler, watcher[0]).contains(paused),
                        "without its cell the device holds no bandwidth and pauses"))
                .thenExecute(() -> helper.setBlock(target, Blocks.STONE))
                .thenExecuteAfter(20, () -> helper.assertValueEqual(log(scheduler, watcher[0]), List.of(paused),
                        "a paused device starts nothing on the event"))
                .thenExecute(() -> insertCell(helper, controller))
                .thenWaitUntil(() -> helper.assertValueEqual(log(scheduler, watcher[0]),
                        List.of(paused, "resumed", "started", "done: 'ping'"),
                        "the event that came while it was paused starts its run once it resumes"))
                .thenExecute(() -> {
                    installed.forEach(scheduler::uninstall);
                    deleteProgram(helper, file);
                })
                .thenSucceed();
    }

    /**
     * An elastic installation shares the bandwidth the fixed ones leave, and a move takes at most its share's bytes per
     * tick: asked for 64 iron, it moves 16 (user decision 2026-10-20). A fixed installation is sized to leave 40 to 59
     * B/s, two bytes a tick.
     */
    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 600)
    public static void anElasticMoveTakesItsShareOfTheFederationComputationSystem(GameTestHelper helper) {
        var controller = placeLinkedFederationComputationSystem(helper, "eco_computation_system_l4");
        var port = CABLE.west();
        helper.setBlock(port, space.controlnet.ae2federation.automation.AutomationRegistration.INTERFACE.get()
                .defaultBlockState().setValue(BlockStateProperties.FACING, Direction.NORTH));
        var source = port.north();
        var target = port.north(2);
        helper.setBlock(source, Blocks.CHEST);
        helper.setBlock(target, Blocks.CHEST);
        ((ChestBlockEntity) helper.getBlockEntity(source)).setItem(0, new ItemStack(Items.IRON_INGOT, 64));
        var file = "neoeco_elastic";
        var scheduler = ProgramScheduler.get(helper.getLevel().getServer());
        var installed = new java.util.ArrayList<String>();
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(controller) instanceof ComputingHost host
                        && host.capacity().compute() > 0, "waiting for it to form"))
                .thenExecute(() -> insertCell(helper, controller))
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(controller) instanceof ComputingHost host
                        && host.id() != null && host.capacity().bandwidth() > 0
                        && helper.getBlockEntity(port) instanceof AutomationInterfaceBlockEntity automationInterface
                        && automationInterface.id() != null && sharesDomain(helper, controller, port),
                        "waiting for it to form with a cell, beside the interface"))
                .thenExecute(() -> {
                    var host = (ComputingHost) helper.getBlockEntity(controller);
                    var bytes = (host.capacity().bandwidth() - 40) / 20;
                    writeProgram(helper, file, """
                            def hog(port: Interface) -> None:
                                mc.move(ae2f.select(port, 1), ae2f.select(port, 2), "minecraft:iron_ingot", %d)

                            def shift(port: Interface) -> None:
                                source = ae2f.select(port, 1)
                                n = mc.amount(source, "minecraft:iron_ingot")
                                mc.move(source, ae2f.select(port, 2), "minecraft:iron_ingot", n)
                            """.formatted(bytes * 8));
                    var portId = ((AutomationInterfaceBlockEntity) helper.getBlockEntity(port)).id();
                    installed.add(scheduler.install(host.id(), file, "hog", List.of(portId)).id());
                    var shift = scheduler.install(host.id(), file, "shift", List.of(portId)).id();
                    installed.add(shift);
                    helper.assertTrue(scheduler.cost(shift).elastic(), "a move of what a chest holds is elastic");
                    scheduler.trigger(shift);
                })
                .thenWaitUntil(() -> helper.assertTrue(log(scheduler, installed.get(1)).contains("done: None"),
                        "the move ran"))
                .thenExecute(() -> {
                    helper.assertValueEqual(((ChestBlockEntity) helper.getBlockEntity(target)).getItem(0).getCount(),
                            16, "it moved two bytes of iron");
                    helper.assertValueEqual(((ChestBlockEntity) helper.getBlockEntity(source)).getItem(0).getCount(),
                            48, "the rest stayed");
                    installed.forEach(scheduler::uninstall);
                    deleteProgram(helper, file);
                })
                .thenSucceed();
    }

    /** An L4 computation cell into the upper drive of {@link #placeFederationComputationSystem}'s structure. */
    private static void insertCell(GameTestHelper helper, BlockPos controller) {
        var left = drive(helper, controller).insertItem(0, new ItemStack(BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("neoecoae", "eco_computation_cell_l4"))), false);
        helper.assertTrue(left.isEmpty(), "the drive took the cell");
    }

    private static net.neoforged.neoforge.items.IItemHandler drive(GameTestHelper helper, BlockPos controller) {
        var drive = helper.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(controller.east(2).above()), null);
        helper.assertTrue(drive != null, "the drive takes cells");
        return drive;
    }

    private static void refused(GameTestHelper helper, Runnable action, String reason) {
        try {
            action.run();
        } catch (IllegalArgumentException e) {
            helper.assertTrue(e.getMessage().contains(reason), "refused for " + reason + ": " + e.getMessage());
            return;
        }
        helper.fail("expected a refusal for " + reason);
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
        var controller = placeLinkedFederationComputationSystem(helper, "eco_computation_system_l4");
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
                    helper.assertTrue(device[0] != null && sharesDomain(helper, controller, CABLE),
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

    /** The default communication interface, in the cell where Neo ECO's ME interface would go, and its cable. */
    private static final BlockPos INTERFACE = NeoEcoCompatGameTests.COMPUTATION;
    private static final BlockPos CABLE = INTERFACE.west();

    /**
     * {@link #placeFederationComputationSystem} with casing where Neo ECO's ME interface would go, then a
     * communication interface there and a Federation Cable against it, at {@link #CABLE}.
     */
    private static BlockPos placeLinkedFederationComputationSystem(GameTestHelper helper, String controllerId) {
        var controller = placeFederationComputationSystem(helper, "computation_casing", controllerId);
        helper.setBlock(INTERFACE, communicationInterface());
        helper.setBlock(CABLE, space.controlnet.ae2federation.router.RouterRegistration.FEDERATION_CABLE.get());
        return controller;
    }

    /** The Federation communication interface, by its ID: the compatibility tests see no Neo ECO class. */
    private static net.minecraft.world.level.block.Block communicationInterface() {
        return BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("ae2federation",
                "eco_computation_interface"));
    }

    /** The side face of {@link #placeFederationComputationSystem}'s structure: front column, then back, bottom up. */
    private static List<BlockPos> sideFace(BlockPos controller) {
        var front = controller.west();
        var back = front.south();
        return List.of(front.below(), front, front.above(), back.below(), back, back.above());
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
