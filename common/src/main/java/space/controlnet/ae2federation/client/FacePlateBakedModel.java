package space.controlnet.ae2federation.client;

import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.function.Function;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import space.controlnet.ae2federation.client.DeviceStructureGeometry.Side;
import space.controlnet.ae2federation.router.RouterBlock;

/**
 * A formed communication interface's model: a {@link FacePlateGeometry} plate on its outward face, composed from the
 * interface's four connected-face tiles. A lone interface is framed all round; the four of a filled 2x2 square, whose
 * {@link RouterBlock#CONNECTED} bits join them (user, 2026-10-21), make one panel. Replacing the four tiles changes the
 * look without code. Without them, the JSON plate stays.
 */
public final class FacePlateBakedModel extends BakedModelWrapper<BakedModel> {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The interface exists only with Neo ECO, whose classes this must not load: it is found by ID. */
    private static final ResourceLocation INTERFACE =
            ResourceLocation.fromNamespaceAndPath("ae2federation", "eco_computation_interface");
    private static final String TILES = "block/eco_computation/interface_";

    private final List<List<BakedQuad>> quads;

    private FacePlateBakedModel(BakedModel base, List<List<BakedQuad>> quads) {
        super(base);
        this.quads = quads;
    }

    /** Wraps every formed blockstate model of the communication interface once they are baked. */
    public static void bake(ModelEvent.ModifyBakingResult event) {
        var block = BuiltInRegistries.BLOCK.getOptional(INTERFACE).orElse(null);
        if (block == null || !(block.getStateDefinition().getProperty("formed") instanceof BooleanProperty formed)) {
            return;
        }
        FacePlateGeometry geometry;
        try {
            geometry = new FacePlateGeometry(
                    DeviceStructureBakedModel.tiles(INTERFACE.getNamespace(), "textures/" + TILES), TILES);
        } catch (Exception exception) {
            LOGGER.warn("Formed {} faces unavailable; it keeps its plain plate", INTERFACE, exception);
            return;
        }
        Function<String, TextureAtlasSprite> sprites = texture -> event.getTextureGetter().apply(new Material(
                TextureAtlas.LOCATION_BLOCKS, ResourceLocation.fromNamespaceAndPath(INTERFACE.getNamespace(), texture)));
        var built = new HashMap<List<Object>, List<List<BakedQuad>>>();
        for (var state : block.getStateDefinition().getPossibleStates()) {
            if (!state.getValue(formed)) continue;
            var location = BlockModelShaper.stateToModelLocation(state);
            var base = event.getModels().get(location);
            if (base == null) continue;
            var facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            int connections = connections(state);
            var quads = built.computeIfAbsent(List.of(facing, connections), key -> DeviceStructureBakedModel.bakeQuads(
                    geometry.build(Side.valueOf(facing.name()), DeviceStructureBakedModel.sides(connections)),
                    sprites));
            event.getModels().put(location, new FacePlateBakedModel(base, quads));
        }
    }

    /** Its {@link RouterBlock#CONNECTED} bits; {@link RouterBlock#connections} reads only the Router's own blocks. */
    private static int connections(BlockState state) {
        int connections = 0;
        for (int i = 0; i < RouterBlock.CONNECTED.length; i++) {
            if (state.getValue(RouterBlock.CONNECTED[i])) connections |= 1 << i;
        }
        return connections;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return quads.get(side == null ? 6 : side.get3DDataValue());
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
            ModelData data, @Nullable RenderType renderType) {
        return quads.get(side == null ? 6 : side.get3DDataValue());
    }
}
