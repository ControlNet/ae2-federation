package space.controlnet.ae2federation.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import space.controlnet.ae2federation.router.CableVisualConnections;
import space.controlnet.ae2federation.router.FederationCableBlockEntity;
import space.controlnet.ae2federation.router.RouterRegistration;

/** Opt-in visual experiment using Minecraft's standard emissive translucent shader. */
public final class CableFlowRenderer implements BlockEntityRenderer<FederationCableBlockEntity> {
    private static volatile boolean enabled = Boolean.getBoolean("ae2federation.cableFlowPrototype");
    private static final RenderType FLOW = RenderType.entityTranslucentEmissive(
            ResourceLocation.fromNamespaceAndPath("ae2federation", "textures/entity/cable_flow.png"), false);

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(RouterRegistration.FEDERATION_CABLE_BLOCK_ENTITY.get(),
                context -> new CableFlowRenderer());
    }

    public static boolean isEnabled() {
        return enabled;
    }

    public static void registerCommands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("ae2f_cable_preview")
                .then(Commands.literal("on").executes(context -> setEnabled(true)))
                .then(Commands.literal("off").executes(context -> setEnabled(false))));
    }

    private static int setEnabled(boolean value) {
        enabled = value;
        var client = Minecraft.getInstance();
        client.levelRenderer.allChanged();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(
                    "Cable flow preview: " + (enabled ? "ON (standard shader)" : "OFF (V07)")), false);
        }
        return 1;
    }

    @Override
    public void render(FederationCableBlockEntity cable, float partialTick, PoseStack stack,
            MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var level = cable.getLevel();
        if (!enabled || level == null) return;
        var pos = cable.getBlockPos();
        int mask = CableVisualConnections.mask(level, pos);
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

    @Override
    public boolean shouldRender(FederationCableBlockEntity cable, Vec3 camera) {
        return enabled && BlockEntityRenderer.super.shouldRender(cable, camera);
    }
}
