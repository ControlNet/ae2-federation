package space.controlnet.ae2federation.test.scale;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.storage.StorageCells;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import net.minecraft.core.registries.BuiltInRegistries;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;

public final class ScaleGridFixture implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleGridFixture.class);
    private final GameTestHelper helper;
    private final List<BlockPos> positions = new ArrayList<>();

    public ScaleGridFixture(GameTestHelper helper, ScaleFactoryProfile profile) {
        this(helper, profile.gridCount());
    }

    public ScaleGridFixture(GameTestHelper helper, int gridCount) {
        this.helper = helper;
        for (int y = 2; y <= 14 && positions.size() < gridCount; y += 3) {
            for (int z = 1; z <= 15 && positions.size() < gridCount; z += 2) {
                for (int x = 1; x <= 15 && positions.size() < gridCount; x += 2) {
                    if (y == 2 && z <= 3 && x <= 5) continue;
                    positions.add(new BlockPos(x, y, z));
                }
            }
        }
        if (positions.size() != gridCount) {
            throw new IllegalArgumentException("Scale tier exceeds bounded physical Grid fixture capacity");
        }
        for (var position : positions) {
            helper.setBlock(position, AEBlocks.ME_CHEST.block());
            helper.setBlock(position.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
            var chest = helper.<MEChestBlockEntity>getBlockEntity(position);
            var cell = AEItems.ITEM_CELL_1K.stack();
            helper.assertTrue(StorageCells.getCellInventory(cell, null) != null,
                    "Scale source must own an actual AE2 cell");
            chest.setCell(cell);
        }
    }

    public boolean ready() {
        return positions.stream().allMatch(position -> {
            var entity = helper.getLevel().getBlockEntity(helper.absolutePos(position));
            if (!(entity instanceof MEChestBlockEntity chest)) return false;
            var node = chest.getMainNode().getNode();
            return node != null && node.hasGridBooted() && node.isActive()
                    && FederationDomainRegistryAccess.confirmedNetworkId(node.getGrid()).isPresent();
        });
    }

    public void recordNodes(String stage) {
        for (var position : positions) {
            recordNode(stage, position);
        }
    }

    public void recordNode(String stage, BlockPos position) {
        var absolute = helper.absolutePos(position);
        var entity = helper.getLevel().getBlockEntity(absolute);
        IGridNode node = null;
        if (entity instanceof MEChestBlockEntity chest) node = chest.getMainNode().getNode();
        else if (entity instanceof AENetworkedBlockEntity host) node = host.getMainNode().getNode();
        recordManagedNode(stage, position, node);
    }

    public void recordManagedNode(String stage, BlockPos position, IGridNode node) {
        var absolute = helper.absolutePos(position);
        var grid = node == null ? null : node.getGrid();
        var receipt = "AE2F_SCALE_NODE stage=" + stage + " position=" + position
                + " block=" + BuiltInRegistries.BLOCK.getKey(helper.getLevel().getBlockState(absolute).getBlock())
                + " node=" + (node == null ? "absent" : Integer.toUnsignedString(System.identityHashCode(node)))
                + " booted=" + (node != null && node.hasGridBooted())
                + " active=" + (node != null && node.isActive())
                + " grid=" + (grid == null ? "absent" : Integer.toUnsignedString(System.identityHashCode(grid)))
                + " networkId=" + (grid == null ? "unconfirmed" : FederationDomainRegistryAccess.confirmedNetworkId(grid)
                        .map(id -> id.value().toString()).orElse("unconfirmed"));
        LOGGER.info("{}", receipt);
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (!configured.isBlank()) {
            var path = Path.of(configured).toAbsolutePath().resolveSibling("scale-small-nodes.log");
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, receipt + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot persist native scale Grid receipt", exception);
            }
        }
    }

    public int distinctActiveGrids() {
        return activeGrids().size();
    }

    public int distinctActiveGridsIncluding(IGrid... additional) {
        var grids = activeGrids();
        for (var grid : additional) {
            if (!grids.add(grid)) {
                throw new IllegalStateException("Processing Grid merged with another scale Grid");
            }
        }
        return grids.size();
    }

    private java.util.Set<IGrid> activeGrids() {
        var grids = Collections.newSetFromMap(new IdentityHashMap<IGrid, Boolean>());
        for (var position : positions) {
            var node = helper.<MEChestBlockEntity>getBlockEntity(position).getMainNode().getNode();
            if (node == null || !node.hasGridBooted() || !node.isActive()) {
                throw new IllegalStateException("Scale Grid is not active at " + position);
            }
            grids.add(node.getGrid());
        }
        return grids;
    }

    @Override
    public void close() {
        for (var position : positions) {
            helper.setBlock(position, Blocks.AIR);
            helper.setBlock(position.below(), Blocks.AIR);
        }
    }
}
