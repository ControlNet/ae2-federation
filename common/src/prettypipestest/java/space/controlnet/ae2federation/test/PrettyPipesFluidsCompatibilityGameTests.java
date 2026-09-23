package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEFluidKey;
import appeng.api.storage.MEStorage;
import appeng.blockentity.storage.SkyStoneTankBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.parts.storagebus.StorageBusPart;
import de.ellpeck.prettypipes.network.PipeNetwork;
import dev.quarris.ppfluids.pipe.FluidPipeBlockEntity;
import dev.quarris.ppfluids.pipenetwork.FluidNetworkLocation;
import dev.quarris.ppfluids.pipenetwork.PipeNetworkUtil;
import dev.quarris.ppfluids.registry.BlockSetup;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
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
public final class PrettyPipesFluidsCompatibilityGameTests {
    private PrettyPipesFluidsCompatibilityGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void compatPrettyFluids(GameTestHelper helper) {
        var base = new BlockPos(5, 3, 5);
        var fixtures = new PolicyBridgeFixtures(helper, base);
        fixtures.installStorageCells();
        var sink = base.east().north().above();
        var source = sink.east(4);
        var sourcePipe = sink.east(3);
        var destinationPipe = sink.east();
        var forcedChunks = new HashSet<ChunkPos>();
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
                helper.setBlock(sink, AEBlocks.SKY_STONE_TANK.block());
                helper.setBlock(source, AEBlocks.SKY_STONE_TANK.block());
                for (var offset = 1; offset <= 3; offset++) {
                    helper.setBlock(sink.east(offset), BlockSetup.FLUID_PIPE.get());
                }
                var center = new ChunkPos(helper.absolutePos(sink.east(2)));
                for (var chunkX = center.x - 1; chunkX <= center.x + 1; chunkX++) {
                    for (var chunkZ = center.z - 1; chunkZ <= center.z + 1; chunkZ++) {
                        var chunk = new ChunkPos(chunkX, chunkZ);
                        if (!helper.getLevel().getForcedChunks().contains(chunk.toLong())) {
                            helper.getLevel().setChunkForced(chunkX, chunkZ, true);
                            forcedChunks.add(chunk);
                        }
                    }
                }
                bus[0] = PartHelper.setPart(helper.getLevel(), helper.absolutePos(base.east().north()),
                        Direction.UP, null, AEParts.STORAGE_BUS.get());
                helper.assertTrue(bus[0] != null, "Native Storage Bus must face the real fluid destination tank");
                bus[0].getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("gn", providerNetwork));
                var cable = PartHelper.getPart(helper.getLevel(), helper.absolutePos(base.east().north()), null);
                helper.assertTrue(cable != null, "Native logistics cable must remain present");
                if (bus[0].getGridNode().getConnections().stream().noneMatch(connection ->
                        connection.a() == cable.getGridNode() || connection.b() == cable.getGridNode())) {
                    GridHelper.createConnection(cable.getGridNode(), bus[0].getGridNode());
                }
                bus[0].setPriority(100);
                networkPlaced[0] = true;
                helper.assertTrue(false, "Waiting for native fluid pipes and Storage Bus initialization");
            }
            var network = PipeNetwork.get(helper.getLevel());
            var sourceAbsolute = helper.absolutePos(source);
            helper.assertTrue(Math.abs(sourceAbsolute.getX()) < 1000 && Math.abs(sourceAbsolute.getZ()) < 1000,
                    "Real fluid transport must execute near origin where the pinned float motion is representable");
            var sinkAbsolute = helper.absolutePos(sink);
            var sourcePipeAbsolute = helper.absolutePos(sourcePipe);
            var destinationPipeAbsolute = helper.absolutePos(destinationPipe);
            helper.assertTrue(network.isNode(sourcePipeAbsolute) && network.isNode(destinationPipeAbsolute),
                    "Real Pretty Pipes Fluids endpoints must register in one network");
            var pipe = helper.<FluidPipeBlockEntity>getBlockEntity(sourcePipe);
            var sourceTank = helper.<SkyStoneTankBlockEntity>getBlockEntity(source);
            var sinkTank = helper.<SkyStoneTankBlockEntity>getBlockEntity(sink);
            var sourceHandler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                    sourceAbsolute, Direction.WEST);
            var sinkHandler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK,
                    sinkAbsolute, Direction.EAST);
            helper.assertTrue(sourceHandler != null && sinkHandler != null
                    && sourceHandler == sourceTank.getFluidHandler() && sinkHandler == sinkTank.getFluidHandler()
                    && pipe.getFluidHandler(Direction.EAST) == sourceHandler,
                    "Source and destination must be the tanks' own native fluid capabilities");
            if (phase[0] == 0) {
                var mounts = new ArrayList<MEStorage>();
                bus[0].mountInventories((storage, priority) -> mounts.add(storage));
                helper.assertValueEqual(mounts.size(), 1, "Native Storage Bus must mount the destination tank");
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
                        "Federation must retain the exact native tank Storage Bus owner and delegate");
                helper.assertValueEqual(sourceHandler.fill(new FluidStack(Fluids.WATER, 1000),
                        IFluidHandler.FluidAction.EXECUTE), 1000, "Real source tank must accept water");
                var remainder = PipeNetworkUtil.requestExistingFluid(helper.getLevel(),
                        new FluidNetworkLocation(sourcePipeAbsolute, Direction.EAST), destinationPipeAbsolute,
                        sinkAbsolute, null, new FluidStack(Fluids.WATER, 1000));
                helper.assertTrue(remainder.isEmpty() && sourceHandler.getFluidInTank(0).isEmpty(),
                        "Pretty Pipes Fluids must drain its native source into the connected pipe route");
                phase[0] = 1;
                phaseTick[0] = 0;
                helper.assertTrue(false, "Waiting for native fluid delivery");
            }
            if (++phaseTick[0] < 120) {
                helper.assertTrue(false, "Waiting for identical fluid pipe tick budget");
            }
            if (phase[0] == 1) {
                helper.assertValueEqual(sinkHandler.getFluidInTank(0).getAmount(), 1000,
                        "Native fluid pipe must deliver water into the real destination tank: sourcePipeItems="
                        + network.getItemsInPipe(sourcePipeAbsolute).size() + " destinationPipeItems="
                        + network.getItemsInPipe(destinationPipeAbsolute).size() + " onTheWay="
                        + network.getItemsOnTheWay(sinkAbsolute, null) + " sourcePipe="
                        + network.getItemsInPipe(sourcePipeAbsolute) + " areaLoaded="
                        + helper.getLevel().isAreaLoaded(sourcePipeAbsolute, 1));
                nativeExtracted[0] = nativeStorage[0].extract(AEFluidKey.of(Fluids.WATER), 250,
                        Actionable.MODULATE, IActionSource.empty());
                helper.assertValueEqual(nativeExtracted[0], 250L, "Native Storage Bus must debit the delivered water");
                helper.assertValueEqual(sinkHandler.getFluidInTank(0).getAmount(), 750,
                        "Native delegate must debit the real destination tank");
                sinkHandler.drain(750, IFluidHandler.FluidAction.EXECUTE);
                sourceHandler.fill(new FluidStack(Fluids.WATER, 1000), IFluidHandler.FluidAction.EXECUTE);
                var remainder = PipeNetworkUtil.requestExistingFluid(helper.getLevel(),
                        new FluidNetworkLocation(sourcePipeAbsolute, Direction.EAST), destinationPipeAbsolute,
                        sinkAbsolute, null, new FluidStack(Fluids.WATER, 1000));
                helper.assertTrue(remainder.isEmpty(), "Federation phase must use the same native fluid pipe route");
                phase[0] = 2;
                phaseTick[0] = 0;
                helper.assertTrue(false, "Waiting for Federation-layout fluid delivery");
            }
            helper.assertValueEqual(sinkHandler.getFluidInTank(0).getAmount(), 1000,
                    "Same fluid network must deliver into the same physical destination tank");
            var projectedExtracted = projection[0].extract(AEFluidKey.of(Fluids.WATER), 250,
                    Actionable.MODULATE, IActionSource.empty());
            helper.assertValueEqual(projectedExtracted, nativeExtracted[0],
                    "Native and Federation extraction must debit identical fluid amounts");
            helper.assertValueEqual(sinkHandler.getFluidInTank(0).getAmount(), 750,
                    "Federation projection must debit the same tank");
            ResourceEvidence.write("compatprettyfluids", 6, Map.of(
                    "nativeExtracted", Long.toString(nativeExtracted[0]),
                    "projectedExtracted", Long.toString(projectedExtracted),
                    "destinationRemaining", Integer.toString(sinkHandler.getFluidInTank(0).getAmount()),
                    "pipeOwner", Integer.toUnsignedString(System.identityHashCode(network)),
                    "nativeStorageIdentity", Integer.toUnsignedString(System.identityHashCode(nativeStorage[0])),
                    "nativeBusIdentity", Integer.toUnsignedString(System.identityHashCode(bus[0]))));
            forcedChunks.forEach(chunk -> helper.getLevel().setChunkForced(chunk.x, chunk.z, false));
            fixtures.close();
        });
    }
}
