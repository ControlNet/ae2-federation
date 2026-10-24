package space.controlnet.ae2federation.mixin;

import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderTarget;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import space.controlnet.ae2federation.ae2.processing.FederationPatternProviderTargetCache;

/** Verified against AE2 19.2.17 (the build) and 19.2.18 (the compatibility profiles). */
@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicTargetBinding {
    /**
     * A bound Lane's target is its authorized Federation target, whatever sits next to the Provider block. The native
     * lookup must not run for it: it would open a capability cache on that neighbour. A method wrapper rather than a
     * cancelled head, so another mod's wrapper of findAdapter still chains, and unbound Providers run everything.
     */
    @WrapMethod(method = "findAdapter")
    private PatternProviderTarget ae2federation$findAuthorizedTarget(Direction side,
            Operation<PatternProviderTarget> original) {
        var result = FederationPatternProviderTargetCache.find((PatternProviderLogic) (Object) this);
        if (result.bound()) {
            return (PatternProviderTarget) result.target();
        }
        return original.call(side);
    }

    /**
     * Native pushPattern offers inputs to an adjacent ICraftingMachine before it asks findAdapter for a target. A Lane
     * bound to a Federation target has no physical adjacency: whatever sits next to the Provider block is not the
     * authorized destination, so it must never receive the Lane's inputs. A wrapper rather than a redirect, so another
     * mod's wrapper of the same call still applies.
     */
    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target = "Lappeng/api/implementations/blockentities/"
            + "ICraftingMachine;of(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/core/Direction;)Lappeng/api/implementations/blockentities/ICraftingMachine;"))
    private ICraftingMachine ae2federation$skipAdjacentMachineForBoundLane(Level level, BlockPos position,
            Direction side, Operation<ICraftingMachine> original) {
        if (FederationPatternProviderTargetCache.isBound((PatternProviderLogic) (Object) this)) {
            return null;
        }
        return original.call(level, position, side);
    }
}
