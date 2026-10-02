package space.controlnet.ae2federation.crafting.remote;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import java.util.Set;

/**
 * What a provider network can craft for one consumer under its Crafting rule, offered on the consumer's Grid as AE2
 * emitable items. The consumer's planner then treats each key as something the network can request, its terminal
 * lists it as craftable, and its CPU waits for the key to arrive; {@link RemoteCraftingService} fulfils that wait with
 * a native job on the provider. No pattern is copied: the provider's patterns, CPUs and materials stay its own.
 */
final class CraftingProjection implements ICraftingProvider {
    private Set<AEKey> keys = Set.of();

    Set<AEKey> keys() {
        return keys;
    }

    /** Returns whether the keys changed, in which case the consumer's crafting service must refresh this provider. */
    boolean update(Set<AEKey> next) {
        if (keys.equals(next)) return false;
        keys = Set.copyOf(next);
        return true;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return List.of();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        return false;
    }

    @Override
    public boolean isBusy() {
        return false;
    }

    @Override
    public Set<AEKey> getEmitableItems() {
        return keys;
    }
}
