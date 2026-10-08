package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.parts.automation.ExportBusPart;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.p2p.P2PTunnels;
import space.controlnet.ae2federation.test.p2p.QuantumP2PCarrier;
import space.controlnet.ae2federation.test.world.OtherDimensionSite;

/**
 * Dev-only exporter (manual, not in the manifest): builds the cross-dimension production fixture with the registered
 * blocks and writes it as two structure templates, one per dimension, for a production server without the testmod.
 * <p>
 * Overworld: a source network (creative energy cell, ME chest holding 16 cobblestone, 1k crafting storage, an ME
 * Terminal on a cable west of the chest) and a Federation Pattern Provider east of the chest, its Federation face on a
 * Federation cable in front of the {@link QuantumP2PCarrier}'s overworld tunnel. A Router on the chest joins the
 * source network to the Federation Domain through Federation cable over the Provider. The Provider holds a cobblestone to
 * stone Processing Pattern and no mapping: the player maps it in the Provider's screen. A second Federation cable
 * south of the first leads to a twin Endpoint with no subnet, placed so that it shares its coordinates with the nether
 * Endpoint once both templates are placed as the properties file says.
 * <p>
 * Nether: the carrier's nether tunnel, a Federation cable, and the Endpoint facing it. The Endpoint's subnet is an ME
 * chest and a creative energy cell south of it; cables from the chest lead up and over a real furnace above the
 * Endpoint, where an Export Bus feeds it cobblestone. A hopper under the furnace returns the stone into the Endpoint.
 * East of it, a storage network of its own (ME chest, creative energy cell) joins the Domain through a Router and
 * Federation cable, so the Domain has a member network in each dimension.
 * <p>
 * Run it with {@code runGameTestServer -PfederationGameTestId=crossdimensionproductionexport
 * -PfederationNativeEvidenceFile=<dir>/evidence.properties}; the templates and properties go next to that file.
 */
@PrefixGameTestTemplate(false)
public final class CrossDimensionProductionExportGameTests {
    static final BlockPos SOURCE_POWER = new BlockPos(1, 2, 2);
    static final BlockPos SOURCE_CHEST = new BlockPos(1, 2, 3);
    static final BlockPos SOURCE_CPU = new BlockPos(1, 2, 4);
    static final BlockPos PROVIDER = QuantumP2PCarrier.OVERWORLD_FRONT.west();
    static final BlockPos TERMINAL_CABLE = SOURCE_CHEST.west();
    static final BlockPos TWIN_CABLE = QuantumP2PCarrier.OVERWORLD_FRONT.south();
    static final BlockPos TWIN = TWIN_CABLE.south();
    static final BlockPos OVERWORLD_ROUTER = SOURCE_CHEST.above();
    static final BlockPos[] OVERWORLD_ROUTER_CABLES = { OVERWORLD_ROUTER.east(), OVERWORLD_ROUTER.east(2) };
    static final BlockPos OVERWORLD_FROM = new BlockPos(0, 1, 1);
    static final Vec3i OVERWORLD_SIZE = new Vec3i(9, 3, 5);

    static final BlockPos ENDPOINT = QuantumP2PCarrier.NETHER_FRONT.south();
    static final BlockPos SUBNET_CHEST = ENDPOINT.south();
    static final BlockPos SUBNET_POWER = SUBNET_CHEST.south();
    static final BlockPos HOPPER = ENDPOINT.above();
    static final BlockPos FURNACE = HOPPER.above();
    static final BlockPos[] SUBNET_CABLES = { SUBNET_CHEST.above(), SUBNET_CHEST.above(2), SUBNET_CHEST.above(3),
            FURNACE.above() };
    static final BlockPos STORAGE_ROUTER = ENDPOINT.east(2);
    static final BlockPos STORAGE_CHEST = STORAGE_ROUTER.south();
    static final BlockPos STORAGE_POWER = STORAGE_CHEST.south();
    static final BlockPos[] NETHER_ROUTER_CABLES = { ENDPOINT.east(), QuantumP2PCarrier.NETHER_FRONT.east() };
    static final BlockPos NETHER_SIZE = new BlockPos(4, 4, 8);

