package space.controlnet.ae2federation.test.port;

import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import java.util.EnumSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.fabric.port.HubBoundaryTopology.BoundaryPort;

public final class NativePortFixtures implements AutoCloseable {
    private static final IGridNodeListener<NativePortFixtures> LISTENER = (owner, node) -> {
    };

    private final GameTestHelper helper;
    private final List<IManagedGridNode> managedNodes = new ArrayList<>();

    public NativePortFixtures(GameTestHelper helper) {
        this.helper = helper;
    }

    public void placeChest(BlockPos position) {
        helper.setBlock(position, AEBlocks.ME_CHEST.block());
    }

    public void placeCable(BlockPos position) {
        placeCable(position, AEColor.TRANSPARENT);
    }

    public void placeCable(BlockPos position, AEColor color) {
        var part = PartHelper.setPart(helper.getLevel(), helper.absolutePos(position), null, null,
                AEParts.GLASS_CABLE.item(color));
        helper.assertTrue(part != null, "Native glass cable fixture must be placed");
    }

    public IGridNode chestNode(BlockPos position) {
        var chest = helper.<MEChestBlockEntity>getBlockEntity(position);
        var node = chest.getMainNode().getNode();
        helper.assertTrue(node != null, "Native ME chest node must be initialized");
        return node;
    }

    public IGridNode exposedNode(BlockPos position, Direction side) {
        var node = GridHelper.getExposedNode(helper.getLevel(), helper.absolutePos(position), side);
        helper.assertTrue(node != null, "Native neighbor must expose a node on the attachment side");
        return node;
    }

    public IGridNode createBoundary(Direction face, BlockPos position) {
        var managed = GridHelper.createManagedNode(this, LISTENER)
                .setInWorldNode(true)
                .setIdlePowerUsage(0)
                .setExposedOnSides(EnumSet.of(face));
        managed.create(helper.getLevel(), helper.absolutePos(position));
        managedNodes.add(managed);
        var node = managed.getNode();
        helper.assertTrue(node != null, "Boundary node must be initialized");
        return node;
    }

    @Override
    public void close() {
        managedNodes.forEach(IManagedGridNode::destroy);
        managedNodes.clear();
    }
}
