package space.controlnet.ae2federation.test;

import appeng.api.networking.GridHelper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.EnumMap;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.ae2.NativeAttachmentResolver;
import space.controlnet.ae2federation.fabric.port.HubBoundaryTopology;
import space.controlnet.ae2federation.fabric.port.HubBoundaryTopology.BoundaryPort;
import space.controlnet.ae2federation.test.port.NativePortFixtures;

@PrefixGameTestTemplate(false)
public final class PortGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger(PortGameTests.class);
    private static final String STRUCTURE = "ae2federation_test:harness_native_smoke";

    private PortGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void portsBridgeCableDevice(GameTestHelper helper) {
        var fixtures = new NativePortFixtures(helper);
        var cableBoundary = new BlockPos(1, 1, 1);
        var deviceBoundary = new BlockPos(1, 1, 4);
        fixtures.placeCable(cableBoundary.east());
        fixtures.placeChest(deviceBoundary.east());
        var created = new boolean[] { false };
        var cableNode = new appeng.api.networking.IGridNode[1];
        var deviceNode = new appeng.api.networking.IGridNode[1];
        helper.succeedWhen(() -> {
            fixtures.exposedNode(cableBoundary.east(), Direction.WEST);
            fixtures.exposedNode(deviceBoundary.east(), Direction.WEST);
            if (!created[0]) {
                cableNode[0] = fixtures.createBoundary(Direction.EAST, cableBoundary);
                deviceNode[0] = fixtures.createBoundary(Direction.EAST, deviceBoundary);
                created[0] = true;
            }
            var cable = NativeAttachmentResolver.resolve(helper.getLevel(), helper.absolutePos(cableBoundary),
                    Direction.EAST, cableNode[0]);
            var device = NativeAttachmentResolver.resolve(helper.getLevel(), helper.absolutePos(deviceBoundary),
                    Direction.EAST, deviceNode[0]);
            helper.assertTrue(cable.isPresent(), "Bridge boundary must accept a real native cable attachment");
            helper.assertTrue(device.isPresent(), "Bridge boundary must accept a real native device attachment");
            helper.assertTrue(cable.orElseThrow().neighborNode().getOwner().getClass()
                    != device.orElseThrow().neighborNode().getOwner().getClass(),
                    "Cable and device acceptance must not be a class whitelist alias");
            writeEvidence("portsbridgecabledevice", 6, Map.of(
                    "cableAttached", "true", "deviceAttached", "true",
                    "cableGrid", identity(cable.orElseThrow().grid()),
                    "deviceGrid", identity(device.orElseThrow().grid()),
                    "attachmentPolicy", "native-exposed-edge"));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void portsHubSixGrids(GameTestHelper helper) {
        hubCase(helper, false);
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void portsHubRepeatedGrid(GameTestHelper helper) {
        hubCase(helper, true);
    }

    private static void hubCase(GameTestHelper helper, boolean repeatFirstGrid) {
        var fixtures = new NativePortFixtures(helper);
        var center = new BlockPos(6, 6, 6);
        for (var face : Direction.values()) {
            fixtures.placeChest(center.relative(face, 2));
        }
        var connected = new boolean[] { false };
        var ports = new EnumMap<Direction, BoundaryPort>(Direction.class);
        helper.succeedWhen(() -> {
            for (var face : Direction.values()) {
                fixtures.chestNode(center.relative(face, 2));
            }
            if (repeatFirstGrid && !connected[0]) {
                GridHelper.createConnection(fixtures.chestNode(center.relative(Direction.NORTH, 2)),
                        fixtures.chestNode(center.relative(Direction.SOUTH, 2)));
                connected[0] = true;
                helper.assertTrue(false, "Waiting for native repeated Grid connection to settle");
            }
            if (ports.isEmpty()) {
                for (var face : Direction.values()) {
                    var boundaryPosition = center.relative(face);
                    ports.put(face, new BoundaryPort(helper.absolutePos(boundaryPosition),
                            fixtures.createBoundary(face, boundaryPosition)));
                }
            }
            var topology = HubBoundaryTopology.resolve(helper.getLevel(), ports).orElseThrow();
            var distinctGrids = topology.facesByGrid().size();
            helper.assertValueEqual(topology.attachments().size(), 6, "Hub must retain six independent face records");
            helper.assertValueEqual(distinctGrids, repeatFirstGrid ? 5 : 6,
                    "Hub native Grid identities must be deduplicatable without collapsing face ownership");
            helper.assertTrue(topology.attachments().values().stream()
                    .allMatch(attachment -> attachment.boundaryNode().getConnections().size() == 1),
                    "Every Hub boundary node must own exactly one external native edge");
            var facts = new java.util.LinkedHashMap<String, String>();
            facts.put("faceCount", "6");
            facts.put("distinctGridCount", Integer.toString(distinctGrids));
            facts.put("deduplicated", Boolean.toString(repeatFirstGrid));
            for (var face : Direction.values()) {
                facts.put("face." + face.getSerializedName(), identity(topology.attachments().get(face).grid()));
            }
            writeEvidence(repeatFirstGrid ? "portshubrepeatedgrid" : "portshubsixgrids",
                    repeatFirstGrid ? 7 : 8, facts);
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void portsRejectFloatingNode(GameTestHelper helper) {
        var fixtures = new NativePortFixtures(helper);
        var floatingPosition = new BlockPos(1, 1, 1);
        var unsupportedPosition = new BlockPos(1, 1, 4);
        var ambiguousPosition = new BlockPos(1, 1, 7);
        var replacedPosition = new BlockPos(4, 1, 1);
        fixtures.placeChest(ambiguousPosition.east());
        fixtures.placeChest(ambiguousPosition.east(3));
        fixtures.placeChest(replacedPosition.east());
        helper.setBlock(unsupportedPosition.east(), Blocks.STONE);
        var floating = fixtures.createBoundary(Direction.EAST, floatingPosition);
        var unsupported = fixtures.createBoundary(Direction.EAST, unsupportedPosition);
        var ambiguous = new appeng.api.networking.IGridNode[1];
        var replaced = new appeng.api.networking.IGridNode[1];
        var connected = new boolean[] { false };
        var replacementRemoved = new boolean[] { false };
        helper.succeedWhen(() -> {
            fixtures.chestNode(ambiguousPosition.east());
            fixtures.chestNode(ambiguousPosition.east(3));
            if (!replacementRemoved[0]) {
                fixtures.chestNode(replacedPosition.east());
                if (replaced[0] == null) {
                    replaced[0] = fixtures.createBoundary(Direction.EAST, replacedPosition);
                }
                helper.assertTrue(NativeAttachmentResolver.resolve(helper.getLevel(), helper.absolutePos(replacedPosition),
                        Direction.EAST, replaced[0]).isPresent(),
                        "Replacement fixture must begin as a valid native attachment");
                helper.setBlock(replacedPosition.east(), Blocks.AIR);
                replacementRemoved[0] = true;
                helper.assertTrue(false, "Waiting for replaced native host removal to settle");
            }
            if (ambiguous[0] == null) {
                ambiguous[0] = fixtures.createBoundary(Direction.EAST, ambiguousPosition);
            }
            if (!connected[0] && ambiguous[0].getInWorldConnections().containsKey(Direction.EAST)) {
                GridHelper.createConnection(ambiguous[0], fixtures.chestNode(ambiguousPosition.east(3)));
                connected[0] = true;
            }
            helper.assertTrue(floating.getGrid() != null, "Floating node fixture must own an allocated native Grid");
            helper.assertTrue(NativeAttachmentResolver.resolve(helper.getLevel(), helper.absolutePos(floatingPosition),
                    Direction.EAST, floating).isEmpty(), "Allocated floating outer node must fail closed");
            helper.assertTrue(NativeAttachmentResolver.resolve(helper.getLevel(), helper.absolutePos(unsupportedPosition),
                    Direction.EAST, unsupported).isEmpty(), "Unsupported neighbor must fail closed");
            helper.assertTrue(connected[0], "Ambiguous fixture must contain an additional native edge");
            helper.assertTrue(NativeAttachmentResolver.resolve(helper.getLevel(), helper.absolutePos(ambiguousPosition),
                    Direction.EAST, ambiguous[0]).isEmpty(), "Ambiguous multi-edge boundary must fail closed");
            helper.assertTrue(NativeAttachmentResolver.resolve(helper.getLevel(), helper.absolutePos(replacedPosition),
                    Direction.EAST, replaced[0]).isEmpty(), "Replaced native neighbor must fail closed");
            writeEvidence("portsrejectfloatingnode", 7, Map.of(
                    "floatingGridAllocated", "true", "floatingAccepted", "false",
                    "unsupportedAccepted", "false", "ambiguousAccepted", "false",
                    "replacedInitiallyAttached", "true", "replacedAccepted", "false"));
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void portsRejectCrossGridJoin(GameTestHelper helper) {
        var fixtures = new NativePortFixtures(helper);
        var center = new BlockPos(6, 6, 6);
        for (var face : Direction.values()) {
            fixtures.placeChest(center.relative(face, 2));
        }
        var ports = new EnumMap<Direction, BoundaryPort>(Direction.class);
        helper.succeedWhen(() -> {
            for (var face : Direction.values()) {
                fixtures.chestNode(center.relative(face, 2));
            }
            if (ports.isEmpty()) {
                for (var face : Direction.values()) {
                    var boundaryPosition = center.relative(face);
                    ports.put(face, new BoundaryPort(helper.absolutePos(boundaryPosition),
                            fixtures.createBoundary(face, boundaryPosition)));
                }
            }
            var resolved = HubBoundaryTopology.resolve(helper.getLevel(), ports);
            helper.assertTrue(resolved.isPresent(), "Hub boundary topology must finish native attachment discovery");
            var topology = resolved.orElseThrow();
            var northGrid = topology.attachments().get(Direction.NORTH).grid();
            var southGrid = topology.attachments().get(Direction.SOUTH).grid();
            helper.assertTrue(northGrid != southGrid, "Cross-grid fixture must begin with distinct native Grids");
            helper.assertTrue(!topology.permitsNativeJoin(Direction.NORTH, Direction.SOUTH),
                    "Hub must reject a requested native join between boundary faces");
            helper.assertTrue(topology.attachments().get(Direction.NORTH).boundaryNode().getGrid() != topology
                    .attachments().get(Direction.SOUTH).boundaryNode().getGrid(),
                    "Rejected join must leave native Grid identities distinct");
            writeEvidence("portsrejectcrossgridjoin", 6, Map.of(
                    "joinRequested", "true", "joinAccepted", "false", "gridsRemainDistinct", "true",
                    "northGrid", identity(northGrid), "southGrid", identity(southGrid)));
            fixtures.close();
        });
    }

    private static String identity(Object value) {
        return Integer.toUnsignedString(System.identityHashCode(value));
    }

    private static void writeEvidence(String testId, int assertions, Map<String, String> facts) {
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
        properties.setProperty("operations", "1");
        properties.setProperty("inserted", "0");
        properties.setProperty("extracted", "0");
        properties.setProperty("elapsedNanos", "0");
        var orderedFacts = new TreeMap<>(facts);
        orderedFacts.forEach((fact, value) -> {
            properties.setProperty(fact, value);
            LOGGER.info("AE2F_PORT_TRACE testId={} fact={} value={}", testId, fact, value);
        });
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, "AE2 Federation native port topology evidence");
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write native port evidence to " + path, exception);
        }
    }
}
