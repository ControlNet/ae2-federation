package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.config.CpuSelectionMode;
import appeng.api.config.FuzzyMode;
import appeng.api.config.RedstoneMode;
import appeng.api.config.Settings;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageCells;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.parts.automation.ImportBusPart;
import appeng.parts.storagebus.StorageBusPart;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.automation.AutomationInterfaceBlockEntity;
import space.controlnet.ae2federation.automation.AutomationInterfaces;
import space.controlnet.ae2federation.automation.AutomationRegistration;
import space.controlnet.ae2federation.automation.AutomationRule;
import space.controlnet.ae2federation.automation.ProgramDryRun;
import space.controlnet.ae2federation.automation.ProgramHost;
import space.controlnet.ae2federation.automation.ProgramRun;
import space.controlnet.ae2federation.automation.ProgramStore;
import space.controlnet.ae2federation.automation.ProgramSync;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.script.DictValue;
import space.controlnet.ae2federation.script.RunError;
import space.controlnet.ae2federation.script.Runner;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.automation.TestEnd;
import space.controlnet.ae2federation.test.automation.WallClock;
import space.controlnet.ae2federation.test.crafting.NativeCraftingFixtures;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/**
 * Programs run against a real domain: a Switch with two ME Chests (two networks, one with an item cell) and a
 * powered network, a Federation Cable, and an Automation Interface facing a block. Each test writes a program file,
 * runs one function through {@link ProgramRun} tick by tick, and checks its result and the world.
 */
@PrefixGameTestTemplate(false)
public final class ProgramHostGameTests {
    private static final BlockPos CENTER = new BlockPos(6, 6, 6);
    private static final BlockPos INTERFACE = CENTER.east();
    private static final BlockPos TARGET = CENTER.east(2);

