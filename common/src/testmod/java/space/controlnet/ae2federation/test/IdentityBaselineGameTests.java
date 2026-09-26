package space.controlnet.ae2federation.test;

import appeng.api.networking.IGridNode;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.test.identity.NativeNodeDataProbe;

@PrefixGameTestTemplate(false)
public final class IdentityBaselineGameTests {
    private IdentityBaselineGameTests() {
    }

    @GameTest(
            templateNamespace = FederationTestMod.MOD_ID,
            template = "harness_native_smoke",
            timeoutTicks = 200,
            required = true,
            manualOnly = true)
    public static void identityNativeNodeBaseline(GameTestHelper helper) {
        switch (System.getProperty("ae2federation.identityPhase", "")) {
            case "prepare" -> prepare(helper);
            case "verify" -> verify(helper);
            default -> helper.fail("Unknown identity baseline phase");
        }
    }

    private static void prepare(GameTestHelper helper) {
        var storagePos = helper.getLevel().getSharedSpawnPos().offset(8, 2, 0);
        helper.getLevel().setBlockAndUpdate(storagePos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.getLevel().setBlockAndUpdate(storagePos, AEBlocks.ME_CHEST.block().defaultBlockState());
        var chest = (MEChestBlockEntity) helper.getLevel().getBlockEntity(storagePos);
        var cell = AEItems.ITEM_CELL_1K.stack();
        helper.assertTrue(StorageCells.getCellInventory(cell, null) != null,
                "AE2 item cell inventory must be available");
        chest.setCell(cell);
        helper.succeedWhen(() -> {
            var node = node(helper, storagePos);
            helper.assertTrue(node.hasGridBooted(), "Baseline native grid must boot before save");
            var marker = node.getGrid().getService(NativeNodeDataProbe.class).marker(node).orElseThrow();
            writeState(storagePos, marker);
            helper.assertTrue(!marker.isBlank(), "Baseline service must assign native node data");
        });
    }

    private static void verify(GameTestHelper helper) {
        var state = readState();
        var absolutePos = new BlockPos(
                Integer.parseInt(state.getProperty("x")),
                Integer.parseInt(state.getProperty("y")),
                Integer.parseInt(state.getProperty("z")));
        helper.succeedWhen(() -> {
            var blockEntity = helper.getLevel().getBlockEntity(absolutePos);
            helper.assertTrue(blockEntity instanceof MEChestBlockEntity,
                    "Native ME chest must survive the actual server restart");
            var node = ((MEChestBlockEntity) blockEntity).getMainNode().getNode();
            helper.assertTrue(node != null, "Restarted native node must be reconstructed");
            var marker = node.getGrid().getService(NativeNodeDataProbe.class).marker(node).orElseThrow();
            helper.assertValueEqual(marker, state.getProperty("marker"),
                    "AE2 addNode must restore the marker written by saveNodeData");
        });
    }

    private static IGridNode node(GameTestHelper helper, BlockPos absolutePos) {
        var chest = (MEChestBlockEntity) helper.getLevel().getBlockEntity(absolutePos);
        helper.assertTrue(chest != null, "AE2 native host must exist in the spawn chunk");
        var node = chest.getMainNode().getNode();
        helper.assertTrue(node != null, "AE2 native node must be initialized");
        return node;
    }

    private static void writeState(BlockPos pos, String marker) {
        var path = statePath();
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        var properties = new Properties();
        properties.setProperty("x", Integer.toString(pos.getX()));
        properties.setProperty("y", Integer.toString(pos.getY()));
        properties.setProperty("z", Integer.toString(pos.getZ()));
        properties.setProperty("marker", marker);
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "Native AE2 node persistence baseline");
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write identity baseline state", exception);
        }
    }

    private static Properties readState() {
        var properties = new Properties();
        try (var input = Files.newInputStream(statePath())) {
            properties.load(input);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read identity baseline state", exception);
        }
    }

    private static Path statePath() {
        var configured = System.getProperty("ae2federation.identityStateFile", "");
        if (configured.isBlank()) {
            throw new IllegalStateException("Missing ae2federation.identityStateFile");
        }
        return Path.of(configured).toAbsolutePath().normalize();
    }
}
