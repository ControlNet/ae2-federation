package space.controlnet.ae2federation.test.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.helpers.patternprovider.PatternProviderLogic;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import space.controlnet.ae2federation.test.processing.AddonPatternHookEmulation;

/** Hooks AE2's pattern refresh where addons hook it, for {@link AddonPatternHookEmulation}. */
@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicAddonRefreshMixin {
    @Inject(method = "updatePatterns", at = @At("HEAD"), require = 1)
    private void ae2federation_test$recordRefresh(CallbackInfo callback) {
        AddonPatternHookEmulation.recordRefresh((PatternProviderLogic) (Object) this);
    }

    @Redirect(method = "updatePatterns", require = 1, at = @At(value = "INVOKE",
            target = "Lappeng/api/crafting/PatternDetailsHelper;decodePattern(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Lappeng/api/crafting/IPatternDetails;"))
    private IPatternDetails ae2federation_test$filterDecoded(ItemStack stack, Level level) {
        return AddonPatternHookEmulation.filter(PatternDetailsHelper.decodePattern(stack, level));
    }
}
