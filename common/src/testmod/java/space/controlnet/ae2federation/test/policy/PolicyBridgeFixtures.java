package space.controlnet.ae2federation.test.policy;

import appeng.api.networking.IGrid;
import appeng.api.util.AEColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.test.bridge.BridgeFixtures;

public final class PolicyBridgeFixtures implements AutoCloseable {
    private final GameTestHelper helper;
    private final BridgeFixtures bridges;
    private final BlockPos firstPosition;
    private final BlockPos secondPosition;
    private MultipartBridgePart first;
    private MultipartBridgePart second;

    public PolicyBridgeFixtures(GameTestHelper helper, BlockPos firstPosition) {
        this.helper = helper;
        this.firstPosition = firstPosition;
        secondPosition = firstPosition.east();
        bridges = new BridgeFixtures(helper);
        bridges.nativePorts().placeCable(firstPosition, AEColor.RED);
        bridges.nativePorts().placeCable(secondPosition, AEColor.RED);
        bridges.nativePorts().placeChest(firstPosition.south());
        bridges.nativePorts().placeCable(firstPosition.north(), AEColor.BLUE);
        bridges.nativePorts().placeCable(secondPosition.north(), AEColor.BLUE);
        bridges.nativePorts().placeChest(firstPosition.north(2));
    }

    public boolean networksSettled() {
        return FabricRegistryAccess.confirmedNetworkId(mainGrid()).isPresent()
                && FabricRegistryAccess.confirmedNetworkId(outerGrid()).isPresent();
    }

    public MultipartBridgePart placeFirstBridge() {
        first = bridges.placeBridge(firstPosition, Direction.NORTH);
        return first;
    }

    public MultipartBridgePart placeSecondBridge() {
        second = bridges.placeBridge(secondPosition, Direction.NORTH);
        return second;
    }

    public boolean firstBridgeReady() {
        return bridgeReady(first);
    }

    public boolean secondBridgeReady() {
        return bridgeReady(second);
    }

    public void removeFirstBridge() {
        helper.assertTrue(first.getHost().removePart(first), "Original Bridge part must be removed from its AE2 host");
    }

    public IGrid mainGrid() {
        return bridges.nativePorts().exposedNode(firstPosition, Direction.UP).getGrid();
    }

    public IGrid outerGrid() {
        return bridges.nativePorts().exposedNode(firstPosition.north(), Direction.UP).getGrid();
    }

    public NetworkId mainNetwork() {
        return FabricRegistryAccess.confirmedNetworkId(mainGrid()).orElseThrow();
    }

    public NetworkId outerNetwork() {
        return FabricRegistryAccess.confirmedNetworkId(outerGrid()).orElseThrow();
    }

    public int firstBridgeIdentity() {
        return System.identityHashCode(first);
    }

    public int secondBridgeIdentity() {
        return System.identityHashCode(second);
    }

    private boolean bridgeReady(MultipartBridgePart bridge) {
        if (bridge == null || bridge.membershipCandidate().isEmpty()) {
            return false;
        }
        var mainFabrics = FabricRegistryAccess.get(helper.getLevel()).fabricsFor(mainNetwork());
        var outerFabrics = FabricRegistryAccess.get(helper.getLevel()).fabricsFor(outerNetwork());
        return mainFabrics.stream().anyMatch(outerFabrics::contains);
    }

    @Override
    public void close() {
        bridges.close();
    }
}
