package space.controlnet.ae2federation.crafting.projection;

import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.AEKey;
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
        /** {@code containerItems}: what the pushed inputs leave behind, counted before the push. */
        void pushed(PatternProjection projection, IPatternDetails details, KeyCounter containerItems);
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

    /**
     * Pushes to the real provider. A machine may take the inputs out of the holder as it accepts them (AE2's Molecular
     * Assembler empties every counter), so the container items they leave behind are counted first and owed only once
     * the push is accepted. The holder itself goes to the provider untouched.
     */
    @Override
    public boolean pushPattern(IPatternDetails patternDetails, KeyCounter[] inputHolder) {
        if (!live) return false;
        var containerItems = containerItems(patternDetails, inputHolder);
        if (!real.pushPattern(patternDetails, inputHolder)) return false;
        listener.pushed(this, patternDetails, containerItems);
        return true;
    }

    /**
     * The container items a push of {@code inputs} leaves behind, such as the empty bucket of a water bucket, as AE2's
     * {@code CraftingCpuHelper.extractPatternInputs} counts them for the CPU that waits for them: for each input slot,
     * the remaining key of each key the CPU actually put there (a substitute, or water as a fluid, may leave nothing),
     * once per template it took.
     */
    static KeyCounter containerItems(IPatternDetails details, KeyCounter[] inputs) {
        var containerItems = new KeyCounter();
        var patternInputs = details.getInputs();
        for (int index = 0; index < inputs.length && index < patternInputs.length; index++) {
            if (inputs[index] == null) continue;
            var input = patternInputs[index];
            for (var stack : inputs[index]) {
                var remaining = input.getRemainingKey(stack.getKey());
                if (remaining != null) {
                    containerItems.add(remaining, stack.getLongValue() / templateAmount(input, stack.getKey()));
                }
            }
        }
        return containerItems;
    }

    /** The amount of the template AE2 took {@code key} for: an exact possible input first, then a fuzzy one. */
    private static long templateAmount(IPatternDetails.IInput input, AEKey key) {
        for (var template : input.getPossibleInputs()) {
            if (template.what().equals(key)) return Math.max(1, template.amount());
        }
        for (var template : input.getPossibleInputs()) {
            if (template.what().fuzzyEquals(key, FuzzyMode.IGNORE_ALL)) return Math.max(1, template.amount());
        }
        return 1;
    }

    @Override
    public boolean isBusy() {
        return !live || real.isBusy();
    }
}
