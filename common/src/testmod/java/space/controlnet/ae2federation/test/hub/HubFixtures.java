package space.controlnet.ae2federation.test.hub;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import java.util.Collections;
import java.util.IdentityHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.fabric.port.HubPortBinding;
import space.controlnet.ae2federation.fabric.port.HubPortKind;
import space.controlnet.ae2federation.hub.HubBlockEntity;
import space.controlnet.ae2federation.hub.HubRegistration;
import space.controlnet.ae2federation.test.port.NativePortFixtures;

public final class HubFixtures implements AutoCloseable {
    private final GameTestHelper helper;
    private final NativePortFixtures nativePorts;

    public HubFixtures(GameTestHelper helper) {
        this.helper = helper;
        nativePorts = new NativePortFixtures(helper);
    }

    public HubBlockEntity placeHub(BlockPos position) {
        helper.setBlock(position, HubRegistration.HUB.get());
        return helper.getBlockEntity(position);
    }

    public HubBlockEntity hub(BlockPos position) {
        return helper.getBlockEntity(position);
    }

    public NativePortFixtures nativePorts() {
        return nativePorts;
    }

    public void placeNativeDevice(BlockPos hubPosition, Direction face) {
        nativePorts.placeChest(hubPosition.relative(face));
    }

    public void placeNativeCable(BlockPos hubPosition, Direction face) {
        nativePorts.placeCable(hubPosition.relative(face));
    }

    public void placeFederationCable(BlockPos hubPosition, Direction face) {
        helper.setBlock(hubPosition.relative(face), HubRegistration.FEDERATION_CABLE.get());
    }

    public void placeFederationCable(BlockPos position) {
        helper.setBlock(position, HubRegistration.FEDERATION_CABLE.get());
    }

    public void placeUnsupported(BlockPos hubPosition, Direction face) {
        helper.setBlock(hubPosition.relative(face), Blocks.STONE);
    }

    public IGridNode nativeDeviceNode(BlockPos hubPosition, Direction face) {
        return nativePorts.chestNode(hubPosition.relative(face));
    }

    public void joinNorthSouthNativeDevicesAroundHub(BlockPos hubPosition) {
        nativePorts.placeCable(hubPosition.north().west());
        nativePorts.placeCable(hubPosition.north().west(2));
        nativePorts.placeCable(hubPosition.west(2));
        nativePorts.placeCable(hubPosition.south().west(2));
        nativePorts.placeCable(hubPosition.south().west());
    }

    public boolean hasInternalNativeConnection(HubBlockEntity hub) {
        var nodes = Collections.newSetFromMap(new IdentityHashMap<IGridNode, Boolean>());
        for (var face : Direction.values()) {
            var node = hub.boundaryNode(face);
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes.stream().anyMatch(node -> node.getConnections().stream()
                .map(connection -> connection.getOtherSide(node))
                .anyMatch(nodes::contains));
    }

    public boolean allBoundaryNodesUseZeroIdlePower(HubBlockEntity hub) {
        for (var face : Direction.values()) {
            var node = hub.boundaryNode(face);
            if (node == null || node.getIdlePowerUsage() != 0.0) {
                return false;
            }
        }
        return true;
    }

    public boolean allFederationLinksAreReciprocal(HubBlockEntity hub) {
        for (var face : Direction.values()) {
            if (hub.binding(face) instanceof HubPortBinding.Federation binding
                    && !hub.fabricPort(face).connectsTo(binding.port())) {
                return false;
            }
        }
        return true;
    }

    public long count(HubBlockEntity hub, HubPortKind kind) {
        return hub.bindings().values().stream().filter(binding -> binding.kind() == kind).count();
    }

    @Override
    public void close() {
        nativePorts.close();
    }
}
