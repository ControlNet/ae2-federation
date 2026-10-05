package space.controlnet.ae2federation.mixin.client;

import guideme.Guide;
import guideme.GuideBuilder;
import guideme.compiler.TagCompiler;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.client.guide.FederationTopologyTagCompiler;

/**
 * Adds {@code <FederationTopology>} to AE2's guide, which our pages join. GuideME takes tag compilers only through the
 * {@link GuideBuilder} that builds a guide, and AE2 builds its own privately with no hook for other mods, so this
 * hands the compiler to that builder just before it builds.
 */
@Mixin(value = GuideBuilder.class, remap = false)
public abstract class GuideBuilderMixin {
    private static final ResourceLocation AE2_GUIDE = ResourceLocation.fromNamespaceAndPath("ae2", "guide");

    @Shadow
    @Final
    private ResourceLocation id;

    @Inject(method = "build", at = @At("HEAD"))
    private void ae2federation$addTopologyTag(CallbackInfoReturnable<Guide> callback) {
        if (AE2_GUIDE.equals(id)) {
            ((GuideBuilder) (Object) this).extension(TagCompiler.EXTENSION_POINT, new FederationTopologyTagCompiler());
        }
    }
}
