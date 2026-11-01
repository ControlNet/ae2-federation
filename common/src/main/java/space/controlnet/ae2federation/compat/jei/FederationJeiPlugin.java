package space.controlnet.ae2federation.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;
import space.controlnet.ae2federation.client.automation.ViewerScanClient;

/** Hands JEI's recipes to the server's programs ({@link ViewerScanClient}) while JEI has them. */
@JeiPlugin
public final class FederationJeiPlugin implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath("ae2federation", "recipes");
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        ViewerScanClient.scanner(new JeiRecipeScanner(runtime));
    }

    @Override
    public void onRuntimeUnavailable() {
        ViewerScanClient.scanner(null);
    }
}
