package space.controlnet.ae2federation.test.processing;

import appeng.api.stacks.AEItemKey;
import net.minecraft.world.item.Items;

record ProcessingBenchmarkMeasurement(long acceptedInput, long deliveredPrimary, long deliveredByproduct,
        long finalInput, ProcessingBenchmarkObservation.Snapshot observation, long elapsedNanos) {
    static ProcessingBenchmarkMeasurement capture(ControlledProcessingMachine machine,
            ProcessingCraftingCoordinator crafting, ProcessingBenchmarkObservation.Scene scene, long startedNanos) {
        return new ProcessingBenchmarkMeasurement(machine.consumedInput(), crafting.outputAmount(),
                byproductAmount(crafting), crafting.inputAmount(), ProcessingBenchmarkObservation.snapshot(scene),
                System.nanoTime() - startedNanos);
    }

    static long byproductAmount(ProcessingCraftingCoordinator crafting) {
        long amount = 0;
        for (var entry : crafting.storage().getAvailableStacks()) {
            if (entry.getKey() instanceof AEItemKey itemKey && itemKey.getItem() == Items.GOLD_INGOT) {
                amount = Math.addExact(amount, entry.getLongValue());
            }
        }
        return amount;
    }
}
