package space.controlnet.ae2federation.client.automation;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.automation.ViewerRecipes;
import space.controlnet.ae2federation.automation.ViewerScans;

/**
 * The client's side of {@link ViewerScans}: tells the server when the recipe viewer has its recipes, and on request
 * reads them a few milliseconds a tick and sends them in pages small enough for one packet each, the categories after
 * the recipes. A viewer plugin hands its {@link ViewerScanner} over when its recipes are ready.
 *
 * <p>Assistant choices: 4 ms a tick, so the scan costs a frame or two of time spread over seconds; pages of at most
 * 30,000 bytes, under the 32,767 bytes of a packet a client sends; a recipe larger than a page alone is left out.
 */
public final class ViewerScanClient {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ViewerScanClient.class);
    static final int PAGE_BYTES = 30_000;
    private static final long BUDGET_NANOS = 4_000_000L;

    private static @Nullable ViewerScanner scanner;
    private static @Nullable Running running;
    private static Runnable sendReady = () -> { };
    private static Consumer<ViewerScans.Page> sendPage = page -> { };

    private ViewerScanClient() {
    }

    /** Once, on the client: how the client's messages reach the server. */
    public static void register(Runnable ready, Consumer<ViewerScans.Page> page) {
        sendReady = ready;
        sendPage = page;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> announce());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> running = null);
    }

    /** A viewer plugin's scanner once its recipes are ready; null when they are gone, as during a reload. */
    public static void scanner(@Nullable ViewerScanner ready) {
        scanner = ready;
        running = null;
        announce();
    }

    /** The server asks for the recipes. */
    public static void requested(int scan) {
        if (scanner == null || Minecraft.getInstance().getConnection() == null) {
            sendPage.accept(new ViewerScans.Page(scan, -1, true, List.of(), List.of()));
            return;
        }
        var connection = Minecraft.getInstance().getConnection();
        running = new Running(scan, scanner.start(), new RegistryFriendlyByteBuf(Unpooled.buffer(),
                connection.registryAccess(), connection.getConnectionType()));
    }

    private static void announce() {
        if (scanner != null && Minecraft.getInstance().getConnection() != null) {
            sendReady.run();
        }
    }

    private static void tick() {
        var scan = running;
        if (scan == null) {
            return;
        }
        boolean done;
        try {
            done = scan.job.step(System.nanoTime() + BUDGET_NANOS, scan::add);
            if (done) {
                scan.job.categories().forEach(scan::add);
                scan.send(true);
                LOGGER.info("Sent {} recipes from the recipe viewer, {} left out as too large", scan.recipes,
                        scan.tooLarge);
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("The recipe viewer scan failed", failure);
            sendPage.accept(new ViewerScans.Page(scan.scan, -1, true, List.of(), List.of()));
            done = true;
        }
        if (done && running == scan) {
            running = null;
        }
    }

    /** A scan being sent: the page being filled, measured by encoding each entry. */
    private static final class Running {
        final int scan;
        final ViewerScanner.Job job;
        final RegistryFriendlyByteBuf measure;
        final List<ViewerRecipes.Category> categories = new ArrayList<>();
        final List<ViewerRecipes.Recipe> page = new ArrayList<>();
        int bytes;
        int index;
        int recipes;
        int tooLarge;

        Running(int scan, ViewerScanner.Job job, RegistryFriendlyByteBuf measure) {
            this.scan = scan;
            this.job = job;
            this.measure = measure;
        }

        void add(ViewerRecipes.Recipe recipe) {
            measure.clear();
            ViewerRecipes.Recipe.STREAM_CODEC.encode(measure, recipe);
            if (fit(measure.writerIndex())) {
                page.add(recipe);
                recipes++;
            }
        }

        void add(ViewerRecipes.Category category) {
            measure.clear();
            ViewerRecipes.Category.STREAM_CODEC.encode(measure, category);
            if (fit(measure.writerIndex())) {
                categories.add(category);
            }
        }

        /** Whether an entry of that size goes on a page, sending the full page first. */
        private boolean fit(int size) {
            if (size > PAGE_BYTES - 64) {
                tooLarge++;
                return false;
            }
            if (bytes + size > PAGE_BYTES - 64 || page.size() + categories.size() >= ViewerRecipes.MAX_PAGE_RECIPES) {
                send(false);
            }
            bytes += size;
            return true;
        }

        void send(boolean last) {
            sendPage.accept(new ViewerScans.Page(scan, index++, last, List.copyOf(categories), List.copyOf(page)));
            categories.clear();
            page.clear();
            bytes = 0;
        }
    }
}
