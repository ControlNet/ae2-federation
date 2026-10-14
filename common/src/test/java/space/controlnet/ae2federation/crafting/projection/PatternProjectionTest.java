package space.controlnet.ae2federation.crafting.projection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import org.junit.jupiter.api.Test;

class PatternProjectionTest {
    @Test
    void priorityChangesRefreshEveryConsumerExactlyOnce() {
        var provider = new Provider();
        var first = new PatternProjection(provider, (p, d, c, i) -> () -> {});
        var second = new PatternProjection(provider, (p, d, c, i) -> () -> {});
        assertFalse(first.refresh());
        for (int priority : new int[] {10, -5, 0}) {
            provider.priority = priority;
            assertTrue(first.refresh());
            assertTrue(second.refresh());
            assertFalse(first.refresh());
            assertFalse(second.refresh());
        }
    }

    private static final class Provider implements ICraftingProvider {
        private int priority;
        public List<IPatternDetails> getAvailablePatterns() { return List.of(); }
        public int getPatternPriority() { return priority; }
        public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) { return false; }
        public boolean isBusy() { return false; }
    }
}
