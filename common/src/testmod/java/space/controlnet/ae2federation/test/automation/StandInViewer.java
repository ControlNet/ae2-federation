package space.controlnet.ae2federation.test.automation;

import io.netty.buffer.Unpooled;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.connection.ConnectionType;
import space.controlnet.ae2federation.automation.BucketedRecipes;
import space.controlnet.ae2federation.automation.RecipeBuckets;
import space.controlnet.ae2federation.automation.ViewerRecipes;
import space.controlnet.ae2federation.automation.ViewerScans;
import space.controlnet.ae2federation.neoforge.network.ViewerRecipePayloads;

/**
 * A mock player's recipe viewer, standing in for a client's JEI in GameTests: it says hello with its categories and
 * answers the server's tasks ({@link ViewerScans}), every message through its payload's codec. It fingerprints its
 * records itself, as a client does from JEI: partitioned by category and ID, else by what the recipe takes and makes;
 * its digest is the record's text. It answers when pumped, not inside the server's call, as a client answers a
 * packet later.
 */
public final class StandInViewer {
    private static final Map<ServerPlayer, StandInViewer> VIEWERS = new HashMap<>();
    private static final ViewerScans.Sender SENDER = new ViewerScans.Sender() {
        @Override
        public void task(ServerPlayer player, ViewerScans.Task task) {
            var viewer = VIEWERS.get(player);
            if (viewer != null) {
                viewer.inbox.add(through(viewer.server, ViewerRecipePayloads.Task.STREAM_CODEC,
                        new ViewerRecipePayloads.Task(task)).task());
            }
        }

        @Override
        public void reads(ServerPlayer player, ViewerScans.Reads reads) {
            var viewer = VIEWERS.get(player);
            if (viewer != null) {
                viewer.inbox.add(through(viewer.server, ViewerRecipePayloads.Reads.STREAM_CODEC,
                        new ViewerRecipePayloads.Reads(reads)).reads());
            }
        }
    };

    public final ServerPlayer player;
    private final MinecraftServer server;
    private final ArrayDeque<Object> inbox = new ArrayDeque<>();
    private final Map<String, List<String>> machines = new LinkedHashMap<>();
    private List<ViewerRecipes.Recipe> recipes = List.of();
    private int generation;
    /** The task the server's next reads belong to: a viewer holds one lease at a time. */
    private ViewerScans.Task lastTask;
    /** Whether it lets tasks pass unanswered, as a client that froze. */
    public boolean silent;
    /** Whether it numbers its records from 1, as a client that lost a part. */
    public boolean outOfOrder;
    /** The tasks it was given, and the recipes it sent read. */
    public int tasks;
    public int read;

    /** A mock player's viewer showing these categories (ID to machine items) and recipes. */
    @SuppressWarnings("removal") // The only mock player a server sees in its player list in 1.21.1.
    public StandInViewer(GameTestHelper helper, Map<String, List<String>> categories,
            List<ViewerRecipes.Recipe> recipes) {
        this.player = helper.makeMockServerPlayerInLevel();
        this.server = helper.getLevel().getServer();
        this.machines.putAll(categories);
        this.recipes = List.copyOf(recipes);
        VIEWERS.put(player, this);
    }

    /** Stands in for every client from now on; the previous way the server reached them back. */
    public static ViewerScans.Sender install() {
        return ViewerScans.sendWith(SENDER);
    }

    /** Its viewer reloaded and shows these recipes now. */
    public void show(List<ViewerRecipes.Recipe> recipes) {
        this.recipes = List.copyOf(recipes);
        generation++;
        inbox.clear();
    }

    /** Says hello: its categories, two to a part, each with its count. */
    public void hello() {
        var counts = recipes.stream().collect(Collectors.groupingBy(ViewerRecipes.Recipe::category,
                Collectors.counting()));
        var listed = new ArrayList<ViewerScans.Listed>();
        machines.forEach((id, items) -> listed.add(new ViewerScans.Listed(id, counts.getOrDefault(id, 0L).intValue(),
                items)));
        var part = 0;
        for (var from = 0; from == 0 || from < listed.size(); from += 2) {
            var to = Math.min(listed.size(), from + 2);
            ViewerScans.hello(player, through(server, ViewerRecipePayloads.Hello.STREAM_CODEC,
                    new ViewerRecipePayloads.Hello(new ViewerScans.Hello(generation, part++, to == listed.size(),
                            List.copyOf(listed.subList(from, to))))).hello());
        }
    }

