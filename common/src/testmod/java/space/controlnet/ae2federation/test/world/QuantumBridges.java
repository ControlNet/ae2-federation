package space.controlnet.ae2federation.test.world;

import appeng.api.ids.AEComponents;
import appeng.blockentity.qnb.QuantumBridgeBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.IGridConnectedBlockEntity;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;

/**
 * TEST-ONLY: real AE2 Quantum Network Bridges. A bridge is the flat 3x3 form, its link chamber in the middle and eight
 * rings around it, holding one entangled singularity; two bridges whose singularities share a frequency link once both
 * are powered, in any dimensions.
 */
public final class QuantumBridges {
    private QuantumBridges() {
    }

    /** A frequency no other test's bridges use. */
    public static long randomFrequency(GameTestHelper helper) {
        return 1 + (helper.getLevel().getRandom().nextLong() & (Long.MAX_VALUE >> 1));
    }

    /**
     * Builds a bridge around {@code chamber} with {@code place}, which sets a block and returns its block entity. With a
     * {@code network}, every block carries that network's identity, so the link merges no second one.
     */
    public static void build(BlockPos chamber, long frequency, @Nullable NetworkId network,
            BiFunction<BlockPos, BlockState, BlockEntity> place) {
        QuantumBridgeBlockEntity link = null;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                var block = x == 0 && z == 0 ? AEBlocks.QUANTUM_LINK : AEBlocks.QUANTUM_RING;
                var entity = place.apply(chamber.offset(x, 0, z), block.block().defaultBlockState());
                if (network != null) seed(entity, network);
                if (x == 0 && z == 0) link = (QuantumBridgeBlockEntity) entity;
            }
        }
        var singularity = new ItemStack(AEItems.QUANTUM_ENTANGLED_SINGULARITY.asItem());
        singularity.set(AEComponents.ENTANGLED_SINGULARITY_ID, frequency);
        link.getInternalInventory().setItemDirect(0, singularity);
    }

    /** Gives a freshly placed AE2 block entity's node {@code network}'s identity. */
    public static void seed(BlockEntity entity, NetworkId network) {
        ((IGridConnectedBlockEntity) entity).getMainNode()
                .loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", network));
    }
}
