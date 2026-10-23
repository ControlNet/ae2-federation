package space.controlnet.ae2federation.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import guideme.scene.GuidebookLevelRenderer;
import guideme.scene.level.GuidebookLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.controlnet.ae2federation.client.BeamPortalRenderer;

/** Defers portal light until scene terrain has populated depth, including scene exports. */
@Mixin(value = GuidebookLevelRenderer.class, remap = false)
public abstract class GuidebookLevelRendererMixin {
    @WrapMethod(method = "renderContent")
    private void ae2federation$scopePortals(GuidebookLevel level, MultiBufferSource.BufferSource buffers,
            Operation<Void> original) {
        BeamPortalRenderer.inScene(() -> original.call(level, buffers));
    }

    /**
     * GuideME 21.1.1 runs renderContent's passes inside RenderSystem.runAsFancy's lambda, and its translucent pass
     * queues every block's solid and cutout quads a second time; left queued, they are drawn after the portal and
     * cover it, as it writes no depth. Drawn here, at their first pass's depth, they change nothing. Another build may
     * name the lambda differently: then scenes show no portal, and the scope above still clears what was recorded.
     */
    @Inject(method = "lambda$renderContent$0", at = @At("TAIL"), require = 0)
    private void ae2federation$drawPortals(GuidebookLevel level, MultiBufferSource.BufferSource buffers,
            CallbackInfo callback) {
        for (var layer : RenderType.chunkBufferLayers()) {
            if (layer != RenderType.translucent()) buffers.endBatch(layer);
        }
        BeamPortalRenderer.drawScene(buffers);
    }
}
