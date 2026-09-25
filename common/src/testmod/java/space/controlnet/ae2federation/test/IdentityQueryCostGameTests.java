package space.controlnet.ae2federation.test;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.identity.IdentityStatus;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityService;

/**
 * Measures stable identity reads on live AE2 Grids and checks cross-Grid invalidation. Only public identity service API
 * is used for the measured path so the same test can run against earlier revisions for a before/after comparison;
 * index counters are read reflectively when the running build exposes them.
 */
@PrefixGameTestTemplate(false)
public final class IdentityQueryCostGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(IdentityQueryCostGameTests.class);
    private static final String STRUCTURE = "ae2federation_test:harness_native_smoke";
    private static final String DATA_KEY = "ae2federation_network_identity";
    private static final int MAIN_NODES = 256;
    private static final int OTHER_GRIDS = 32;
    private static final int OTHER_NODES = 32;
    private static final int READS = 2_000;
    private static final IGridNodeListener<Object> LISTENER = (owner, node) -> {
    };

    private IdentityQueryCostGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void identityStableQueryCost(GameTestHelper helper) {
        var created = new ArrayList<IManagedGridNode>();
        var mainNetwork = new NetworkId(UUID.randomUUID());
        var mainLineages = new ArrayList<UUID>();
        var main = grid(helper, created, mainNetwork, MAIN_NODES, mainLineages);
        for (int grid = 0; grid < OTHER_GRIDS; grid++) {
            grid(helper, created, new NetworkId(UUID.randomUUID()), OTHER_NODES, new ArrayList<>());
        }
        var service = main.getFirst().getGrid().getService(NetworkIdentityService.class);
        try {
            helper.assertValueEqual(service.settlement().status(), IdentityStatus.SETTLED,
                    "Seeded main Grid must settle before measurement");
            var before = counters(helper);
            var started = System.nanoTime();
            for (int read = 0; read < READS; read++) {
                service.settlement();
            }
            var elapsed = System.nanoTime() - started;
            var after = counters(helper);

            var copy = node(helper, created, lineageTag(mainNetwork, mainLineages.get(7)), 900);
            var copied = service.settlement().status();
            destroy(created, copy);
            var restoredAfterCopy = service.settlement().status();
            var splitNode = node(helper, created, lineageTag(mainNetwork, UUID.randomUUID()), 901);
            var split = service.settlement().status();
            destroy(created, splitNode);
            var restoredAfterSplit = service.settlement().status();

            helper.assertValueEqual(copied, IdentityStatus.COPIED_LIVE_IDENTITY,
                    "A copied node on another Grid must invalidate the unchanged main Grid");
            helper.assertValueEqual(restoredAfterCopy, IdentityStatus.SETTLED,
                    "Removing the copied node on the other Grid must re-settle the main Grid");
            helper.assertValueEqual(split, IdentityStatus.AMBIGUOUS_SPLIT,
                    "Another Grid claiming the same NetworkId must invalidate the main Grid");
            helper.assertValueEqual(restoredAfterSplit, IdentityStatus.SETTLED,
                    "Removing the split claimant must re-settle the main Grid");
            var facts = new LinkedHashMap<String, String>();
            facts.put("mainNodes", Integer.toString(MAIN_NODES));
            facts.put("otherGrids", Integer.toString(OTHER_GRIDS));
            facts.put("otherNodesPerGrid", Integer.toString(OTHER_NODES));
            facts.put("stableReads", Integer.toString(READS));
            facts.put("stableReadElapsedNanos", Long.toString(elapsed));
            facts.put("stableReadNanosPerCall", Long.toString(elapsed / READS));
            facts.put("indexCounters", Boolean.toString(before != null));
            if (before != null) {
                facts.put("stableSettlementComputations", Long.toString(after[0] - before[0]));
                facts.put("stableLineageLookups", Long.toString(after[1] - before[1]));
                facts.put("stableCachedReads", Long.toString(after[2] - before[2]));
                helper.assertValueEqual(after[0] - before[0], 0L, "Stable reads must not recompute settlement");
                helper.assertValueEqual(after[1] - before[1], 0L, "Stable reads must not scan lineages");
            }
            facts.put("crossGridCopied", copied.name());
            facts.put("crossGridCopyRestored", restoredAfterCopy.name());
            facts.put("crossGridSplit", split.name());
            facts.put("crossGridSplitRestored", restoredAfterSplit.name());
            writeEvidence("identitystablequerycost", before != null ? 6 : 4, facts);
            helper.succeed();
        } finally {
            created.forEach(IManagedGridNode::destroy);
        }
    }

    private static List<IManagedGridNode> grid(GameTestHelper helper, List<IManagedGridNode> created,
            NetworkId network, int size, List<UUID> nodeIds) {
        var nodes = new ArrayList<IManagedGridNode>(size);
        for (int index = 0; index < size; index++) {
            var nodeId = UUID.randomUUID();
            nodeIds.add(nodeId);
            var node = node(helper, created, lineageTag(network, nodeId), created.size());
            if (!nodes.isEmpty()) {
                GridHelper.createConnection(nodes.getLast().getNode(), node.getNode());
            }
            nodes.add(node);
        }
        return nodes;
    }

    private static IManagedGridNode node(GameTestHelper helper, List<IManagedGridNode> created, CompoundTag seed,
            int index) {
        var owner = new Object();
        var node = GridHelper.createManagedNode(owner, LISTENER).setTagName("cost").setInWorldNode(false)
                .setIdlePowerUsage(0);
        node.loadFromNBT(seed);
        node.create(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)));
        created.add(node);
        return node;
    }

    private static void destroy(List<IManagedGridNode> created, IManagedGridNode node) {
        node.destroy();
        created.remove(node);
    }

    private static CompoundTag lineageTag(NetworkId network, UUID nodeId) {
        var identity = new CompoundTag();
        identity.putInt("schema", 1);
        identity.putUUID("network", network.value());
        identity.putUUID("node", nodeId);
        identity.putLong("revision", 1);
        var node = new CompoundTag();
        node.put(DATA_KEY, identity);
        var root = new CompoundTag();
        root.put("cost", node);
        return root;
    }

    private static long[] counters(GameTestHelper helper) {
        try {
            var registry = Class.forName("space.controlnet.ae2federation.identity.NetworkIdentityRegistry")
                    .getMethod("get", net.minecraft.server.level.ServerLevel.class).invoke(null, helper.getLevel());
            var index = registry.getClass().getMethod("claimIndex").invoke(registry);
            return new long[] { (long) index.getClass().getMethod("settlementComputations").invoke(index),
                    (long) index.getClass().getMethod("lineageLookups").invoke(index),
                    (long) index.getClass().getMethod("cachedReads").invoke(index) };
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    private static void writeEvidence(String testId, int assertions, Map<String, String> facts) {
        facts.forEach((name, value) -> LOGGER.info("AE2F_IDENTITY_COST testId={} fact={} value={}", testId, name,
                value));
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (configured.isBlank()) {
            return;
        }
        var path = Path.of(configured).toAbsolutePath().normalize();
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        var properties = new Properties();
        properties.setProperty("schemaVersion", "1");
        properties.setProperty("status", "passed");
        properties.setProperty("kind", "verify");
        properties.setProperty("testId", testId);
        properties.setProperty("structure", STRUCTURE);
        properties.setProperty("assertions", Integer.toString(assertions));
        properties.setProperty("operations", Integer.toString(READS));
        properties.setProperty("inserted", "0");
        properties.setProperty("extracted", "0");
        properties.setProperty("elapsedNanos", facts.getOrDefault("stableReadElapsedNanos", "0"));
        facts.forEach(properties::setProperty);
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "AE2 Federation identity query cost evidence");
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write identity cost evidence to " + path, exception);
        }
    }
}
