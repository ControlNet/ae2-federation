package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.block.CropGrowEvent;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.automation.AutomationInterfaceBlockEntity;
import space.controlnet.ae2federation.automation.AutomationInterfaceBook;
import space.controlnet.ae2federation.automation.AutomationInterfaces;
import space.controlnet.ae2federation.automation.AutomationRegistration;
import space.controlnet.ae2federation.automation.ComputingDeviceBlockEntity;
import space.controlnet.ae2federation.automation.ChunkAssembler;
import space.controlnet.ae2federation.automation.ComputingDevices;
import space.controlnet.ae2federation.automation.Installation;
import space.controlnet.ae2federation.automation.InstallationLog;
import space.controlnet.ae2federation.automation.ProgramScheduler;
import space.controlnet.ae2federation.automation.ProgramStore;
import space.controlnet.ae2federation.automation.ProgramSync;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/**
 * Installed functions run on a Creative Computing Device: a device and an Automation Interface joined by ME Federation
 * Cable, the interface facing a block. Each test writes its own program file, installs a function through the
 * scheduler, as the commands do, and watches the world and the installation's log.
 */
@PrefixGameTestTemplate(false)
public final class ProgramSchedulerGameTests {
    private static final BlockPos CENTER = new BlockPos(6, 6, 6);
    private static final BlockPos DEVICE = CENTER.west();
    private static final BlockPos INTERFACE = CENTER.east();
    private static final BlockPos TARGET = CENTER.east(2);

    /** A slow function, to see what triggers do while it runs. */
    private static final String SLOW = """
            def slow() -> int:
                mc.wait(100)
                return 1
            """;

