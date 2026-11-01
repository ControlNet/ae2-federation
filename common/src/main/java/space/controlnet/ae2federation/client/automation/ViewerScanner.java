package space.controlnet.ae2federation.client.automation;

import java.util.List;
import java.util.function.Consumer;
import space.controlnet.ae2federation.automation.ViewerRecipes;

/** A recipe viewer's processing recipes, read a few at a time on the client ({@link ViewerScanClient}). */
public interface ViewerScanner {
    Job start();

    /** One reading of all the recipes. */
    interface Job {
        /** Reads recipes, giving each to {@code out}, until {@code deadline} ({@link System#nanoTime}); true at the end. */
        boolean step(long deadline, Consumer<ViewerRecipes.Recipe> out);

        /** The categories read, each with its machines. */
        List<ViewerRecipes.Category> categories();
    }
}
