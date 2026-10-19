package space.controlnet.ae2federation.mixin.compat;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.CraftingService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;

/**
 * AE2 19.2.17 compatibility: the public registration API has no removal event. Observe its completed removal,
 * not NetworkCraftingProviders.removeProvider, which is also used internally during refresh's remove/add pair.
 * No registrations are replayed and AE2 remains the authoritative provider registry.
 */
@Mixin(CraftingService.class)
public abstract class CraftingGlobalProviderLifecycleMixin {
    @Shadow @Final private IGrid grid;

    @Unique private MinecraftServer ae2federation$server;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void ae2federation$captureServer(CallbackInfo callback) {
        // The constructor has a pivot; unregistration may happen after the last node left the Grid.
        ae2federation$server = grid.getPivot().getLevel().getServer();
    }

    @Inject(method = "removeGlobalCraftingProvider", at = @At("RETURN"))
    private void ae2federation$removed(ICraftingProvider provider, CallbackInfo callback) {
        CraftingProjectionService.globalProviderRemoved(ae2federation$server, grid, provider);
    }
}
