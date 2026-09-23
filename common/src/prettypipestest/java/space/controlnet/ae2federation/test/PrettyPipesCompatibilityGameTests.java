package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.core.definitions.AEParts;
import appeng.parts.storagebus.StorageBusPart;
import de.ellpeck.prettypipes.Registry;
import de.ellpeck.prettypipes.network.PipeNetwork;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.resources.ResourceEvidence;

@PrefixGameTestTemplate(false)
public final class PrettyPipesCompatibilityGameTests {
    private PrettyPipesCompatibilityGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void compatPrettyItems(GameTestHelper helper) {
        var base = new BlockPos(5, 3, 5);
        var fixtures = new PolicyBridgeFixtures(helper, base);
        fixtures.installStorageCells();
        var sink = base.east().north().above();
        var source = sink.east(4);
        var loadedChunks = new HashSet<ChunkPos>();
        var bridgePlaced = new boolean[1];
        var networkPlaced = new boolean[1];
        var phase = new int[1];
        var phaseTick = new int[1];
        var nativeStorage = new MEStorage[1];
        var projection = new MEStorage[1];
        var bus = new StorageBusPart[1];
        var nativeExtracted = new long[1];
        helper.succeedWhen(() -> {
            if (!bridgePlaced[0] && fixtures.networksSettled()) {
                fixtures.placeFirstBridge();
                bridgePlaced[0] = true;
                helper.assertTrue(false, "Waiting for logistics Fabric");
            }
            helper.assertTrue(fixtures.networksSettled() && fixtures.firstBridgeReady(),
                    "Both native networks and the logistics Fabric must settle");
            if (!networkPlaced[0]) {
                var providerNetwork = fixtures.outerNetwork();
                helper.setBlock(sink, Blocks.CHEST);
                helper.setBlock(source, Blocks.CHEST);
                for (var offset = 1; offset <= 3; offset++) {
                    helper.setBlock(sink.east(offset), Registry.pipeBlock);
                }
                var center = new ChunkPos(helper.absolutePos(sink.east(2)));
                for (var x = center.x - 1; x <= center.x + 1; x++) {
                    for (var z = center.z - 1; z <= center.z + 1; z++) {
                        var chunk = new ChunkPos(x, z);
                        if (!helper.getLevel().getForcedChunks().contains(chunk.toLong())) {
                            helper.getLevel().setChunkForced(x, z, true);
                            loadedChunks.add(chunk);
                        }
                    }
                }
                bus[0] = PartHelper.setPart(helper.getLevel(), helper.absolutePos(base.east().north()),
                        Direction.UP, null, AEParts.STORAGE_BUS.get());
                helper.assertTrue(bus[0] != null, "Native AE2 Storage Bus must face the pipe destination chest");
                bus[0].getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("gn", providerNetwork));
                var cable = PartHelper.getPart(helper.getLevel(), helper.absolutePos(base.east().north()), null);
                helper.assertTrue(cable != null, "Native logistics cable must remain present");
                if (bus[0].getGridNode().getConnections().stream().noneMatch(connection ->
                        connection.a() == cable.getGridNode() || connection.b() == cable.getGridNode())) {
                    GridHelper.createConnection(cable.getGridNode(), bus[0].getGridNode());
                }
                bus[0].setPriority(100);
                networkPlaced[0] = true;
                helper.assertTrue(false, "Waiting for native pipe and Storage Bus initialization");
            }
            helper.assertTrue(fixtures.networksSettled() && fixtures.firstBridgeReady(),
                    "Pipe placement must retain both native Grid identities");
            var input = helper.<ChestBlockEntity>getBlockEntity(source);
            var output = helper.<ChestBlockEntity>getBlockEntity(sink);
            var network = PipeNetwork.get(helper.getLevel());
            var sinkAbsolute = helper.absolutePos(sink);
            var sourceAbsolute = helper.absolutePos(source);
            helper.assertTrue(Math.abs(sourceAbsolute.getX()) < 1000 && Math.abs(sourceAbsolute.getZ()) < 1000,
                    "Real item transport must execute near origin where the pinned float motion is representable");
            var destinationPipe = helper.absolutePos(sink.east());
            var sourcePipe = helper.absolutePos(sink.east(3));
            if (phase[0] == 0) {
                helper.assertTrue(network.isNode(sourcePipe) && network.isNode(destinationPipe),
                        "Connected Pretty Pipes must register both endpoint nodes");
                var mounts = new ArrayList<MEStorage>();
                bus[0].mountInventories((storage, priority) -> mounts.add(storage));
                helper.assertValueEqual(mounts.size(), 1, "AE2 must mount the pipe destination chest");
                nativeStorage[0] = mounts.getFirst();
                var key = PolicyLifecycleGameTests.storageKey(fixtures);
                PolicyService.get(helper.getLevel()).edit(
                        new PolicyEdit(key, PolicyRevision.NONE, PolicyRule.storageDefaults()));
                var service = StorageMountService.get(helper.getLevel());
                service.observeConnectedGrids(fixtures.mainGrid(), fixtures.outerGrid());
                projection[0] = service.projection(key);
                helper.assertTrue(projection[0] != null && service.sourceDomain(key).sources().stream()
                        .anyMatch(entry -> entry.storage() == nativeStorage[0])
                        && service.sourceDomain(key).sourceNodes().stream().anyMatch(node -> node.getOwner() == bus[0]),
                        "Federation must retain the exact native pipe destination backend");
                input.setItem(0, new ItemStack(Items.IRON_INGOT, 16));
                var remainder = network.requestExistingItem(destinationPipe, sinkAbsolute, null,
                        new ItemStack(Items.IRON_INGOT, 16));
                helper.assertTrue(remainder.isEmpty() && input.getItem(0).isEmpty(),
                        "Pretty Pipes must extract all input into its real connected item network");
                phase[0] = 1;
                phaseTick[0] = 0;
                helper.assertTrue(false, "Waiting for native Pretty Pipes delivery");
            }
            if (++phaseTick[0] < 120) helper.assertTrue(false, "Waiting for identical pipe tick budget");
            if (phase[0] == 1) {
                helper.assertValueEqual(output.getItem(0).getCount(), 16,
                        "Native Pretty Pipes network must deliver into the actual destination chest: source="
                        + input.getItem(0).getCount() + " inFlight=" + network.getItemsOnTheWay(sinkAbsolute, null)
                        + " sourcePipeItems=" + network.getItemsInPipe(sourcePipe).size()
                        + " destinationPipeItems=" + network.getItemsInPipe(destinationPipe).size()
                        + " areaLoaded=" + helper.getLevel().isAreaLoaded(sourcePipe, 1)
                        + " item=" + network.getItemsInPipe(sourcePipe));
                nativeExtracted[0] = nativeStorage[0].extract(AEItemKey.of(Items.IRON_INGOT), 4,
                        Actionable.MODULATE, IActionSource.empty());
                helper.assertValueEqual(output.getItem(0).getCount(), 12, "Native delegate must debit that chest");
                output.setItem(0, ItemStack.EMPTY);
                input.setItem(0, new ItemStack(Items.IRON_INGOT, 16));
                var remainder = network.requestExistingItem(destinationPipe, sinkAbsolute, null,
                        new ItemStack(Items.IRON_INGOT, 16));
                helper.assertTrue(remainder.isEmpty(), "Federation phase must use the same native pipe route");
                phase[0] = 2;
                phaseTick[0] = 0;
                helper.assertTrue(false, "Waiting for Federation-layout pipe delivery");
            }
            helper.assertValueEqual(output.getItem(0).getCount(), 16,
                    "The same pipe network must deliver into the same physical destination");
            var projectedExtracted = projection[0].extract(AEItemKey.of(Items.IRON_INGOT), 4,
                    Actionable.MODULATE, IActionSource.empty());
            helper.assertValueEqual(projectedExtracted, nativeExtracted[0],
                    "Native and Federation extraction must debit identical amounts");
            helper.assertValueEqual(nativeExtracted[0], 4L, "Native bus must debit four physical ingots");
            helper.assertValueEqual(output.getItem(0).getCount(), 12,
                    "Federation projection must debit the same pipe destination backend");
            ResourceEvidence.write("compatprettyitems", 6, Map.of(
                    "nativeExtracted", Long.toString(nativeExtracted[0]),
                    "projectedExtracted", Long.toString(projectedExtracted),
                    "destinationRemaining", Integer.toString(output.getItem(0).getCount()),
                    "pipeOwner", Integer.toUnsignedString(System.identityHashCode(network)),
                    "nativeStorageIdentity", Integer.toUnsignedString(System.identityHashCode(nativeStorage[0])),
                    "nativeBusIdentity", Integer.toUnsignedString(System.identityHashCode(bus[0]))));
            loadedChunks.forEach(chunk -> helper.getLevel().setChunkForced(chunk.x, chunk.z, false));
            fixtures.close();
        });
    }
}
