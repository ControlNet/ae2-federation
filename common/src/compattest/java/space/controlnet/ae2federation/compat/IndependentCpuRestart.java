package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEItemKey;
import appeng.blockentity.storage.MEChestBlockEntity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture;
import space.controlnet.ae2federation.test.world.RestartChunkTickets;

/** Two real JVM phases using the same world; tickets and chunk loads here belong exclusively to the test harness. */
final class IndependentCpuRestart {
    private static final Path STATE = Path.of("independent-cpu-restart.properties");

    static void prepare(GameTestHelper helper, PatternProjectionFixture fixture, BlockPos cpu) {
        var state = new Properties();
        var consumerChest = helper.absolutePos(new BlockPos(5, 3, 6));
        var providerChest = helper.absolutePos(new BlockPos(5, 3, 3));
        state.setProperty("pid", Long.toString(ProcessHandle.current().pid()));
        state.setProperty("consumerChest", Long.toString(consumerChest.asLong()));
        state.setProperty("providerChest", Long.toString(providerChest.asLong()));
        state.setProperty("consumer", fixture.consumerNetwork().value().toString());
        state.setProperty("executing", fixture.providerNetwork().value().toString());
        state.setProperty("cpu", Long.toString(fixture.site().absolute(cpu).asLong()));
        state.setProperty("cpuLow", Long.toString(fixture.site().absolute(new BlockPos(-1, 0, -1)).asLong()));
        state.setProperty("cpuHigh", Long.toString(fixture.site().absolute(PatternProjectionFixture.SITE_SIZE).asLong()));
        var job = CraftingReturnLedger.get(helper.getLevel()).jobs(fixture.consumerNetwork()).getFirst();
        state.setProperty("craftingId", job.craftingId().toString());
        var low = helper.absolutePos(new BlockPos(-8, 0, -8));
        var high = helper.absolutePos(new BlockPos(24, 0, 24));
        state.setProperty("mainLow", Long.toString(low.asLong()));
        state.setProperty("mainHigh", Long.toString(high.asLong()));
        RestartChunkTickets.force(helper.getLevel(), consumerChest, chunks(low, high), true);
        try (var stream = Files.newOutputStream(STATE)) {
            state.store(stream, "Real independent CPU unload and restart fixture");
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        // Do not close the fixture: the ordinary server shutdown now saves these actual blocks and the owed stock.
    }

    static void resume(GameTestHelper helper, boolean advanced) {
        var state = new Properties();
        try (var stream = Files.newInputStream(STATE)) {
            state.load(stream);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        helper.assertTrue(Long.parseLong(state.getProperty("pid")) != ProcessHandle.current().pid(),
                "Resume must execute in a different real Java process");
        var consumer = new NetworkId(UUID.fromString(state.getProperty("consumer")));
        var executing = new NetworkId(UUID.fromString(state.getProperty("executing")));
        var cpu = pos(state, "cpu");
        var consumerPos = pos(state, "consumerChest");
        var providerPos = pos(state, "providerChest");
        var nether = helper.getLevel().getServer().getLevel(Level.NETHER);
        var stone = AEItemKey.of(Items.STONE);
        int[] phase = {0};
        long[] startSweeps = {0};
        helper.succeedWhen(() -> {
            helper.assertTrue(helper.getLevel().isLoaded(consumerPos) && helper.getLevel().isLoaded(providerPos),
                    "Persistent test tickets must restore both main Grids before loading the CPU");
            var consumerChest = (MEChestBlockEntity) helper.getLevel().getBlockEntity(consumerPos);
            var providerChest = (MEChestBlockEntity) helper.getLevel().getBlockEntity(providerPos);
            helper.assertTrue(consumerChest != null && providerChest != null, "Both saved native storage blocks survive restart");
            var grid = consumerChest.getMainNode().getGrid();
            helper.assertTrue(grid != null && providerChest.getMainNode().getGrid() != null, "Waiting for saved native Grid restoration");
            helper.assertTrue(FederationDomainRegistryAccess.confirmedNetworkId(grid).filter(consumer::equals).isPresent(),
                    "The restored consumer retains its dimension-aware identity");
            var ledger = CraftingReturnLedger.get(helper.getLevel());
            var service = CraftingProjectionService.get(helper.getLevel());
            if (phase[0] == 0) {
                helper.assertFalse(nether.isLoaded(cpu), "The independent CPU must remain unloaded after actual process restart");
                helper.assertValueEqual(grid.getCraftingService().getCpus().size(), 0, "No substitute CPU is loaded");
                helper.assertValueEqual(ledger.owed(executing, consumer, stone), 2L, "Both debts survived actual save and restart");
                helper.assertValueEqual(ledger.held(executing, consumer, stone), 2L, "Both actual held arrivals survived restart");
                helper.assertTrue(ledger.jobs(consumer).stream().anyMatch(job -> !job.nativeCpu()
                        && job.craftingId().toString().equals(state.getProperty("craftingId"))),
                        "The original independent crafting UUID survived restart");
                startSweeps[0] = service.ledgerSweeps();
                phase[0] = 1;
            }
            if (phase[0] == 1) {
                helper.assertTrue(service.ledgerSweeps() - startSweeps[0] >= 4,
                        "Four post-restart sweeps must preserve the independently unloaded CPU");
                helper.assertFalse(nether.isLoaded(cpu), "The CPU stayed unloaded throughout post-restart sweeps");
                helper.assertValueEqual(ledger.owed(executing, consumer, stone), 2L, "Debt survives beyond cleanup cycles");
                helper.assertValueEqual(consumerChest.getOriginalCellInventory(0).getAvailableStacks().get(stone), 0L,
                        "No premature ordinary-storage return while the independent CPU is absent");
                var regions = chunks(pos(state, "cpuLow"), pos(state, "cpuHigh"));
                RestartChunkTickets.force(nether, cpu, regions, true);
                // Test-only loading mirrors OtherDimensionSite: surrounding chunks must be full before entities tick.
                for (var chunk : regions) for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
                    nether.getChunk(chunk.x + dx, chunk.z + dz);
                }
                phase[0] = 2;
            }
            helper.assertTrue(nether.isLoaded(cpu), "Waiting for the saved independent CPU chunk to reload");
            helper.assertTrue(grid.getCraftingService().getCpus().stream().anyMatch(value ->
                    value.getClass().getName().equals(advanced ? "net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU"
                            : "com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCPU")
                            || value.getClass().getName().equals("com.moakiee.ae2lt.crafting.timewheel.TimeWheelCraftingCpuPool")),
                    "The real independent CPU must rejoin the original consumer Grid");
            helper.assertTrue(grid.getCraftingService().getCpus().stream().noneMatch(value -> value.isBusy()),
                    "The original job completes after restart without submitting another request");
            helper.assertValueEqual(ledger.owed(executing, consumer, stone), 0L, "Actual return settles the persisted debt");
            helper.assertValueEqual(consumerChest.getOriginalCellInventory(0).getAvailableStacks().get(stone), 2L,
                    "Both physical outputs reach the consumer");
            helper.assertValueEqual(providerChest.getOriginalCellInventory(0).getAvailableStacks().get(stone), 0L,
                    "No output duplicates remain in provider storage");
            RestartChunkTickets.force(nether, cpu, chunks(pos(state, "cpuLow"), pos(state, "cpuHigh")), false);
            RestartChunkTickets.force(helper.getLevel(), consumerPos, chunks(pos(state, "mainLow"), pos(state, "mainHigh")), false);
        });
    }

    private static BlockPos pos(Properties state, String name) {
        return BlockPos.of(Long.parseLong(state.getProperty(name)));
    }

    private static java.util.List<ChunkPos> chunks(BlockPos low, BlockPos high) {
        var result = new ArrayList<ChunkPos>();
        var first = new ChunkPos(low);
        var last = new ChunkPos(high);
        for (int x = first.x; x <= last.x; x++) for (int z = first.z; z <= last.z; z++) result.add(new ChunkPos(x, z));
        return result;
    }
}
