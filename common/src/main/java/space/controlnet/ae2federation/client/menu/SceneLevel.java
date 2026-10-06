package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * The level the 3D preview draws from: LDLib2's view of the player's world, which also passes on each block's model
 * data. Without it, a block whose model is built from that data, AE2's cables and their parts among them, draws
 * nothing, and a Drive shows no cells.
 */
final class SceneLevel extends TrackedDummyWorld {
    SceneLevel(Level world) {
        super(world);
    }

    @Override
    public ModelData getModelData(BlockPos pos) {
        var world = proxyWorld.get();
        // A block left out of the scene reads as air, so it has no model data either.
        if (world == null || getBlockState(pos).isAir()) return super.getModelData(pos);
        return world.getModelData(pos);
    }
}
