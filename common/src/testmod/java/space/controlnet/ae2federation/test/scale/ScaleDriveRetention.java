package space.controlnet.ae2federation.test.scale;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.test.processing.ProcessingCraftingGrid;

public final class ScaleDriveRetention implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScaleDriveRetention.class);
    private static final BlockPos DRIVE_POS = new BlockPos(4, 2, 2);
    private final GameTestHelper helper;
    private final ProcessingCraftingGrid crafting;
    private final DriveBlockEntity drive;
    private final int cellCount;
    private final BlockPos position;

    public ScaleDriveRetention(GameTestHelper helper, ProcessingCraftingGrid crafting, NetworkId sourceId) {
        this(helper, crafting, sourceId, 2);
    }

    public ScaleDriveRetention(GameTestHelper helper, ProcessingCraftingGrid crafting, NetworkId sourceId, int cellCount) {
        this(helper, crafting, sourceId, cellCount, DRIVE_POS);
    }

    public ScaleDriveRetention(GameTestHelper helper, ProcessingCraftingGrid crafting, NetworkId sourceId, int cellCount,
            BlockPos position) {
        this.helper = helper;
        this.crafting = crafting;
        this.cellCount = cellCount;
        this.position = position;
        helper.setBlock(position, AEBlocks.DRIVE.block());
        drive = helper.getBlockEntity(position);
        drive.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", sourceId));
        for (int slot = 0; slot < cellCount; slot++) {
            var cell = AEItems.ITEM_CELL_16K.stack();
            helper.assertTrue(StorageCells.getCellInventory(cell, null) != null, "Native Drive cell must resolve");
            drive.getInternalInventory().setItemDirect(slot, cell);
        }
    }

    public boolean ready(IGridNode source) {
        var node = drive.getMainNode().getNode();
        if (node == null || !node.hasGridBooted() || !node.isActive() || !drive.isPowered()
                || node.getGrid() != source.getGrid()) return false;
        for (int slot = 0; slot < cellCount; slot++) {
            if (drive.getCellInventory(slot) == null) return false;
        }
        return true;
    }

    public void retain(List<ScaleNativeProcessingProbe.CatalogSelection> selections, int index) {
        var selection = selections.get(index);
        var key = selection.output();
        var destinationSlot = index / 63;
        var source = crafting.storage();
        helper.assertValueEqual(amount(source, key), 16L, "Callback output must first occupy direct physical chest");
        if (destinationSlot >= cellCount) {
            for (int slot = 0; slot < cellCount; slot++) {
                helper.assertValueEqual(cell(slot).insert(key, 16, Actionable.SIMULATE, IActionSource.empty()), 0L,
                        "Occupied physical Drive cell must reject the next distinct output type");
            }
            throw new IllegalStateException("Physical Drive cell capacity exhausted at job=" + (index + 1)
                    + " outputKey=" + key.getId() + " installedCells=" + cellCount);
        }
        var destination = cell(destinationSlot);
        helper.assertValueEqual(amount(destination, key), 0L, "Selected Drive cell must not pre-own output");
        for (int slot = 0; slot < cellCount; slot++) {
            if (slot != destinationSlot) {
                helper.assertValueEqual(amount(cell(slot), key), 0L, "Other Drive cell must not own selected output");
            }
        }
        helper.assertValueEqual(destination.insert(key, 16, Actionable.SIMULATE, IActionSource.empty()), 16L,
                "Designated native Drive cell must have room for callback output");
        helper.assertValueEqual(source.extract(key, 16, Actionable.MODULATE, IActionSource.empty()), 16L,
                "Direct callback chest must release exactly its completed output");
        helper.assertValueEqual(destination.insert(key, 16, Actionable.MODULATE, IActionSource.empty()), 16L,
                "Designated physical Drive cell must retain extracted output");
        helper.assertValueEqual(amount(source, key), 0L, "Direct chest must be empty for drained output");
        helper.assertValueEqual(amount(destination, key), 16L, "Drive cell must grow by exactly 16 output units");
        for (int slot = 0; slot < cellCount; slot++) {
            if (slot != destinationSlot) {
                helper.assertValueEqual(amount(cell(slot), key), 0L, "Output must not leak into other Drive cell");
            }
        }
        for (int prior = 0; prior <= index; prior++) {
            AEItemKey priorKey = selections.get(prior).output();
            helper.assertValueEqual(amount(source, priorKey), 0L, "Prior output must not remain in callback chest");
            helper.assertValueEqual(amount(cell(prior / 63), priorKey), 16L,
                    "Earlier typed output must remain physically present in its Drive cell");
            for (int slot = 0; slot < cellCount; slot++) {
                if (slot != prior / 63) {
                    helper.assertValueEqual(amount(cell(slot), priorKey), 0L,
                            "Earlier typed output must not be copied into other Drive cell");
                }
            }
        }
        helper.assertValueEqual(amount(crafting.node().getGrid().getStorageService().getInventory(), key), 16L,
                "Native Grid must see the mounted physical Drive cell after chest drain");
        var distribution = index == selections.size() - 1 ? assertFinalDistribution(selections) : "";
        var receipt = "AE2F_SCALE_DRIVE job=" + (index + 1) + " physicalSlot=" + selection.slot()
                + " outputKey=" + key.getId() + " chestBefore=16 chestAfter=0 driveSlot=" + destinationSlot
                + " driveBefore=0 driveAfter=16 " + (cellCount == 2 ? "otherCell=0" : "otherCells=0")
                + " retainedTypes=" + (index + 1)
                + " retainedUnits=" + 16 * (index + 1)
                + (cellCount == 5 ? distribution : "");
        LOGGER.info("{}", receipt);
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (!configured.isBlank()) {
            var path = Path.of(configured).toAbsolutePath().resolveSibling("scale-drive-retention.log");
            try {
                Files.createDirectories(path.getParent());
                Files.writeString(path, receipt + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException exception) {
                throw new IllegalStateException("Cannot persist physical Drive receipt", exception);
            }
        }
    }

    public void drainCompletedReplay(List<ScaleNativeProcessingProbe.CatalogSelection> selections) {
        helper.assertValueEqual(selections.size(), 256, "A full physical catalog must precede Drive drain");
        for (int index = 0; index < selections.size(); index++) {
            var key = selections.get(index).output();
            helper.assertValueEqual(cell(index / 63).extract(key, 16, Actionable.MODULATE, IActionSource.empty()),
                    16L, "Completed typed Drive output must be accounted as a consumed replay unit");
        }
        for (int slot = 0; slot < cellCount; slot++) {
            helper.assertTrue(cell(slot).getAvailableStacks().isEmpty(),
                    "Next replay must start with an empty physical Drive cell: " + slot);
        }
    }

    private String assertFinalDistribution(List<ScaleNativeProcessingProbe.CatalogSelection> selections) {
        var totalTypes = 0;
        var totalUnits = 0L;
        var typeCounts = new java.util.StringJoiner(",");
        var unitCounts = new java.util.StringJoiner(",");
        for (int slot = 0; slot < cellCount; slot++) {
            var actual = cell(slot).getAvailableStacks();
            var expected = new java.util.HashSet<AEItemKey>();
            var cellUnits = 0L;
            for (int index = slot * 63; index < Math.min((slot + 1) * 63, selections.size()); index++) {
                expected.add(selections.get(index).output());
            }
            helper.assertTrue(actual.keySet().equals(expected), "Physical Drive cell has incorrect typed output keys: " + slot);
            for (var key : expected) {
                helper.assertValueEqual(actual.get(key), 16L, "Physical Drive output quantity differs: " + slot);
                totalUnits += actual.get(key);
                cellUnits += actual.get(key);
            }
            totalTypes += actual.size();
            typeCounts.add(Integer.toString(actual.size()));
            unitCounts.add(Long.toString(cellUnits));
        }
        helper.assertValueEqual(totalTypes, selections.size(), "Physical Drive retained distinct output types");
        helper.assertValueEqual(totalUnits, 16L * selections.size(), "Physical Drive retained output units");
        return " cellTypes=" + typeCounts + " cellUnits=" + unitCounts;
    }

    private MEStorage cell(int slot) {
        return drive.getCellInventory(slot);
    }

    private static long amount(MEStorage storage, AEItemKey key) {
        return storage.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    @Override
    public void close() {
        helper.setBlock(position, Blocks.AIR);
    }
}
