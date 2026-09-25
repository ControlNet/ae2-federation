package space.controlnet.ae2federation.test.scale;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.config.Actionable;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import java.lang.reflect.Field;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.identity.NetworkIdentityRegistry;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.identity.NodeLineage;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.test.mixed.MixedMachineBlockEntity;
import space.controlnet.ae2federation.test.mixed.MixedMachineRegistration;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;

public final class ScaleFederationIdentityTarget implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleFederationIdentityTarget.class);
    private static final BlockPos ENDPOINT = NativeProviderLaneFixtures.TARGET_POS.east();
    private static final Field LIVE_CLAIMS;

    static {
        try {
            LIVE_CLAIMS = NetworkIdentityRegistry.class.getDeclaredField("liveClaims");
            LIVE_CLAIMS.setAccessible(true);
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private final GameTestHelper helper;
    private final boolean batch16;
    private final BlockPos endpointPosition;
    private final BlockPos anchorPosition;
    private final BlockPos machinePosition;
    private final BlockPos exportPosition;
    private NetworkId anchoredId;
    private appeng.parts.automation.ExportBusPart exportBus;

    public ScaleFederationIdentityTarget(GameTestHelper helper) {
        this(helper, false);
    }

    public ScaleFederationIdentityTarget(GameTestHelper helper, boolean batch16) {
        this(helper, batch16, ENDPOINT);
    }

    public ScaleFederationIdentityTarget(GameTestHelper helper, boolean batch16, BlockPos endpointPosition) {
        this.helper = helper;
        this.batch16 = batch16;
        this.endpointPosition = endpointPosition;
        anchorPosition = endpointPosition.north();
        machinePosition = endpointPosition.south();
        exportPosition = machinePosition.east();
        helper.setBlock(anchorPosition, AEBlocks.ME_CHEST.block());
        helper.setBlock(anchorPosition.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        var cell = AEItems.ITEM_CELL_1K.stack();
        helper.assertTrue(StorageCells.getCellInventory(cell, null) != null, "Target anchor needs a physical cell");
        anchor().setCell(cell);
    }

    public boolean anchorReady(IGrid source) {
        var node = anchor().getMainNode().getNode();
        return node != null && node.hasGridBooted() && node.isActive() && node.getGrid() != source
                && FabricRegistryAccess.confirmedNetworkId(node.getGrid()).isPresent();
    }

    public void placeEndpoint() {
        helper.setBlock(endpointPosition, ProcessingRegistration.ENDPOINT.get());
        endpoint().getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", anchorId()));
    }

    public boolean endpointReady() {
        var endpoint = endpoint().getMainNode().getNode();
        var anchor = anchor().getMainNode().getNode();
        return endpoint != null && endpoint.hasGridBooted() && endpoint.isActive()
                && endpoint.getGrid() == anchor.getGrid() && endpoint().binding() != null;
    }

    public void placeExportBus() {
        exportBus = PartHelper.setPart(helper.getLevel(), helper.absolutePos(exportPosition), Direction.WEST, null,
                AEParts.EXPORT_BUS.get());
        helper.assertTrue(exportBus != null, "Target Export Bus must be a physical AE2 part");
        exportBus.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("gn", anchorId()));
    }

    public boolean connectExportBus() {
        var bus = exportBus.getGridNode();
        var anchor = anchor().getMainNode().getNode();
        if (bus == null || !bus.hasGridBooted()) return false;
        if (bus.getGrid() != anchor.getGrid()) {
            GridHelper.createConnection(bus, anchor);
            return false;
        }
        return true;
    }

    public boolean exportBusReady() {
        var bus = exportBus.getGridNode();
        return bus != null && bus.hasGridBooted() && exportBus.isActive() && bus.getGrid() == grid();
    }

    public IGrid grid() {
        return anchor().getMainNode().getGrid();
    }

    BlockPos endpointPosition() {
        return endpointPosition;
    }

    long targetCobble() {
        return amount(anchor().getOriginalCellInventory(0), Items.COBBLESTONE);
    }

    public long inputAmount(AEItemKey key) {
        return anchor().getOriginalCellInventory(0).extract(key, Long.MAX_VALUE, Actionable.SIMULATE,
                IActionSource.empty());
    }

    void export(AEItemKey key) {
        if (!batch16 || inputAmount(key) != 16) {
            throw new IllegalStateException("Federation target must receive 16 native inputs before Export Bus activation");
        }
        exportBus.getConfig().setStack(0, new GenericStack(key, 1));
    }

    MixedMachineBlockEntity placeProcessingMachine() {
        helper.setBlock(machinePosition, MixedMachineRegistration.BLOCK.get());
        if (!batch16) exportBus.getConfig().insert(0, AEItemKey.of(Items.COBBLESTONE), 1, Actionable.MODULATE);
        return helper.getBlockEntity(machinePosition);
    }

    IItemHandler returnHandler() {
        return helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                helper.absolutePos(endpointPosition), Direction.SOUTH);
    }

    public NetworkId anchorId() {
        if (anchoredId == null) anchoredId = FabricRegistryAccess.confirmedNetworkId(grid()).orElseThrow();
        return anchoredId;
    }

    public boolean sameLineage() {
        var anchor = anchor().getMainNode().getNode();
        var endpoint = endpoint().getMainNode().getNode();
        return lineage(endpoint).networkId().equals(lineage(anchor).networkId())
                && !lineage(endpoint).nodeId().equals(lineage(anchor).nodeId());
    }

    public boolean busLineage() {
        var anchor = anchor().getMainNode().getNode();
        var endpoint = endpoint().getMainNode().getNode();
        var bus = exportBus.getGridNode();
        return lineage(bus).networkId().equals(anchorId())
                && !lineage(bus).nodeId().equals(lineage(anchor).nodeId())
                && !lineage(bus).nodeId().equals(lineage(endpoint).nodeId());
    }

    public boolean settled() {
        return grid().getService(NetworkIdentityService.class).settlement().status()
                == space.controlnet.ae2federation.identity.IdentityStatus.SETTLED
                && FabricRegistryAccess.confirmedNetworkId(grid()).orElseThrow().equals(anchorId());
    }

    public void snapshot(String stage, IGridNode source, MEStorage sourceStorage) {
        var receipt = "AE2F_SCALE_IDENTITY stage=" + stage
                + " source=" + node(NativeProviderLaneFixtures.TARGET_POS.west(), source)
                + " anchor=" + node(anchorPosition, anchor().getMainNode().getNode())
                + " endpoint=" + node(endpointPosition,
                        helper.getLevel().getBlockEntity(helper.absolutePos(endpointPosition)) instanceof EndpointBlockEntity
                                ? endpoint().getMainNode().getNode() : null)
                + " bus=" + node(exportPosition, exportBus == null ? null : exportBus.getGridNode())
                + " registryGridCount=" + claims().size() + " targetClaims=" + targetClaims()
                + " sourceCobble=" + amount(sourceStorage, Items.COBBLESTONE)
                + " sourceDiamond=" + amount(sourceStorage, Items.DIAMOND)
                + " targetCobble=" + amount(anchor().getOriginalCellInventory(0), Items.COBBLESTONE)
                + " targetDiamond=" + amount(anchor().getOriginalCellInventory(0), Items.DIAMOND);
        LOGGER.info("{}", receipt);
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (!configured.isBlank()) {
            var path = Path.of(configured).toAbsolutePath().resolveSibling("scale-small-identity.log");
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, receipt + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot persist native target identity receipt", exception);
            }
        }
    }

    public String targetClaims() {
        return claims().entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(lineage -> lineage.networkId().equals(anchorId())))
                .sorted(Comparator.comparingInt(entry -> System.identityHashCode(entry.getKey())))
                .map(entry -> identity(entry.getKey()) + ":" + entry.getValue()).toList().toString();
    }

    public boolean onlyAnchorClaim() {
        var matching = claims().entrySet().stream()
                .filter(entry -> entry.getValue().stream().anyMatch(lineage -> lineage.networkId().equals(anchorId())))
                .toList();
        return matching.size() == 1 && matching.getFirst().getKey() == grid();
    }

    private static long amount(MEStorage inventory, net.minecraft.world.item.Item item) {
        return inventory.extract(AEItemKey.of(item), Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    private String node(BlockPos position, IGridNode node) {
        if (node == null) return position + ":absent";
        return position + ":grid=" + identity(node.getGrid()) + ",active=" + node.isActive()
                + ",lineage=" + lineage(node) + ",settlement="
                + node.getGrid().getService(NetworkIdentityService.class).settlement();
    }

    private NodeLineage lineage(IGridNode node) {
        return node.getGrid().getService(NetworkIdentityService.class).lineage(node);
    }

    private static String identity(IGrid grid) {
        return Integer.toUnsignedString(System.identityHashCode(grid));
    }

    @SuppressWarnings("unchecked")
    private Map<IGrid, Set<NodeLineage>> claims() {
        try {
            return (Map<IGrid, Set<NodeLineage>>) LIVE_CLAIMS.get(NetworkIdentityRegistry.get(helper.getLevel()));
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot inspect live native identity claims", exception);
        }
    }

    private MEChestBlockEntity anchor() {
        return helper.getBlockEntity(anchorPosition);
    }

    EndpointBlockEntity endpoint() {
        return helper.getBlockEntity(endpointPosition);
    }

    @Override
    public void close() {
        helper.setBlock(exportPosition, Blocks.AIR);
        helper.setBlock(machinePosition, Blocks.AIR);
        helper.setBlock(endpointPosition, Blocks.AIR);
        helper.setBlock(anchorPosition, Blocks.AIR);
        helper.setBlock(anchorPosition.below(), Blocks.AIR);
    }
}