    static final AEItemKey COBBLESTONE = AEItemKey.of(Items.COBBLESTONE);
    static final AEItemKey STONE = AEItemKey.of(Items.STONE);

    private CrossDimensionProductionExportGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            timeoutTicks = 1200)
    public static void crossDimensionProductionExport(GameTestHelper helper) {
        var site = OtherDimensionSite.nether(helper, NETHER_SIZE);
        var carrier = new QuantumP2PCarrier(helper, site);
        var stage = new int[1];
        var cables = new int[1];
        helper.succeedWhen(() -> {
            if (stage[0] == 0) {
                helper.assertTrue(site.ready(), "Waiting for the nether site to tick: " + site.tickDiagnostics());
                helper.setBlock(SOURCE_POWER, AEBlocks.CREATIVE_ENERGY_CELL.block());
                helper.setBlock(SOURCE_CHEST, AEBlocks.ME_CHEST.block());
                helper.<MEChestBlockEntity>getBlockEntity(SOURCE_CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
                helper.setBlock(SOURCE_CPU, AEBlocks.CRAFTING_STORAGE_1K.block());
                helper.setBlock(PROVIDER, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.EAST));
                site.setBlock(ENDPOINT, ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.NORTH));
                site.setBlock(SUBNET_CHEST, AEBlocks.ME_CHEST.block().defaultBlockState());
                site.<MEChestBlockEntity>getBlockEntity(SUBNET_CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
                site.setBlock(SUBNET_POWER, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                site.setBlock(STORAGE_CHEST, AEBlocks.ME_CHEST.block().defaultBlockState());
                site.<MEChestBlockEntity>getBlockEntity(STORAGE_CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
                site.setBlock(STORAGE_POWER, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
                carrier.build();
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(settled(grid(helper.getBlockEntity(SOURCE_CHEST)))
                        && settled(grid(site.getBlockEntity(SUBNET_CHEST)))
                        && settled(grid(site.getBlockEntity(STORAGE_CHEST))), "Waiting for every network to settle");
                helper.assertTrue(carrier.linked(), "Waiting for the Quantum Bridge to join the carrier's halves");
                carrier.pair();
                helper.setBlock(QuantumP2PCarrier.OVERWORLD_FRONT, RouterRegistration.FEDERATION_CABLE.get());
                helper.setBlock(TWIN_CABLE, RouterRegistration.FEDERATION_CABLE.get());
                helper.setBlock(TWIN, ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.NORTH));
                site.setBlock(QuantumP2PCarrier.NETHER_FRONT, RouterRegistration.FEDERATION_CABLE.get()
                        .defaultBlockState());
                helper.setBlock(OVERWORLD_ROUTER, RouterRegistration.ROUTER.get());
                for (var cable : OVERWORLD_ROUTER_CABLES) helper.setBlock(cable, RouterRegistration.FEDERATION_CABLE.get());
                site.setBlock(STORAGE_ROUTER, RouterRegistration.ROUTER.get().defaultBlockState());
                for (var cable : NETHER_ROUTER_CABLES) {
                    site.setBlock(cable, RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
                }
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                // One cable per tick, each after the previous one joined the settled network it extends: two fresh
                // nodes that meet first would form a network of their own.
                if (cables[0] > 0) {
                    var previous = cables[0] <= SUBNET_CABLES.length
                            ? node(site.level(), site.absolute(SUBNET_CABLES[cables[0] - 1]))
                            : node(helper.getLevel(), helper.absolutePos(TERMINAL_CABLE));
                    var expected = cables[0] <= SUBNET_CABLES.length ? grid(site.getBlockEntity(SUBNET_CHEST))
                            : grid(helper.getBlockEntity(SOURCE_CHEST));
                    helper.assertTrue(previous != null && previous.getGrid() == expected && settled(expected),
                            "Waiting for cable " + cables[0] + " to join its network");
                }
                if (cables[0] < SUBNET_CABLES.length) {
                    helper.assertTrue(P2PTunnels.placeCable(site.level(), site.absolute(SUBNET_CABLES[cables[0]])),
                            "A subnet cable must be placed");
                    cables[0]++;
                    helper.fail("Placing subnet cables");
                }
                if (cables[0] == SUBNET_CABLES.length) {
                    helper.assertTrue(P2PTunnels.placeCable(helper.getLevel(), helper.absolutePos(TERMINAL_CABLE)),
                            "The terminal's cable must be placed");
                    cables[0]++;
                    helper.fail("Placing the terminal's cable");
                }
                site.setBlock(FURNACE, Blocks.FURNACE.defaultBlockState());
                site.<FurnaceBlockEntity>getBlockEntity(FURNACE).setItem(1, new ItemStack(Items.COAL, 64));
                site.setBlock(HOPPER, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
                helper.assertTrue(PartHelper.setPart(site.level(), site.absolute(FURNACE.above()), Direction.DOWN, null,
                        AEParts.EXPORT_BUS.asItem()) != null, "The Export Bus must be placed over the furnace");
                helper.assertTrue(PartHelper.setPart(helper.getLevel(), helper.absolutePos(TERMINAL_CABLE),
                        Direction.WEST, null, AEParts.TERMINAL.asItem()) != null, "The ME Terminal must be placed");
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                var bus = (ExportBusPart) PartHelper.getPart(site.level(), site.absolute(FURNACE.above()), Direction.DOWN);
                helper.assertTrue(bus.getMainNode().isActive(), "Waiting for the Export Bus to be powered");
                bus.getConfig().setStack(0, new GenericStack(COBBLESTONE, 1));
                var source = grid(helper.getBlockEntity(SOURCE_CHEST));
                helper.assertValueEqual(source.getStorageService().getInventory().insert(COBBLESTONE, 16,
                        Actionable.MODULATE, IActionSource.empty()), 16L, "The source chest takes the cobblestone");
                FederationPatternProviderBlockEntity provider = helper.getBlockEntity(PROVIDER);
                var pattern = PatternDetailsHelper.encodeProcessingPattern(List.of(new GenericStack(COBBLESTONE, 1)),
                        List.of(new GenericStack(STONE, 1)));
                helper.assertTrue(provider.getTerminalPatternInventory().insertItem(0, pattern, false).isEmpty(),
                        "The Provider takes the pattern");
                stage[0] = 4;
            }
            var source = grid(helper.getBlockEntity(SOURCE_CHEST));
            var subnet = grid(site.getBlockEntity(SUBNET_CHEST));
            helper.assertTrue(settled(source) && settled(subnet), "Waiting for both networks to settle");
            helper.assertTrue(node(site.level(), site.absolute(FURNACE.above())).getGrid() == subnet,
                    "The Export Bus is on the Endpoint's subnet");
            var endpointNode = FederationDomainRegistryAccess.nodeId(site.level(), site.absolute(ENDPOINT));
            var twinNode = FederationDomainRegistryAccess.nodeId(helper.getLevel(), helper.absolutePos(TWIN));
            var registry = FederationDomainRegistryAccess.get(helper.getLevel());
            var sourceId = FederationDomainRegistryAccess.confirmedNetworkId(source).orElseThrow();
            helper.assertTrue(registry.federationdomainsFor(sourceId).stream().map(registry::federationDomain)
                    .anyMatch(domain -> domain.isPresent() && domain.get().nodes().contains(endpointNode)
                            && domain.get().nodes().contains(twinNode)),
                    "Waiting for the nether Endpoint and its twin to join the Provider's Domain");
            var storageId = FederationDomainRegistryAccess.confirmedNetworkId(grid(site.getBlockEntity(STORAGE_CHEST)))
                    .orElseThrow();
            helper.assertTrue(registry.federationdomainsFor(sourceId).stream().map(registry::federationDomain)
                    .anyMatch(domain -> domain.isPresent() && domain.get().memberships().containsKey(sourceId)
                            && domain.get().memberships().containsKey(storageId)
                            && domain.get().nodes().contains(endpointNode)),
                    "Waiting for both Routers to make the source and the nether storage members of that Domain");
            export(helper, site, sourceId.value().toString(),
                    FederationDomainRegistryAccess.confirmedNetworkId(subnet).orElseThrow().value().toString());
            site.close();
        });
    }

    private static void export(GameTestHelper helper, OtherDimensionSite site, String sourceNetwork,
            String subnetNetwork) {
        var overworld = new StructureTemplate();
        overworld.fillFromWorld(helper.getLevel(), helper.absolutePos(OVERWORLD_FROM), OVERWORLD_SIZE, false, null);
        var nether = new StructureTemplate();
        nether.fillFromWorld(site.level(), site.absolute(BlockPos.ZERO), NETHER_SIZE, false, null);
        var properties = new Properties();
        properties.setProperty("sourceNetwork", sourceNetwork);
        properties.setProperty("subnetNetwork", subnetNetwork);
        // Positions inside each template, relative to the template's own corner.
        properties.setProperty("overworld.provider", pos(PROVIDER.subtract(OVERWORLD_FROM)));
        properties.setProperty("overworld.sourceChest", pos(SOURCE_CHEST.subtract(OVERWORLD_FROM)));
        properties.setProperty("overworld.terminalCable", pos(TERMINAL_CABLE.subtract(OVERWORLD_FROM)));
        properties.setProperty("overworld.cpu", pos(SOURCE_CPU.subtract(OVERWORLD_FROM)));
        properties.setProperty("overworld.twin", pos(TWIN.subtract(OVERWORLD_FROM)));
        properties.setProperty("nether.endpoint", pos(ENDPOINT));
        properties.setProperty("nether.subnetChest", pos(SUBNET_CHEST));
        properties.setProperty("nether.furnace", pos(FURNACE));
        properties.setProperty("overworld.router", pos(OVERWORLD_ROUTER.subtract(OVERWORLD_FROM)));
        properties.setProperty("nether.storageRouter", pos(STORAGE_ROUTER));
        properties.setProperty("sourceCobblestone", "16");
        try {
            var directory = Path.of(System.getProperty("ae2federation.nativeEvidenceFile")).toAbsolutePath().getParent();
            Files.createDirectories(directory);
            NbtIo.writeCompressed(overworld.save(new CompoundTag()), directory.resolve("crossdim_overworld.nbt"));
            NbtIo.writeCompressed(nether.save(new CompoundTag()), directory.resolve("crossdim_nether.nbt"));
            try (var output = Files.newOutputStream(directory.resolve("crossdim.properties"))) {
                properties.store(output, "Positions inside crossdim_overworld.nbt and crossdim_nether.nbt");
            }
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static IGrid grid(BlockEntity entity) {
        var node = entity instanceof IGridConnectedBlockEntity connected ? connected.getMainNode().getNode() : null;
        return node == null ? null : node.getGrid();
    }

    private static IGridNode node(net.minecraft.server.level.ServerLevel level, BlockPos position) {
        var node = GridHelper.getExposedNode(level, position, Direction.DOWN);
        return node != null ? node : GridHelper.getExposedNode(level, position, Direction.NORTH);
    }

    private static boolean settled(IGrid grid) {
        return grid != null && FederationDomainRegistryAccess.confirmedNetworkId(grid).isPresent();
    }

    private static String pos(BlockPos position) {
        return position.getX() + "," + position.getY() + "," + position.getZ();
    }
}
