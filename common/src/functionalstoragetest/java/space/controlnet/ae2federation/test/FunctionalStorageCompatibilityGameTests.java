package space.controlnet.ae2federation.test;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEParts;
import appeng.parts.storagebus.StorageBusPart;
import com.buuz135.functionalstorage.FunctionalStorage;
import com.buuz135.functionalstorage.block.tile.DrawerTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.storage.mount.StorageMountService;
import space.controlnet.ae2federation.test.policy.PolicyBridgeFixtures;
import space.controlnet.ae2federation.test.resources.ResourceEvidence;
import java.util.Map;
import java.util.ArrayList;
import appeng.api.storage.MEStorage;

@PrefixGameTestTemplate(false)
public final class FunctionalStorageCompatibilityGameTests {
    private FunctionalStorageCompatibilityGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 400, required = true, manualOnly = true)
    public static void compatFunctionalStorage(GameTestHelper helper) {
        var base = new BlockPos(5, 3, 5);
        var fixture = new PolicyBridgeFixtures(helper, base);
        fixture.installStorageCells();
        var drawerPosition = base.east().north().above();
        StorageBusPart[] bus = new StorageBusPart[1];
        var drawerPlaced = new boolean[1];
        var bridge = new space.controlnet.ae2federation.bridge.MultipartBridgePart[1];
        var bridgePlaced = new boolean[1];
        helper.succeedWhen(() -> {
            if (!bridgePlaced[0] && fixture.networksSettled()) {
                bridge[0] = fixture.placeFirstBridge();
                bridgePlaced[0] = true;
                helper.assertTrue(false, "Waiting for storage Fabric");
            }
            helper.assertTrue(fixture.networksSettled(), "Native networks must settle with their attached Storage Bus: "
                    + fixture.mainGrid().getService(NetworkIdentityService.class).settlement().status() + "/"
                    + fixture.outerGrid().getService(NetworkIdentityService.class).settlement().status());
            helper.assertTrue(fixture.firstBridgeReady(), "Storage Fabric must settle: "
                    + (bridge[0] == null ? "native networks unsettled"
                    : bridge[0].operationalReason() + " bus=" + fixture.outerGrid()));
            if (!drawerPlaced[0]) {
                var providerNetwork = fixture.outerNetwork();
                var drawerBlock = FunctionalStorage.DRAWER_TYPES.get(FunctionalStorage.DrawerType.X_1)
                        .getFirst().getBlock();
                helper.setBlock(drawerPosition, drawerBlock);
                bus[0] = PartHelper.setPart(helper.getLevel(), helper.absolutePos(base.east().north()),
                        Direction.UP, null, AEParts.STORAGE_BUS.get());
                helper.assertTrue(bus[0] != null, "Storage Bus must face the actual drawer");
                bus[0].getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("gn", providerNetwork));
                var cable = PartHelper.getPart(helper.getLevel(), helper.absolutePos(base.east().north()), null);
                helper.assertTrue(cable != null, "Native Storage Bus cable must remain present");
                if (bus[0].getGridNode().getConnections().stream().noneMatch(connection ->
                        connection.a() == cable.getGridNode() || connection.b() == cable.getGridNode())) {
                    GridHelper.createConnection(cable.getGridNode(), bus[0].getGridNode());
                }
                bus[0].setPriority(100);
                drawerPlaced[0] = true;
                helper.assertTrue(false, "Waiting for Storage Bus initialization");
            }
            var drawer = helper.<DrawerTile>getBlockEntity(drawerPosition);
            var handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK,
                    helper.absolutePos(drawerPosition), Direction.DOWN);
            helper.assertTrue(handler != null && drawer.getStorage() == handler,
                    "The native drawer must own the exposed item capability");
            var nativeMounts = new ArrayList<MEStorage>();
            bus[0].mountInventories((storage, priority) -> nativeMounts.add(storage));
            helper.assertValueEqual(nativeMounts.size(), 1, "Native Storage Bus must mount the actual drawer once");
            var nativeStorage = nativeMounts.getFirst();
            var key = AEItemKey.of(Items.IRON_INGOT);
            var policyKey = PolicyLifecycleGameTests.storageKey(fixture);
            PolicyService.get(helper.getLevel()).edit(
                    new PolicyEdit(policyKey, PolicyRevision.NONE, PolicyRule.storageDefaults()));
            var mounts = StorageMountService.get(helper.getLevel());
            mounts.observeConnectedGrids(fixture.mainGrid(), fixture.outerGrid());
            var projection = mounts.projection(policyKey);
            helper.assertTrue(projection != null, "Federation must project the Storage Bus source");
            var domain = mounts.sourceDomain(policyKey);
            helper.assertTrue(domain.sources().stream().anyMatch(source -> source.storage() == nativeStorage)
                            && domain.sourceNodes().stream().anyMatch(node -> node.getOwner() == bus[0]),
                    "The projected source must be the exact native Storage Bus delegate");
            var nativeInserted = nativeStorage.insert(key, 16, Actionable.MODULATE, IActionSource.empty());
            var nativeExtracted = nativeStorage.extract(key, 4, Actionable.MODULATE, IActionSource.empty());
            var nativeRemaining = handler.getStackInSlot(0).getCount();
            handler.extractItem(0, nativeRemaining, false);
            var projectedInserted = projection.insert(key, 16, Actionable.MODULATE, IActionSource.empty());
            var projectedExtracted = projection.extract(key, 4, Actionable.MODULATE, IActionSource.empty());
            var projectedRemaining = handler.getStackInSlot(0).getCount();
            helper.assertValueEqual(nativeInserted, 16L, "Native bus must insert into the drawer");
            helper.assertValueEqual(nativeExtracted, 4L, "Native bus must extract from the drawer");
            helper.assertValueEqual(projectedInserted, nativeInserted, "Identical insertion must reach the same drawer");
            helper.assertValueEqual(projectedExtracted, nativeExtracted, "Identical extraction must reach the same drawer");
            helper.assertValueEqual(projectedRemaining, nativeRemaining, "Both paths must retain exact drawer contents");
            ResourceEvidence.write("compatfunctionalstorage", 6, Map.of(
                    "nativeInserted", Long.toString(nativeInserted), "nativeExtracted", Long.toString(nativeExtracted),
                    "projectedInserted", Long.toString(projectedInserted),
                    "projectedExtracted", Long.toString(projectedExtracted),
                    "nativeRemaining", Integer.toString(nativeRemaining),
                    "projectedRemaining", Integer.toString(projectedRemaining),
                    "nativeBusIdentity", Integer.toUnsignedString(System.identityHashCode(bus[0])),
                    "nativeStorageIdentity", Integer.toUnsignedString(System.identityHashCode(nativeStorage))));
            fixture.close();
        });
    }
}
