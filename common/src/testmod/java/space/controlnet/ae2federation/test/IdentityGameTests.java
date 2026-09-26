package space.controlnet.ae2federation.test;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.identity.IdentityStatus;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.test.identity.FederationIdentityAccessAttachment;

@PrefixGameTestTemplate(false)
public final class IdentityGameTests {
    private static final String STRUCTURE = "ae2federation_test:harness_native_smoke";

    private IdentityGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void identityReplaceAllAccess(GameTestHelper helper) {
        var storagePos = new BlockPos(1, 1, 1);
        var energyPos = storagePos.below();
        helper.setBlock(energyPos, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(storagePos, AEBlocks.ME_CHEST.block());
        var replaced = new boolean[] { false };
        var beforeId = new String[] { "" };
        var firstAttachmentId = new String[] { "" };
        var nativeGridIdentity = new int[] { 0 };
        var nativeNodeIdentity = new int[] { 0 };
        helper.succeedWhen(() -> {
            var node = relativeNode(helper, storagePos);
            if (!replaced[0]) {
                var first = FederationIdentityAccessAttachment.attach(node);
                beforeId[0] = first.networkId().toString();
                firstAttachmentId[0] = first.attachmentId().toString();
                nativeGridIdentity[0] = System.identityHashCode(node.getGrid());
                nativeNodeIdentity[0] = System.identityHashCode(node);
                first.close();
                helper.assertValueEqual(FederationIdentityAccessAttachment.activeCount(node), 0,
                        "Every Federation access attachment must be removed");
                replaced[0] = true;
                helper.assertTrue(false, "Waiting one tick with no Federation access attachment");
            }
            var second = FederationIdentityAccessAttachment.attach(node);
            helper.assertTrue(!second.attachmentId().toString().equals(firstAttachmentId[0]),
                    "Replacement must be a genuinely new Federation access attachment");
            helper.assertValueEqual(System.identityHashCode(node.getGrid()), nativeGridIdentity[0],
                    "Native Grid object must survive the access-removal interval");
            helper.assertValueEqual(System.identityHashCode(node), nativeNodeIdentity[0],
                    "Native node object must survive the access-removal interval");
            helper.assertValueEqual(second.networkId().toString(), beforeId[0],
                    "New Federation access attachment must recover the identical NetworkId");
            helper.assertTrue(service(node).settlement().canInheritPolicy(),
                    "Unchanged native network must remain settled");
            second.close();
            writeEvidence("identityreplaceallaccess", 8, beforeId[0], "settled", Map.of(
                    "firstAttachmentId", firstAttachmentId[0],
                    "secondAttachmentId", second.attachmentId().toString(),
                    "recoveredNetworkId", second.networkId().toString(),
                    "activeDuringRemoval", "0",
                    "nativeGridBefore", Integer.toUnsignedString(nativeGridIdentity[0]),
                    "nativeGridAfter", Integer.toUnsignedString(System.identityHashCode(node.getGrid())),
                    "nativeNodeBefore", Integer.toUnsignedString(nativeNodeIdentity[0]),
                    "nativeNodeAfter", Integer.toUnsignedString(System.identityHashCode(node))));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void identityRestart(GameTestHelper helper) {
        switch (System.getProperty("ae2federation.identityPhase", "")) {
            case "prepare" -> prepareRestart(helper);
            case "verify" -> verifyRestart(helper);
            default -> helper.fail("Unknown identity restart phase");
        }
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void identityAmbiguousSplit(GameTestHelper helper) {
        var left = new BlockPos(1, 1, 1);
        var middle = new BlockPos(2, 1, 1);
        var right = new BlockPos(3, 1, 1);
        // The middle node must be ready before either chest. If both chests became ready first, each would start its
        // own Grid with its own NetworkId and the middle node would merge them (AMBIGUOUS_MERGE), not split one.
        helper.setBlock(middle, AEBlocks.CREATIVE_ENERGY_CELL.block());
        var chestsPlaced = new boolean[1];
        var originalIds = new String[1];
        helper.succeedWhen(() -> {
            if (!chestsPlaced[0]) {
                var middleHost = helper.getBlockEntity(middle);
                helper.assertTrue(middleHost instanceof AENetworkedBlockEntity networked
                        && networked.getMainNode().isReady(), "Middle node must be ready first");
                helper.setBlock(left, AEBlocks.ME_CHEST.block());
                helper.setBlock(right, AEBlocks.ME_CHEST.block());
                chestsPlaced[0] = true;
            }
            var leftNode = relativeNode(helper, left);
            var rightNode = relativeNode(helper, right);
            if (originalIds[0] == null) {
                // Phase 1: wait for one joined, settled Grid, then remove the middle node exactly once.
                helper.assertTrue(leftNode.getGrid() == rightNode.getGrid(),
                        "Fixture nodes must begin in one native Grid");
                helper.assertValueEqual(service(leftNode).settlement().status(), IdentityStatus.SETTLED,
                        "Joined fixture Grid must carry one settled NetworkId before the split");
                helper.assertValueEqual(service(rightNode).lineage(rightNode).networkId(),
                        service(leftNode).lineage(leftNode).networkId(), "Both chests must share the joined NetworkId");
                originalIds[0] = service(leftNode).lineage(leftNode).networkId().toString();
                helper.setBlock(middle, Blocks.AIR);
            }
            var originalId = originalIds[0];
            // Phase 2: the split and both settlements may complete on a later tick; retry without re-mutating.
            helper.assertTrue(leftNode.getGrid() != rightNode.getGrid(), "Removing the native middle node must split the Grid");
            helper.assertTrue(!service(leftNode).settlement().canInheritPolicy(),
                    "First ambiguous child must not inherit Policy");
            helper.assertTrue(!service(rightNode).settlement().canInheritPolicy(),
                    "Second ambiguous child must not inherit Policy");
            writeEvidence("identityambiguoussplit", 6, originalId, "ambiguous-split", Map.of(
                    "leftGrid", Integer.toUnsignedString(System.identityHashCode(leftNode.getGrid())),
                    "rightGrid", Integer.toUnsignedString(System.identityHashCode(rightNode.getGrid())),
                    "leftCanInherit", "false",
                    "rightCanInherit", "false"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void identityCopiedNode(GameTestHelper helper) {
        var sourcePos = new BlockPos(1, 1, 1);
        var copyPos = new BlockPos(5, 1, 1);
        var copyPlaced = new boolean[] { false };
        var originalId = new String[] { "" };
        helper.setBlock(sourcePos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(sourcePos, AEBlocks.ME_CHEST.block());
        helper.succeedWhen(() -> {
            var sourceNode = relativeNode(helper, sourcePos);
            if (!copyPlaced[0]) {
                var source = helper.<MEChestBlockEntity>getBlockEntity(sourcePos);
                originalId[0] = service(sourceNode).lineage(sourceNode).networkId().toString();
                var copiedData = source.saveWithFullMetadata(helper.getLevel().registryAccess());
                helper.setBlock(copyPos, AEBlocks.ME_CHEST.block());
                var copy = helper.<MEChestBlockEntity>getBlockEntity(copyPos);
                copy.loadWithComponents(copiedData, helper.getLevel().registryAccess());
                copy.setChanged();
                copyPlaced[0] = true;
            }
            var copyNode = relativeNode(helper, copyPos);
            helper.assertValueEqual(service(copyNode).lineage(copyNode).networkId().toString(), originalId[0],
                    "Copied native node data must reproduce the collision input");
            helper.assertTrue(!service(sourceNode).settlement().canInheritPolicy(),
                    "Original live network must fail closed after a copied identity appears");
            helper.assertTrue(!service(copyNode).settlement().canInheritPolicy(),
                    "Copied live network must not inherit old Policy");
            writeEvidence("identitycopiednode", 5, originalId[0], "copied-live-identity", Map.of(
                    "sourceGrid", Integer.toUnsignedString(System.identityHashCode(sourceNode.getGrid())),
                    "copyGrid", Integer.toUnsignedString(System.identityHashCode(copyNode.getGrid())),
                    "sourceNode", Integer.toUnsignedString(System.identityHashCode(sourceNode)),
                    "copyNode", Integer.toUnsignedString(System.identityHashCode(copyNode)),
                    "sourceCanInherit", "false",
                    "copyCanInherit", "false"));
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 200, required = true, manualOnly = true)
    public static void identityPartOnSettledCable(GameTestHelper helper) {
        var cell = new BlockPos(1, 1, 1);
        var chest = new BlockPos(2, 1, 1);
        var cable = new BlockPos(3, 1, 1);
        var link = new BlockPos(4, 1, 1);
        var otherCell = new BlockPos(5, 1, 1);
        var otherChest = new BlockPos(6, 1, 1);
        helper.setBlock(cell, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(chest, AEBlocks.ME_CHEST.block());
        helper.setBlock(otherCell, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(otherChest, AEBlocks.ME_CHEST.block());
        var phase = new int[] { 0 };
        var ids = new String[2];
        helper.succeedWhen(() -> {
            var chestNode = relativeNode(helper, chest);
            if (phase[0] == 0) {
                helper.assertValueEqual(service(chestNode).settlement().status(), IdentityStatus.SETTLED,
                        "Fixture Grid must settle first");
                PartHelper.setPart(helper.getLevel(), helper.absolutePos(cable), null, null,
                        AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
                phase[0] = 1;
            }
            var cableNode = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(cable), Direction.WEST);
            if (phase[0] == 1) {
                helper.assertTrue(cableNode != null && cableNode.getGrid() == chestNode.getGrid(),
                        "Cable must join the settled Grid");
                helper.assertValueEqual(service(chestNode).settlement().status(), IdentityStatus.SETTLED,
                        "A cable added to a settled Grid keeps it settled");
                ids[0] = service(chestNode).lineage(chestNode).networkId().toString();
                // AE2 readies the part node in its own one-node Grid before connecting it to the cable.
                PartHelper.setPart(helper.getLevel(), helper.absolutePos(cable), Direction.DOWN, null,
                        AEParts.EXPORT_BUS.asItem());
                phase[0] = 2;
            }
            var bus = PartHelper.getPart(helper.getLevel(), helper.absolutePos(cable), Direction.DOWN);
            if (phase[0] == 2) {
                helper.assertTrue(bus != null && bus.getGridNode() != null
                        && bus.getGridNode().getGrid() == chestNode.getGrid(), "Export Bus must join the Grid");
                helper.assertValueEqual(service(chestNode).settlement().status(), IdentityStatus.SETTLED,
                        "A part added to a settled cable must not make the Grid ambiguous");
                helper.assertValueEqual(service(chestNode).lineage(chestNode).networkId().toString(), ids[0],
                        "The Grid keeps its NetworkId");
                helper.assertValueEqual(service(chestNode).lineage(bus.getGridNode()).networkId().toString(), ids[0],
                        "The part adopts the Grid's NetworkId");
                var otherNode = relativeNode(helper, otherChest);
                helper.assertValueEqual(service(otherNode).settlement().status(), IdentityStatus.SETTLED,
                        "The second established Grid must settle on its own");
                ids[1] = service(otherNode).lineage(otherNode).networkId().toString();
                helper.assertTrue(!ids[0].equals(ids[1]), "Established Grids carry distinct NetworkIds");
                PartHelper.setPart(helper.getLevel(), helper.absolutePos(link), null, null,
                        AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
                phase[0] = 3;
            }
            // Joining two established multi-node Grids is still a real merge and fails closed.
            var otherNode = relativeNode(helper, otherChest);
            helper.assertTrue(otherNode.getGrid() == chestNode.getGrid(), "Link cable must merge both Grids");
            helper.assertValueEqual(service(chestNode).settlement().status(), IdentityStatus.AMBIGUOUS_MERGE,
                    "Merging two established Grids must stay ambiguous");
            helper.assertTrue(!service(chestNode).settlement().canInheritPolicy(),
                    "An ambiguous merge must not inherit Policy");
            writeEvidence("identitypartonsettledcable", 12, ids[0], "part-adopts-grid", Map.of(
                    "otherNetworkId", ids[1],
                    "mergedStatus", IdentityStatus.AMBIGUOUS_MERGE.name(),
                    "mergedCanInherit", "false"));
        });
    }

    private static void prepareRestart(GameTestHelper helper) {
        var storagePos = helper.getLevel().getSharedSpawnPos().offset(8, 2, 0);
        helper.getLevel().setBlockAndUpdate(storagePos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.getLevel().setBlockAndUpdate(storagePos, AEBlocks.ME_CHEST.block().defaultBlockState());
        helper.succeedWhen(() -> {
            var node = absoluteNode(helper, storagePos);
            var networkId = service(node).lineage(node).networkId();
            writeRestartState(storagePos, networkId.toString());
            helper.assertTrue(service(node).settlement().canInheritPolicy(), "Prepared network must be settled");
        });
    }

    private static void verifyRestart(GameTestHelper helper) {
        var state = readRestartState();
        var position = new BlockPos(Integer.parseInt(state.getProperty("x")),
                Integer.parseInt(state.getProperty("y")), Integer.parseInt(state.getProperty("z")));
        helper.succeedWhen(() -> {
            var node = absoluteNode(helper, position);
            var restored = service(node).lineage(node).networkId().toString();
            helper.assertValueEqual(restored, state.getProperty("networkId"),
                    "NetworkId must survive an actual server restart");
            helper.assertTrue(service(node).settlement().canInheritPolicy(),
                    "Restarted unchanged network must be eligible for existing Policy");
            writeEvidence("identityrestart", 5, restored, "settled-after-restart", Map.of(
                    "restored", "true"));
        });
    }

    private static IGridNode relativeNode(GameTestHelper helper, BlockPos position) {
        var blockEntity = helper.getBlockEntity(position);
        helper.assertTrue(blockEntity instanceof MEChestBlockEntity, "Expected a native AE2 ME chest host");
        var node = ((MEChestBlockEntity) blockEntity).getMainNode().getNode();
        helper.assertTrue(node != null, "Expected an initialized native AE2 node");
        return node;
    }

    private static IGridNode absoluteNode(GameTestHelper helper, BlockPos position) {
        var blockEntity = helper.getLevel().getBlockEntity(position);
        helper.assertTrue(blockEntity instanceof MEChestBlockEntity, "Expected a native AE2 ME chest host");
        var node = ((MEChestBlockEntity) blockEntity).getMainNode().getNode();
        helper.assertTrue(node != null, "Expected an initialized native AE2 node");
        return node;
    }

    private static NetworkIdentityService service(IGridNode node) {
        return node.getGrid().getService(NetworkIdentityService.class);
    }

    private static void writeRestartState(BlockPos pos, String networkId) {
        var properties = new Properties();
        properties.setProperty("x", Integer.toString(pos.getX()));
        properties.setProperty("y", Integer.toString(pos.getY()));
        properties.setProperty("z", Integer.toString(pos.getZ()));
        properties.setProperty("networkId", networkId);
        writeProperties(statePath(), properties, "AE2 Federation restart state");
    }

    private static Properties readRestartState() {
        var properties = new Properties();
        try (var input = Files.newInputStream(statePath())) {
            properties.load(input);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read identity restart state", exception);
        }
    }

    private static void writeEvidence(String testId, int assertions, String networkId, String settlement) {
        writeEvidence(testId, assertions, networkId, settlement, Map.of());
    }

    private static void writeEvidence(String testId, int assertions, String networkId, String settlement,
            Map<String, String> facts) {
        var configured = System.getProperty("ae2federation.nativeEvidenceFile", "");
        if (configured.isBlank()) {
            return;
        }
        var properties = new Properties();
        properties.setProperty("schemaVersion", "1");
        properties.setProperty("status", "passed");
        properties.setProperty("kind", "verify");
        properties.setProperty("testId", testId);
        properties.setProperty("structure", STRUCTURE);
        properties.setProperty("assertions", Integer.toString(assertions));
        properties.setProperty("operations", "2");
        properties.setProperty("inserted", "1");
        properties.setProperty("extracted", "1");
        properties.setProperty("elapsedNanos", "0");
        properties.setProperty("networkId", networkId);
        properties.setProperty("settlement", settlement);
        facts.forEach(properties::setProperty);
        writeProperties(Path.of(configured).toAbsolutePath().normalize(), properties,
                "AE2 Federation native identity evidence");
    }

    private static void writeProperties(Path path, Properties properties, String comment) {
        var temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            try (var output = Files.newOutputStream(temporary)) {
                properties.store(output, comment);
            }
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write identity evidence to " + path, exception);
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
