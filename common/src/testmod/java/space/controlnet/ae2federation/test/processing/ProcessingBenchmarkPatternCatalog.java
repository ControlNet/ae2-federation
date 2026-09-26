package space.controlnet.ae2federation.test.processing;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;
import net.minecraft.world.item.Items;

final class ProcessingBenchmarkPatternCatalog {
    private ProcessingBenchmarkPatternCatalog() {
    }

    static List<IntPredicate> assignments(GeneratedFactoryTopology topology) {
        var mappings = topology.patternLaneAssignments();
        var laneCount = mappings.stream().flatMap(Set::stream).mapToInt(Integer::intValue).max().orElseThrow() + 1;
        var assignments = new ArrayList<IntPredicate>(laneCount);
        for (int lane = 0; lane < laneCount; lane++) {
            var laneIndex = lane;
            assignments.add(slot -> mappings.get(slot).contains(laneIndex));
        }
        return List.copyOf(assignments);
    }

    static void install(NativeProviderLaneFixtures fixture, ProcessingBenchmarkProfile profile) {
        install(profile, fixture::setPattern);
        for (int lane = 0; lane < profile.logicalLanes(); lane++) {
            fixture.lockUntilResult(lane);
        }
        fixture.refreshPatterns();
    }

    static void install(ProviderTargetRuntimeFixtures fixture, ProcessingBenchmarkProfile profile) {
        install(profile, fixture::setPattern);
        fixture.refreshPatterns();
    }

    static void install(GeneratedFederationTargets fixture, ProcessingBenchmarkProfile profile) {
        install(profile, fixture::setPattern);
        for (int lane = 0; lane < profile.logicalLanes(); lane++) {
            fixture.lockUntilResult(lane);
        }
        fixture.refreshPatterns();
    }

    private static void install(ProcessingBenchmarkProfile profile, PatternInstaller installer) {
        for (int slot = 0; slot < profile.physicalPatterns(); slot++) {
            installer.set(slot, List.of(stack(Items.COBBLESTONE, profile.batchUnits())),
                    List.of(ProcessingBenchmarkPatternMarker.generic(slot, profile.primaryOutputUnits()),
                            stack(Items.GOLD_INGOT, profile.byproductOutputUnits())));
        }
    }

    private static GenericStack stack(net.minecraft.world.item.Item item, long amount) {
        return new GenericStack(AEItemKey.of(item), amount);
    }

    @FunctionalInterface
    private interface PatternInstaller {
        void set(int slot, List<GenericStack> inputs, List<GenericStack> outputs);
    }
}
