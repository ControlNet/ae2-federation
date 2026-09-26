package space.controlnet.ae2federation.test.processing;

import appeng.api.crafting.IPatternDetails;
import java.util.HashSet;
import java.util.Set;

final class ProcessingBenchmarkTopologyView {
    private ProcessingBenchmarkTopologyView() {
    }

    static Set<IPatternDetails> patterns(GeneratedSceneFixture fixture) {
        var patterns = new HashSet<IPatternDetails>();
        fixture.lanes().forEach(lane -> patterns.addAll(lane.getAvailablePatterns()));
        return Set.copyOf(patterns);
    }

    static int providerEntries(GeneratedSceneFixture fixture, Set<IPatternDetails> patterns) {
        var service = (appeng.me.service.CraftingService) fixture.sourceGrid().getCraftingService();
        return patterns.stream().mapToInt(pattern -> {
            var count = new int[1];
            service.getProviders(pattern).forEach(provider -> count[0]++);
            return count[0];
        }).sum();
    }
}
