package space.controlnet.ae2federation.test.processing;

import static space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures.item;

import appeng.api.stacks.AEItemKey;
import appeng.api.config.LockCraftingMode;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.externalstorage.GenericStackItemStorage;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.items.IItemHandler;

final class ControlledProcessingMachine {
    private final ProcessingBenchmarkObservation.Scene scene;
    private final NativeProviderLaneFixtures nativeFixture;
    private final GeneratedFederationTargets federationFixture;
    private final ProcessingCraftingCoordinator coordinator;
    private final ProcessingBenchmarkProfile profile;
    private long completedBatches;
    private long consumedInput;
    private long producedPrimary;
    private long producedByproduct;
    private int patternSlot = -1;
    private EndpointCohortScenario cohortScenario;
    private boolean deferredReturnWake;

    ControlledProcessingMachine(ProcessingBenchmarkObservation.Scene scene, NativeProviderLaneFixtures nativeFixture,
            GeneratedFederationTargets federationFixture, ProcessingCraftingCoordinator coordinator,
            ProcessingBenchmarkProfile profile) {
        this.scene = scene;
        this.nativeFixture = nativeFixture;
        this.federationFixture = federationFixture;
        this.coordinator = coordinator;
        this.profile = profile;
    }

    boolean tick() {
        ProcessingBenchmarkObservation.select(scene);
        if (deferredReturnWake) {
            wakeReturnOwner();
            deferredReturnWake = false;
        }
        observeOwners();
        var available = targetInput();
        if (available < profile.batchUnits()) {
            return false;
        }
        var laneIndex = ProcessingBenchmarkObservation.lastAcceptedLane(scene);
        if (laneIndex < 0) {
            throw new IllegalStateException("AE2 accepted Processing input without an observed native Lane");
        }
        var extracted = extractTarget(profile.batchUnits());
        if (extracted != profile.batchUnits()) {
            throw new IllegalStateException("Controlled Processing machine could not consume the accepted batch");
        }
        var lane = lane(laneIndex);
        var handler = scene == ProcessingBenchmarkObservation.Scene.NATIVE
                ? new GenericStackItemStorage(lane.getReturnInv())
                : federationFixture.endpointBinding(laneIndex).runtime().itemReturn(Direction.NORTH).orElseThrow();
        var primaryRemainder = insertAcross(handler,
                ProcessingBenchmarkPatternMarker.stack(patternSlot, profile.primaryOutputUnits()));
        var byproductRemainder = insertAcross(handler,
                new ItemStack(Items.GOLD_INGOT, profile.byproductOutputUnits()));
        if (!primaryRemainder.isEmpty() || !byproductRemainder.isEmpty()) {
            throw new IllegalStateException("Controlled machine output did not enter native return ownership"
                    + ": slots=" + handler.getSlots() + ",primaryRemainder=" + primaryRemainder.getCount()
                    + ",byproductRemainder=" + byproductRemainder.getCount()
                    + ",returns=" + ProcessingNativeObservation.returnInventory(lane));
        }
        if (lane.getCraftingLockedReason() == LockCraftingMode.LOCK_UNTIL_RESULT) {
            ProcessingBenchmarkObservation.recordResultLockedLane(laneIndex);
        }
        observeOwners();
        if (cohortScenario == EndpointCohortScenario.RETURN_CONGESTED) {
            deferredReturnWake = true;
        } else {
            wakeReturnOwner();
        }
        ProcessingBenchmarkObservation.recordNotification(laneIndex);
        consumedInput = Math.addExact(consumedInput, extracted);
        producedPrimary = Math.addExact(producedPrimary,
                profile.primaryOutputUnits() - primaryRemainder.getCount());
        producedByproduct = Math.addExact(producedByproduct,
                profile.byproductOutputUnits() - byproductRemainder.getCount());
        completedBatches++;
        ProcessingBenchmarkObservation.recordMachineCompletion(laneIndex);
        return true;
    }

    long completedBatches() {
        return completedBatches;
    }

    void selectPattern(int selectedPatternSlot) {
        patternSlot = selectedPatternSlot;
    }

    void selectCohortScenario(EndpointCohortScenario selectedScenario) {
        cohortScenario = selectedScenario;
    }

    long consumedInput() {
        return consumedInput;
    }

    long producedPrimary() {
        return producedPrimary;
    }

    long producedByproduct() {
        return producedByproduct;
    }

    private static ItemStack insertAcross(IItemHandler handler, ItemStack offered) {
        var remainder = offered;
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, false);
        }
        return remainder;
    }

    private long targetInput() {
        return scene == ProcessingBenchmarkObservation.Scene.NATIVE
                ? nativeFixture.targetItemCount(Items.COBBLESTONE)
                : federationFixture.targetAmount(AEItemKey.of(Items.COBBLESTONE));
    }

    private long extractTarget(long amount) {
        return scene == ProcessingBenchmarkObservation.Scene.NATIVE
                ? nativeFixture.extractTargetItem(Items.COBBLESTONE, amount)
                : federationFixture.extractTarget(AEItemKey.of(Items.COBBLESTONE), amount);
    }

    private PatternProviderLogic lane(int index) {
        return scene == ProcessingBenchmarkObservation.Scene.NATIVE
                ? nativeFixture.lane(index) : federationFixture.providerLogic(index);
    }

    private int laneCount() {
        return scene == ProcessingBenchmarkObservation.Scene.NATIVE
                ? nativeFixture.laneCount() : federationFixture.laneCount();
    }

    private void wakeReturnOwner() {
        if (scene == ProcessingBenchmarkObservation.Scene.NATIVE) {
            nativeFixture.wakeNativeTicker();
        } else {
            federationFixture.wakeProviderTicker();
        }
    }

    private void observeOwners() {
        long returnUnits = 0;
        long sendUnits = 0;
        int sendEntries = 0;
        int retryOwners = 0;
        for (int index = 0; index < laneCount(); index++) {
            var lane = lane(index);
            long laneReturns = 0;
            for (int slot = 0; slot < lane.getReturnInv().size(); slot++) {
                laneReturns = Math.addExact(laneReturns, lane.getReturnInv().getAmount(slot));
            }
            var send = ProcessingRegressionFixtures.sendList(lane);
            var laneSend = send.stream().mapToLong(appeng.api.stacks.GenericStack::amount).sum();
            returnUnits = Math.addExact(returnUnits, laneReturns);
            sendUnits = Math.addExact(sendUnits, laneSend);
            sendEntries += send.size();
            if (laneReturns > 0 || laneSend > 0) {
                retryOwners++;
            }
            ProcessingBenchmarkObservation.observeLaneOwnerState(index, laneReturns, laneSend);
        }
        ProcessingBenchmarkObservation.observeOwnerState(returnUnits, sendUnits, sendEntries, retryOwners);
    }
}
