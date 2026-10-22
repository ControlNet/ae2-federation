package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
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
import space.controlnet.ae2federation.automation.ProgramDryRun;
import space.controlnet.ae2federation.automation.ProgramHost;
import space.controlnet.ae2federation.automation.ProgramRun;
import space.controlnet.ae2federation.automation.ProgramStore;
import space.controlnet.ae2federation.automation.ProgramSync;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.script.Runner;
import space.controlnet.ae2federation.router.RouterRegistration;
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
                    var status = holder[0].tick(1000);
                    if (status == Runner.Status.FAILED) {
                        var failure = holder[0].runner().failure();
                        helper.fail("The program failed: " + failure.error().type() + ": "
                                + failure.error().getMessage());
                    }
                    helper.assertTrue(status == Runner.Status.DONE, "Running " + file);
                })
                .thenExecute(() -> {
                    helper.assertValueEqual(holder[0].runner().result(), expected, message);
                    after.run();
                    RouterEvidence.write(file.replace("_", ""), 1, Map.of("result",
                            String.valueOf(holder[0].runner().result())));
                    delete(store, file);
                    store.reload();
                })
                .thenSucceed();
    }

    private static void write(ProgramStore store, String name, String text) {
        try {
            Files.writeString(store.folder().resolve(name + ".ae2f"), text);
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
