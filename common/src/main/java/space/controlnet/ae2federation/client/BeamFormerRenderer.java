package space.controlnet.ae2federation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import space.controlnet.ae2federation.beam.BeamFormerBlockEntity;
import space.controlnet.ae2federation.beam.BeamRegistration;

/**
 * A linked Beam Former pair's beam, from front to front: the cable's light, stretched across the square and drawn one
 * block at a time along the beam so its pulses keep a cable's pace. It shows the pair's capacity count as a cable's
 * beam does, and red when the domain is over capacity or unpowered. The end facing the positive direction draws it.
 */
public final class BeamFormerRenderer implements BlockEntityRenderer<BeamFormerBlockEntity> {
    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(BeamRegistration.BEAM_FORMER_BLOCK_ENTITY.get(),
                context -> new BeamFormerRenderer());
    }

    @Override
    public void render(BeamFormerBlockEntity former, float partialTick, PoseStack stack, MultiBufferSource buffers,
            int packedLight, int packedOverlay) {
        var level = former.getLevel();
        var look = former.look();
        var facing = former.facing();
        if (level == null || look == null || facing.getAxisDirection() != Direction.AxisDirection.POSITIVE) return;
        var axis = facing.getAxis();
        var origin = former.getBlockPos();
        int gap = look.partner().get(axis) - origin.get(axis) - 1;
        if (gap <= 0) return;
        double target = look.load();
        // An unpowered domain's beam alarms at the width of no load, as a cable's does.
        var style = look.unpowered() ? CableBeamStyle.fromLoad(0, true)
                : CableBeamStyle.fromLoad(former.beamLoad(target, Util.getNanos()), target > 1);
        int mask = 3 << 2 * axis.ordinal();
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        long tick = level.getGameTime();
        float across = look.side();
        for (int step = 1; step <= gap; step++) {
            var cell = origin.relative(facing, step);
            stack.pushPose();
            stack.translate(facing.getStepX() * step, facing.getStepY() * step, facing.getStepZ() * step);
            stack.scale(axis == Direction.Axis.X ? 1 : across, axis == Direction.Axis.Y ? 1 : across,
                    axis == Direction.Axis.Z ? 1 : across);
            double distance = camera.distanceToSqr(cell.getX() + across / 2, cell.getY() + across / 2,
                    cell.getZ() + across / 2);
            CableFlowRenderer.emitCell(stack, buffers, mask, cell.getX(), cell.getY(), cell.getZ(), tick, partialTick,
                    style, CableBeamMesh.detail(distance), look.side() > 1);
            stack.popPose();
        }
        CableFlowRenderer.endSceneBatch(level, buffers);
    }

    /** The beam reaches from this end's front to the partner's, across the square. */
    @Override
    public AABB getRenderBoundingBox(BeamFormerBlockEntity former) {
        var look = former.look();
        var origin = former.getBlockPos();
        if (look == null) return new AABB(origin);
        var partner = look.partner();
        return new AABB(Math.min(origin.getX(), partner.getX()), Math.min(origin.getY(), partner.getY()),
                Math.min(origin.getZ(), partner.getZ()), Math.max(origin.getX(), partner.getX()) + look.side(),
                Math.max(origin.getY(), partner.getY()) + look.side(),
                Math.max(origin.getZ(), partner.getZ()) + look.side());
    }

    @Override
    public boolean shouldRenderOffScreen(BeamFormerBlockEntity former) {
        return former.look() != null;
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
