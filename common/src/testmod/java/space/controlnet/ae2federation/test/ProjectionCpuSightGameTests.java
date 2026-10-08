package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.cobblestone;
import static space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.stone;

import java.util.Map;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture;
import space.controlnet.ae2federation.test.crafting.PatternProjectionFixture.ConsumerCpus;
import space.controlnet.ae2federation.test.policy.PolicyEvidence;

/**
 * The consumer's CPU out of sight: its chunk really unloads (a nether site whose tickets are released, on the consumer
 * network over a real Quantum Network Bridge) while the rest of the consumer network stays loaded. AE2 takes the CPU out
 * of its Grid with the job intact, so the consumer reports nothing requested; that must not be read as a cancel.
 */
@PrefixGameTestTemplate(false)
public final class ProjectionCpuSightGameTests {
    /** Ledger sweeps to sit through while the CPU is out of sight: two idle looks used to forget a debt. */
    private static final long SWEEPS_OUT_OF_SIGHT = 4;

    private ProjectionCpuSightGameTests() {
    }

    /**
     * The CPU's chunk unloads after both pushes, the products arrive while it is gone, and four ledger sweeps pass. When
     * the chunk loads again, the same job, never requested again, gets its two stone and finishes.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1600)
    public static void projectionCpuOutOfSight(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper, ConsumerCpus.NETHER);
        var stage = new int[1];
        var sweeps = new long[1];
        helper.succeedWhen(() -> {
            if (stage[0] < 4 && pushedTwoStone(helper, fixture, stage, 2)) {
                fixture.site().releaseTickets();
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertFalse(fixture.site().loaded(), "Waiting for the CPU's chunks to unload");
                helper.assertValueEqual(fixture.visibleConsumerCpus(), 0, "The unloaded CPU leaves the consumer's Grid");
                sweeps[0] = fixture.ledgerSweeps();
                helper.assertValueEqual(fixture.runMachine(2), 2L, "The provider network takes the stone");
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertTrue(fixture.ledgerSweeps() - sweeps[0] >= SWEEPS_OUT_OF_SIGHT,
                        "Letting the ledger look while the CPU is out of sight");
                helper.assertFalse(fixture.site().loaded(), "The CPU stays unloaded throughout");
                helper.assertValueEqual(fixture.owed(stone()), 2L,
                        "The debt stays while the CPU is out of sight; " + fixture.returns(stone()));
                helper.assertValueEqual(fixture.heldForConsumer(stone()), 2L, "The stone that came back is held for it");
                helper.assertValueEqual(fixture.held(fixture.providerChest(), stone()), 2L,
                        "The stone waits on the provider network");
                helper.assertValueEqual(fixture.held(fixture.consumerChest(), stone()), 0L, "Nothing reached the consumer");
                fixture.site().forceAgain();
                stage[0] = 6;
            }
            if (stage[0] == 6) {
                helper.assertTrue(fixture.site().ticking() && fixture.netherCpu() != null,
                        "Waiting for the CPU's chunks to load again");
                stage[0] = 7;
            }
            helper.assertValueEqual(fixture.busyConsumerCpus(), 0L,
                    "Waiting for the reloaded job to finish; " + fixture.returns(stone()));
            assertSettled(helper, fixture, 2, 0);
            helper.assertTrue(fixture.netherCpu() != null && fixture.netherCpu().craftingLogic.getInventory().list
                    .isEmpty(), "The CPU holds nothing afterwards");
            PolicyEvidence.write("projectioncpuoutofsight", 14, Map.of("sweepsOutOfSight", "4",
                    "heldWhileUnloaded", "2", "requestedAgain", "false", "consumerStone", "2", "providerStone", "0"));
            fixture.close();
        });
    }

    /**
     * Two jobs wait for stone from the same provider network: one on the consumer's overworld CPU, which is cancelled,
     * and one on its nether CPU, whose chunk unloads. Nothing on the consumer waits for stone now, yet the nether job's
     * returns are kept; it finishes once loaded, and the cancelled job's late stone stays on the provider network.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 1800)
    public static void projectionCpuOutOfSightOtherJob(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper, ConsumerCpus.BOTH);
        var stage = new int[1];
        var sweeps = new long[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks, the providers and both CPUs");
            if (stage[0] == 0) {
                fixture.enableRules();
                fixture.putInConsumer(cobblestone(), 4);
                stage[0] = 1;
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.consumerService().isCraftable(stone()), "Waiting for the projection");
                fixture.begin(stone(), 2);
                stage[0] = 2;
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the overworld job's plan");
                helper.assertTrue(fixture.submitOn(fixture.overworldCpu()), "The overworld CPU takes the first job");
                fixture.begin(stone(), 2);
                stage[0] = 3;
            }
            if (stage[0] == 3) {
                helper.assertTrue(fixture.planReady(), "Waiting for the nether job's plan");
                helper.assertTrue(fixture.submitOn(fixture.netherCpu()), "The nether CPU takes the second job");
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 4L, "Waiting for all four pushes");
                helper.assertValueEqual(fixture.owed(stone()), 4L, "Both jobs are owed their stone");
                helper.assertValueEqual(fixture.watchedJobs(), 2, "Both jobs are watched");
                fixture.overworldCpu().cancelJob();
                fixture.site().releaseTickets();
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertFalse(fixture.site().loaded(), "Waiting for the nether CPU's chunks to unload");
                helper.assertValueEqual(fixture.visibleConsumerCpus(), 1, "Only the overworld CPU is in sight");
                helper.assertFalse(fixture.overworldCpu().isBusy(), "The overworld job is cancelled");
                sweeps[0] = fixture.ledgerSweeps();
                helper.assertValueEqual(fixture.runMachine(4), 4L, "The provider network takes all four stone");
                stage[0] = 6;
            }
            if (stage[0] == 6) {
                helper.assertTrue(fixture.ledgerSweeps() - sweeps[0] >= SWEEPS_OUT_OF_SIGHT,
                        "Letting the ledger look while nothing in sight waits for stone");
                helper.assertValueEqual(fixture.owed(stone()), 4L,
                        "The out-of-sight job keeps the debt; " + fixture.returns(stone()));
                helper.assertValueEqual(fixture.heldForConsumer(stone()), 4L, "All four stone are held for the consumer");
                fixture.site().forceAgain();
                stage[0] = 7;
            }
            if (stage[0] == 7) {
                helper.assertTrue(fixture.site().ticking() && fixture.netherCpu() != null,
                        "Waiting for the nether CPU's chunks to load again");
                stage[0] = 8;
            }
            helper.assertValueEqual(fixture.busyConsumerCpus(), 0L,
                    "Waiting for the nether job to finish; " + fixture.returns(stone()));
            helper.assertValueEqual(fixture.owed(stone()), 0L,
                    "Waiting for the rest of the debt to be forgotten; " + fixture.returns(stone()));
            assertSettled(helper, fixture, 2, 2);
            PolicyEvidence.write("projectioncpuoutofsightotherjob", 15, Map.of("jobs", "2", "cancelled", "1",
                    "debtKeptOutOfSight", "4", "consumerStone", "2", "lateStoneOnProvider", "2"));
            fixture.close();
        });
    }

    /**
     * A restart while the CPU's chunk is unloaded: Federation's crafting service starts anew, its ledger is read back
     * from its saved form, and the overworld side reloads at once while the CPU stays unloaded for well past the first
     * sweeps. The stone arrives in that time, and the job finishes once the CPU loads.
     *
     * <p>Simulated restart: the overworld block entities reload in one tick ({@code BlockEntityReload}), the crafting
     * service is closed as the server's stop closes it, and the ledger goes through {@code save}/{@code load}. The
     * server itself keeps running, and the CPU's chunk really unloads and loads from its save.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", manualOnly = true,
            required = true, timeoutTicks = 2000)
    public static void projectionCpuLateAfterRestart(GameTestHelper helper) {
        var fixture = new PatternProjectionFixture(helper, ConsumerCpus.NETHER);
        var stage = new int[1];
        var sweeps = new long[1];
        helper.succeedWhen(() -> {
            if (stage[0] < 4 && pushedTwoStone(helper, fixture, stage, 2)) {
                fixture.site().releaseTickets();
                stage[0] = 4;
            }
            if (stage[0] == 4) {
                helper.assertFalse(fixture.site().loaded(), "Waiting for the CPU's chunks to unload");
                var level = helper.getLevel();
                var server = level.getServer();
                CraftingProjectionService.closeServer(server);
                var registries = level.registryAccess();
                var saved = CraftingReturnLedger.get(level).save(new CompoundTag(), registries);
                server.overworld().getDataStorage().set(CraftingReturnLedger.DATA_NAME,
                        CraftingReturnLedger.load(saved, registries));
                fixture.reloadAll();
                stage[0] = 5;
            }
            if (stage[0] == 5) {
                helper.assertTrue(fixture.reloadedWithoutConsumerCpu(), "Waiting for the overworld side to stand again");
                helper.assertTrue(fixture.providerRouted(), "Waiting for the new service's router");
                helper.assertValueEqual(fixture.owed(stone()), 2L, "The saved debt is back");
                helper.assertValueEqual(fixture.watchedJobs(), 1, "The saved job is watched again");
                sweeps[0] = fixture.ledgerSweeps();
                helper.assertValueEqual(fixture.runMachine(2), 2L, "The provider network takes the stone");
                stage[0] = 6;
            }
            if (stage[0] == 6) {
                helper.assertTrue(fixture.ledgerSweeps() - sweeps[0] >= SWEEPS_OUT_OF_SIGHT,
                        "Letting the new service look while the CPU is still unloaded");
                helper.assertFalse(fixture.site().loaded(), "The CPU stays unloaded throughout");
                helper.assertValueEqual(fixture.owed(stone()), 2L,
                        "The debt stays after the restart; " + fixture.returns(stone()));
                helper.assertValueEqual(fixture.heldForConsumer(stone()), 2L, "The stone that came back is held for it");
                fixture.site().forceAgain();
                stage[0] = 7;
            }
            if (stage[0] == 7) {
                helper.assertTrue(fixture.site().ticking() && fixture.netherCpu() != null,
                        "Waiting for the CPU's chunks to load again");
                stage[0] = 8;
            }
            helper.assertValueEqual(fixture.busyConsumerCpus(), 0L,
                    "Waiting for the restored job to finish; " + fixture.returns(stone()));
            assertSettled(helper, fixture, 2, 0);
            PolicyEvidence.write("projectioncpulateafterrestart", 14, Map.of("restartSimulated", "true",
                    "cpuChunkReallyUnloaded", "true", "debtAfterRestart", "2", "consumerStone", "2"));
            fixture.close();
        });
    }

    /**
     * Brings both networks up, turns the rules on and runs a two-stone job on the consumer until both pushes sit in the
     * hand-run machine; true once they do.
     */
    private static boolean pushedTwoStone(GameTestHelper helper, PatternProjectionFixture fixture, int[] stage,
            int cobblestone) {
        helper.assertTrue(stage[0] > 0 || fixture.ready(), "Waiting for both networks, the providers and the CPU");
        if (stage[0] == 0) {
            fixture.enableRules();
            fixture.putInConsumer(cobblestone(), cobblestone);
            stage[0] = 1;
        }
        if (stage[0] == 1) {
            helper.assertTrue(fixture.consumerService().isCraftable(stone()), "Waiting for the projection");
            fixture.begin(stone(), 2);
            stage[0] = 2;
        }
        if (stage[0] == 2) {
            helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
            helper.assertTrue(fixture.submit(), "The consumer's CPU must take the job");
            stage[0] = 3;
        }
        helper.assertValueEqual(fixture.inMachine(Items.COBBLESTONE), 2L, "Waiting for both pushes");
        helper.assertValueEqual(fixture.owed(stone()), 2L, "The provider network owes the consumer two stone");
        helper.assertValueEqual(fixture.watchedJobs(), 1, "The consumer's job is watched");
        return true;
    }

    /** Where every stone ended: none lost, none made twice, nothing owed, held or in transit. */
    private static void assertSettled(GameTestHelper helper, PatternProjectionFixture fixture, long consumerStone,
            long providerStone) {
        helper.assertValueEqual(fixture.held(fixture.consumerChest(), stone()), consumerStone,
                "The consumer stores its jobs' stone");
        helper.assertValueEqual(fixture.held(fixture.providerChest(), stone()), providerStone,
                "The provider network keeps only late stone of a cancelled job");
        helper.assertValueEqual(fixture.owed(stone()), 0L, "Nothing is owed afterwards");
        helper.assertValueEqual(fixture.heldForConsumer(stone()), 0L, "Nothing is held afterwards");
        helper.assertValueEqual(fixture.transit(stone()), 0L, "Nothing is in transit afterwards");
        helper.assertValueEqual(fixture.held(fixture.consumerChest(), cobblestone()), 0L, "The cobblestone was used");
    }
}
