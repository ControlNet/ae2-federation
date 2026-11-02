package space.controlnet.ae2federation.compat.neoecoae;

import cn.dancingsnow.neoecoae.client.rendering.FixedBlockEntityRenderers;
import cn.dancingsnow.neoecoae.client.rendering.IFixedBlockEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.slf4j.Logger;
import space.controlnet.ae2federation.client.FederationComputationModel;

/**
 * Neo ECO and Neo ECO Prototype draw some formed parts, such as their cooling controllers, with fixed block entity
 * renderers, which ask the model for its quads with no position or model data. This registers each such renderer
 * again behind one that tells {@link FederationComputationModel} when the part it draws stands in a formed Federation
 * structure, so the part is drawn dark there as the others are (user, 2026-11-02). It refers to Neo ECO, so only a
 * client with Neo ECO loaded may reach it, once the hosts' client setup has registered their renderers.
 */
public final class HostFixedRenderers {
    private static final Logger LOGGER = LogUtils.getLogger();

    private HostFixedRenderers() {
    }

    public static void install() {
        Map<BlockEntityType<?>, IFixedBlockEntityRenderer<?>> registered;
        try {
            var field = FixedBlockEntityRenderers.class.getDeclaredField("renderers");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            var renderers = (Map<BlockEntityType<?>, IFixedBlockEntityRenderer<?>>) field.get(null);
            registered = new HashMap<>(renderers);
        } catch (ReflectiveOperationException | RuntimeException unreadable) {
            LOGGER.warn("Neo ECO's fixed renderers are unreadable; parts they draw keep the host's look", unreadable);
            return;
        }
        registered.forEach(HostFixedRenderers::mark);
    }

    @SuppressWarnings("unchecked")
    private static <T extends BlockEntity> void mark(BlockEntityType<?> type, IFixedBlockEntityRenderer<?> own) {
        if (own instanceof Marked<?>) return;
        FixedBlockEntityRenderers.register((BlockEntityType<T>) type, new Marked<>((IFixedBlockEntityRenderer<T>) own));
    }

    private record Marked<T extends BlockEntity>(IFixedBlockEntityRenderer<T> own) implements IFixedBlockEntityRenderer<T> {
        @Override
        public void renderFixed(T entity, float partialTick, PoseStack pose, MultiBufferSource buffers, int light,
                int overlay) {
            FederationComputationModel.drawing(FederationComputationLooks.contains(entity.getBlockPos()),
                    () -> own.renderFixed(entity, partialTick, pose, buffers, light, overlay));
        }
    }
}
