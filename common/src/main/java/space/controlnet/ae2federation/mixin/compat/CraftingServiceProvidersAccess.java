package space.controlnet.ae2federation.mixin.compat;

import appeng.me.service.CraftingService;
import appeng.me.service.helpers.NetworkCraftingProviders;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only access to AE2 19.2.17's authoritative crafting registrations. */
@Mixin(CraftingService.class)
public interface CraftingServiceProvidersAccess {
    @Accessor("craftingProviders")
    NetworkCraftingProviders ae2federation$providers();
}