    /** Answers what the server sent; true if there was anything. */
    public boolean pump() {
        var message = inbox.poll();
        if (message == null) {
            return false;
        }
        if (message instanceof ViewerScans.Task task) {
            tasks++;
            if (!silent) {
                answer(task);
            }
        } else if (message instanceof ViewerScans.Reads reads && !silent) {
            answer(reads);
        }
        return true;
    }

    /** Pumps the viewers until none has anything left to answer. */
    public static void pumpAll(List<StandInViewer> viewers) {
        var any = true;
        while (any) {
            any = false;
            for (var viewer : viewers) {
                while (viewer.pump()) {
                    any = true;
                }
            }
        }
    }

    /** Leaves the server. */
    public void leave() {
        ViewerScans.left(player);
        server.getPlayerList().remove(player);
        VIEWERS.remove(player);
    }

    private void answer(ViewerScans.Task task) {
        var differing = new ArrayList<Integer>();
        var prints = new ArrayList<RecipeBuckets.Fingerprint>();
        for (var index = 0; index < task.digests().size(); index++) {
            var bucket = task.first() + index;
            var mine = fingerprints(task.category()).stream()
                    .filter(print -> RecipeBuckets.bucket(print.partition(), task.bits()) == bucket).toList();
            if (task.force() || RecipeBuckets.digest(mine) != task.digests().get(index)) {
                differing.add(bucket);
                prints.addAll(mine);
            }
        }
        var part = 0;
        for (var from = 0; from == 0 || from < prints.size(); from += 100) {
            var to = Math.min(prints.size(), from + 100);
            ViewerScans.prints(player, through(server, ViewerRecipePayloads.Prints.STREAM_CODEC,
                    new ViewerRecipePayloads.Prints(new ViewerScans.Prints(task.lease(), part++, to == prints.size(),
                            from == 0 ? List.copyOf(differing) : List.of(), List.copyOf(prints.subList(from, to)))))
                    .prints());
        }
        lastTask = task;
    }

    private void answer(ViewerScans.Reads reads) {
        var behind = new HashMap<RecipeBuckets.Fingerprint, ArrayDeque<ViewerRecipes.Recipe>>();
        for (var recipe : recipes) {
            if (recipe.category().equals(lastTask.category())) {
                behind.computeIfAbsent(fingerprint(recipe), each -> new ArrayDeque<>()).add(recipe);
            }
        }
        var entries = new ArrayList<BucketedRecipes.Entry<ViewerRecipes.Recipe>>();
        for (var print : reads.fingerprints()) {
            var recipe = behind.get(print).poll();
            entries.add(new BucketedRecipes.Entry<>(print, BucketedRecipes.Status.RECIPE, recipe));
            read++;
        }
        var part = outOfOrder ? 1 : 0;
        for (var from = 0; from == 0 || from < entries.size(); from += 50) {
            var to = Math.min(entries.size(), from + 50);
            ViewerScans.records(player, through(server, ViewerRecipePayloads.Records.STREAM_CODEC,
                    new ViewerRecipePayloads.Records(new ViewerScans.Records(reads.lease(), part++,
                            to == entries.size(), List.copyOf(entries.subList(from, to))))).records());
        }
    }

    private List<RecipeBuckets.Fingerprint> fingerprints(String category) {
        return recipes.stream().filter(recipe -> recipe.category().equals(category)).map(StandInViewer::fingerprint)
                .toList();
    }

    public static RecipeBuckets.Fingerprint fingerprint(ViewerRecipes.Recipe recipe) {
        var partition = recipe.id().map(id -> "id:" + id).orElseGet(() -> recipe.inputs().stream()
                .map(slot -> slot.stacks().get(0).what().getId() + "x" + slot.stacks().get(0).amount()).sorted()
                .collect(Collectors.joining(",")) + ">" + recipe.outputs().stream()
                .map(stack -> stack.what().getId() + "x" + stack.amount()).sorted().collect(Collectors.joining(",")));
        return new RecipeBuckets.Fingerprint(RecipeBuckets.hash(recipe.category() + "\0" + partition),
                RecipeBuckets.hash(recipe.toString()));
    }

    private static <T> T through(MinecraftServer server, StreamCodec<RegistryFriendlyByteBuf, T> codec, T payload) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), server.registryAccess(), ConnectionType.NEOFORGE);
        codec.encode(buffer, payload);
        return codec.decode(buffer);
    }
}
