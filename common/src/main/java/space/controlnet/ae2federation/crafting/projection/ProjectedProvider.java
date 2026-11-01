package space.controlnet.ae2federation.crafting.projection;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import java.util.function.BooleanSupplier;

/** A projection, as an {@link AddonProjections} adapter answers for it. */
public interface ProjectedProvider {
    /** Whether the projection still offers its patterns; a withdrawn one pushes nothing. */
    boolean isLive();

    /** The provider of another network the projection shows. */
    ICraftingProvider real();

    /**
     * Hands over a batch of {@code details}' crafts: {@code handOver} pushes it to the real provider, and once it is
     * accepted the consumer is owed {@code outputs} and {@code containerItems}, the batch's totals, as for a push of a
     * pattern with those outputs. {@code inputs} are what the batch takes, counted before the push.
     */
    boolean pushBatch(IPatternDetails details, List<GenericStack> outputs, KeyCounter containerItems,
            KeyCounter inputs, BooleanSupplier handOver);

    /** The container items a push of {@code inputs} for {@code details} leaves behind, as AE2's CPU counts them. */
    static KeyCounter containerItems(IPatternDetails details, KeyCounter[] inputs) {
        return PatternProjection.containerItems(details, inputs);
    }
}
