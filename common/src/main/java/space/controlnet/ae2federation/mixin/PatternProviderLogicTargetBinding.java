package space.controlnet.ae2federation.mixin;

import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.helpers.patternprovider.PatternProviderLogic;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.ae2.processing.FederationPatternProviderTargetCache;

@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicTargetBinding {
    @Inject(method = "findAdapter", at = @At("HEAD"), cancellable = true)
    private void ae2federation$findAuthorizedTarget(Direction side, CallbackInfoReturnable<Object> callback) {
        var result = FederationPatternProviderTargetCache.find((PatternProviderLogic) (Object) this);
        if (result.bound()) {
            callback.setReturnValue(result.target());
        }
    }

    /**
     * Native pushPattern offers inputs to an adjacent ICraftingMachine before it asks findAdapter for a target. A Lane
     * bound to a Federation target has no physical adjacency: whatever sits next to the Provider block is not the
     * authorized destination, so it must never receive the Lane's inputs.
     */
    @Redirect(method = "pushPattern", at = @At(value = "INVOKE", target = "Lappeng/api/implementations/blockentities/"
            + "ICraftingMachine;of(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/core/Direction;)Lappeng/api/implementations/blockentities/ICraftingMachine;"))
    private ICraftingMachine ae2federation$skipAdjacentMachineForBoundLane(Level level, BlockPos position,
            Direction side) {
        if (FederationPatternProviderTargetCache.isBound((PatternProviderLogic) (Object) this)) {
            return null;
        }
        return ICraftingMachine.of(level, position, side);
    }
}
