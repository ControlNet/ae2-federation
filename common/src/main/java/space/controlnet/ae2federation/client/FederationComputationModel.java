package space.controlnet.ae2federation.client;

import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import space.controlnet.ae2federation.compat.neoecoae.FederationComputationLooks;

/**
 * Neo ECO's and Neo ECO Prototype's computation parts, drawn dark inside a formed Federation computation structure
 * (user, 2026-11-02): each texture with a dark copy under {@code eco_computation/dark/<host>/}, at its path in the
 * host's computation folder, is drawn as that copy. {@code build_eco_computation_placeholder.py} writes them, so it
 * decides which parts turn dark. The same parts in a host structure, unformed, or as items keep the host's look. The
 * parts are found by ID, so no Neo ECO class loads.
 */
public final class FederationComputationModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Boolean> FEDERATION = new ModelProperty<>();
    /** Each host's part ID prefix and its part textures' folder. */
    private static final Map<String, String[]> HOSTS = Map.of(
            "neoecoae", new String[] {"computation_", "block/compute/"},
            "neoecoprototype", new String[] {"simplify_computation_", "block/computation_recolor/"});
    private static final String DARK = "block/eco_computation/dark/";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final @Nullable Field ORIGINAL = original();
    /** Set while a host renderer that passes no model data draws a part of a formed Federation structure. */
    private static final ThreadLocal<Boolean> DRAWING_FEDERATION = ThreadLocal.withInitial(() -> false);

    private final Map<TextureAtlasSprite, TextureAtlasSprite> dark;
    private final Map<BakedQuad, BakedQuad> darkened = new ConcurrentHashMap<>();

    private FederationComputationModel(BakedModel base, Map<TextureAtlasSprite, TextureAtlasSprite> dark) {
        super(base);
        this.dark = dark;
    }

    /**
     * Wraps every blockstate model of the hosts' computation parts once they are baked, if any of their textures has
     * a dark copy.
     */
    public static void bake(ModelEvent.ModifyBakingResult event) {
        var copies = Minecraft.getInstance().getResourceManager().listResources("textures/block/eco_computation/dark",
                path -> path.getPath().endsWith(".png")).keySet();
        for (var host : HOSTS.entrySet()) {
            var dark = new HashMap<TextureAtlasSprite, TextureAtlasSprite>();
            var prefix = "textures/" + DARK + host.getKey() + "/";
            for (var copy : copies) {
                if (!copy.getNamespace().equals("ae2federation") || !copy.getPath().startsWith(prefix)) continue;
                var relative = copy.getPath().substring(prefix.length(), copy.getPath().length() - ".png".length());
                var from = sprite(event, ResourceLocation.fromNamespaceAndPath(host.getKey(), host.getValue()[1] + relative));
                var to = sprite(event, ResourceLocation.fromNamespaceAndPath("ae2federation",
                        DARK + host.getKey() + "/" + relative));
                if (from != null && to != null) dark.put(from, to);
            }
            if (dark.isEmpty()) continue;
            var sprites = Map.copyOf(dark);
            for (var block : BuiltInRegistries.BLOCK) {
                var id = BuiltInRegistries.BLOCK.getKey(block);
                if (!id.getNamespace().equals(host.getKey()) || !id.getPath().startsWith(host.getValue()[0])) continue;
                for (var state : block.getStateDefinition().getPossibleStates()) {
                    var location = BlockModelShaper.stateToModelLocation(state);
                    var base = event.getModels().get(location);
                    if (base != null) event.getModels().put(location, wrap(base, sprites));
                }
            }
        }
    }

    /**
     * {@code base} wrapped, or, when it is a host's own wrapper, which the host's renderers find by its class, the
     * model inside it.
     */
    private static BakedModel wrap(BakedModel base, Map<TextureAtlasSprite, TextureAtlasSprite> sprites) {
        if (base instanceof BakedModelWrapper<?> host && ORIGINAL != null) {
            try {
                var inner = (BakedModel) ORIGINAL.get(host);
                if (!(inner instanceof FederationComputationModel)) {
                    ORIGINAL.set(host, new FederationComputationModel(inner, sprites));
                }
                return host;
            } catch (ReflectiveOperationException | RuntimeException unwritable) {
                LOGGER.warn("Cannot reach inside {}; it keeps the host's look", host.getClass().getName(), unwritable);
                return host;
            }
        }
        return new FederationComputationModel(base, sprites);
    }

    private static @Nullable Field original() {
        try {
            var field = BakedModelWrapper.class.getDeclaredField("originalModel");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            LOGGER.warn("Host model wrappers are unreachable; parts inside them keep the host's look", unreadable);
            return null;
        }
    }

    private static @Nullable TextureAtlasSprite sprite(ModelEvent.ModifyBakingResult event, ResourceLocation texture) {
        var sprite = event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS, texture));
        return sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation()) ? null : sprite;
    }

    /** Runs {@code draw}, a host renderer that passes no model data, drawing a Federation structure's part if asked. */
    public static void drawing(boolean federation, Runnable draw) {
        DRAWING_FEDERATION.set(federation);
        try {
            draw.run();
        } finally {
            DRAWING_FEDERATION.set(false);
        }
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData data) {
        var own = super.getModelData(level, pos, state, data);
        return FederationComputationLooks.contains(pos) ? own.derive().with(FEDERATION, true).build() : own;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, RandomSource random,
            ModelData data, @Nullable RenderType renderType) {
        var quads = super.getQuads(state, side, random, data, renderType);
        if (!Boolean.TRUE.equals(data.get(FEDERATION)) && !DRAWING_FEDERATION.get()) return quads;
        List<BakedQuad> result = null;
        for (int i = 0; i < quads.size(); i++) {
            var quad = quads.get(i);
            if (!dark.containsKey(quad.getSprite())) continue;
            if (result == null) result = new ArrayList<>(quads);
            result.set(i, darkened.computeIfAbsent(quad, this::darken));
        }
        return result == null ? quads : result;
    }

    /** The quad on the dark copy of its sprite, at the same place in it. */
    private BakedQuad darken(BakedQuad quad) {
        var from = quad.getSprite();
        var to = dark.get(from);
        var vertices = quad.getVertices().clone();
        for (int at = 0; at < vertices.length; at += IQuadTransformer.STRIDE) {
            float u = Float.intBitsToFloat(vertices[at + IQuadTransformer.UV0]);
            float v = Float.intBitsToFloat(vertices[at + IQuadTransformer.UV0 + 1]);
            vertices[at + IQuadTransformer.UV0] = Float.floatToRawIntBits(to.getU(from.getUOffset(u)));
            vertices[at + IQuadTransformer.UV0 + 1] = Float.floatToRawIntBits(to.getV(from.getVOffset(v)));
        }
        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), to, quad.isShade(),
                quad.hasAmbientOcclusion());
    }
}
