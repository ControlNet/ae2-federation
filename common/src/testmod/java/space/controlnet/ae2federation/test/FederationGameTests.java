package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.StorageCells;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import java.util.Locale;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PrefixGameTestTemplate(false)
public final class FederationGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(FederationGameTests.class);
    private static final BlockPos STORAGE_POS = new BlockPos(1, 1, 1);
    private static final BlockPos ENERGY_POS = STORAGE_POS.below();
    private static final int OPERATIONS = 1_000;

    private FederationGameTests() {
    }

    @GameTest(
            templateNamespace = FederationTestMod.MOD_ID,
            template = "harness_native_smoke",
            timeoutTicks = 200,
            required = true)
    public static void harnessNativeSmoke(GameTestHelper helper) {
        NativeAe2Layout.build(helper);
        helper.succeedWhen(() -> {
            var node = NativeAe2Layout.node(helper);
            helper.assertTrue(node.hasGridBooted(), "AE2 grid must finish booting");
            helper.assertTrue(node.isActive(), "AE2 storage node must be active");
            var storage = node.getGrid().getStorageService().getInventory();
            var key = AEItemKey.of(Items.IRON_INGOT);
            var inserted = storage.insert(key, 64, Actionable.MODULATE, IActionSource.empty());
            helper.assertValueEqual(inserted, 64L, "AE2 network must accept the fixture insertion");
            helper.assertValueEqual(storage.extract(key, 64, Actionable.MODULATE, IActionSource.empty()), 64L,
                    "AE2 network must return the inserted fixture items");
            writeNativeEvidence("verify", "harnessnativesmoke", "ae2federation_test:harness_native_smoke",
                    6, 2, 64, 64, 0);
            LOGGER.info("AE2F_GT_RESULT id=ae2federation_test:harness_native_smoke assertions=6 operations=2");
        });
    }

    @GameTest(
            templateNamespace = FederationTestMod.MOD_ID,
            template = "harness_native_smoke",
            timeoutTicks = 200,
            required = true,
            manualOnly = true)
    public static void harnessBenchmarkSmoke(GameTestHelper helper) {
        NativeAe2Layout.build(helper);
        helper.succeedWhen(() -> {
            var node = NativeAe2Layout.node(helper);
            helper.assertTrue(node.hasGridBooted(), "AE2 benchmark grid must finish booting");
            helper.assertTrue(node.isActive(), "AE2 benchmark storage node must be active");
            var storage = node.getGrid().getStorageService().getInventory();
            var key = AEItemKey.of(Items.GOLD_INGOT);
            long benchmarkInserted = 0;
            long benchmarkExtracted = 0;
            var started = System.nanoTime();
            var operationCount = Boolean.getBoolean("ae2federation.benchmarkEmpty") ? 0 : OPERATIONS;
            helper.assertTrue(operationCount > 0, "Benchmark profile must request nonzero AE2 work");
            for (var operation = 0; operation < operationCount; operation++) {
                benchmarkInserted += storage.insert(key, 1, Actionable.MODULATE, IActionSource.empty());
                benchmarkExtracted += storage.extract(key, 1, Actionable.MODULATE, IActionSource.empty());
            }
            var elapsedNanos = System.nanoTime() - started;
            helper.assertValueEqual(benchmarkInserted, (long) operationCount,
                    "Every benchmark insertion must perform work");
            helper.assertValueEqual(benchmarkExtracted, benchmarkInserted,
                    "Benchmark extraction must reconcile every inserted item");
            helper.assertTrue(elapsedNanos > 0, "Benchmark elapsed time must be measurable");
            writeNativeEvidence("benchmark", "harnessbenchmarksmoke", "ae2federation_test:harness_native_smoke",
                    8, operationCount, benchmarkInserted, benchmarkExtracted, elapsedNanos);
            LOGGER.info("AE2F_BENCHMARK_RESULT profile=harness-native-smoke assertions=8 operations={} inserted={} extracted={} elapsedNanos={}",
                    operationCount, benchmarkInserted, benchmarkExtracted, elapsedNanos);
        });
    }

    private static void writeNativeEvidence(String kind, String testId, String structure, int assertions,
            int operations, long inserted, long extracted, long elapsedNanos) {
        var configuredPath = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (configuredPath.isBlank()) {
            return;
        }
        var evidence = Path.of(configuredPath).toAbsolutePath().normalize();
        var temporary = evidence.resolveSibling(evidence.getFileName() + ".tmp");
        var properties = new Properties();
        properties.setProperty("schemaVersion", "1");
        properties.setProperty("status", "passed");
        properties.setProperty("kind", kind);
        properties.setProperty("testId", testId);
        properties.setProperty("structure", structure);
        properties.setProperty("assertions", Integer.toString(assertions));
        properties.setProperty("operations", Integer.toString(operations));
        properties.setProperty("inserted", Long.toString(inserted));
        properties.setProperty("extracted", Long.toString(extracted));
        properties.setProperty("elapsedNanos", Long.toString(elapsedNanos));
        try {
            Files.createDirectories(evidence.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "AE2 Federation native GameTest evidence");
            }
            Files.move(temporary, evidence, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write native GameTest evidence to " + evidence, exception);
        }
    }

    @GameTest(
            templateNamespace = FederationTestMod.MOD_ID,
            template = "harness_native_smoke",
            timeoutTicks = 40,
            required = true,
            manualOnly = true)
    public static void harnessRequiredFailure(GameTestHelper helper) {
        helper.fail("AE2F_FAULT required failure injection");
    }

    @GameTest(
            templateNamespace = FederationTestMod.MOD_ID,
            template = "harness_native_smoke",
            timeoutTicks = 20,
            required = true,
            manualOnly = true)
    public static void harnessTimeout(GameTestHelper helper) {
        LOGGER.info("AE2F_FAULT timeout injection armed at tick {}", helper.getTick());
    }

    private static final class NativeAe2Layout {
        private NativeAe2Layout() {
        }

        private static void build(GameTestHelper helper) {
            helper.setBlock(ENERGY_POS, AEBlocks.CREATIVE_ENERGY_CELL.block());
            helper.setBlock(STORAGE_POS, AEBlocks.ME_CHEST.block());
            var chest = helper.<MEChestBlockEntity>getBlockEntity(STORAGE_POS);
            var cell = AEItems.ITEM_CELL_1K.stack();
            var cellInventory = StorageCells.getCellInventory(cell, null);
            helper.assertTrue(cellInventory != null, "AE2 item cell inventory must be available");
            chest.setCell(cell);
            LOGGER.info("AE2F_NATIVE_TRACE operation=layout-build machine={} level={} label=controlled-test-machine",
                    Integer.toHexString(System.identityHashCode(chest)),
                    helper.getLevel().dimension().location().toString().toLowerCase(Locale.ROOT));
        }

        private static appeng.api.networking.IGridNode node(GameTestHelper helper) {
            var chest = helper.<MEChestBlockEntity>getBlockEntity(STORAGE_POS);
            var node = chest.getMainNode().getNode();
            helper.assertTrue(node != null, "AE2 storage node must be exposed after first tick");
            LOGGER.info("AE2F_NATIVE_TRACE operation=node-resolve machine={} node={} label=controlled-test-machine",
                    Integer.toHexString(System.identityHashCode(chest)),
                    Integer.toHexString(System.identityHashCode(node)));
            return node;
        }
    }
}
