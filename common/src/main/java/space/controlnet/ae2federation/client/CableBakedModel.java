package space.controlnet.ae2federation.client;

import java.util.List;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.router.CableVisualConnections;

/** Selects pre-baked geometry only when the chunk mesh is rebuilt. No per-frame geometry or network state. */
public final class CableBakedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Integer> MASK = new ModelProperty<>();
    private static final ModelData[] DATA = new ModelData[64];
    static {
        for (int mask = 0; mask < DATA.length; mask++) {
            DATA[mask] = ModelData.builder().with(MASK, mask).build();
        }
    }
    private final BakedModel[] variants;

    private CableBakedModel(BakedModel[] variants) {
        super(variants[0]);
        this.variants = variants;
    }

    private static ModelResourceLocation location(int mask) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath(
                "ae2federation", "block/cable/%02d".formatted(mask)));
    }

    public static void register(ModelEvent.RegisterAdditional event) {
        for (int mask = 0; mask < 64; mask++) {
            event.register(location(mask));
        }
    }

    public static void bake(ModelEvent.ModifyBakingResult event) {
        var variants = new BakedModel[64];
        for (int mask = 0; mask < 64; mask++) {
            variants[mask] = java.util.Objects.requireNonNull(event.getModels().get(location(mask)));
        }
        event.getModels().put(new ModelResourceLocation(
                ResourceLocation.fromNamespaceAndPath("ae2federation", "cable"), ""), new CableBakedModel(variants));
    }

    private BakedModel select(ModelData data) {
        var mask = data.get(MASK);
        return variants[mask == null ? 0 : mask];
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos position, BlockState state, ModelData data) {
        return DATA[CableVisualConnections.mask(level, position)];
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
            ModelData data, @Nullable RenderType renderType) {
        if (CableFlowRenderer.isEnabled() && state != null) {
            if (renderType == RenderType.cutout()) return List.of();
            if (renderType == null) {
                var quads = new java.util.ArrayList<BakedQuad>();
                for (var layer : select(data).getRenderTypes(state, random, ModelData.EMPTY)) {
                    if (layer != RenderType.cutout()) {
                        quads.addAll(select(data).getQuads(state, side, random, ModelData.EMPTY, layer));
                    }
                }
                return quads;
            }
        }
        return select(data).getQuads(state, side, random, ModelData.EMPTY, renderType);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return select(data).getRenderTypes(state, random, ModelData.EMPTY);
    }
}