    private ProgramSchedulerGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void installedFunctionLightsALampWhenRun(GameTestHelper helper) {
        var scene = Scene.build(helper, "sched_lamp", """
                def light(port: Interface, level: int = 15) -> str:
                    mc.set_redstone(port, level)
                    mc.wait(2)
                    return mc.block_id(ae2f.select(port)) + " " + str(mc.state(ae2f.select(port), "lit"))
                """);
        helper.setBlock(TARGET, Blocks.REDSTONE_LAMP);
        var id = new String[1];
        scene.whenReady(() -> {
            var installation = scene.scheduler().install(scene.device(), "sched_lamp", "light",
                    List.of(scene.interfaceId()));
            scene.installed(installation);
            id[0] = installation.id();
            helper.assertValueEqual(installation.trigger(), new Installation.Trigger.Manual(), "Manual by default");
            scene.scheduler().trigger(installation.id());
        }).then(2, () -> {
            var log = InstallationLog.parse(ProgramSync.get(helper.getLevel().getServer())
                    .handle(1, "log", List.of(id[0])).message());
            helper.assertValueEqual(log.running(), 1, "The tab's log request sees the run going");
        }).then(8, () -> {
            helper.assertBlockProperty(TARGET, BlockStateProperties.LIT, true);
            helper.assertValueEqual(scene.log(0), List.of("started", "done: 'minecraft:redstone_lamp true'"),
                    "Ran once");
            var sync = ProgramSync.get(helper.getLevel().getServer());
            var log = InstallationLog.parse(sync.handle(2, "log", List.of(id[0])).message());
            helper.assertValueEqual(log.running(), 0, "Done");
            helper.assertValueEqual(log.lines().stream().map(InstallationLog.Line::text).toList(),
                    List.of("done: 'minecraft:redstone_lamp true'", "started"), "Newest first");
            helper.assertFalse(sync.handle(3, "log", List.of("install-nothing")).ok(), "An unknown ID is refused");
        }).finish(() -> {
            AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(INTERFACE);
            automationInterface.setRedstone(0);
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void aScheduleKeepsToTheRateLimit(GameTestHelper helper) {
        var scene = Scene.build(helper, "sched_rate", """
                def tick() -> int:
                    return mc.time()
                """);
        var started = new long[1];
        scene.whenReady(() -> {
            var installation = scene.scheduler().install(scene.device(), "sched_rate", "tick", List.of());
            scene.installed(installation);
            scene.scheduler().setTrigger(installation.id(), new Installation.Trigger.Schedule(1));
            scene.scheduler().setInterval(installation.id(), 20);
            started[0] = helper.getLevel().getGameTime();
        }).then(61, () -> {
            var starts = scene.log(0).stream().filter(line -> line.equals("started")).count();
            helper.assertValueEqual(starts, 4L, "Every tick is asked for, but one start per 20 ticks: 0, 20, 40, 60");
        }).finish(() -> {
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void redstoneStartsOncePerRisingEdge(GameTestHelper helper) {
        var scene = Scene.build(helper, "sched_redstone", """
                def ping() -> str:
                    return "ping"
                """);
        scene.whenReady(() -> {
            var installation = scene.scheduler().install(scene.device(), "sched_redstone", "ping", List.of());
            scene.installed(installation);
            scene.scheduler().setTrigger(installation.id(), new Installation.Trigger.Redstone());
            scene.scheduler().setInterval(installation.id(), 5);
            helper.setBlock(DEVICE.above(), Blocks.REDSTONE_BLOCK);
        }).then(10, () -> {
            helper.setBlock(DEVICE.above(), Blocks.AIR);
            helper.setBlock(DEVICE.above(), Blocks.REDSTONE_BLOCK);
        }).then(10, () -> {
            helper.setBlock(DEVICE.above(), Blocks.REDSTONE_BLOCK);
            var starts = scene.log(0).stream().filter(line -> line.equals("started")).count();
            helper.assertValueEqual(starts, 2L, "One start per signal turning on, none while it stays on");
        }).finish(() -> helper.setBlock(DEVICE.above(), Blocks.AIR));
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void overlapModesDecideWhatASecondTriggerDoes(GameTestHelper helper) {
        var scene = Scene.build(helper, "sched_overlap", SLOW);
        scene.whenReady(() -> {
            for (var overlap : Installation.Overlap.values()) {
                var installation = scene.scheduler().install(scene.device(), "sched_overlap", "slow", List.of());
                scene.installed(installation);
                scene.scheduler().setOverlap(installation.id(), overlap);
                scene.scheduler().setInterval(installation.id(), 5);
                scene.scheduler().trigger(installation.id());
            }
        }).then(10, () -> scene.installations().forEach(installation -> scene.scheduler().trigger(installation.id())))
                .then(10, () -> {
            helper.assertValueEqual(scene.running(0), 1, "ignore: the second trigger was dropped");
            helper.assertValueEqual(scene.log(0), List.of("started"), "ignore");
            helper.assertValueEqual(scene.running(1), 1, "restart: still one run");
            helper.assertValueEqual(scene.log(1), List.of("started", "stopped 1 run(s) for a restart", "started"),
                    "restart");
            helper.assertValueEqual(scene.running(2), 2, "parallel: a second run beside the first");
        }).finish(() -> {
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void printWritesToTheLog(GameTestHelper helper) {
        var scene = Scene.build(helper, "sched_print", """
                def report() -> int:
                    for i in range(2):
                        print("pass", i, sep=" #")
                    return 2
                """);
        scene.whenReady(() -> {
            var installation = scene.scheduler().install(scene.device(), "sched_print", "report", List.of());
            scene.installed(installation);
            scene.scheduler().trigger(installation.id());
        }).then(10, () -> helper.assertValueEqual(scene.log(0), List.of("started", "pass #0", "pass #1", "done: 2"),
                "Each print is a line of the installation's log, between its start and its end"))
                .finish(() -> {
                });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void runsUseThePushedVersion(GameTestHelper helper) {
        var scene = Scene.build(helper, "sched_push", """
                def version() -> int:
                    return 1
                """);
        scene.whenReady(() -> {
            var installation = scene.scheduler().install(scene.device(), "sched_push", "version", List.of());
            scene.installed(installation);
            scene.scheduler().setInterval(installation.id(), 5);
            scene.write("""
                    def version() -> int:
                        return 2
                    """);
            scene.scheduler().trigger(installation.id());
        }).then(10, () -> {
            scene.scheduler().push("sched_push");
            scene.scheduler().trigger(scene.installations().get(0).id());
        }).then(10, () -> helper.assertValueEqual(scene.log(0), List.of("started", "done: 1", "started", "done: 2"),
                "The saved edit runs only after the push")).finish(() -> {
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void aConditionStartsWhenItTurnsTrue(GameTestHelper helper) {
        // is_stone and what have no return hint: the condition takes the inferred type.
        var scene = Scene.build(helper, "sched_condition", """
                def is_stone(port: Interface):
                    return mc.block_id(ae2f.select(port)) == "minecraft:stone"

                def what(port: Interface):
                    return mc.block_id(ae2f.select(port))

                def signal(port: Interface) -> bool:
                    return mc.set_redstone(port, 15)

                def act(port: Interface) -> bool:
                    return signal(port)
                """);
        scene.whenReady(() -> {
            var installation = scene.scheduler().install(scene.device(), "sched_condition", "act",
                    List.of(scene.interfaceId()));
            scene.installed(installation);
            var refused = assertRefused(() -> scene.scheduler().setTrigger(installation.id(),
                    new Installation.Trigger.Condition("act", List.of(scene.interfaceId()))));
            helper.assertValueEqual(refused, "act acts on the world; a condition may only read",
                    "A condition that calls an effect, even through another function, is refused");
            helper.assertValueEqual(assertRefused(() -> scene.scheduler().setTrigger(installation.id(),
                    new Installation.Trigger.Condition("what", List.of(scene.interfaceId())))),
                    "what does not give a bool", "An inferred str is not a condition");
            scene.scheduler().setTrigger(installation.id(),
                    new Installation.Trigger.Condition("is_stone", List.of(scene.interfaceId())));
            scene.scheduler().setInterval(installation.id(), 5);
        }).then(20, () -> {
            helper.assertValueEqual(scene.log(0), List.of(), "Air in front: nothing starts");
            helper.setBlock(TARGET, Blocks.STONE);
        }).then(20, () -> {
            AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(INTERFACE);
            helper.assertValueEqual(automationInterface.redstone(), 15, "The function ran");
            helper.assertValueEqual(scene.log(0), List.of("started", "done: True"),
                    "The stone turned the condition true once; it staying true starts nothing more");
        }).finish(() -> {
            AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(INTERFACE);
            automationInterface.setRedstone(0);
        });
    }

    /** set_redstone reaches real redstone in front: a lamp lights, dust carries the level, 0 turns it off. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void setRedstonePowersTheBlockInFront(GameTestHelper helper) {
        var scene = Scene.build(helper, "sched_signal", """
                def signal(port: Interface, level: int) -> bool:
                    return mc.set_redstone(port, level)
                """);
        helper.setBlock(TARGET, Blocks.REDSTONE_LAMP);
        scene.whenReady(() -> {
            var installation = scene.scheduler().install(scene.device(), "sched_signal", "signal",
                    List.of(scene.interfaceId(), "15"));
            scene.installed(installation);
            scene.scheduler().trigger(installation.id());
        }).then(10, () -> {
            helper.assertValueEqual(scene.log(0), List.of("started", "done: True"), "The call ran");
            helper.assertBlockProperty(TARGET, net.minecraft.world.level.block.RedstoneLampBlock.LIT, true);
            helper.setBlock(TARGET, Blocks.REDSTONE_WIRE);
        }).then(5, () -> {
            helper.assertBlockProperty(TARGET, net.minecraft.world.level.block.RedStoneWireBlock.POWER, 15);
            scene.scheduler().setArgs(scene.installations().get(0).id(), List.of(scene.interfaceId(), "0"));
            scene.scheduler().trigger(scene.installations().get(0).id());
        }).then(10, () -> helper.assertBlockProperty(TARGET,
                net.minecraft.world.level.block.RedStoneWireBlock.POWER, 0)).finish(() -> {
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void breakingTheDeviceStopsItsRunsAndDropsItsInstallations(GameTestHelper helper) {
        var scene = Scene.build(helper, "sched_broken", SLOW);
        scene.whenReady(() -> {
            var installation = scene.scheduler().install(scene.device(), "sched_broken", "slow", List.of());
            scene.installed(installation);
            scene.scheduler().trigger(installation.id());
        }).then(5, () -> {
            helper.assertValueEqual(scene.running(0), 1, "Running");
            helper.setBlock(DEVICE, Blocks.AIR);
        }).then(2, () -> {
            helper.assertValueEqual(scene.running(0), 0, "Its run stopped");
            helper.assertTrue(ComputingDevices.get(helper.getLevel()).installation(scene.installations().get(0).id())
                    .isEmpty(), "Its installation went with it");
            helper.assertTrue(ComputingDevices.get(helper.getLevel()).entry(scene.device()).isEmpty(),
                    "Its ID is free again");
        }).finish(() -> {
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void blockEventsStartAtTheBlockAnInterfacePointsAt(GameTestHelper helper) {
        var scene = Scene.build(helper, "sched_blocks", """
                def ping() -> str:
                    return "ping"
                """);
        var events = List.of("block_changed", "crop_grew", "right_clicked");
        scene.whenReady(() -> {
            for (var event : events) {
                var installation = scene.scheduler().install(scene.device(), "sched_blocks", "ping", List.of());
                scene.installed(installation);
                scene.scheduler().setTrigger(installation.id(),
                        new Installation.Trigger.Event(event, List.of(scene.interfaceId())));
                scene.scheduler().setInterval(installation.id(), 5);
            }
            helper.assertValueEqual(assertRefused(() -> scene.scheduler().setTrigger(scene.installations().get(0).id(),
                    new Installation.Trigger.Event("block_changed", List.of(scene.interfaceId(), "5")))),
                    "the reach must be from 1 to 4", "Reach");
        }).then(10, () -> {
            helper.setBlock(TARGET.above(), Blocks.STONE);
            helper.setBlock(TARGET, Blocks.STONE);
        }).then(10, () -> {
            var level = helper.getLevel();
            var at = helper.absolutePos(TARGET);
            NeoForge.EVENT_BUS.post(new CropGrowEvent.Post(level, at, Blocks.STONE.defaultBlockState(),
                    Blocks.STONE.defaultBlockState()));
        }).then(10, () -> {
            var at = helper.absolutePos(TARGET);
            NeoForge.EVENT_BUS.post(new PlayerInteractEvent.RightClickBlock(helper.makeMockPlayer(GameType.SURVIVAL),
                    InteractionHand.MAIN_HAND, at, new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false)));
        }).then(10, () -> {
            for (int i = 0; i < events.size(); i++) {
                helper.assertValueEqual(scene.log(i), List.of("started", "done: 'ping'"),
                        events.get(i) + " started once, only for its own event at its own block");
            }
        }).finish(() -> {
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void aStockChangeStartsARun(GameTestHelper helper) {
        var scene = Scene.build(helper, "sched_stock", """
                def ping() -> str:
                    return "ping"
                """);
        var fixtures = new RouterFixtures(helper);
        var switchAt = CENTER.north();
        fixtures.placeSwitch(switchAt);
        fixtures.placeNativeDevice(switchAt, Direction.UP);
        helper.setBlock(switchAt.above(2), AEBlocks.CREATIVE_ENERGY_CELL.block());
        MEChestBlockEntity chest = helper.getBlockEntity(switchAt.above());
        chest.setCell(AEItems.ITEM_CELL_1K.stack());
        scene.whenReady(() -> {
            var level = helper.getLevel();
            var domain = ComputingDevices.get(level).domainOf(level, scene.device()).orElseThrow();
            helper.assertValueEqual(FederationDomainRegistryAccess.get(level).federationDomain(domain)
                    .map(snapshot -> snapshot.memberships().size()).orElse(0), 1, "Waiting for the network");
        }, () -> {
            var installation = scene.scheduler().install(scene.device(), "sched_stock", "ping", List.of());
            scene.installed(installation);
            scene.scheduler().setTrigger(installation.id(),
                    new Installation.Trigger.Event("stock_changed", List.of("minecraft:iron_ingot")));
            scene.scheduler().setInterval(installation.id(), 5);
        }).then(20, () -> {
            helper.assertValueEqual(scene.log(0), List.of(), "The first reading only sets the baseline");
            helper.assertValueEqual(chest.getMainNode().getGrid().getStorageService().getInventory().insert(
                    AEItemKey.of(Items.IRON_INGOT), 5, Actionable.MODULATE, IActionSource.empty()), 5L, "Stocked");
        }).then(20, () -> {
            helper.assertValueEqual(scene.log(0), List.of("started", "done: 'ping'"), "The stock changed once");
            // The tab's stock request, as the completion list's footer asks it.
            var level = helper.getLevel();
            var domain = ComputingDevices.get(level).domainOf(level, scene.device()).orElseThrow().value();
            var sync = ProgramSync.get(level.getServer());
            helper.assertValueEqual(sync.handle(1, "stock", List.of(domain, "minecraft:iron_ingot")).message(), "5",
                    "The domain's stock");
            helper.assertFalse(sync.handle(2, "stock", List.of(domain, "minecraft:nothing")).ok(),
                    "An unknown resource is refused, not thrown");
        }).finish(fixtures::close);
    }

    /** The Automation tab's traffic, without the network: uploads in chunks, push, install, run, the index. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void theTabsTrafficUploadsInstallsAndRuns(GameTestHelper helper) {
        var first = """
                def light(port: Interface) -> bool:
                    return False
                """;
        var scene = Scene.build(helper, "sched_sync", first);
        helper.setBlock(TARGET, Blocks.REDSTONE_LAMP);
        var edited = "# " + "a long comment to need two chunks ".repeat(300) + """

                def light(port: Interface) -> bool:
                    return mc.set_redstone(port, 15)
                """;
        var sender = java.util.UUID.randomUUID();
        scene.whenReady(() -> {
            var sync = ProgramSync.get(helper.getLevel().getServer());
            var chunks = ChunkAssembler.split(edited);
            helper.assertValueEqual(chunks.size(), 2, "Two chunks");
            helper.assertValueEqual(sync.receive(sender, 1, "sched_sync", ProgramSync.version(first), 1, 2,
                    chunks.get(1)), java.util.Optional.empty(), "Waiting for the first chunk");
            helper.assertValueEqual(sync.receive(sender, 1, "sched_sync", ProgramSync.version(first), 0, 2,
                    chunks.get(0)), java.util.Optional.of(new ProgramSync.Answer(1, true, "saved sched_sync")),
                    "Saved once whole");
            helper.assertValueEqual(sync.receive(sender, 2, "sched_sync", ProgramSync.version(first), 0, 1, edited
                    .substring(0, 10)), java.util.Optional.of(new ProgramSync.Answer(2, true,
                    "saved sched_sync; it replaced a version saved meanwhile")), "The last write wins, and says so");
            sync.receive(sender, 3, "sched_sync", ProgramSync.version(first), 0, 2, chunks.get(0));
            helper.assertValueEqual(sync.receive(sender, 3, "sched_sync", ProgramSync.version(first), 1, 2,
                    chunks.get(1)), java.util.Optional.of(new ProgramSync.Answer(3, true,
                    "saved sched_sync; it replaced a version saved meanwhile")), "Back to the edit");
            helper.assertFalse(sync.receive(sender, 9, "sched_sync", "", 0, 1, edited).orElseThrow().ok(),
                    "A chunk over the limit is refused");
            helper.assertFalse(sync.receive(sender, 4, "bad name", "", 0, 1, edited).orElseThrow().ok(),
                    "A file name must be a Python name");
            helper.assertTrue(sync.handle(5, "push", List.of("sched_sync")).ok(), "Pushed");
            var file = sync.index().files().stream().filter(info -> info.name().equals("sched_sync")).findFirst()
                    .orElseThrow();
            helper.assertTrue(file.current() && file.pushed() && file.problems() == 0, "Pushed and clean: " + file);
            helper.assertValueEqual(file.version(), ProgramSync.version(edited), "Version");
            helper.assertValueEqual(file.functions().get(0), new ProgramSync.FunctionInfo("light",
                    List.of("port: Interface"), "bool", true), "Signature");
            var installed = sync.handle(6, "install", List.of(scene.device(), "sched_sync", "light",
                    scene.interfaceId()));
            helper.assertTrue(installed.ok(), installed.message());
            var id = installed.message().substring("installed ".length());
            scene.installed(ComputingDevices.get(helper.getLevel()).installation(id).orElseThrow());
            helper.assertValueEqual(sync.handle(7, "trigger", List.of(id, "every 400")),
                    new ProgramSync.Answer(7, true, "every 400"), "Trigger set from its text");
            helper.assertFalse(sync.handle(8, "delete", List.of("sched_sync")).ok(), "Installed files stay");
            var listed = sync.index().installations().stream().filter(info -> info.id().equals(id)).findFirst()
                    .orElseThrow();
            helper.assertValueEqual(listed.args(), java.util.List.of(scene.interfaceId()), "Arguments as typed");
            helper.assertTrue(sync.index().devices().stream().anyMatch(device -> device.id().equals(scene.device())
                    && !device.domain().isEmpty()), "The device and its domain are listed");
        }).then(10, () -> helper.assertBlockProperty(TARGET, BlockStateProperties.LIT, true)).finish(() -> {
            AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(INTERFACE);
            automationInterface.setRedstone(0);
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void theTabEditsArgumentsAndListsAliases(GameTestHelper helper) {
        var scene = Scene.build(helper, "sched_args", """
                def light(port: Interface, level: int) -> bool:
                    return mc.set_redstone(port, level)
                """);
        helper.setBlock(TARGET, Blocks.REDSTONE_LAMP);
        var id = new String[1];
        scene.whenReady(() -> {
            var sync = ProgramSync.get(helper.getLevel().getServer());
            var installed = sync.handle(1, "install", List.of(scene.device(), "sched_args", "light",
                    scene.interfaceId(), "0"));
            helper.assertTrue(installed.ok(), installed.message());
            id[0] = installed.message().substring("installed ".length());
            scene.installed(ComputingDevices.get(helper.getLevel()).installation(id[0]).orElseThrow());
            helper.assertTrue(sync.handle(2, "trigger", List.of(id[0], "every 400")).ok(), "Trigger set");
            helper.assertFalse(sync.handle(3, "args", List.of(id[0], scene.interfaceId())).ok(),
                    "Every parameter needs its argument");
            helper.assertFalse(sync.handle(4, "args", List.of(id[0], scene.interfaceId(), "bright")).ok(),
                    "An argument must read as its parameter's type");
            helper.assertValueEqual(sync.handle(5, "args", List.of(id[0], scene.interfaceId(), "15")),
                    new ProgramSync.Answer(5, true, "['" + scene.interfaceId() + "', 15]"), "Arguments changed");
            var installation = ComputingDevices.get(helper.getLevel()).installation(id[0]).orElseThrow();
            helper.assertValueEqual(installation.trigger(), new Installation.Trigger.Schedule(400),
                    "The same installation, its trigger kept");
            scene.scheduler().trigger(id[0]);

            var devices = ComputingDevices.get(helper.getLevel());
            helper.assertValueEqual(devices.setAlias(helper.getLevel(), scene.device(), "device-abcdef"),
                    AutomationInterfaceBook.AliasResult.INVALID, "An alias may not read as an ID");
            helper.assertValueEqual(devices.setAlias(helper.getLevel(), scene.device(), "  core lab "),
                    AutomationInterfaceBook.AliasResult.SET, "Alias set");
            AutomationInterfaces.get(helper.getLevel()).setAlias(helper.getLevel(), scene.interfaceId(), "lamp");
            var index = sync.index();
            var device = index.devices().stream().filter(info -> info.id().equals(scene.device())).findFirst()
                    .orElseThrow();
            helper.assertValueEqual(device.alias(), "core lab", "The device's alias is listed");
            var listed = index.interfaces().stream().filter(info -> info.id().equals(scene.interfaceId()))
                    .findFirst().orElseThrow();
            helper.assertValueEqual(listed.alias(), "lamp", "The interface's alias is listed");
            helper.assertValueEqual(listed.domain(), device.domain(), "In the device's domain");
            helper.assertValueEqual(listed.position(), helper.absolutePos(INTERFACE), "At its place");
        }).then(10, () -> helper.assertBlockProperty(TARGET, BlockStateProperties.LIT, true)).finish(() -> {
            AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(INTERFACE);
            automationInterface.setRedstone(0);
            ComputingDevices.get(helper.getLevel()).setAlias(helper.getLevel(), scene.device(), "");
            AutomationInterfaces.get(helper.getLevel()).setAlias(helper.getLevel(), scene.interfaceId(), "");
        });
    }

    private static String assertRefused(Runnable call) {
        try {
            call.run();
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        throw new AssertionError("expected a refusal");
    }

    /** The device, cable and interface, the test's program file, and what the test installed. */
    private static final class Scene {
        private final GameTestHelper helper;
        private final String file;
        private final List<Installation> installed = new java.util.ArrayList<>();
        private String device;

        private Scene(GameTestHelper helper, String file) {
            this.helper = helper;
            this.file = file;
        }

        static Scene build(GameTestHelper helper, String file, String text) {
            BlockPos.betweenClosed(CENTER.offset(-3, -1, -2), CENTER.offset(3, 3, 2))
                    .forEach(position -> helper.setBlock(position, Blocks.AIR));
            helper.setBlock(TARGET.below(), Blocks.STONE);
            helper.setBlock(DEVICE, AutomationRegistration.DEVICE.get());
            helper.setBlock(CENTER, RouterRegistration.FEDERATION_CABLE.get());
            helper.setBlock(INTERFACE, AutomationRegistration.INTERFACE.get().defaultBlockState()
                    .setValue(BlockStateProperties.FACING, Direction.EAST));
            var scene = new Scene(helper, file);
            scene.write(text);
            return scene;
        }

        ProgramScheduler scheduler() {
            return ProgramScheduler.get(helper.getLevel().getServer());
        }

        /** The device's ID, kept once known: a test may break the device. */
        String device() {
            if (device == null && helper.getLevel().getBlockEntity(helper.absolutePos(DEVICE))
                    instanceof ComputingDeviceBlockEntity entity) {
                device = entity.id();
            }
            return device;
        }

        String interfaceId() {
            AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(INTERFACE);
            return automationInterface.id();
        }

        void installed(Installation installation) {
            installed.add(installation);
        }

        List<Installation> installations() {
            return installed;
        }

        List<String> log(int index) {
            return scheduler().log(installed.get(index).id()).stream().map(ProgramScheduler.LogEntry::text).toList();
        }

        int running(int index) {
            return scheduler().running(installed.get(index).id());
        }

        void write(String text) {
            var store = ProgramStore.get(helper.getLevel().getServer());
            try {
                Files.writeString(store.folder().resolve(file + ".ae2f"), text);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            store.reload();
        }

        /** Waits until the device and the interface share a domain, then runs {@code start}; steps follow. */
        Steps whenReady(Runnable start) {
            return whenReady(() -> {
            }, start);
        }

        /** As {@link #whenReady(Runnable)}, also waiting for {@code ready} to pass. */
        Steps whenReady(Runnable ready, Runnable start) {
            var level = helper.getLevel();
            return new Steps(helper.startSequence().thenWaitUntil(() -> {
                helper.assertTrue(device() != null && interfaceId() != null, "Waiting for the IDs");
                var deviceDomain = ComputingDevices.get(level).domainOf(level, device());
                helper.assertTrue(deviceDomain.isPresent()
                        && deviceDomain.equals(AutomationInterfaces.get(level).domainOf(level, interfaceId())),
                        "Waiting for the device and the interface to share a domain");
                ready.run();
            }).thenExecute(start));
        }

        final class Steps {
            private GameTestSequence sequence;

            private Steps(GameTestSequence sequence) {
                this.sequence = sequence;
            }

            Steps then(int ticks, Runnable step) {
                sequence = sequence.thenExecuteAfter(ticks, step);
                return this;
            }

            /** Runs {@code cleanup}, then uninstalls what the test installed and deletes its file. */
            void finish(Runnable cleanup) {
                var level = helper.getLevel();
                sequence.thenExecute(() -> {
                    cleanup.run();
                    RouterEvidence.write(file.replace("_", ""), 1, Map.of("installations",
                            String.valueOf(installed.size())));
                    installed.forEach(installation -> scheduler().uninstall(installation.id()));
                    var store = ProgramStore.get(level.getServer());
                    try {
                        Files.deleteIfExists(store.folder().resolve(file + ".ae2f"));
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                    store.reload();
                }).thenSucceed();
            }
        }
    }
}
