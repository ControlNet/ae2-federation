package space.controlnet.ae2federation.test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.persistence.FederationDomainSavedData;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.domain.FederationDomainEvidence;
import space.controlnet.ae2federation.test.world.RestartChunkTickets;

/**
 * A Federation domain keeps its id across a server restart. The first process builds a cable domain, which takes the
 * first id, then Router, cable, cable, Router, breaks the first domain and saves; the second process loads the Routers'
 * chunks and finds their domain under its old id, which a renumbering registry would have given the first id instead.
 */
@PrefixGameTestTemplate(false)
public final class DomainIdRestartGameTests {
    private static final String TEST_ID = "domainidrestart";
    /** Outside the 3x3x3 template, so nothing the runner clears around a test touches the fixture. */
    private static final BlockPos FIRST = new BlockPos(5, 3, 5);
    private static final BlockPos DECOY = new BlockPos(5, 3, 8);

    private DomainIdRestartGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void domainIdRestart(GameTestHelper helper) {
        switch (System.getProperty("ae2federation.domainIdPhase", "")) {
            case "prepare" -> prepare(helper);
            case "verify" -> verify(helper);
            default -> helper.fail("Unknown domain id restart phase");
        }
    }

    private static void prepare(GameTestHelper helper) {
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var line = List.of(FIRST, FIRST.east(), FIRST.east(2), FIRST.east(3)).stream().map(helper::absolutePos).toList();
        var decoy = helper.absolutePos(DECOY);
        var phase = new int[1];
        var decoyId = new FederationDomainId[1];
        helper.setBlock(DECOY, RouterRegistration.FEDERATION_CABLE.get());
        helper.succeedWhen(() -> {
            if (phase[0] == 0) {
                decoyId[0] = registry.federationDomainOf(node(helper, decoy))
                        .orElseThrow(() -> new GameTestAssertException("Waiting for the first domain")).federationDomainId();
                helper.setBlock(FIRST, RouterRegistration.ROUTER.get());
                helper.setBlock(FIRST.east(), RouterRegistration.FEDERATION_CABLE.get());
                helper.setBlock(FIRST.east(2), RouterRegistration.FEDERATION_CABLE.get());
                helper.setBlock(FIRST.east(3), RouterRegistration.ROUTER.get());
                phase[0] = 1;
            }
            var saved = joined(helper, line);
            if (phase[0] == 1) {
                helper.setBlock(DECOY, Blocks.AIR);
                phase[0] = 2;
            }
            var chunks = line.stream().map(ChunkPos::new).collect(Collectors.toSet());
            RestartChunkTickets.force(helper.getLevel(), line.getFirst(), chunks, true);
            var state = new Properties();
            state.setProperty("positions", line.stream().map(position -> Long.toString(position.asLong()))
                    .collect(Collectors.joining(",")));
            state.setProperty("domainId", saved.value());
            state.setProperty("decoyId", decoyId[0].value());
            state.setProperty("prepareProcessId", Long.toString(ProcessHandle.current().pid()));
            write(state);
        });
    }

    private static void verify(GameTestHelper helper) {
        var state = read();
        var line = Arrays.stream(state.getProperty("positions").split(",")).map(Long::parseLong).map(BlockPos::of).toList();
        var saved = new FederationDomainId(state.getProperty("domainId"));
        var decoyId = new FederationDomainId(state.getProperty("decoyId"));
        var records = FederationDomainSavedData.get(helper.getLevel().getServer()).records();
        var fresh = new BlockPos(5, 3, 8);
        var phase = new int[1];
        helper.succeedWhen(() -> {
            var restored = joined(helper, line);
            if (phase[0] == 0) {
                helper.setBlock(fresh, RouterRegistration.FEDERATION_CABLE.get());
                phase[0] = 1;
            }
            var added = FederationDomainRegistryAccess.get(helper.getLevel())
                    .federationDomainOf(node(helper, helper.absolutePos(fresh)))
                    .orElseThrow(() -> new GameTestAssertException("Waiting for the new cable's domain")).federationDomainId();
            var verifyProcessId = ProcessHandle.current().pid();
            helper.assertTrue(verifyProcessId != Long.parseLong(state.getProperty("prepareProcessId")),
                    "Restart phases must use distinct JVM processes");
            helper.assertTrue(!saved.equals(FederationDomainId.physical(1)),
                    "The saved domain must not hold the id a renumbering registry gives first");
            helper.assertValueEqual(restored, saved, "The domain must keep its id across the restart");
            var anchors = records.anchors(sequence(saved)).orElseThrow().devices();
            helper.assertValueEqual(anchors, Set.of(node(helper, line.getFirst()), node(helper, line.getLast())),
                    "The saved domain is anchored by its two Routers");
            helper.assertTrue(records.anchors(sequence(decoyId)).isEmpty(),
                    "The broken domain's record must be gone");
            helper.assertTrue(sequence(added) > sequence(saved) && sequence(added) > sequence(decoyId),
                    "A new domain must take an id after every saved one");
            helper.assertTrue(records.sequence() >= sequence(added), "The saved sequence follows new ids");
            RestartChunkTickets.force(helper.getLevel(), line.getFirst(),
                    line.stream().map(ChunkPos::new).collect(Collectors.toSet()), false);
            FederationDomainEvidence.write(TEST_ID, 7, Map.of("restoredId", restored.value(), "savedId", saved.value(),
                    "decoyId", decoyId.value(), "newId", added.value(), "decoyRecordDropped", "true",
                    "prepareProcessId", state.getProperty("prepareProcessId"),
                    "verifyProcessId", Long.toString(verifyProcessId)));
        });
    }

    /** The id of the one domain that holds every node of {@code line}; waits while they are not joined yet. */
    private static FederationDomainId joined(GameTestHelper helper, List<BlockPos> line) {
        var registry = FederationDomainRegistryAccess.get(helper.getLevel());
        var domain = registry.federationDomainOf(node(helper, line.getFirst()))
                .orElseThrow(() -> new GameTestAssertException("Waiting for the Routers' domain"));
        if (!line.stream().allMatch(position -> domain.nodes().contains(node(helper, position)))) {
            throw new GameTestAssertException("Waiting for the Routers and cables to join");
        }
        return domain.federationDomainId();
    }

    private static FederationDomainNodeId node(GameTestHelper helper, BlockPos absolute) {
        return FederationDomainRegistryAccess.nodeId(helper.getLevel(), absolute);
    }

    private static long sequence(FederationDomainId id) {
        return Long.parseLong(id.value().substring("physical:".length()));
    }

    private static void write(Properties state) {
        try {
            Files.createDirectories(path().getParent());
            try (var output = Files.newOutputStream(path())) {
                state.store(output, "AE2 Federation domain id restart state");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write domain id restart state", exception);
        }
    }

    private static Properties read() {
        var state = new Properties();
        try (var input = Files.newInputStream(path())) {
            state.load(input);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read domain id restart state", exception);
        }
        return state;
    }

    private static Path path() {
        var configured = System.getProperty("ae2federation.domainIdStateFile", "");
        if (configured.isBlank()) {
            throw new IllegalStateException("Missing ae2federation.domainIdStateFile");
        }
        return Path.of(configured).toAbsolutePath().normalize();
    }
}
