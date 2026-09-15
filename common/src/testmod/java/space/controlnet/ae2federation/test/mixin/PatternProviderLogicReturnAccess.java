package space.controlnet.ae2federation.test.mixin;

import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(PatternProviderLogic.class)
public interface PatternProviderLogicReturnAccess {
    @Accessor("sendList")
    List<GenericStack> ae2federation_test$getSendList();

    @Invoker("onStackReturnedToNetwork")
    void ae2federation_test$onStackReturned(GenericStack stack);
}
