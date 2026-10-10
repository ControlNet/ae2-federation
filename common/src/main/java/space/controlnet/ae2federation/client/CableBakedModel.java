package space.controlnet.ae2federation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.router.CableVisualConnections;

/**
 * A Federation Cable's model: AE2's dense cable geometry ({@link FederationCableBuilder}), built from code for each
 * set of side connections the first time a chunk needs it, and kept until the next resource reload. The connections
 * are read when the chunk mesh is rebuilt; there is no per-frame geometry or network state.
 */
public final class CableBakedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Integer> CONNECTIONS = new ModelProperty<>();
    private static final ModelData[] DATA = new ModelData[CableVisualConnections.MODEL_COUNT];
    private static final ChunkRenderTypeSet LAYERS = ChunkRenderTypeSet.of(RenderType.translucent());
    private static final ResourceLocation CABLE = ResourceLocation.fromNamespaceAndPath("ae2federation", "cable");

    private final Map<Integer, List<BakedQuad>> shapes = new ConcurrentHashMap<>();
    private volatile List<BakedQuad> item;

    private CableBakedModel(BakedModel base) {
        super(base);
    }

    /** Wraps the cable's placeholder block and item models once they are baked. */
    public static void bake(ModelEvent.ModifyBakingResult event) {
        for (var location : List.of(new ModelResourceLocation(CABLE, ""), ModelResourceLocation.inventory(CABLE))) {
            var base = java.util.Objects.requireNonNull(event.getModels().get(location), location.toString());
            event.getModels().put(location, new CableBakedModel(base));
        }
    }

    private static FederationCableBuilder builder() {
        var atlas = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS);
        return new FederationCableBuilder(atlas.apply(texture("dense/core")), atlas.apply(texture("dense/line")));
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath("ae2federation", "part/cable/" + name);
    }

    private List<BakedQuad> quads(ModelData data) {
        var connections = data.get(CONNECTIONS);
        return shapes.computeIfAbsent(connections == null ? 0 : connections, key -> builder().build(key));
    }

    private List<BakedQuad> itemQuads() {
        var quads = item;
        if (quads == null) {
            quads = builder().item();
            item = quads;
        }
        return quads;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos position, BlockState state, ModelData data) {
        int connections = CableVisualConnections.model(level, position);
        var modelData = DATA[connections];
        if (modelData == null) {
            modelData = ModelData.builder().with(CONNECTIONS, connections).build();
            DATA[connections] = modelData;
        }
        return modelData;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return getQuads(state, side, random, ModelData.EMPTY, null);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
            ModelData data, @Nullable RenderType renderType) {
        // Like AE2's cable bus, every quad is unculled: a cable never fills a block face.
        if (side != null) return List.of();
        if (state == null) return itemQuads();
        if (renderType != null && renderType != RenderType.translucent()) return List.of();
        return quads(data);
    }

    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state, RandomSource random, ModelData data) {
        return LAYERS;
    }

    @Override
    public List<RenderType> getRenderTypes(ItemStack stack, boolean fabulous) {
        return List.of(fabulous ? Sheets.translucentItemSheet() : Sheets.translucentCullBlockSheet());
    }

    @Override
    public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
        return List.of(this);
    }

    @Override
    public BakedModel applyTransform(ItemDisplayContext context, PoseStack poseStack, boolean leftHand) {
        // The wrapper's default hands back the placeholder model, which has no quads.
        super.applyTransform(context, poseStack, leftHand);
        return this;
    }
}
