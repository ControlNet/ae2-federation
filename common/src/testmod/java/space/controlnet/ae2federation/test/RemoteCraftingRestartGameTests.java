package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import space.controlnet.ae2federation.crafting.remote.RemoteCraftingService;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.test.crafting.CraftingBindingFixture;
import space.controlnet.ae2federation.test.crafting.NativeCraftingEvidence;
import space.controlnet.ae2federation.test.world.RestartChunkTickets;

/**
 * A remote crafting job across an actual server restart. The prepare run builds the two networks at its test
 * position and keeps their chunks loaded with a persistent NeoForge ticket (saved with the world, so the next server
 * loads them at start, while its own test is placed elsewhere), starts a consumer job and holds the provider's job,
 * then the server saves and stops. The verify run loads that world in a new process and
 * only observes and resumes: the binding, the consumer's listing, both CPUs' jobs and the requester's link must come
 * back from the save alone, and the job must finish once without being requested again.
 */
@PrefixGameTestTemplate(false)
public final class RemoteCraftingRestartGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();

    private RemoteCraftingRestartGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 1600, required = true, manualOnly = true)
    public static void craftingRemoteRestart(GameTestHelper helper) {
        switch (System.getProperty("ae2federation.craftingPhase", "")) {
            case "prepare" -> prepare(helper);
            case "verify" -> verify(helper);
            default -> helper.fail("Unknown crafting restart phase");
        }
    }

    private static void prepare(GameTestHelper helper) {
        var fixture = new CraftingBindingFixture(helper, true);
        var chunks = fixture.chunks();
        var ticketOwner = helper.absolutePos(BlockPos.ZERO);
        RestartChunkTickets.force(helper.getLevel(), ticketOwner, chunks, true);
        var stage = new int[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(fixture.ready(), "Waiting for native Crafting topology: " + fixture.readinessState());
            if (stage[0] == 0) {
                fixture.enable();
                fixture.insertMaterials(2);
                fixture.addConsumerCpu();
                stage[0] = 1;
                helper.assertTrue(false, "Waiting for the consumer CPU");
            }
            if (stage[0] == 1) {
                helper.assertTrue(fixture.consumerCpuReady(), "Waiting for the consumer CPU to join its Grid");
                helper.assertTrue(fixture.consumerService().getCraftables(key -> true)
                                .contains(CraftingBindingFixture.outputKey()),
                        "Waiting for the consumer to list the provider's craftable output");
                fixture.beginOnConsumer(4);
                stage[0] = 2;
                helper.assertTrue(false, "Waiting for the consumer's plan");
            }
            if (stage[0] == 2) {
                helper.assertTrue(fixture.planReady(), "Waiting for the consumer's plan");
                helper.assertTrue(fixture.submitOnConsumer(), "The consumer must start the job on its own CPU");
                stage[0] = 3;
                helper.assertTrue(false, "Waiting for the provider job");
            }
            helper.assertValueEqual(fixture.busyCpuCount(), 1L, "Waiting for the provider job to start");
            // AE2 saves a suspended job as suspended, so it is still running when the server stops.
            fixture.suspendCpu();
            var state = new Properties();
            put(state, "providerCpu", helper.absolutePos(fixture.providerCpuPos()));
            put(state, "consumerCpu", helper.absolutePos(fixture.consumerCpuPos()));
            put(state, "providerChest", helper.absolutePos(fixture.providerChestPos()));
            put(state, "consumerChest", helper.absolutePos(fixture.consumerChestPos()));
            state.setProperty("consumerNetwork", fixture.key().consumerNetworkId().value().toString());
            state.setProperty("providerNetwork", fixture.key().providerNetworkId().value().toString());
            put(state, "ticketOwner", ticketOwner);
            state.setProperty("ticketChunks", String.join(";", chunks.stream()
                    .map(chunk -> chunk.x + ":" + chunk.z).toList()));
            var processId = Long.toString(ProcessHandle.current().pid());
            state.setProperty("prepareProcessId", processId);
            writeState(state);
            LOGGER.info("AE2F_CRAFT_RESTART_PREPARE testId=craftingremoterestart processId={}", processId);
        });
    }

    private static void verify(GameTestHelper helper) {
        var state = readState();
        var providerCpu = position(state, "providerCpu");
        var consumerCpu = position(state, "consumerCpu");
        var providerChest = position(state, "providerChest");
        var consumerChest = position(state, "consumerChest");
        var key = new PolicyKey(new NetworkId(UUID.fromString(state.getProperty("consumerNetwork"))),
                new NetworkId(UUID.fromString(state.getProperty("providerNetwork"))), PolicyCapability.CRAFTING);
        var prepareProcessId = state.getProperty("prepareProcessId");
        var verifyProcessId = Long.toString(ProcessHandle.current().pid());
        var startedAt = new long[] { -1 };
        var resumed = new boolean[1];
        helper.succeedWhen(() -> {
            if (startedAt[0] < 0) startedAt[0] = helper.getTick();
            helper.assertTrue(prepareProcessId != null && !prepareProcessId.equals(verifyProcessId),
                    "The verify run must be a new server process");
            if (!resumed[0]) {
                // Past AE2's 60-tick window for a missing requester and the service's startup window.
                helper.assertTrue(helper.getTick() >= startedAt[0] + 200, "Waiting through the restart window");
                var consumerGrid = chest(helper, consumerChest).getMainNode().getGrid();
                helper.assertTrue(consumerGrid != null, "The consumer's chest must rejoin a Grid after the restart");
                helper.assertTrue(consumerGrid.getCraftingService()
                                .getCraftables(candidate -> true).contains(CraftingBindingFixture.outputKey()),
                        "The consumer must list the provider's craftable output again after the restart");
                helper.assertTrue(busy(helper, providerCpu), "The provider CPU must restore its job");
                helper.assertTrue(busy(helper, consumerCpu), "The consumer CPU must restore its job");
                helper.assertValueEqual(RemoteCraftingService.get(helper.getLevel()).submissionCount(key), 0,
                        "The restarted server must not request the job again");
                cluster(helper, providerCpu).craftingLogic.setJobSuspended(false);
                resumed[0] = true;
                helper.assertTrue(false, "Waiting for the resumed provider job");
            }
            var delivered = amount(helper, consumerChest, CraftingBindingFixture.outputKey());
            helper.assertValueEqual(delivered, 4L, "Four sticks must arrive after the restart");
            var material = amount(helper, providerChest, CraftingBindingFixture.inputKey());
            var residue = amount(helper, providerChest, CraftingBindingFixture.outputKey());
            helper.assertValueEqual(material, 0L, "Only the job's two planks may be used");
            helper.assertValueEqual(residue, 0L, "No sticks may stay with the provider");
            helper.assertTrue(!busy(helper, providerCpu) && !busy(helper, consumerCpu), "Both jobs must finish");
            var resubmissions = RemoteCraftingService.get(helper.getLevel()).submissionCount(key);
            helper.assertValueEqual(resubmissions, 0, "The restarted server must not request the job again");
            NativeCraftingEvidence.write("craftingremoterestart", 12, Map.of(
                    "prepareProcessId", prepareProcessId,
                    "verifyProcessId", verifyProcessId,
                    "resultInserted", Long.toString(delivered),
                    "providerMaterialAfter", Long.toString(material),
                    "providerResidue", Long.toString(residue),
                    "resubmissions", Integer.toString(resubmissions),
                    "busyCpus", Integer.toString((busy(helper, providerCpu) ? 1 : 0) + (busy(helper, consumerCpu) ? 1 : 0))));
            var chunks = new java.util.ArrayList<ChunkPos>();
            for (var chunk : state.getProperty("ticketChunks").split(";")) {
                var parts = chunk.split(":");
                chunks.add(new ChunkPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1])));
            }
            RestartChunkTickets.force(helper.getLevel(), position(state, "ticketOwner"), chunks, false);
        });
    }

    private static boolean busy(GameTestHelper helper, BlockPos position) {
        var cluster = cluster(helper, position);
        return cluster != null && cluster.isBusy();
    }

    private static CraftingCPUCluster cluster(GameTestHelper helper, BlockPos position) {
        helper.assertTrue(helper.getLevel().getBlockEntity(position) instanceof CraftingBlockEntity,
                "Expected a crafting CPU at " + position.toShortString());
        return ((CraftingBlockEntity) helper.getLevel().getBlockEntity(position)).getCluster();
    }

    private static MEChestBlockEntity chest(GameTestHelper helper, BlockPos position) {
        helper.assertTrue(helper.getLevel().getBlockEntity(position) instanceof MEChestBlockEntity,
                "Expected an ME chest at " + position.toShortString());
        return (MEChestBlockEntity) helper.getLevel().getBlockEntity(position);
    }

    /** The chest's own cell, not its Grid, so nothing mounted from another network is counted. */
    private static long amount(GameTestHelper helper, BlockPos chest, AEKey key) {
        var cell = chest(helper, chest).getOriginalCellInventory(0);
        helper.assertTrue(cell != null, "Expected a storage cell in the chest at " + chest.toShortString());
        return cell.extract(key, Long.MAX_VALUE, Actionable.SIMULATE, IActionSource.empty());
    }

    private static void put(Properties state, String name, BlockPos position) {
        state.setProperty(name, position.getX() + "," + position.getY() + "," + position.getZ());
    }

    private static BlockPos position(Properties state, String name) {
        var parts = state.getProperty(name).split(",");
        return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    private static Path stateFile() {
        var configured = System.getProperty("ae2federation.craftingStateFile", "");
        if (configured.isBlank()) throw new IllegalStateException("Missing crafting restart state file");
        return Path.of(configured).toAbsolutePath().normalize();
    }

    private static void writeState(Properties state) {
        var path = stateFile();
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(path)) {
                state.store(output, "AE2 Federation remote crafting restart state");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write crafting restart state to " + path, exception);
        }
    }

    private static Properties readState() {
        var state = new Properties();
        try (var input = Files.newInputStream(stateFile())) {
            state.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read crafting restart state", exception);
        }
        return state;
    }
}
