package space.controlnet.ae2federation.mixin.compat;

import appeng.me.service.helpers.NetworkCraftingProviders;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Global registrations belong to the native Grid and disappear with it; no mirrored registry. */
@Mixin(NetworkCraftingProviders.class)
public interface NetworkCraftingProvidersAccess {
    @Accessor("globalProviders")
    List<?> ae2federation$globals();
}
