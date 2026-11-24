package space.controlnet.ae2federation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Quaternionf;
import space.controlnet.ae2federation.bundle.BundleBeam;
import space.controlnet.ae2federation.power.PowerRegistration;
import space.controlnet.ae2federation.quantum.QuantumRegistration;
import space.controlnet.ae2federation.router.FederationCableBlockEntity;
import space.controlnet.ae2federation.router.RouterBlock;

/** Only moving core layers and optical carriers are drawn each frame; the chassis stays in chunk meshes. */
public final class RefinedDeviceRenderer<T extends BlockEntity> implements BlockEntityRenderer<T> {
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PowerRegistration.BEAM_CONVERTER_BLOCK_ENTITY.get(),c -> new RefinedDeviceRenderer<>());
        event.registerBlockEntityRenderer(QuantumRegistration.QUANTUM_CUBE_BLOCK_ENTITY.get(),c -> new RefinedDeviceRenderer<>());
    }
    private RefinedDeviceBody body(T device) {
        var state=device.getBlockState();
        // Only the lowest corner can own the animated part, so other members never walk the body.
        int negative=(1<<Direction.WEST.get3DDataValue())|(1<<Direction.DOWN.get3DDataValue())|(1<<Direction.NORTH.get3DDataValue());
        if (!RouterBlock.formed(state) || (RouterBlock.connections(state)&negative)!=0 || device.getLevel()==null) return null;
        return RefinedDeviceBody.find(device.getLevel(),device.getBlockPos(),state);
    }
    @Override
    public void render(T device,float partialTick,PoseStack stack,MultiBufferSource buffers,int light,int overlay) {
        var body=body(device);
        if (body==null) return;
        var level=device.getLevel(); var pos=device.getBlockPos();
        var centre=new Vec3(body.centre(Direction.Axis.X),body.centre(Direction.Axis.Y),body.centre(Direction.Axis.Z));
        stack.pushPose();
        stack.translate(centre.x-pos.getX(),centre.y-pos.getY(),centre.z-pos.getZ());
        if (!body.laser()) {
            float radius=(body.section()+2)/4f;
            stack.pushPose(); stack.scale(radius,radius,radius);
            float angle=((level.getGameTime()%160)+partialTick)*(float)(2*Math.PI/160);
            drawCore("core-centre",stack,buffers,light,overlay);
            for (var layer:new String[]{"outer","inner"}) {
                stack.pushPose();
                // Blender XYZ Euler, converted from Z-up to Minecraft Y-up.
                stack.mulPose(Axis.YP.rotation(layer.equals("outer") ? angle : -angle));
                stack.mulPose(Axis.ZP.rotation(-.12f));
                stack.mulPose(Axis.XP.rotation(layer.equals("outer") ? .12f : -.25f));
                drawCore("core-"+layer,stack,buffers,light,overlay);
                stack.popPose();
            }
            stack.popPose();
        }
        if (body.laser()) {
            var axis=body.facing().getAxis();
            double half=(body.max().get(axis)-body.min().get(axis)+1)/2.0;
            var front=Vec3.atLowerCornerOf(body.facing().getNormal()).scale(half);
            beam(stack,buffers,front,front.scale(-.75),body.section(),level.getGameTime(),partialTick,CableBeamStyle.fromLoad(0,false));
        } else {
            // Each connected cable contributes one carrier; bundle non-owners contribute none. A thick bundle against a
            // face from its side turns its one beam into the body once per stretch: its layer's beam owner carries it.
            var sideOwners=new java.util.HashSet<BlockPos>();
            for (var face:Direction.values()) {
                var axis=face.getAxis();
                int edge=face.getAxisDirection()==Direction.AxisDirection.POSITIVE ? body.max().get(axis) : body.min().get(axis);
                int bit=java.util.List.of(space.controlnet.ae2federation.router.CableVisualConnections.DIRECTIONS)
                        .indexOf(face.getOpposite());
                for (var cell:BlockPos.betweenClosed(body.min(),body.max())) {
                    if (cell.get(axis)!=edge) continue;
                    // The port rule excludes the frame's outer one-block border.
                    boolean inside=true;
                    for (var across:Direction.Axis.values()) if (across!=axis
                            && (cell.get(across)<=body.min().get(across) || cell.get(across)>=body.max().get(across))) inside=false;
                    if (!inside) continue;
                    var adjacent=cell.relative(face);
                    if (!(level.getBlockEntity(adjacent) instanceof FederationCableBlockEntity cable)) continue;
                    var bundle=cable.bundleBeam();
                    var segment=cable.bundleSegment();
                    if (bundle!=null && segment!=null && bundle.axis()!=axis) {
                        var owner=sideOwner(level,adjacent,segment,bundle.axis());
                        if (owner==null || !sideOwners.add(owner.pos()) || (owner.beam().mask()&1<<bit)==0) continue;
                        int side=owner.beam().side();
                        var start=Vec3.atLowerCornerOf(owner.pos());
                        for (var across:Direction.Axis.values()) {
                            var direction=Direction.fromAxisAndDirection(across,Direction.AxisDirection.POSITIVE);
                            double reach=across==owner.beam().axis() ? owner.beam().length()/2.0 : side/2.0;
                            start=start.add(Vec3.atLowerCornerOf(direction.getNormal()).scale(reach));
                        }
                        start=start.add(Vec3.atLowerCornerOf(face.getOpposite().getNormal()).scale(side/2.0));
                        beam(stack,buffers,start.subtract(centre),Vec3.ZERO,side,level.getGameTime(),partialTick,
                                CableFlowRenderer.style(owner.cable()));
                        continue;
                    }
                    if (bundle!=null && bundle.length()==0 && face.getAxisDirection()==Direction.AxisDirection.NEGATIVE)
                        bundle=covering(level,adjacent,face,bundle);
                    if (((bundle==null ? cable.flowMask() : bundle.mask()) & 1<<bit)==0) continue;
                    int side=1;
                    var start=Vec3.atCenterOf(cell).add(Vec3.atLowerCornerOf(face.getNormal()).scale(.5));
                    if (bundle!=null) {
                        if (bundle.length()==0 || bundle.axis()!=axis) continue;
                        side=bundle.side();
                        for (var across:Direction.Axis.values()) if (across!=axis) {
                            var direction=Direction.fromAxisAndDirection(across,Direction.AxisDirection.POSITIVE);
                            start=start.add(Vec3.atLowerCornerOf(direction.getNormal()).scale((side-1)/2.0));
                        }
                    }
                    beam(stack,buffers,start.subtract(centre),Vec3.ZERO,side,level.getGameTime(),partialTick,
                            CableFlowRenderer.style(cable));
                }
            }
        }
        stack.popPose();
        CableFlowRenderer.endSceneBatch(level,buffers);
    }
    /**
     * The beam of the box an earlier layer stretches over {@code cell}'s layer, looking back {@code back} along the
     * bundle, or {@code own} when no box covers it: such a layer draws none, but the carrier still leaves through it.
     */
    private static BundleBeam covering(net.minecraft.world.level.Level level,BlockPos cell,Direction back,BundleBeam own) {
        for (int k=1;k<own.side();k++) {
            if (!(level.getBlockEntity(cell.relative(back,k)) instanceof FederationCableBlockEntity cable)) break;
            var beam=cable.bundleBeam();
            if (beam==null || beam.axis()!=back.getAxis()) break;
            if (beam.length()>k) return beam;
            if (beam.length()>0) break;
        }
        return own;
    }
    /** The cell that draws the beam over a layer, with that beam and its cable. */
    private record Owner(BlockPos pos,BundleBeam beam,FederationCableBlockEntity cable) {}
    /**
     * The owner of the beam that runs over {@code cell}'s layer of {@code segment}: the layer's low corner, or the
     * earlier layer whose box stretches over it.
     */
    private static Owner sideOwner(net.minecraft.world.level.Level level,BlockPos cell,
            space.controlnet.ae2federation.bundle.BundleSegment segment,Direction.Axis along) {
        var corner=cell;
        for (var across:Direction.Axis.values()) if (across!=along)
            corner=space.controlnet.ae2federation.bundle.BundleSegment.with(corner,across,segment.min().get(across));
        var back=Direction.fromAxisAndDirection(along,Direction.AxisDirection.NEGATIVE);
        for (int k=0;k<3;k++) {
            var at=corner.relative(back,k);
            if (!(level.getBlockEntity(at) instanceof FederationCableBlockEntity cable)) return null;
            var beam=cable.bundleBeam();
            if (beam==null || beam.axis()!=along) return null;
            if (beam.length()>k) return new Owner(at,beam,cable);
            if (beam.length()>0) return null;
        }
        return null;
    }
    private static void drawCore(String name,PoseStack stack,MultiBufferSource buffers,int light,int overlay) {
        for (int pass=0;pass<2;pass++) {
            var out=buffers.getBuffer(pass==0 ? RenderType.entityCutoutNoCull(RefinedDeviceAssets.SURFACE_FILE)
                    : RenderType.entityTranslucentEmissive(RefinedDeviceAssets.EMISSION_FILE));
            for (var quad:RefinedDeviceAssets.mesh(name)) {
                var n=quad.normal();
                for (var v:quad.vertices()) out.addVertex(stack.last(),(float)v.x(),(float)v.y(),(float)v.z())
                        .setColor(255,255,255,255).setUv((float)v.u(),(float)v.v()).setOverlay(overlay)
                        .setLight(pass==0 ? light : LightTexture.FULL_BRIGHT)
                        .setNormal(stack.last(),(float)n[0],(float)n[1],(float)n[2]);
            }
        }
    }
    private static void beam(PoseStack stack,MultiBufferSource buffers,Vec3 start,Vec3 end,int side,long tick,
            float partialTick,CableBeamStyle style) {
        var delta=end.subtract(start); double length=delta.length();
        if (length<.001) return;
        stack.pushPose(); stack.translate(start.x,start.y,start.z);
        stack.mulPose(new Quaternionf().rotationTo(0,0,1,(float)(delta.x/length),(float)(delta.y/length),(float)(delta.z/length)));
        stack.scale(1,1,(float)length);
        stack.translate(-side/2.0,-side/2.0,0);
        if (side==1) CableFlowRenderer.emitCell(stack,buffers,3<<4,0,0,0,tick,partialTick,style,1);
        else CableFlowRenderer.emitBundle(stack,buffers,new BundleBeam(Direction.Axis.Z,3<<4,1,side),0,0,0,tick,partialTick,style,1);
        stack.popPose();
    }
    @Override public AABB getRenderBoundingBox(T device) {
        var body=body(device);
        return body==null ? new AABB(device.getBlockPos()) : new AABB(Vec3.atLowerCornerOf(body.min()),Vec3.atLowerCornerOf(body.max().offset(1,1,1)));
    }
    /** The owner's body may reach into sections the camera keeps while it culls the owner's own. */
    @Override public boolean shouldRenderOffScreen(T device) {
        var state=device.getBlockState();
        int negative=(1<<Direction.WEST.get3DDataValue())|(1<<Direction.DOWN.get3DDataValue())|(1<<Direction.NORTH.get3DDataValue());
        return RouterBlock.formed(state) && (RouterBlock.connections(state)&negative)==0;
    }
    @Override public int getViewDistance() { return 128; }
}
