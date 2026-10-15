package space.controlnet.ae2federation.mixin.compat;

import appeng.api.networking.crafting.ICraftingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** AE2 19.2.17 ProviderState; exposes the registered object without replaying registration. */
@Mixin(targets = "appeng.me.service.helpers.NetworkCraftingProviders$ProviderState")
public interface CraftingProviderStateAccess {
    @Accessor("provider")
    ICraftingProvider ae2federation$provider();
}
