package space.controlnet.ae2federation.test.router;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import java.util.Collections;
import java.util.IdentityHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.domain.port.RouterPortBinding;
import space.controlnet.ae2federation.domain.port.RouterPortKind;
import space.controlnet.ae2federation.router.RouterBlockEntity;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.port.NativePortFixtures;

public final class RouterFixtures implements AutoCloseable {
    private final GameTestHelper helper;
    private final NativePortFixtures nativePorts;

    public RouterFixtures(GameTestHelper helper) {
        this.helper = helper;
        nativePorts = new NativePortFixtures(helper);
    }

    public RouterBlockEntity placeRouter(BlockPos position) {
        helper.setBlock(position, RouterRegistration.ROUTER.get());
        return helper.getBlockEntity(position);
    }

    public RouterBlockEntity router(BlockPos position) {
        return helper.getBlockEntity(position);
    }

    public NativePortFixtures nativePorts() {
        return nativePorts;
    }

    public void placeNativeDevice(BlockPos routerPosition, Direction face) {
        nativePorts.placeChest(routerPosition.relative(face));
    }

    public void placeNativeCable(BlockPos routerPosition, Direction face) {
        nativePorts.placeCable(routerPosition.relative(face));
    }

    public void placeFederationCable(BlockPos routerPosition, Direction face) {
        helper.setBlock(routerPosition.relative(face), RouterRegistration.FEDERATION_CABLE.get());
    }

    public void placeFederationCable(BlockPos position) {
        helper.setBlock(position, RouterRegistration.FEDERATION_CABLE.get());
    }

    public void placeUnsupported(BlockPos routerPosition, Direction face) {
        helper.setBlock(routerPosition.relative(face), Blocks.STONE);
    }

    public IGridNode nativeDeviceNode(BlockPos routerPosition, Direction face) {
        return nativePorts.chestNode(routerPosition.relative(face));
    }

    public void joinNorthSouthNativeDevicesAroundRouter(BlockPos routerPosition) {
        nativePorts.placeCable(routerPosition.north().west());
        nativePorts.placeCable(routerPosition.north().west(2));
        nativePorts.placeCable(routerPosition.west(2));
        nativePorts.placeCable(routerPosition.south().west(2));
        nativePorts.placeCable(routerPosition.south().west());
    }

    public boolean hasInternalNativeConnection(RouterBlockEntity router) {
        var nodes = Collections.newSetFromMap(new IdentityHashMap<IGridNode, Boolean>());
        for (var face : Direction.values()) {
            var node = router.boundaryNode(face);
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes.stream().anyMatch(node -> node.getConnections().stream()
                .map(connection -> connection.getOtherSide(node))
                .anyMatch(nodes::contains));
    }

    public boolean allBoundaryNodesUseZeroIdlePower(RouterBlockEntity router) {
        for (var face : Direction.values()) {
            var node = router.boundaryNode(face);
            if (node == null || node.getIdlePowerUsage() != 0.0) {
                return false;
            }
        }
        return true;
    }

    public boolean allFederationLinksAreReciprocal(RouterBlockEntity router) {
        for (var face : Direction.values()) {
            if (router.binding(face) instanceof RouterPortBinding.Federation binding
                    && !router.federationDomainPort(face).connectsTo(binding.port())) {
                return false;
            }
        }
        return true;
    }

    public long count(RouterBlockEntity router, RouterPortKind kind) {
        return router.bindings().values().stream().filter(binding -> binding.kind() == kind).count();
    }

    @Override
    public void close() {
        nativePorts.close();
    }
}
