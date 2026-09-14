package space.controlnet.ae2federation.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.helpers.patternprovider.PatternProviderLogic;
import java.util.List;
import java.util.Set;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicAccess {
    @Accessor("patterns")
    List<IPatternDetails> ae2federation$getPatterns();

    @Accessor("patternInputs")
    Set<AEKey> ae2federation$getPatternInputs();
}
