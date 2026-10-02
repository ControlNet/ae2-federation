package space.controlnet.ae2federation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import space.controlnet.ae2federation.router.FederationCableBlockEntity;
import space.controlnet.ae2federation.router.RouterRegistration;

/** Cable interiors rendered with Minecraft's standard emissive translucent shader. */
public final class CableFlowRenderer implements BlockEntityRenderer<FederationCableBlockEntity> {
    private static final RenderType FLOW = RenderType.entityTranslucentEmissive(
            ResourceLocation.fromNamespaceAndPath("ae2federation", "textures/entity/cable_flow.png"), false);

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(RouterRegistration.FEDERATION_CABLE_BLOCK_ENTITY.get(),
                context -> new CableFlowRenderer());
    }

    @Override
    public void render(FederationCableBlockEntity cable, float partialTick, PoseStack stack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var level = cable.getLevel();
        if (level == null) return;
        var pos = cable.getBlockPos();
        int mask = cable.flowMask();
        var vertices = buffers.getBuffer(FLOW);
        var pose = stack.last();
        // These are decorative pulses, not packet paths or measured traffic.
        for (var ribbon : CableFlowGeometry.ribbons(mask)) {
            int coordinate = switch (ribbon.axis()) {
                case 0 -> pos.getX();
                case 1 -> pos.getY();
                default -> pos.getZ();
            };
            float phase = CableFlowGeometry.phase(coordinate, level.getGameTime(), partialTick);
            for (var vertex : ribbon.vertices()) {
                float u = switch (ribbon.layer()) {
                    case FLOW -> phase + vertex.along();
                    case HUB -> vertex.along();
                    case BASE, BEND -> 0.03125f;
                };
                float v = vertex.across() * 0.5f + (ribbon.layer() == CableFlowGeometry.Layer.HUB ? 0.5f : 0);
                vertices.addVertex(pose, vertex.x(), vertex.y(), vertex.z())
                        .setColor(255, 255, 255, Math.round(vertex.alpha() * 255))
                        .setUv(u, v)
                        .setOverlay(OverlayTexture.NO_OVERLAY)
                        .setLight(LightTexture.FULL_BRIGHT)
                        .setNormal(pose, 0, 1, 0);
            }
        }
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