    private ProgramHostGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void programMovesItemsBetweenAChestAndTheDomain(GameTestHelper helper) {
        var fixtures = build(helper, Blocks.CHEST.defaultBlockState());
        Container chest = helper.getBlockEntity(TARGET);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        run(helper, fixtures, "host_items", """
                def run(port: Interface) -> int:
                    chest = ae2f.select(port)
                    before = mc.amount(chest, "minecraft:iron_ingot")
                    ae2.import_from(chest, "minecraft:iron_ingot", 6)
                    stored = ae2.amount("minecraft:iron_ingot")
                    listed = 0
                    for stack in ae2.storage():
                        if stack.resource == "minecraft:iron_ingot":
                            listed = stack.amount
                    ae2.export_to(chest, "minecraft:iron_ingot", 2)
                    return before * 1000000 + stored * 10000 + listed * 100 + mc.amount(chest, "minecraft:iron_ingot")
                """, 10_060_606L, "10 in the chest; 6 imported, read and listed; 2 exported back leaves 6",
                () -> helper.assertValueEqual(chest.getItem(0).getCount(), 6, "The chest holds 6"));
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void programLightsALampThroughTheInterface(GameTestHelper helper) {
        var fixtures = build(helper, Blocks.REDSTONE_LAMP.defaultBlockState());
        run(helper, fixtures, "host_lamp", """
                def run(port: Interface) -> str:
                    mc.set_redstone(port, 15)
                    mc.wait(3)
                    lamp = ae2f.select(port)
                    return mc.block_id(lamp) + " " + str(mc.state(lamp, "lit")) + " " + str(mc.redstone(lamp))
                """, "minecraft:redstone_lamp true 15", "The lamp in front is lit by the interface's front face",
                () -> {
                    AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(INTERFACE);
                    automationInterface.setRedstone(0);
                });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void dryRunReadsTheWorldAndOnlySimulatesActions(GameTestHelper helper) {
        var fixtures = build(helper, Blocks.CHEST.defaultBlockState());
        Container chest = helper.getBlockEntity(TARGET);
        chest.setItem(0, new ItemStack(Items.IRON_INGOT, 10));
        var file = "host_dry";
        var text = """
                def run(port: Interface) -> str:
                    chest = ae2f.select(port)
                    moved = ae2.import_from(chest, "minecraft:iron_ingot", 6)
                    lit = mc.set_redstone(port, 15)
                    mc.wait(100)
                    broke = mc.break_block(chest)
                    return " ".join([str(moved), str(lit), str(broke), str(mc.amount(chest, "minecraft:iron_ingot")),
                                     str(ae2.amount("minecraft:iron_ingot"))])
                """;
        var level = helper.getLevel();
        var server = level.getServer();
        var store = ProgramStore.get(server);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(INTERFACE);
                    helper.assertTrue(automationInterface.id() != null, "Waiting for the interface's ID");
                    var domain = AutomationInterfaces.get(level).domainOf(level, automationInterface.id());
                    helper.assertTrue(domain.isPresent(), "Waiting for the interface's domain");
                    var settled = FederationDomainRegistryAccess.get(level).federationDomain(domain.get())
                            .map(snapshot -> snapshot.memberships().size()).orElse(0);
                    helper.assertValueEqual(settled, 2, "Waiting for the networks to settle in the domain");
                })
                .thenExecute(() -> {
                    write(store, file, text);
                    store.reload();
                    AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(INTERFACE);
                    var domain = AutomationInterfaces.get(level).domainOf(level, automationInterface.id())
                            .orElseThrow();
                    var answer = ProgramSync.get(server).handle(1, "dry", List.of(domain.value(), file, "run",
                            ProgramSync.version(text), automationInterface.id()));
                    helper.assertTrue(answer.ok(), "The dry run answered: " + answer.message());
                    var dry = ProgramDryRun.decode(answer.message());
                    helper.assertValueEqual(dry.status(), ProgramDryRun.Status.DONE, "The dry run finished");
                    helper.assertTrue(dry.values().containsValue("'True True True 10 0'"),
                            "Every action would succeed, and nothing moved: " + dry.values());
                    helper.assertTrue(dry.values().containsValue("Block('minecraft:chest')"),
                            "The selected block shows as the block there: " + dry.values());
                    helper.assertValueEqual(chest.getItem(0).getCount(), 10, "The chest still holds 10");
                    helper.assertBlockPresent(Blocks.CHEST, TARGET);
                    helper.assertValueEqual(level.getBestNeighborSignal(helper.absolutePos(TARGET)), 0,
                            "The interface emits no redstone");
                    var stale = ProgramSync.get(server).handle(2, "dry", List.of(domain.value(), file, "run",
                            "0000000000000000", automationInterface.id()));
                    helper.assertFalse(stale.ok(), "A dry run of a version other than the saved one is refused");
                    RouterEvidence.write("hostdry", 1, Map.of("result", answer.message().split("\n")[1]));
                    delete(store, file);
                    store.reload();
                    fixtures.close();
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void programBreaksABlockIntoTheDomainAndPlacesItBack(GameTestHelper helper) {
        var fixtures = build(helper, Blocks.COBBLESTONE.defaultBlockState());
        run(helper, fixtures, "host_blocks", """
                def run(port: Interface) -> str:
                    block = ae2f.select(port)
                    broke = mc.break_block(block)
                    gone = mc.block_id(block)
                    stored = ae2.amount("minecraft:cobblestone")
                    placed = mc.place_block(block, "minecraft:cobblestone")
                    again = mc.place_block(block, "minecraft:cobblestone")
                    own = mc.break_block(port)
                    return " ".join([str(broke), gone, str(stored), str(placed), str(again), str(own),
                                     str(ae2.amount("minecraft:cobblestone"))])
                """, "True minecraft:air 1 True False False 0",
                "Broken into the domain, placed back from it, never twice, and the interface never breaks itself",
                () -> helper.assertBlockPresent(Blocks.COBBLESTONE, TARGET));
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void programSetsAStorageRuleBetweenItsNetworks(GameTestHelper helper) {
        var fixtures = build(helper, Blocks.AIR.defaultBlockState());
        run(helper, fixtures, "host_rules", """
                def run() -> str:
                    networks = ae2f.networks()
                    before = ae2f.rule(networks[0], networks[1], "storage")
                    ae2f.set_rule(networks[0], networks[1], "storage", "reexport")
                    after = ae2f.rule(networks[0], networks[1], "storage")
                    ae2f.set_filter(networks[0], networks[1], "storage", "allow", ["minecraft:iron_ingot"])
                    kept = ae2f.rule(networks[0], networks[1], "storage")
                    ae2f.set_rule(networks[0], networks[1], "storage", "off")
                    last = ae2f.rule(networks[0], networks[1], "storage")
                    return " ".join([str(len(networks)), str(before), str(after), str(kept), str(last)])
                """, "2 off reexport reexport off",
                "A program sets and reads a rule between the two networks of its domain; a filter keeps the mode",
                () -> {
                });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void programKeepsFilesInTheWorldsWorkspace(GameTestHelper helper) {
        var fixtures = build(helper, Blocks.STONE.defaultBlockState());
        var level = helper.getLevel();
        var server = level.getServer();
        var folder = ProgramHost.workspace(server).resolve("gametest");
        removeFolder(folder);
        run(helper, fixtures, "host_files", """
                def run() -> str:
                    wrote = fs.write("gametest/notes.txt", "iron 64;")
                    added = fs.append("gametest/notes.txt", "gold 32")
                    text = fs.read("gametest/notes.txt")
                    names = fs.list("gametest")
                    top = "gametest/" in fs.list()
                    deleted = fs.delete("gametest/notes.txt")
                    emptied = fs.delete("gametest")
                    return " ".join([str(wrote), str(added), str(text), str(names), str(top), str(deleted), str(emptied),
                                     str(fs.exists("gametest")), str(fs.read("gametest/notes.txt"))])
                """, "True True iron 64;gold 32 ['notes.txt'] True True True False None",
                "A program writes, adds to, reads, lists and deletes a file in the world's workspace", () -> {
                    helper.assertFalse(Files.exists(folder), "The program removed its folder");
                    // A dry run checks a change but leaves the disk alone.
                    AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(INTERFACE);
                    var domain = AutomationInterfaces.get(level).domainOf(level, automationInterface.id())
                            .orElseThrow();
                    var dry = ProgramHost.dryRun(server, domain);
                    helper.assertValueEqual(dry.call("fs", "write", List.of("gametest/dry.txt", "x")), true,
                            "A dry run's write answers as if done");
                    helper.assertFalse(Files.exists(folder), "A dry run's write leaves nothing on disk");
                });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void programAsksALocalWebServer(GameTestHelper helper) {
        var fixtures = build(helper, Blocks.STONE.defaultBlockState());
        var level = helper.getLevel();
        var server = level.getServer();
        // A web server on this machine stands in for the outside world.
        var seen = new java.util.concurrent.atomic.AtomicInteger();
        com.sun.net.httpserver.HttpServer web;
        try {
            web = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        web.createContext("/echo", exchange -> {
            seen.incrementAndGet();
            var body = ("got " + new String(exchange.getRequestBody().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8)).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("X-Seen", exchange.getRequestHeaders().getFirst("X-Token"));
            exchange.sendResponseHeaders(201, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        web.start();
        TestEnd.then(helper, () -> web.stop(0));
        var url = "http://127.0.0.1:" + web.getAddress().getPort() + "/echo";
        run(helper, fixtures, "host_http", """
                def run() -> str:
                    answer = net.http("POST", "%s", "iron", {"X-Token": "abc"})
                    return str(answer.status) + " " + answer.text + " " + answer.headers["x-seen"]
                """.formatted(url), "201 got iron abc",
                "A program posts to a web server and reads its answer, waiting for it over ticks", () -> {
                    // A dry run sends nothing and answers status 0.
                    AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(INTERFACE);
                    var domain = AutomationInterfaces.get(level).domainOf(level, automationInterface.id())
                            .orElseThrow();
                    var dry = (space.controlnet.ae2federation.script.RecordValue) ProgramHost.dryRun(server, domain)
                            .call("net", "http", List.of("GET", url, "", new space.controlnet.ae2federation.script
                                    .DictValue()));
                    helper.assertValueEqual(dry.field("status"), 0L, "A dry run's request answers status 0");
                    helper.assertValueEqual(seen.get(), 1, "Only the real run reached the server");
                });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void theServerTurnsOutsideCallsOff(GameTestHelper helper) {
        var fixtures = build(helper, Blocks.STONE.defaultBlockState());
        var level = helper.getLevel();
        var server = level.getServer();
        run(helper, fixtures, "host_switches", """
                def run() -> bool:
                    return fs.exists("gametest-switches")
                """, false, "With the defaults a program reaches the workspace", () -> {
                    AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(INTERFACE);
                    var domain = AutomationInterfaces.get(level).domainOf(level, automationInterface.id())
                            .orElseThrow();
                    var host = new ProgramHost(server, domain, "gametest:host_switches");
                    var dry = ProgramHost.dryRun(server, domain);
                    // The rule is shared by every test: off and back on within this one step.
                    AutomationRule.set(false, false, false, AutomationRule.DEFAULT_TIMEOUT_SECONDS,
                            AutomationRule.DEFAULT_WORKSPACE_MB);
                    try {
                        helper.assertValueEqual(refusal(() -> host.call("fs", "list", List.of(""))),
                                "PermissionError: file access is turned off on this server (allowFileAccess)",
                                "Files are off");
                        helper.assertValueEqual(refusal(() -> dry.call("fs", "write", List.of("a.txt", "x"))),
                                "PermissionError: file access is turned off on this server (allowFileAccess)",
                                "A dry run says so too");
                        helper.assertValueEqual(refusal(() -> host.call("net", "http",
                                        List.of("GET", "http://127.0.0.1:1/", "", new DictValue()))),
                                "PermissionError: HTTP requests are turned off on this server (allowHttp)",
                                "HTTP is off");
                        helper.assertValueEqual(refusal(() -> host.call("net", "receive",
                                        List.of("ws://127.0.0.1:1/"))),
                                "PermissionError: websockets are turned off on this server (allowWebsockets)",
                                "Websockets are off");
                        helper.assertValueEqual(host.call("mc", "time", List.of()) instanceof Long, true,
                                "Calls inside the game still work");
                    } finally {
                        AutomationRule.reset();
                    }
                });
    }

    private static String refusal(Runnable call) {
        try {
            call.run();
        } catch (RunError e) {
            return e.toString();
        }
        throw new AssertionError("expected the call to be refused");
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void programTalksOverAWebSocket(GameTestHelper helper) {
        var fixtures = build(helper, Blocks.STONE.defaultBlockState());
        var level = helper.getLevel();
        var server = level.getServer();
        // A websocket server on this machine stands in for the outside world.
        var echo = echoServer();
        var url = echo.url("feed");
        var owner = "gametest:host_websocket";
        TestEnd.then(helper, () -> {
            space.controlnet.ae2federation.automation.WebSockets.of(server).closeOwner(owner);
            try {
                echo.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        run(helper, fixtures, "host_websocket", """
                def run() -> str:
                    opened = net.connect("%s")
                    sent = net.send("%s", "iron 64")
                    return str(opened) + " " + str(sent)
                """.formatted(url, url), "True True",
                "A program connects and sends a message, waiting for each without counting ticks", () -> {
                    var sockets = space.controlnet.ae2federation.automation.WebSockets.of(server);
                    // The answer comes in real time; a batch of tests runs ticks back to back.
                    WallClock.await("the echo arrives", () -> sockets.received(owner, url) >= 1);
                    helper.assertValueEqual(echo.received(), List.of("iron 64"), "The server got the message");
                    AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(INTERFACE);
                    var domain = AutomationInterfaces.get(level).domainOf(level, automationInterface.id())
                            .orElseThrow();
                    var host = new ProgramHost(server, domain, owner);
                    helper.assertValueEqual(host.call("net", "connected", List.of(url)), true,
                            "The connection outlives the run that opened it");
                    helper.assertValueEqual(host.call("net", "receive", List.of(url)), "echo: iron 64",
                            "The next run of the installation reads the answer");
                    helper.assertTrue(host.call("net", "receive", List.of(url)) == null, "Nothing more queued");
                });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void programMovesBetweenItsNetworks(GameTestHelper helper) {
        var fixtures = build(helper, Blocks.AIR.defaultBlockState());
        // The north chest's network starts with ten iron; the other cell is empty.
        var cell = AEItems.ITEM_CELL_1K.stack();
        StorageCells.getCellInventory(cell, null).insert(AEItemKey.of(Items.IRON_INGOT), 10, Actionable.MODULATE,
                IActionSource.empty());
        MEChestBlockEntity north = helper.getBlockEntity(CENTER.west().north());
        north.setCell(cell);
        run(helper, fixtures, "host_move", """
                def run() -> str:
                    networks = ae2f.networks()
                    source = networks[0]
                    target = networks[1]
                    if ae2.amount("minecraft:iron_ingot", target) > 0:
                        source = networks[1]
                        target = networks[0]
                    before = ae2.amount("minecraft:iron_ingot", source)
                    moved = ae2.move(source, target, "minecraft:iron_ingot", 4)
                    none = ae2.move(source, target, "minecraft:iron_ingot", 0)
                    return " ".join([str(before), str(moved), str(ae2.amount("minecraft:iron_ingot", source)),
                                     str(ae2.amount("minecraft:iron_ingot", target)), str(none)])
                """, "10 True 6 4 False",
                "A program moves four iron from one network of its domain to the other; moving none is False",
                () -> {
                });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void programReadsCraftingAndNetworkStatus(GameTestHelper helper) {
        var fixtures = build(helper, Blocks.AIR.defaultBlockState());
        run(helper, fixtures, "host_status", """
                def run() -> str:
                    networks = ae2f.networks()
                    powered = ae2.energy() > 0
                    return " ".join([str(ae2f.online(networks[0])), str(ae2f.online(networks[1])), str(powered),
                                     str(ae2.craftable("minecraft:iron_ingot")), str(len(ae2.patterns())),
                                     str(ae2.free_cpus()), str(ae2.craft("minecraft:iron_ingot", 1)),
                                     str(ae2.cancel("minecraft:iron_ingot"))])
                """, "True True True False 0 0 False False",
                "Both networks are online and powered; with no pattern or CPU nothing crafts and nothing cancels",
                () -> {
                });
    }

    /**
     * AE2's own crafting scene (ME Chest with planks, a 1k Crafting Storage, a Pattern Provider with a stick pattern
     * and a Molecular Assembler) joined to the domain through a Switch on top of the chest; the program crafts sticks.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void programCraftsThroughItsDomain(GameTestHelper helper) {
        var crafting = new NativeCraftingFixtures(helper, true);
        var switchAt = new BlockPos(1, 3, 1);
        var interfaceAt = switchAt.above(2);
        new RouterFixtures(helper).placeSwitch(switchAt);
        helper.setBlock(switchAt.above(), RouterRegistration.FEDERATION_CABLE.get());
        helper.setBlock(interfaceAt, AutomationRegistration.INTERFACE.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        var stocked = new boolean[] { false };
        run(helper, interfaceAt, 1, () -> {
            helper.assertTrue(crafting.ready(), "Waiting for the pattern");
            if (!stocked[0]) {
                crafting.insertMaterials(2);
                stocked[0] = true;
                // AE2 plans from the storage cache, which sees the planks a tick later.
                helper.fail("Waiting for the storage cache to see the planks");
            }
        }, "host_crafting", """
                def run() -> str:
                    before = " ".join([str(ae2.craftable("minecraft:stick")), ",".join(ae2.patterns()),
                                       str(ae2.free_cpus())])
                    started = ae2.craft("minecraft:stick", 4)
                    waited = 0
                    while ae2.amount("minecraft:stick") < 4 and waited < 200:
                        mc.wait(5)
                        waited += 5
                    return " ".join([before, str(started), str(ae2.amount("minecraft:stick")),
                                     str(ae2.amount("minecraft:oak_planks"))])
                """, "True minecraft:stick 1 True 4 0",
                "The program's job planned, started on the CPU and put four sticks in storage from the two planks",
                crafting::close);
    }

    /**
     * The crafting scene with its only CPU set to "players only": a program does not see it as free and its job does
     * not start there, as AE2 itself picks no such CPU for a machine.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void programLeavesAPlayersOnlyCpuAlone(GameTestHelper helper) {
        var crafting = new NativeCraftingFixtures(helper, true);
        var switchAt = new BlockPos(1, 3, 1);
        var interfaceAt = switchAt.above(2);
        new RouterFixtures(helper).placeSwitch(switchAt);
        helper.setBlock(switchAt.above(), RouterRegistration.FEDERATION_CABLE.get());
        helper.setBlock(interfaceAt, AutomationRegistration.INTERFACE.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        var stocked = new boolean[] { false };
        run(helper, interfaceAt, 1, () -> {
            helper.assertTrue(crafting.ready(), "Waiting for the pattern");
            if (!stocked[0]) {
                crafting.insertMaterials(2);
                crafting.cpuSelectionMode(CpuSelectionMode.PLAYER_ONLY);
                stocked[0] = true;
                helper.fail("Waiting for the storage cache to see the planks");
            }
        }, "host_players_cpu", """
                def run() -> str:
                    free = ae2.free_cpus()
                    started = ae2.craft("minecraft:stick", 4)
                    mc.wait(40)
                    return " ".join([str(free), str(started), str(ae2.amount("minecraft:stick")),
                                     str(ae2.amount("minecraft:oak_planks"))])
                """, "0 False 0 2",
                "A players-only CPU is not free for the program, which starts no job and uses none of the planks",
                crafting::close);
    }

    /**
     * The crafting scene with a second CPU named "Spare": {@code ae2.cpus} lists both, and a job asked for on "Spare"
     * runs there, which the program sees from the CPU's job while it runs. The first CPU takes machine requests only,
     * so AE2 left to itself would pick that one.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void programCraftsOnTheCpuItNames(GameTestHelper helper) {
        var crafting = new NativeCraftingFixtures(helper, true);
        crafting.addNamedCpu("Spare");
        var switchAt = new BlockPos(1, 3, 1);
        var interfaceAt = switchAt.above(2);
        new RouterFixtures(helper).placeSwitch(switchAt);
        helper.setBlock(switchAt.above(), RouterRegistration.FEDERATION_CABLE.get());
        helper.setBlock(interfaceAt, AutomationRegistration.INTERFACE.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        var stocked = new boolean[] { false };
        run(helper, interfaceAt, 1, () -> {
            helper.assertTrue(crafting.ready(), "Waiting for the pattern");
            if (!stocked[0]) {
                crafting.insertMaterials(2);
                crafting.cpuSelectionMode(CpuSelectionMode.MACHINE_ONLY);
                stocked[0] = true;
                helper.fail("Waiting for the storage cache to see the planks");
            }
        }, "host_named_cpu", """
                def run() -> str:
                    listed: list[str] = []
                    for cpu in ae2.cpus():
                        listed.append(str(cpu.name) + "/" + cpu.mode + "/" + str(cpu.storage) + "/" + str(cpu.busy))
                    listed.sort()
                    started = ae2.craft("minecraft:stick", 4, cpu="Spare")
                    seen = "none"
                    waited = 0
                    while ae2.amount("minecraft:stick") < 4 and waited < 200:
                        for cpu in ae2.cpus():
                            job = cpu.job
                            if job is not None:
                                seen = str(cpu.name) + ":" + job.resource + "x" + str(job.amount)
                        mc.wait(1)
                        waited += 1
                    return " ".join([",".join(listed), str(started), seen, str(ae2.amount("minecraft:stick"))])
                """, "None/machine_only/1024/False,Spare/any/1024/False True Spare:minecraft:stickx4 4",
                "Both CPUs are listed; the job asked for on Spare ran there and made four sticks",
                crafting::close);
    }

    /**
     * The crafting scene with a second network (a powered ME Chest north of the Switch). A job counts in
     * {@code ae2.crafting} from the moment it is asked for, while AE2 still plans it, and {@code ae2.cancel} cancels it
     * only on the network it names.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void programCountsAJobAndCancelsItOnItsNetwork(GameTestHelper helper) {
        var crafting = new NativeCraftingFixtures(helper, true);
        var switchAt = new BlockPos(1, 3, 1);
        var interfaceAt = switchAt.above(2);
        new RouterFixtures(helper).placeSwitch(switchAt);
        helper.setBlock(switchAt.north().west(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(switchAt.north(), AEBlocks.ME_CHEST.block());
        helper.setBlock(switchAt.above(), RouterRegistration.FEDERATION_CABLE.get());
        helper.setBlock(interfaceAt, AutomationRegistration.INTERFACE.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.UP));
        var stocked = new boolean[] { false };
        run(helper, interfaceAt, 2, () -> {
            helper.assertTrue(crafting.ready(), "Waiting for the pattern");
            if (!stocked[0]) {
                crafting.insertMaterials(2);
                stocked[0] = true;
                helper.fail("Waiting for the storage cache to see the planks");
            }
        }, "host_crafting_cancel", """
                def run() -> str:
                    networks = ae2f.networks()
                    crafter = networks[0]
                    other = networks[1]
                    if not ae2.craftable("minecraft:stick", crafter):
                        crafter = networks[1]
                        other = networks[0]
                    started = ae2.craft("minecraft:stick", 4)
                    asked = ae2.crafting("minecraft:stick")
                    elsewhere = ae2.cancel("minecraft:stick", other)
                    kept = ae2.crafting("minecraft:stick", crafter)
                    here = ae2.cancel("minecraft:stick", crafter)
                    return " ".join([str(started), str(asked), str(elsewhere), str(kept), str(here),
                                     str(ae2.crafting("minecraft:stick"))])
                """, "True 4 False 4 True 0",
                "A requested job counts at once, survives a cancel on another network and stops on its own",
                crafting::close);
    }

    /**
     * A cable bus in front of the interface with an import bus facing it and a storage bus on top. A program reads
     * the import bus's settings, changes two of them, and sets the storage bus's priority and a filter slot, reached
     * through {@code side="up"}; the parts themselves hold the new values.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void programChangesTheSettingsOfBusesInFrontOfIt(GameTestHelper helper) {
        var fixtures = build(helper, Blocks.AIR.defaultBlockState());
        var level = helper.getLevel();
        ImportBusPart bus = PartHelper.setPart(level, helper.absolutePos(TARGET), Direction.WEST, null,
                AEParts.IMPORT_BUS.asItem());
        StorageBusPart storage = PartHelper.setPart(level, helper.absolutePos(TARGET), Direction.UP, null,
                AEParts.STORAGE_BUS.asItem());
        run(helper, fixtures, "host_settings", """
                def run(port: Interface) -> str:
                    bus = ae2f.select(port)
                    storage = ae2f.select(port, side="up")
                    listed: list[str] = []
                    for setting in ae2.settings(bus):
                        listed.append(setting.name + "=" + setting.value + "/" + str(len(setting.values)))
                    ae2.set_setting(bus, "redstone_controlled", "high_signal")
                    ae2.set_setting(bus, "fuzzy_mode", "percent_50")
                    ae2.set_priority(storage, 7)
                    ae2.set_config(storage, 1, "minecraft:iron_ingot")
                    filters: list[str] = []
                    for slot in ae2.config(storage):
                        if slot is not None:
                            filters.append(slot.resource)
                    return " ".join([",".join(listed), str(ae2.setting(bus, "redstone_controlled")),
                                     str(ae2.setting(bus, "fuzzy_mode")), str(ae2.setting(bus, "blocking_mode")),
                                     str(ae2.priority(storage)), str(ae2.priority(bus)), ",".join(filters),
                                     str(len(ae2.config(storage))), str(len(ae2.settings(port)))])
                """, "fuzzy_mode=ignore_all/5,redstone_controlled=ignore/4 high_signal percent_50 None 7 None "
                        + "minecraft:iron_ingot 63 0",
                "The import bus lists its settings and takes new ones; the storage bus on top takes a priority and a "
                        + "filter; the interface's own block has none",
                () -> {
                    helper.assertValueEqual(bus.getConfigManager().getSetting(Settings.REDSTONE_CONTROLLED),
                            RedstoneMode.HIGH_SIGNAL, "The import bus is redstone controlled");
                    helper.assertValueEqual(bus.getConfigManager().getSetting(Settings.FUZZY_MODE),
                            FuzzyMode.PERCENT_50, "The import bus compares fuzzily");
                    helper.assertValueEqual(storage.getPriority(), 7, "The storage bus has priority 7");
                    helper.assertValueEqual(storage.getConfig().getKey(1), AEItemKey.of(Items.IRON_INGOT),
                            "The storage bus filters iron in its second slot");
                    helper.assertTrue(storage.getConfig().getKey(0) == null, "Its first slot stays empty");
                });
    }

    /**
     * An ME Interface block in front of the interface: the block entity itself takes the settings, priority and
     * config of a program, and a setting it does not have stops the run with a ValueError naming those it has.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void programConfiguresAnMeInterfaceAndStopsAtASettingItLacks(GameTestHelper helper) {
        var fixtures = build(helper, AEBlocks.INTERFACE.block().defaultBlockState());
        run(helper, fixtures, "host_interface_settings", """
                def run(port: Interface) -> bool:
                    box = ae2f.select(port)
                    ae2.set_setting(box, "fuzzy_mode", "percent_99")
                    ae2.set_priority(box, -3)
                    ae2.set_config(box, 0, "minecraft:iron_ingot", 32)
                    ae2.set_config(box, 2, "minecraft:gold_ingot", 5)
                    ae2.set_config(box, 2)
                    return ae2.set_setting(box, "redstone_controlled", "high_signal")
                """, new Stops("ValueError: the block has no setting 'redstone_controlled'; it has fuzzy_mode"),
                "A setting the ME Interface lacks stops the run, naming the one it has",
                () -> {
                    InterfaceBlockEntity box = helper.getBlockEntity(TARGET);
                    helper.assertValueEqual(box.getConfigManager().getSetting(Settings.FUZZY_MODE),
                            FuzzyMode.PERCENT_99, "The interface compares fuzzily");
                    helper.assertValueEqual(box.getPriority(), -3, "The interface has priority -3");
                    helper.assertValueEqual(box.getConfig().getStack(0),
                            new GenericStack(AEItemKey.of(Items.IRON_INGOT), 32), "It stocks 32 iron");
                    helper.assertTrue(box.getConfig().getStack(2) == null, "The emptied slot is empty");
                });
    }

    /**
     * The editor's dry run of setting changes: every change says it would be made, nothing changes, and a value
     * the bus does not take still stops the dry run.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void dryRunLeavesDeviceSettingsAlone(GameTestHelper helper) {
        var fixtures = build(helper, Blocks.AIR.defaultBlockState());
        var level = helper.getLevel();
        ImportBusPart bus = PartHelper.setPart(level, helper.absolutePos(TARGET), Direction.WEST, null,
                AEParts.IMPORT_BUS.asItem());
        var file = "host_dry_settings";
        var text = """
                def run(port: Interface) -> str:
                    bus = ae2f.select(port)
                    changed = ae2.set_setting(bus, "redstone_controlled", "high_signal")
                    filtered = ae2.set_config(bus, 0, "minecraft:iron_ingot")
                    return " ".join([str(changed), str(filtered), str(ae2.setting(bus, "redstone_controlled")),
                                     str(ae2.config(bus)[0])])

                def wrong(port: Interface) -> bool:
                    mode: SettingValue = "upp"
                    return ae2.set_setting(ae2f.select(port), "redstone_controlled", mode)
                """;
        var server = level.getServer();
        var store = ProgramStore.get(server);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(INTERFACE);
                    helper.assertTrue(automationInterface.id() != null, "Waiting for the interface's ID");
                    helper.assertTrue(AutomationInterfaces.get(level).domainOf(level, automationInterface.id())
                            .isPresent(), "Waiting for the interface's domain");
                })
                .thenExecute(() -> {
                    write(store, file, text);
                    store.reload();
                    AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(INTERFACE);
                    var domain = AutomationInterfaces.get(level).domainOf(level, automationInterface.id())
                            .orElseThrow();
                    var answer = ProgramSync.get(server).handle(1, "dry", List.of(domain.value(), file, "run",
                            ProgramSync.version(text), automationInterface.id()));
                    helper.assertTrue(answer.ok(), "The dry run answered: " + answer.message());
                    var dry = ProgramDryRun.decode(answer.message());
                    helper.assertValueEqual(dry.status(), ProgramDryRun.Status.DONE, "The dry run finished");
                    helper.assertTrue(dry.values().containsValue("'True True ignore None'"),
                            "Both changes would be made, and neither was: " + dry.values());
                    helper.assertValueEqual(bus.getConfigManager().getSetting(Settings.REDSTONE_CONTROLLED),
                            RedstoneMode.IGNORE, "The bus still ignores redstone");
                    helper.assertTrue(bus.getConfig().getKey(0) == null, "The bus's filter is still empty");
                    var wrong = ProgramDryRun.decode(ProgramSync.get(server).handle(2, "dry", List.of(domain.value(),
                            file, "wrong", ProgramSync.version(text), automationInterface.id())).message());
                    helper.assertValueEqual(wrong.status(), ProgramDryRun.Status.FAILED, "The wrong value stops it");
                    helper.assertValueEqual(wrong.failure(), "ValueError: redstone_controlled takes ignore, "
                            + "low_signal, high_signal, signal_pulse, not 'upp'", "The error lists the values");
                    RouterEvidence.write("hostdrysettings", 1, Map.of("result", answer.message().split("\n")[1]));
                    delete(store, file);
                    store.reload();
                    fixtures.close();
                })
                .thenSucceed();
    }

    /**
     * Switch at the centre's west with an ME Chest (item cell) and a Creative Energy Cell on top and a second ME
     * Chest to the north with its own cell (two networks; the cells must not touch), cable at the centre, interface
     * east of it facing the {@code target} block.
     */
    private static RouterFixtures build(GameTestHelper helper, BlockState target) {
        BlockPos.betweenClosed(CENTER.offset(-3, -1, -2), CENTER.offset(3, 3, 2))
                .forEach(position -> helper.setBlock(position, Blocks.AIR));
        helper.setBlock(TARGET.below(), Blocks.STONE);
        var fixtures = new RouterFixtures(helper);
        var switchAt = CENTER.west();
        fixtures.placeSwitch(switchAt);
        fixtures.placeNativeDevice(switchAt, Direction.UP);
        fixtures.placeNativeDevice(switchAt, Direction.NORTH);
        helper.setBlock(switchAt.above(2), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(switchAt.north(2), AEBlocks.CREATIVE_ENERGY_CELL.block());
        MEChestBlockEntity drive = helper.getBlockEntity(switchAt.above());
        drive.setCell(AEItems.ITEM_CELL_1K.stack());
        fixtures.placeFederationCable(CENTER);
        helper.setBlock(INTERFACE, AutomationRegistration.INTERFACE.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.setBlock(TARGET, target);
        return fixtures;
    }

    /** Writes the file, waits for the domain to settle, runs {@code run(...)} and compares its result. */
    private static void run(GameTestHelper helper, RouterFixtures fixtures, String file, String text, Object expected,
            String message, Runnable after) {
        run(helper, INTERFACE, 2, () -> {
        }, file, text, expected, message, () -> {
            after.run();
            fixtures.close();
        });
    }

    /**
     * Runs {@code run(...)} through the interface at {@code interfaceAt} once its domain holds {@code networks}
     * networks and {@code ready} passes; {@code after} checks the world and cleans up.
     */
    private static void run(GameTestHelper helper, BlockPos interfaceAt, int networks, Runnable ready, String file,
            String text, Object expected, String message, Runnable after) {
        var level = helper.getLevel();
        var server = level.getServer();
        var store = ProgramStore.get(server);
        var holder = new ProgramRun[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    ready.run();
                    AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(interfaceAt);
                    helper.assertTrue(automationInterface.id() != null, "Waiting for the interface's ID");
                    var domain = AutomationInterfaces.get(level).domainOf(level, automationInterface.id());
                    helper.assertTrue(domain.isPresent(), "Waiting for the interface's domain");
                    var settled = FederationDomainRegistryAccess.get(level).federationDomain(domain.get())
                            .map(snapshot -> snapshot.memberships().size()).orElse(0);
                    helper.assertValueEqual(settled, networks, "Waiting for the networks to settle in the domain");
                })
                .thenExecute(() -> {
                    write(store, file, text);
                    store.reload();
                    AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(interfaceAt);
                    var domain = AutomationInterfaces.get(level).domainOf(level, automationInterface.id())
                            .orElseThrow();
                    var host = new ProgramHost(server, domain, "gametest:" + file);
                    var args = text.contains("def run(port: Interface)")
                            ? List.<Object>of(automationInterface.id()) : List.<Object>of();
                    holder[0] = ProgramRun.start(store.library(), host.catalog(), host, file, "run", args);
                })
                .thenWaitUntil(() -> {
                    var runner = holder[0].runner();
                    if (runner.awaiting()) {
                        // An outside answer comes in real time; a batch of tests runs ticks back to back.
                        WallClock.await("the answer " + file + " waits for", runner::answerArrived);
                    }
                    var status = holder[0].tick(1000);
                    if (status == Runner.Status.FAILED) {
                        var failure = holder[0].runner().failure();
                        var error = failure.error().type() + ": " + failure.error().getMessage();
                        if (expected instanceof Stops stops) {
                            helper.assertValueEqual(error, stops.error(), message);
                            return;
                        }
                        helper.fail("The program failed: " + error);
                    }
                    helper.assertTrue(status == Runner.Status.DONE && !(expected instanceof Stops),
                            "Running " + file);
                })
                .thenExecute(() -> {
                    if (!(expected instanceof Stops)) {
                        helper.assertValueEqual(holder[0].runner().result(), expected, message);
                    }
                    after.run();
                    RouterEvidence.write(file.replace("_", ""), 1, Map.of("result", expected instanceof Stops stops
                            ? stops.error() : String.valueOf(holder[0].runner().result())));
                    delete(store, file);
                    store.reload();
                })
                .thenSucceed();
    }

    /** What {@code run} expects of a program that stops with {@code error} ("ValueError: ...") instead of a result. */
    private record Stops(String error) {
    }

    private static space.controlnet.ae2federation.test.automation.WebSocketEchoServer echoServer() {
        try {
            return new space.controlnet.ae2federation.test.automation.WebSocketEchoServer();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void write(ProgramStore store, String name, String text) {
        try {
            Files.writeString(store.folder().resolve(name + ".ae2f"), text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void removeFolder(java.nio.file.Path folder) {
        if (!Files.exists(folder)) return;
        try (var all = Files.walk(folder)) {
            for (var path : all.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void delete(ProgramStore store, String name) {
        try {
            Files.deleteIfExists(store.folder().resolve(name + ".ae2f"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
