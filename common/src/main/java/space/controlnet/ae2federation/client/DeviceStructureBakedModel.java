package space.controlnet.ae2federation.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.client.Minecraft;
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
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import space.controlnet.ae2federation.client.DeviceStructureGeometry.Device;
import space.controlnet.ae2federation.client.DeviceStructureGeometry.Side;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * A formed Router or Switch member's model: the connected geometry of {@link DeviceStructureGeometry} for the
 * connection properties of its blockstate, baked into the chunk mesh like the standalone model. A block with no
 * connections keeps the artist's standalone model unchanged. Quads are built the first time a chunk needs a connection
 * set and kept until the next resource reload, which rebuilds them from the then current model and texture.
 */
public final class DeviceStructureBakedModel extends BakedModelWrapper<BakedModel> {
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Shapes shapes;
    private final int connections;

    private DeviceStructureBakedModel(BakedModel base, Shapes shapes, int connections) {
        super(base);
        this.shapes = shapes;
        this.connections = connections;
    }

    /** Wraps every connected blockstate model of the Router and the Switch once they are baked. */
    public static void bake(ModelEvent.ModifyBakingResult event) {
        bake(event, RouterRegistration.ROUTER.get(), Device.ROUTER);
        bake(event, RouterRegistration.SWITCH.get(), Device.SWITCH);
    }

    private static void bake(ModelEvent.ModifyBakingResult event, RouterBlock block, Device device) {
        var id = BuiltInRegistries.BLOCK.getKey(block);
        var modelPath = ResourceLocation.fromNamespaceAndPath(id.getNamespace(), "models/block/" + id.getPath() + ".json");
        Shapes shapes;
        try (var reader = new InputStreamReader(Minecraft.getInstance().getResourceManager().open(modelPath),
                StandardCharsets.UTF_8)) {
            var model = JsonParser.parseReader(reader).getAsJsonObject();
            var sprite = event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS, texture(model)));
            shapes = new Shapes(new DeviceStructureGeometry(device, model), sprite);
        } catch (Exception exception) {
            // A resource pack model without the artist's groups still shows the standalone model on every member.
            LOGGER.warn("Formed {} model unavailable; members keep the standalone model", id, exception);
            return;
        }
        for (var state : block.getStateDefinition().getPossibleStates()) {
            int connections = RouterBlock.connections(state);
            var location = BlockModelShaper.stateToModelLocation(state);
            var base = event.getModels().get(location);
            if (connections != 0 && base != null) {
                event.getModels().put(location, new DeviceStructureBakedModel(base, shapes, connections));
            }
        }
    }

    private static ResourceLocation texture(JsonObject model) {
        var name = model.getAsJsonObject("textures").get("0").getAsString();
        return ResourceLocation.parse(name);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random) {
        return shapes.quads(connections, side);
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
            ModelData data, @Nullable RenderType renderType) {
        return shapes.quads(connections, side);
    }

    /** One device's quads for each connection set, by cull face (index 6 holds the unculled ones). */
    private static final class Shapes {
        private final DeviceStructureGeometry geometry;
        private final TextureAtlasSprite sprite;
        private final AtomicReferenceArray<List<List<BakedQuad>>> byConnections = new AtomicReferenceArray<>(64);

        Shapes(DeviceStructureGeometry geometry, TextureAtlasSprite sprite) {
            this.geometry = geometry;
            this.sprite = sprite;
        }

        List<BakedQuad> quads(int connections, @Nullable Direction side) {
            var built = byConnections.get(connections);
            if (built == null) {
                built = build(connections);
                byConnections.compareAndSet(connections, null, built);
            }
            return built.get(side == null ? 6 : side.get3DDataValue());
        }

        private List<List<BakedQuad>> build(int connections) {
            var sides = EnumSet.noneOf(Side.class);
            for (var direction : Direction.values()) {
                if ((connections & 1 << direction.get3DDataValue()) != 0) sides.add(side(direction));
            }
            var lists = new ArrayList<List<BakedQuad>>();
            for (int i = 0; i < 7; i++) lists.add(new ArrayList<>());
            var baker = new QuadBakingVertexConsumer();
            for (var quad : geometry.build(sides)) {
                var normal = DeviceStructureGeometry.normal(quad.positions());
                var direction = Direction.getNearest(normal[0], normal[1], normal[2]);
                var ordered = vanillaOrder(quad, direction);
                baker.setDirection(direction);
                baker.setSprite(sprite);
                baker.setShade(true);
                baker.setHasAmbientOcclusion(true);
                baker.setTintIndex(-1);
                for (int i = 0; i < 4; i++) {
                    var p = quad.positions()[ordered[i]];
                    var uv = quad.uv()[ordered[i]];
                    baker.addVertex((float) p[0], (float) p[1], (float) p[2])
                            .setColor(255, 255, 255, 255)
                            .setUv(sprite.getU((float) uv[0]), sprite.getV((float) uv[1]))
                            .setUv2(0, 0)
                            .setNormal(direction.getStepX(), direction.getStepY(), direction.getStepZ());
                }
                var cull = onBlockFace(quad.positions()[0], direction) ? direction.get3DDataValue() : 6;
                lists.get(cull).add(baker.bakeQuad());
            }
            var result = new ArrayList<List<BakedQuad>>();
            for (var list : lists) result.add(List.copyOf(list));
            return List.copyOf(result);
        }
    }

    private static final Map<Direction, Side> SIDES = new EnumMap<>(Direction.class);
    static {
        for (var direction : Direction.values()) SIDES.put(direction, Side.valueOf(direction.name()));
    }

    private static Side side(Direction direction) {
        return SIDES.get(direction);
    }

    /**
     * The quad's corners in the order vanilla's FaceBakery gives a face toward {@code direction}, which smooth
     * lighting relies on to light each corner from the right neighbours.
     */
    private static int[] vanillaOrder(DeviceStructureGeometry.Quad quad, Direction direction) {
        var lo = new double[] {Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE};
        var hi = new double[] {-Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
        for (var p : quad.positions()) {
            for (int a = 0; a < 3; a++) {
                lo[a] = Math.min(lo[a], p[a]);
                hi[a] = Math.max(hi[a], p[a]);
            }
        }
        var corners = DeviceStructureGeometry.facePoints(lo, hi, side(direction));
        var order = new int[4];
        for (int i = 0; i < 4; i++) {
            order[i] = i;
            for (int j = 0; j < 4; j++) {
                var p = quad.positions()[j];
                if (Math.abs(p[0] - corners[i][0]) < 1e-6 && Math.abs(p[1] - corners[i][1]) < 1e-6
                        && Math.abs(p[2] - corners[i][2]) < 1e-6) {
                    order[i] = j;
                    break;
                }
            }
        }
        return order;
    }

    /** Whether a quad facing {@code direction} lies on the block's own face there, where a full neighbour hides it. */
    private static boolean onBlockFace(double[] corner, Direction direction) {
        double coordinate = corner[direction.getAxis().ordinal()];
        return direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? Math.abs(coordinate - 1) < 1e-6
                : Math.abs(coordinate) < 1e-6;
    }
}
