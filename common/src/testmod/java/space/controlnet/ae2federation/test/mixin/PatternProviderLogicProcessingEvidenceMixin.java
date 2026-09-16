package space.controlnet.ae2federation.test.mixin;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import space.controlnet.ae2federation.test.processing.ProcessingNativeObservation;
import space.controlnet.ae2federation.test.processing.ProcessingBenchmarkObservation;

@Mixin(value = PatternProviderLogic.class, priority = 400)
public abstract class PatternProviderLogicProcessingEvidenceMixin {
    @Inject(method = "pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z",
            at = @At("HEAD"), require = 1)
    private void ae2federation_test$observeProcessingPush(IPatternDetails pattern, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> callback) {
        if (!ProcessingBenchmarkObservation.recordPush(inputs)) {
            ProcessingNativeObservation.recordPush(this, inputs);
        }
    }

    @Inject(method = "pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;)Z",
            at = @At("RETURN"), require = 1)
    private void ae2federation_test$observeProcessingResult(IPatternDetails pattern, KeyCounter[] inputs,
            CallbackInfoReturnable<Boolean> callback) {
        if (!ProcessingBenchmarkObservation.recordPushResult(this, callback.getReturnValueZ())) {
            ProcessingNativeObservation.recordPushResult(this, callback.getReturnValueZ());
        }
    }

    @Inject(method = "addToSendList", at = @At("HEAD"), require = 1)
    private void ae2federation_test$observeNativeRemainder(AEKey key, long amount, CallbackInfo callback) {
        if (!ProcessingBenchmarkObservation.recordRemainder(key, amount)) {
            ProcessingNativeObservation.recordRemainder(this, key, amount);
        }
    }

    @Inject(method = "writeToNBT", at = @At("HEAD"), require = 1)
    private void ae2federation_test$observeWrite(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries,
            CallbackInfo callback) {
        ProcessingNativeObservation.recordLifecycle("nbt-write", this);
        ProcessingNativeObservation.recordLogicState("nbt-write", (PatternProviderLogic) (Object) this, List.of());
    }

    @Inject(method = "readFromNBT", at = @At("RETURN"), require = 1)
    private void ae2federation_test$observeRead(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries,
            CallbackInfo callback) {
        ProcessingNativeObservation.recordLifecycle("nbt-read", this);
        ProcessingNativeObservation.recordLogicState("nbt-read", (PatternProviderLogic) (Object) this, List.of());
    }

    @Inject(method = "addDrops", at = @At("HEAD"), require = 1)
    private void ae2federation_test$observeDrops(List<ItemStack> drops, CallbackInfo callback) {
        ProcessingNativeObservation.recordLifecycle("add-drops", this);
        var occurrence = ProcessingNativeObservation.count("add-drops");
        ProcessingNativeObservation.recordLogicState("add-drops-" + occurrence + "-entry",
                (PatternProviderLogic) (Object) this, drops);
    }

    @Inject(method = "addDrops", at = @At("RETURN"), require = 1)
    private void ae2federation_test$observeDroppedState(List<ItemStack> drops, CallbackInfo callback) {
        var occurrence = ProcessingNativeObservation.count("add-drops");
        ProcessingNativeObservation.recordLogicState("add-drops-" + occurrence + "-return",
                (PatternProviderLogic) (Object) this, drops);
    }

    @Inject(method = "clearContent", at = @At("HEAD"), require = 1)
    private void ae2federation_test$observeClearEntry(CallbackInfo callback) {
        ProcessingNativeObservation.recordLifecycle("clear-content", this);
        var occurrence = ProcessingNativeObservation.count("clear-content");
        ProcessingNativeObservation.recordLogicState("clear-content-" + occurrence + "-entry",
                (PatternProviderLogic) (Object) this, List.of());
    }

    @Inject(method = "clearContent", at = @At("RETURN"), require = 1)
    private void ae2federation_test$observeClearReturn(CallbackInfo callback) {
        var occurrence = ProcessingNativeObservation.count("clear-content");
        ProcessingNativeObservation.recordLogicState("clear-content-" + occurrence + "-return",
                (PatternProviderLogic) (Object) this, List.of());
    }
}
