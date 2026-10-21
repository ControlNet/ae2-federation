package space.controlnet.ae2federation.test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.automation.AutomationInterfaceBlockEntity;
import space.controlnet.ae2federation.automation.AutomationInterfaceBook.AliasResult;
import space.controlnet.ae2federation.automation.AutomationInterfaces;
import space.controlnet.ae2federation.automation.AutomationRegistration;
import space.controlnet.ae2federation.automation.NetworkShortIds;
import space.controlnet.ae2federation.automation.ProgramSync;
import space.controlnet.ae2federation.automation.ProgramStore;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.script.Checker;
import space.controlnet.ae2federation.script.TypeRef;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/**
 * The server's programs folder: files are loaded and checked against the server's interfaces, networks and
 * registries, and the reload command reads the folder again. File names are unique to each test, since batched tests
 * share the server and its folder.
 */
@PrefixGameTestTemplate(false)
public final class ProgramStoreGameTests {
    private static final BlockPos CENTER = new BlockPos(6, 6, 6);

    private ProgramStoreGameTests() {
    }

    /**
     * A program names a network by its short ID. When a merge folds that network into a larger one, the ID names the
     * joined network, so the program keeps working.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void aFoldedNetworksShortIdNamesTheJoinedNetwork(GameTestHelper helper) {
        var small = new BlockPos(1, 1, 1);
        var large = new BlockPos(3, 1, 1);
        helper.setBlock(small, appeng.core.definitions.AEBlocks.ME_CHEST.block());
        helper.setBlock(small.above(), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(large, appeng.core.definitions.AEBlocks.ME_CHEST.block());
        for (var cell = 1; cell <= 4; cell++) {
            helper.setBlock(large.above(cell), appeng.core.definitions.AEBlocks.CREATIVE_ENERGY_CELL.block());
        }
        var shortIds = NetworkShortIds.get(helper.getLevel().getServer());
        var folded = new String[1];
        var survivor = new space.controlnet.ae2federation.identity.NetworkId[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    var smallId = FederationDomainRegistryAccess.confirmedNetworkId(chestGrid(helper, small));
                    var largeId = FederationDomainRegistryAccess.confirmedNetworkId(chestGrid(helper, large));
                    helper.assertTrue(smallId.isPresent() && largeId.isPresent(), "Waiting for both pieces to settle");
                    folded[0] = shortIds.id(smallId.get());
                    survivor[0] = largeId.get();
                })
                .thenExecute(() -> appeng.api.parts.PartHelper.setPart(helper.getLevel(), helper.absolutePos(small.east()),
                        null, null, appeng.core.definitions.AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT)))
                .thenWaitUntil(() -> helper.assertValueEqual(
                        FederationDomainRegistryAccess.confirmedNetworkId(chestGrid(helper, small)),
                        java.util.Optional.of(survivor[0]), "Waiting for the merge to settle on the larger piece"))
                .thenExecute(() -> helper.assertValueEqual(shortIds.network(folded[0]), java.util.Optional.of(survivor[0]),
                        "The folded network's short ID names the joined network"))
                .thenSucceed();
    }

    private static appeng.api.networking.IGrid chestGrid(GameTestHelper helper, BlockPos chest) {
        var host = (appeng.blockentity.storage.MEChestBlockEntity) helper.getBlockEntity(chest);
        var node = host.getMainNode().getNode();
        helper.assertTrue(node != null, "Waiting for the chest's node");
        return node.getGrid();
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void programsAreCheckedAgainstTheServer(GameTestHelper helper) {
        var fixtures = build(helper);
        var server = helper.getLevel().getServer();
        var store = ProgramStore.get(server);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertValueEqual(AutomationInterfaces.get(helper.getLevel())
                        .setAlias(helper.getLevel(), id(helper), "program_gate"), AliasResult.SET,
                        "Waiting for the interface to join its domain and take its alias"))
                .thenWaitUntil(() -> helper.assertFalse(cableNetworks(helper).isEmpty(),
                        "Waiting for the Switch's network to settle in the domain"))
                .thenExecute(() -> {
                    write(store, "store_check", """
                            def open_gate() -> None:
                                if ae2.amount("minecraft:iron_ingot") > 0:
                                    mc.set_redstone("program_gate", 15)

                            def broken() -> None:
                                mc.set_redstone("no_such_gate", 15)
                                ae2.amount("minecraft:not_an_item")
                            """);
                    var library = store.reload();
                    helper.assertTrue(library.names().contains("store_check"), "The reloaded folder has the file");
                    var problems = library.check("store_check").problems().stream().map(Checker.Problem::message)
                            .toList();
                    helper.assertValueEqual(problems, List.of("no interface 'no_such_gate' in this domain",
                            "unknown resource 'minecraft:not_an_item'"),
                            "Only the missing interface and the unknown resource are reported");
                    var catalog = ProgramStore.catalog(server, null);
                    helper.assertValueEqual(catalog.kinds("minecraft:iron_ingot"), Set.of("Item"), "An item");
                    helper.assertValueEqual(catalog.kinds("minecraft:water"), Set.of("Fluid"), "A fluid");
                    helper.assertValueEqual(catalog.kinds("minecraft:not_an_item"), Set.of(),
                            "An unknown ID names nothing, not air");
                    helper.assertTrue(catalog.exists(type("Interface"), id(helper)), "The interface by its ID");
                    var networks = cableNetworks(helper);
                    helper.assertFalse(networks.isEmpty(), "The Switch's network is in the cable's domain");
                    var shortId = NetworkShortIds.get(server).id(networks.get(0));
                    helper.assertValueEqual(NetworkShortIds.get(server).id(networks.get(0)), shortId,
                            "A network keeps its short ID");
                    helper.assertTrue(catalog.exists(type("Network"), shortId), "Programs name it by that ID");
                    var domain = FederationDomainRegistryAccess.get(helper.getLevel()).federationDomainOf(
                            FederationDomainRegistryAccess.nodeId(helper.getLevel(), helper.absolutePos(CENTER)))
                            .orElseThrow().federationDomainId().value();
                    helper.assertTrue(ProgramSync.get(server).index().networks().stream().anyMatch(network ->
                            network.id().equals(shortId) && network.domain().equals(domain)),
                            "The tab's index lists the network in its domain");
                    RouterEvidence.write("programsarecheckedagainsttheserver", 10, Map.of("network", shortId));
                    AutomationInterfaces.get(helper.getLevel()).setAlias(helper.getLevel(), id(helper), "");
                    delete(store, "store_check");
                    fixtures.close();
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 100, required = true, manualOnly = true)
    public static void programsReloadCommandReadsTheFolder(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var store = ProgramStore.get(server);
        write(store, "store_command", "def nothing() -> None:\n    pass\n");
        helper.assertFalse(store.library().names().contains("store_command"), "A new file waits for a reload");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "ae2federation programs reload");
        helper.assertTrue(store.library().names().contains("store_command"), "The command read the folder");
        delete(store, "store_command");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "ae2federation programs reload");
        helper.assertFalse(store.library().names().contains("store_command"), "A deleted file is gone after reload");
        RouterEvidence.write("programsreloadcommandreadsthefolder", 3, Map.of());
        helper.succeed();
    }

    private static RouterFixtures build(GameTestHelper helper) {
        BlockPos.betweenClosed(CENTER.offset(-3, -1, -1), CENTER.offset(3, 2, 1))
                .forEach(position -> helper.setBlock(position, Blocks.AIR));
        var fixtures = new RouterFixtures(helper);
        fixtures.placeSwitch(CENTER.west());
        fixtures.placeNativeDevice(CENTER.west(), Direction.UP);
        fixtures.placeFederationCable(CENTER);
        helper.setBlock(CENTER.east(), AutomationRegistration.INTERFACE.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
        return fixtures;
    }

    private static String id(GameTestHelper helper) {
        AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(CENTER.east());
        var id = automationInterface.id();
        helper.assertTrue(id != null, "Waiting for the interface to claim its ID");
        return id;
    }

    private static List<space.controlnet.ae2federation.identity.NetworkId> cableNetworks(GameTestHelper helper) {
        var level = helper.getLevel();
        var registry = FederationDomainRegistryAccess.get(level);
        return registry.federationDomainOf(FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(CENTER)))
                .map(snapshot -> List.copyOf(snapshot.memberships().keySet())).orElse(List.of());
    }

    private static TypeRef type(String name) {
        return new TypeRef(name, List.of());
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
