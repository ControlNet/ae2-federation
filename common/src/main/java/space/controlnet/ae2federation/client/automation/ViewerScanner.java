package space.controlnet.ae2federation.client.automation;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import space.controlnet.ae2federation.automation.BucketedRecipes;
import space.controlnet.ae2federation.automation.RecipeBuckets;
import space.controlnet.ae2federation.automation.ViewerRecipes;
import space.controlnet.ae2federation.automation.ViewerScans;

/** A recipe viewer's processing recipes, read a few at a time on the client ({@link ViewerScanClient}). */
public interface ViewerScanner {
    /** The IDs of the categories the viewer shows now; cheap enough to ask every second. */
    Set<String> categoryIds();

    /** Lists the categories, each with its count and machines. */
    Job<ViewerScans.Listed> list();

    /** Fingerprints a category's recipes ({@link RecipeBuckets}). */
    Prints fingerprint(String category);

    /** Some work done a few milliseconds a tick. */
    interface Job<T> {
        /** Works, giving each result to {@code out}, until {@code deadline} ({@link System#nanoTime}); true at the end. */
        boolean step(long deadline, Consumer<T> out);
    }

    /** A category's fingerprints; once all are given, the recipes behind them can be read. */
    interface Prints extends Job<RecipeBuckets.Fingerprint> {
        /** Reads the recipes of these fingerprints, an entry each, in order. */
        Job<BucketedRecipes.Entry<ViewerRecipes.Recipe>> read(List<RecipeBuckets.Fingerprint> wanted);
    }
}
