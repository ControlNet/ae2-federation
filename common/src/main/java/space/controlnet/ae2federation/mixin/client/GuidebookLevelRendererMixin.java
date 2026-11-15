package space.controlnet.ae2federation.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.sugar.Local;
import guideme.scene.GuidebookLevelRenderer;
import guideme.scene.level.GuidebookLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.controlnet.ae2federation.client.BeamPortalRenderer;

/**
 * Defers portal light until scene terrain has populated depth, in guide screens and scene exports. GuideME 21.1.1 to
 * 21.1.19 render both through the public renderContent and its lambda {@code $0}. From 21.1.20 the public method (and
 * its lambda {@code $1}) is the export path only; guide screens go through a private renderContent overload with a
 * SceneMeshCache, whose lambda {@code $2} is hooked at head and tail instead. All lambda hooks are optional: a build
 * that names them differently shows no portal in scenes and fails nothing.
 */
@Mixin(value = GuidebookLevelRenderer.class, remap = false)
public abstract class GuidebookLevelRendererMixin {
    @WrapMethod(method = "renderContent(Lguideme/scene/level/GuidebookLevel;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V")
    private void ae2federation$scopePortals(GuidebookLevel level, MultiBufferSource.BufferSource buffers,
            Operation<Void> original) {
        BeamPortalRenderer.inScene(() -> original.call(level, buffers));
    }

    /**
     * GuideME 21.1.1 runs renderContent's passes inside RenderSystem.runAsFancy's lambda, and its translucent pass
     * queues every block's solid and cutout quads a second time; left queued, they are drawn after the portal and
     * cover it, as it writes no depth. Drawn here, at their first pass's depth, they change nothing. GuideME 21.1.20
     * names the export lambda {@code $1} and ends it with a full endBatch, so the flush there finds nothing left.
     */
    @Inject(method = {
            "lambda$renderContent$0(Lguideme/scene/level/GuidebookLevel;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V",
            "lambda$renderContent$1(Lguideme/scene/level/GuidebookLevel;"
                    + "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V"},
            at = @At("TAIL"), require = 0)
    private void ae2federation$drawPortals(GuidebookLevel level, MultiBufferSource.BufferSource buffers,
            CallbackInfo callback) {
        for (var layer : RenderType.chunkBufferLayers()) {
            if (layer != RenderType.translucent()) buffers.endBatch(layer);
        }
        BeamPortalRenderer.drawScene(buffers);
    }

    /** The guide screen pass from GuideME 21.1.20; its SceneMeshCache argument is not on the 21.1.1 build path. */
    @Inject(method = "lambda$renderContent$2(Lguideme/scene/level/GuidebookLevel;Lguideme/scene/SceneMeshCache;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V", at = @At("HEAD"), require = 0)
    private void ae2federation$openMeshScene(CallbackInfo callback) {
        BeamPortalRenderer.beginScene();
    }

    /** Block meshes and every buffered layer are drawn by now, so the portal lands on finished depth. */
    @Inject(method = "lambda$renderContent$2(Lguideme/scene/level/GuidebookLevel;Lguideme/scene/SceneMeshCache;"
            + "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)V", at = @At("TAIL"), require = 0)
    private void ae2federation$drawMeshScenePortals(CallbackInfo callback,
            @Local(argsOnly = true) MultiBufferSource.BufferSource buffers) {
        try {
            BeamPortalRenderer.drawScene(buffers);
        } finally {
            BeamPortalRenderer.endScene();
        }
    }
}
