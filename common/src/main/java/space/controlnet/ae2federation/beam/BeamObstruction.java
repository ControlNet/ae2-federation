package space.controlnet.ae2federation.beam;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What blocks a beam: any block with a collision shape, except glass and what the {@code ae2federation:beam_passes}
 * tag lets through. Fluids, grass, flowers and torches have no collision shape, so a beam passes them; entities never
 * block it.
 */
public final class BeamObstruction {
    public static final TagKey<Block> PASSES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("ae2federation", "beam_passes"));
    private static final TagKey<Block> GLASS_BLOCKS = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("c", "glass_blocks"));
    private static final TagKey<Block> GLASS_PANES = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath("c", "glass_panes"));

    private BeamObstruction() {
    }

    public static boolean blocks(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir() || state.is(GLASS_BLOCKS) || state.is(GLASS_PANES) || state.is(PASSES)) return false;
        return !state.getCollisionShape(level, pos).isEmpty();
    }
}
