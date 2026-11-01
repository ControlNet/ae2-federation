package space.controlnet.ae2federation.automation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.BiConsumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Asks one player's recipe viewer for the recipes it shows, page by page, once per server start and again after each
 * datapack reload, and keeps them in {@link ViewerRecipes} when the last page arrives. A client says it is ready when
 * its viewer has its recipes (after joining, and again after the viewer reloads them); the server asks the first ready
 * player while no scan has finished since the start or the last reload. A scan whose player leaves, or whose pages come
 * out of order, is dropped, and the next ready player is asked.
 *
 * <p>Assistant choice: any player's viewer may send them, as AE2's own terminal encodes whatever processing pattern a
 * player sets up; what it sent replaces what was saved for every program of the server.
 */
public final class ViewerScans {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ViewerScans.class);
    /** More than any pack has; a scan past it is dropped rather than held in memory. */
    static final int MAX_RECIPES = 2_000_000;
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private static BiConsumer<ServerPlayer, Integer> request = (player, scan) -> { };

    private static final class State {
        boolean done;
        final Set<UUID> ready = new LinkedHashSet<>();
        @Nullable UUID scanner;
        int scan;
        int nextPage;
        final List<ViewerRecipes.Category> categories = new ArrayList<>();
        final List<ViewerRecipes.Recipe> recipes = new ArrayList<>();
    }

    /** One page of a scan, as a client sends it; index -1 is a client giving the scan up. */
    public record Page(int scan, int index, boolean last, List<ViewerRecipes.Category> categories,
            List<ViewerRecipes.Recipe> recipes) {
    }

    private ViewerScans() {
    }

    /** Once, at mod start: how a scan request reaches a player. */
    public static void register(BiConsumer<ServerPlayer, Integer> sendRequest) {
        requestWith(sendRequest);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                left(player);
            }
        });
        // With no player, the event is a reload's: the viewers reload their recipes and say they are ready again.
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() == null) {
                reloaded(event.getPlayerList().getServer());
            }
        });
    }

    /** How a scan request reaches a player from now on; the previous way back. GameTests stand in for a client. */
    public static synchronized BiConsumer<ServerPlayer, Integer> requestWith(BiConsumer<ServerPlayer, Integer> send) {
        var previous = request;
        request = send;
        return previous;
    }

    private static synchronized State state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, ignored -> new State());
    }

    /** The player's viewer has its recipes. */
    public static synchronized void ready(ServerPlayer player) {
        var state = state(player.server);
        state.ready.add(player.getUUID());
        if (player.getUUID().equals(state.scanner)) {
            // Its viewer reloaded while scanning: what it sent so far may be from before.
            drop(state);
        }
        ask(player.server, state);
    }

    public static synchronized void page(ServerPlayer player, Page page) {
        var state = state(player.server);
        if (!player.getUUID().equals(state.scanner) || page.scan() != state.scan) {
            return;
        }
        if (page.index() != state.nextPage
                || state.recipes.size() + page.recipes().size() > MAX_RECIPES) {
            if (page.index() >= 0) {
                LOGGER.warn("Dropped a recipe viewer scan from {}: page {} out of order or too many recipes",
                        player.getGameProfile().getName(), page.index());
            }
            state.ready.remove(player.getUUID());
            drop(state);
            ask(player.server, state);
            return;
        }
        state.nextPage++;
        state.categories.addAll(page.categories());
        state.recipes.addAll(page.recipes());
        if (page.last()) {
            ViewerRecipes.get(player.server).replace(state.categories, state.recipes);
            LOGGER.info("Recipe viewer of {} sent {} recipes in {} categories", player.getGameProfile().getName(),
                    state.recipes.size(), state.categories.size());
            state.done = true;
            drop(state);
        }
    }

    static synchronized void left(ServerPlayer player) {
        var state = state(player.server);
        state.ready.remove(player.getUUID());
        if (player.getUUID().equals(state.scanner)) {
            drop(state);
            ask(player.server, state);
        }
    }

    /** Datapacks reloaded: the next ready viewer is asked again. */
    public static synchronized void reloaded(MinecraftServer server) {
        var state = state(server);
        state.done = false;
        state.ready.clear();
        drop(state);
    }

    private static void drop(State state) {
        state.scanner = null;
        state.nextPage = 0;
        state.categories.clear();
        state.recipes.clear();
    }

    private static void ask(MinecraftServer server, State state) {
        if (state.done || state.scanner != null) {
            return;
        }
        for (var id : state.ready) {
            var player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                state.scanner = id;
                state.scan++;
                request.accept(player, state.scan);
                return;
            }
        }
    }
}
