package space.controlnet.ae2federation.mixin;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartItem;
import appeng.parts.CableBusContainer;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import space.controlnet.ae2federation.identity.NativeIdentityInitialization;

/** AE2 creates a part's Grid before attaching its internal connection; scope the complete native operation. */
@Mixin(CableBusContainer.class)
public abstract class CableBusIdentityInitializationMixin {
    @WrapMethod(method = "addPart(Lappeng/api/parts/IPartItem;Lnet/minecraft/core/Direction;Lnet/minecraft/world/entity/player/Player;)Lappeng/api/parts/IPart;")
    private IPart ae2federation$initializePart(IPartItem<?> item, Direction side, Player player,
            Operation<IPart> original) {
        try (var scope = NativeIdentityInitialization.begin()) {
            return original.call(item, side, player);
        }
    }

    @WrapMethod(method = "addToWorld")
    private void ae2federation$initializeHost(Operation<Void> original) {
        try (var scope = NativeIdentityInitialization.begin()) {
            original.call();
        }
    }
}
