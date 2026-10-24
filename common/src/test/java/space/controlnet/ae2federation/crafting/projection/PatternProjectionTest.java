package space.controlnet.ae2federation.crafting.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
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

    @Test
    void newPatternObjectsRefreshEvenWhenEqual() {
        var provider = new Provider();
        provider.patterns = List.of(new Pattern(1));
        var projection = new PatternProjection(provider, (p, d, c, i) -> () -> {});
        assertFalse(projection.refresh());
        provider.patterns = List.of(new Pattern(1));
        assertTrue(projection.refresh());
        assertSame(provider.patterns.getFirst(), projection.getAvailablePatterns().getFirst());
        assertFalse(projection.refresh());
    }

    @Test
    void projectionCarriesTheRealProvidersAddonMarkers() {
        var provider = new MarkedProvider();
        provider.priority = 7;
        var projection = new PatternProjection(provider, (p, d, c, i) -> () -> {});
        var registered = projection.registered();
        assertTrue(registered instanceof Marker);
        assertFalse(registered instanceof WithMethod);
        assertTrue(PatternProjection.isProjection(registered));
        assertTrue(PatternProjection.isProjection(projection));
        assertFalse(PatternProjection.isProjection(provider));
        assertEquals(7, registered.getPatternPriority());
        assertEquals(registered, registered);
        assertNotEquals(registered, projection);
        assertNotEquals(registered, new PatternProjection(provider, (p, d, c, i) -> () -> {}).registered());
    }

    @Test
    void projectionWithoutMarkersIsRegisteredItself() {
        var projection = new PatternProjection(new Provider(), (p, d, c, i) -> () -> {});
        assertSame(projection, projection.registered());
    }

    /** An add-on's mark on its providers: an interface with no methods. */
    interface Marker {
    }

    /** An add-on interface with methods, which a projection cannot answer for and so does not carry. */
    interface WithMethod {
        void act();
    }

    private static final class MarkedProvider extends Provider implements Marker, WithMethod {
        public void act() {}
    }

    /** Test-only pattern: equal to any other with the same id, as re-decoded patterns are. */
    private record Pattern(int id) implements IPatternDetails {
        public AEItemKey getDefinition() { return null; }
        public IInput[] getInputs() { return new IInput[0]; }
        public List<GenericStack> getOutputs() { return List.of(); }
    }

    private static class Provider implements ICraftingProvider {
        int priority;
        List<IPatternDetails> patterns = List.of();
        public List<IPatternDetails> getAvailablePatterns() { return patterns; }
        public int getPatternPriority() { return priority; }
        public boolean pushPattern(IPatternDetails details, KeyCounter[] inputs) { return false; }
        public boolean isBusy() { return false; }
    }
}
