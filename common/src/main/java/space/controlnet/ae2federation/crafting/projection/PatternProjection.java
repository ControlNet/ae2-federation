package space.controlnet.ae2federation.crafting.projection;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import java.util.Objects;

/**
 * One real pattern provider of another network, offered to a consumer network's crafting service as AE2 offers its
 * own providers: the consumer's CPU plans with these patterns, takes the inputs from its own storage (which includes
 * what its storage rule shows of the other network) and pushes them here, and the push goes straight to the real
 * provider. The patterns are the real provider's own objects, so AE2's {@code patterns.contains} check in the push
 * passes, and nothing is wrapped or copied.
 *
 * <p>A withdrawn projection is first marked not live, so a CPU holding it from an earlier plan cannot push through it
 * before AE2 drops it from its provider list.
 */
final class PatternProjection implements ICraftingProvider {
    /** Told of every accepted push, so the outputs the consumer waits for are routed back to it. */
    interface PushListener {
        void pushed(PatternProjection projection, IPatternDetails details, KeyCounter[] inputs);
    }

    private final ICraftingProvider real;
    private final PushListener listener;
    private List<IPatternDetails> patterns;
    private boolean live = true;

    PatternProjection(ICraftingProvider real, PushListener listener) {
        this.real = Objects.requireNonNull(real);
        this.listener = Objects.requireNonNull(listener);
        patterns = List.copyOf(real.getAvailablePatterns());
    }

    ICraftingProvider real() {
        return real;
    }

    /** Takes the real provider's current patterns; true when they changed, so AE2 must re-read them. */
    boolean refresh() {
        var current = real.getAvailablePatterns();
        if (current.equals(patterns)) return false;
        patterns = List.copyOf(current);
        return true;
    }

    void withdraw() {
        live = false;
    }

    @Override
    public List<IPatternDetails> getAvailablePatterns() {
        return live ? patterns : List.of();
    }

    @Override
    public int getPatternPriority() {
        return real.getPatternPriority();
    }

    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!live || !real.pushPattern(patternDetails, inputHolder)) return false;
        listener.pushed(this, patternDetails, inputHolder);
        return true;
    }

    @Override
    public boolean isBusy() {
        return !live || real.isBusy();
    }
}
