package space.controlnet.ae2federation.test.bridge;

import appeng.api.parts.PartHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.bridge.BridgeRegistration;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.test.port.NativePortFixtures;

public final class BridgeFixtures implements AutoCloseable {
    private final GameTestHelper helper;
    private final NativePortFixtures nativePorts;

    public BridgeFixtures(GameTestHelper helper) {
        this.helper = helper;
        this.nativePorts = new NativePortFixtures(helper);
    }

    public void placeNativeHosts(BlockPos bridgePosition) {
        nativePorts.placeChest(bridgePosition.north());
        nativePorts.placeChest(bridgePosition.south(2));
        nativePorts.placeCable(bridgePosition.south());
        nativePorts.placeCable(bridgePosition);
    }

    public MultipartBridgePart placeBridge(BlockPos bridgePosition) {
        return placeBridge(bridgePosition, Direction.NORTH);
    }

    public MultipartBridgePart placeBridge(BlockPos bridgePosition, Direction side) {
        var bridge = PartHelper.setPart(helper.getLevel(), helper.absolutePos(bridgePosition), side, null,
                BridgeRegistration.MULTIPART_BRIDGE.get());
        helper.assertTrue(bridge != null, "Registered Bridge part must be placeable on an AE2 cable bus");
        return bridge;
    }

    public void joinNativeHostsAroundBridge(BlockPos bridgePosition) {
        nativePorts.placeCable(bridgePosition.north().west());
        nativePorts.placeCable(bridgePosition.west());
        nativePorts.placeCable(bridgePosition.south().west());
    }

    public NativePortFixtures nativePorts() {
        return nativePorts;
    }

    @Override
    public void close() {
        nativePorts.close();
    }
}
