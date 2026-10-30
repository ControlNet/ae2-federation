package space.controlnet.ae2federation.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.ChunkRenderTypeSet;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.power.BeamConverterBlock;
import space.controlnet.ae2federation.power.PowerRegistration;
import space.controlnet.ae2federation.quantum.QuantumRegistration;
import space.controlnet.ae2federation.router.RouterBlock;

/** Static chassis in chunk meshes; each member owns clipped geometry with continuous source UVs. */
public final class RefinedDeviceBakedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Key> MEMBER=new ModelProperty<>();
    private record Key(int section, Direction facing, double x, double y, double z) { }
    private final Shapes shapes;
    private RefinedDeviceBakedModel(BakedModel base,Shapes shapes) { super(base); this.shapes=shapes; }

    public static void bake(ModelEvent.ModifyBakingResult event) {
        RefinedDeviceAssets.reload();
        wrap(event,PowerRegistration.BEAM_CONVERTER.get(),true,false,true);
        wrap(event,PowerRegistration.CREATIVE_BEAM_CONVERTER.get(),true,true,false);
        wrap(event,QuantumRegistration.QUANTUM_CUBE.get(),false,false,false);
    }
    private static void wrap(ModelEvent.ModifyBakingResult event,Block block,boolean laser,boolean creative,boolean pixelPair) {
        var surface=event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS,creative ? RefinedDeviceAssets.CREATIVE_SURFACE : RefinedDeviceAssets.SURFACE));
        var emission=event.getTextureGetter().apply(new Material(TextureAtlas.LOCATION_BLOCKS,creative ? RefinedDeviceAssets.CREATIVE_EMISSION : RefinedDeviceAssets.EMISSION));
        var shapes=new Shapes(laser,pixelPair,surface,emission);
        for (var state:block.getStateDefinition().getPossibleStates()) {
            if (!RouterBlock.formed(state)) continue;
            var key=BlockModelShaper.stateToModelLocation(state);
            var base=event.getModels().get(key);
            if (base!=null) event.getModels().put(key,new RefinedDeviceBakedModel(base,shapes));
        }
    }
    /** A 1x1x2 member connects only along its facing; its blockstate picks the artist's converter or head. */
    private boolean pixelMember(BlockState state) {
        if (!shapes.pixelPair || state==null) return false;
        var facing=state.getValue(BeamConverterBlock.FACING);
        int connections=RouterBlock.connections(state);
        return connections==1<<facing.get3DDataValue() || connections==1<<facing.getOpposite().get3DDataValue();
    }
    @Override
    public ModelData getModelData(BlockAndTintGetter level,BlockPos pos,BlockState state,ModelData data) {
        if (pixelMember(state)) return super.getModelData(level,pos,state,data);
        var body=RefinedDeviceBody.find(level,pos,state);
        if (body==null) return ModelData.EMPTY;
        return ModelData.builder().with(MEMBER,new Key(body.section(),body.facing(),
                body.centre(Direction.Axis.X)-pos.getX(),body.centre(Direction.Axis.Y)-pos.getY(),
                body.centre(Direction.Axis.Z)-pos.getZ())).build();
    }
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state,@Nullable Direction side,RandomSource random) {
        return getQuads(state,side,random,ModelData.EMPTY,null);
    }
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state,@Nullable Direction side,RandomSource random,
            ModelData data,@Nullable RenderType renderType) {
        if (state==null || !RouterBlock.formed(state) || pixelMember(state)) {
            return super.getQuads(state,side,random,data,renderType);
        }
        if (side!=null) return List.of();
        var key=data.get(MEMBER);
        if (key!=null) return shapes.members.computeIfAbsent(key,shapes::member);
        return List.of();
    }
    @Override
    public ChunkRenderTypeSet getRenderTypes(BlockState state,RandomSource random,ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.cutout());
    }
    @Override public boolean useAmbientOcclusion() { return false; }

    private static final class Shapes {
        private final boolean laser,pixelPair;
        private final TextureAtlasSprite surface,emission;
        private final Map<Key,List<BakedQuad>> members=new ConcurrentHashMap<>();
        Shapes(boolean laser,boolean pixelPair,TextureAtlasSprite surface,TextureAtlasSprite emission) {
            this.laser=laser; this.pixelPair=pixelPair; this.surface=surface; this.emission=emission;
        }
        List<BakedQuad> member(Key key) {
            return bake(RefinedDeviceMesh.cell(RefinedDeviceAssets.mesh((laser ? "laser-" : "quantum-")+key.section),
                    key.facing,key.x,key.y,key.z));
        }
        List<BakedQuad> bake(List<RefinedDeviceMesh.Quad> source) {
            var result=new ArrayList<BakedQuad>();
            for (int pass=0;pass<2;pass++) {
                var sprite=pass==0 ? surface : emission;
                var baker=new QuadBakingVertexConsumer();
                for (var quad:source) {
                    var n=quad.normal();
                    baker.setDirection(Direction.getNearest(n[0],n[1],n[2]));
                    baker.setSprite(sprite); baker.setShade(pass==0); baker.setHasAmbientOcclusion(false); baker.setTintIndex(-1);
                    for (var v:quad.vertices()) baker.addVertex((float)v.x(),(float)v.y(),(float)v.z())
                            .setColor(255,255,255,255).setUv(sprite.getU((float)v.u()),sprite.getV((float)v.v()))
                            .setUv2(pass==0 ? 0 : 240,pass==0 ? 0 : 240)
                            .setNormal((float)n[0],(float)n[1],(float)n[2]);
                    result.add(baker.bakeQuad());
                }
            }
            return List.copyOf(result);
        }
    }
}
