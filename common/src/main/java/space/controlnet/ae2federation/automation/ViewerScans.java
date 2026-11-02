package space.controlnet.ae2federation.automation;

import io.netty.buffer.ByteBuf;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.automation.RecipeBuckets.Fingerprint;

/**
 * Keeps {@link ViewerRecipes} the same as what players' recipe viewers (JEI) show, reading again only what changed.
 * A client says hello once its viewer has all its categories, listing each with its count and machines. Once per
 * server start and after each datapack reload (a round), the server checks every listed category, a task of at most
 * eight buckets ({@link RecipeBuckets}) at a time, each task leased to one ready viewer:
 *
 * <ol>
 * <li>the server sends the task's bucket digests;</li>
 * <li>the viewer sends its fingerprints of the buckets whose digest differs from its own;</li>
 * <li>the server compares them recipe by recipe and sends the fingerprints it lacks;</li>
 * <li>the viewer reads those and sends them; the server saves each bucket, dropping what the viewer no longer has.</li>
 * </ol>
 *
 * <p>Every ready viewer works on a task of its own at once. A lease ends when its viewer leaves, gives up, sends
 * something out of order, or is silent for two minutes; its task goes to the next ready viewer, and the buckets saved
 * before stay saved. A saved category that a viewer does not list is forgotten only when the server has no mod of its
 * namespace (the pack dropped the mod); otherwise that viewer may just lack a client mod.
 *
 * <p>Assistant choices: any player's viewer may send recipes, as AE2's own terminal encodes whatever processing
 * pattern a player sets up; eight buckets a task and a two-minute lease (2026-11-02 design notes). A viewer whose lease
 * ended other than by leaving gets no more tasks until it says hello again. The first viewer to take a new category's
 * first task sets its bucket count from the count it listed.
 */
public final class ViewerScans {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ViewerScans.class);
    static final int BUCKETS_PER_TASK = 8;
    static final long LEASE_MILLIS = 120_000;
    /** Per message part: more than a part of 30,000 bytes holds, so a part cannot ask for unbounded memory. */
    public static final int MAX_PART_ENTRIES = 4096;
    /** A viewer's categories in all its hello parts: far more than any pack shows. */
    static final int MAX_CATEGORIES = 65_536;
    private static final Map<MinecraftServer, State> STATES = new WeakHashMap<>();
    private static Sender sender = new Sender() {
        @Override
        public void task(ServerPlayer player, Task task) {
        }

        @Override
        public void reads(ServerPlayer player, Reads reads) {
        }
    };
    private static LongSupplier clock = System::currentTimeMillis;
    private static Predicate<String> modLoaded = namespace -> ModList.get().isLoaded(namespace);

    /** Eight bytes as they are: hashes spread over all 64 bits, which a variable length would only lengthen. */
    private static final StreamCodec<ByteBuf, Long> LONG = StreamCodec.of(ByteBuf::writeLong, ByteBuf::readLong);
    public static final StreamCodec<ByteBuf, Fingerprint> FINGERPRINT = StreamCodec.composite(
            LONG, Fingerprint::partition, LONG, Fingerprint::digest, Fingerprint::new);
    public static final StreamCodec<RegistryFriendlyByteBuf, BucketedRecipes.Entry<ViewerRecipes.Recipe>> ENTRY =
            StreamCodec.composite(
                    FINGERPRINT, entry -> entry.fingerprint(),
                    ByteBufCodecs.idMapper(index -> BucketedRecipes.Status.values()[index], Enum::ordinal),
                    entry -> entry.status(),
                    ViewerRecipes.Recipe.STREAM_CODEC.apply(ByteBufCodecs::optional),
                    entry -> Optional.ofNullable(entry.record()),
                    (fingerprint, status, record) -> new BucketedRecipes.Entry<>(fingerprint, status,
                            record.orElse(null)));

    /** A category a viewer shows: its ID, how many recipes it lists, and the items of its machines. */
    public record Listed(String category, int count, List<String> machines) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Listed> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.stringUtf8(256), Listed::category,
                ByteBufCodecs.VAR_INT, Listed::count,
                ByteBufCodecs.stringUtf8(256).apply(ByteBufCodecs.list(ViewerRecipes.MAX_MACHINES)), Listed::machines,
                Listed::new);
    }

    /** A viewer's categories, in parts from 0; a new generation is a new load of the viewer's recipes. */
    public record Hello(int generation, int part, boolean last, List<Listed> categories) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Hello> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Hello::generation,
                ByteBufCodecs.VAR_INT, Hello::part,
                ByteBufCodecs.BOOL, Hello::last,
                Listed.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_PART_ENTRIES)), Hello::categories,
                Hello::new);
    }

    /** A task leased to a viewer: the server's digests of {@code digests.size()} buckets from {@code first}. */
    public record Task(int lease, String category, int bits, int first, List<Long> digests) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Task> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Task::lease,
                ByteBufCodecs.stringUtf8(256), Task::category,
                ByteBufCodecs.VAR_INT, Task::bits,
                ByteBufCodecs.VAR_INT, Task::first,
                LONG.apply(ByteBufCodecs.list(BUCKETS_PER_TASK)), Task::digests,
                Task::new);
    }

    /** The viewer's fingerprints of the task's buckets whose digest differs, in parts; those buckets on any part. */
    public record Prints(int lease, int part, boolean last, List<Integer> buckets, List<Fingerprint> fingerprints) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Prints> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Prints::lease,
                ByteBufCodecs.VAR_INT, Prints::part,
                ByteBufCodecs.BOOL, Prints::last,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(BUCKETS_PER_TASK)), Prints::buckets,
                FINGERPRINT.apply(ByteBufCodecs.list(MAX_PART_ENTRIES)), Prints::fingerprints,
                Prints::new);
    }

    /** The fingerprints the server lacks, to read. */
    public record Reads(int lease, List<Fingerprint> fingerprints) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Reads> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Reads::lease,
                FINGERPRINT.apply(ByteBufCodecs.list(MAX_PART_ENTRIES * BUCKETS_PER_TASK)), Reads::fingerprints,
                Reads::new);
    }

    /** What the viewer read, in parts from 0; part -1 is the viewer giving the task up. */
    public record Records(int lease, int part, boolean last,
            List<BucketedRecipes.Entry<ViewerRecipes.Recipe>> entries) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Records> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Records::lease,
                ByteBufCodecs.VAR_INT, Records::part,
                ByteBufCodecs.BOOL, Records::last,
                ENTRY.apply(ByteBufCodecs.list(MAX_PART_ENTRIES)), Records::entries,
                Records::new);
    }

    /** How far the current round is: categories still to check, leases out, recipes saved, ready viewers. */
    public record Progress(int waiting, int leased, int saved, int viewers) {
        /** Every category a ready viewer listed is checked, and at least one viewer said hello. */
        public boolean done() {
            return viewers > 0 && waiting == 0 && leased == 0;
        }
    }

    /** How the server's messages reach a player's viewer. */
    public interface Sender {
        void task(ServerPlayer player, Task task);

        void reads(ServerPlayer player, Reads reads);
    }

    private static final class State {
        int nextLease = 1;
        final Map<UUID, Viewer> viewers = new LinkedHashMap<>();
        /** This round's categories still to check, in the order viewers listed them. */
        final Map<String, Work> work = new LinkedHashMap<>();
        /** This round's categories handed to {@link #work} once, so a later hello does not check them again. */
        final Set<String> checking = new HashSet<>();
        final Map<Integer, Lease> leases = new HashMap<>();
        int saved;
    }

    private static final class Viewer {
        int generation = Integer.MIN_VALUE;
        int nextPart;
        final Map<String, Listed> parts = new LinkedHashMap<>();
        Map<String, Listed> listed = Map.of();
        boolean ready;
        @Nullable Lease lease;
    }

    /** A category's buckets in this round: those not handed out yet, those to hand out again, and those out. */
    private static final class Work {
        final String category;
        int bits = -1;
        int next;
        final ArrayDeque<Integer> retry = new ArrayDeque<>();
        int out;

        Work(String category) {
            this.category = category;
        }

        boolean finished() {
            return bits >= 0 && next >= 1 << bits && retry.isEmpty() && out == 0;
        }
    }

    private static final class Lease {
        final int id;
        final UUID player;
        final String category;
        final int bits;
        final int first;
        final int count;
        long heard;
        int nextPart;
        final Set<Integer> buckets = new HashSet<>();
        final List<Fingerprint> prints = new ArrayList<>();
        /** After the viewer's fingerprints: what each differing bucket drops, and the reads still awaited. */
        @Nullable Map<Integer, List<Fingerprint>> drops;
        final Map<Fingerprint, Integer> awaited = new HashMap<>();
        final List<BucketedRecipes.Entry<ViewerRecipes.Recipe>> received = new ArrayList<>();

        Lease(int id, UUID player, String category, int bits, int first, int count) {
            this.id = id;
            this.player = player;
            this.category = category;
            this.bits = bits;
            this.first = first;
            this.count = count;
        }
    }

    private ViewerScans() {
    }

    /** Once, at mod start: how the server's messages reach a player. */
    public static void register(Sender send) {
        sendWith(send);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                left(player);
            }
        });
        // With no player, the event is a reload's: the viewers reload their recipes and say hello again.
        NeoForge.EVENT_BUS.addListener((OnDatapackSyncEvent event) -> {
            if (event.getPlayer() == null) {
                reloaded(event.getPlayerList().getServer());
            }
        });
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post event) -> {
            if (event.getServer().getTickCount() % 20 == 0) {
                expire(event.getServer());
            }
        });
    }

    /** How the server's messages reach a player from now on; the previous way back. GameTests stand in for clients. */
    public static synchronized Sender sendWith(Sender send) {
        var previous = sender;
        sender = send;
        return previous;
    }

    /** The clock leases expire by, in milliseconds; the previous one back. GameTests move time on. */
    public static synchronized LongSupplier clockWith(LongSupplier now) {
        var previous = clock;
        clock = now;
        return previous;
    }

    /** Whether the server has a mod of a namespace; the previous test back. GameTests stand in for a dropped mod. */
    public static synchronized Predicate<String> modsWith(Predicate<String> loaded) {
        var previous = modLoaded;
        modLoaded = loaded;
        return previous;
    }

    private static synchronized State state(MinecraftServer server) {
        return STATES.computeIfAbsent(server, ignored -> new State());
    }

    public static synchronized Progress progress(MinecraftServer server) {
        var state = state(server);
        return new Progress(state.work.size(), state.leases.size(), state.saved,
                (int) state.viewers.values().stream().filter(viewer -> viewer.ready).count());
    }

    public static synchronized void hello(ServerPlayer player, Hello hello) {
        var state = state(player.server);
        var viewer = state.viewers.computeIfAbsent(player.getUUID(), id -> new Viewer());
        if (hello.generation() != viewer.generation) {
            // The viewer reloaded its recipes: what it was sending may be from before.
            end(state, viewer);
            viewer.generation = hello.generation();
            viewer.ready = false;
            viewer.listed = Map.of();
        }
        if (hello.part() == 0) {
            viewer.nextPart = 0;
            viewer.parts.clear();
        }
        if (hello.part() != viewer.nextPart || viewer.parts.size() + hello.categories().size() > MAX_CATEGORIES) {
            LOGGER.warn("Dropped a recipe viewer hello from {}: part {} out of order or too many categories",
                    player.getGameProfile().getName(), hello.part());
            viewer.nextPart = 0;
            viewer.parts.clear();
            return;
        }
        viewer.nextPart++;
        hello.categories().forEach(listed -> viewer.parts.put(listed.category(), listed));
        if (!hello.last()) {
            return;
        }
        viewer.listed = Map.copyOf(viewer.parts);
        viewer.parts.clear();
        viewer.nextPart = 0;
        viewer.ready = true;
        listed(player.server, state, viewer);
        assign(player.server, state);
    }

    public static synchronized void prints(ServerPlayer player, Prints prints) {
        var state = state(player.server);
        var lease = lease(state, player, prints.lease());
        if (lease == null) {
            return;
        }
        if (lease.drops != null || prints.part() != lease.nextPart
                || lease.prints.size() + prints.fingerprints().size() > MAX_PART_ENTRIES * BUCKETS_PER_TASK
                || prints.buckets().stream().anyMatch(bucket -> bucket < lease.first
                        || bucket >= lease.first + lease.count)) {
            refuse(player.server, state, lease, "fingerprints out of order or outside the task");
            return;
        }
        lease.heard = clock.getAsLong();
        lease.nextPart++;
        lease.buckets.addAll(prints.buckets());
        lease.prints.addAll(prints.fingerprints());
        if (!prints.last()) {
            return;
        }
        if (lease.prints.stream().anyMatch(print -> !lease.buckets.contains(
                RecipeBuckets.bucket(print.partition(), lease.bits)))) {
            refuse(player.server, state, lease, "a fingerprint of a bucket not listed");
            return;
        }
        var recipes = ViewerRecipes.get(player.server).category(lease.category).recipes();
        var viewerPrints = new TreeMap<Integer, List<Fingerprint>>();
        lease.buckets.forEach(bucket -> viewerPrints.put(bucket, new ArrayList<>()));
        lease.prints.forEach(print -> viewerPrints.get(RecipeBuckets.bucket(print.partition(), lease.bits))
                .add(print));
        var drops = new TreeMap<Integer, List<Fingerprint>>();
        var reads = new ArrayList<Fingerprint>();
        viewerPrints.forEach((bucket, viewer) -> {
            var difference = RecipeBuckets.compare(recipes.fingerprints(bucket), viewer);
            drops.put(bucket, difference.drop());
            reads.addAll(difference.read());
        });
        lease.drops = drops;
        reads.forEach(print -> lease.awaited.merge(print, 1, Integer::sum));
        lease.nextPart = 0;
        if (reads.isEmpty()) {
            commit(player.server, state, lease);
        } else {
            sender.reads(player, new Reads(lease.id, List.copyOf(reads)));
        }
    }

    public static synchronized void records(ServerPlayer player, Records records) {
        var state = state(player.server);
        var lease = lease(state, player, records.lease());
        if (lease == null) {
            return;
        }
        if (records.part() < 0) {
            var viewer = state.viewers.get(lease.player);
            viewer.ready = false;
            end(state, viewer);
            assign(player.server, state);
            return;
        }
        if (lease.drops == null || records.part() != lease.nextPart) {
            refuse(player.server, state, lease, "records out of order");
            return;
        }
        for (var entry : records.entries()) {
            if (lease.awaited.merge(entry.fingerprint(), -1, Integer::sum) < 0) {
                refuse(player.server, state, lease, "a record the server did not ask for");
                return;
            }
        }
        lease.heard = clock.getAsLong();
        lease.nextPart++;
        lease.received.addAll(records.entries());
        if (!records.last()) {
            return;
        }
        if (lease.awaited.values().stream().anyMatch(left -> left != 0)) {
            refuse(player.server, state, lease, "records missing");
            return;
        }
        commit(player.server, state, lease);
    }

    public static synchronized void left(ServerPlayer player) {
        var state = state(player.server);
        var viewer = state.viewers.remove(player.getUUID());
        if (viewer != null) {
            end(state, viewer);
            assign(player.server, state);
        }
    }

    /** Datapacks reloaded: a new round, once the viewers reloaded their recipes and said hello again. */
    public static synchronized void reloaded(MinecraftServer server) {
        var state = state(server);
        state.leases.clear();
        state.work.clear();
        state.checking.clear();
        state.saved = 0;
        state.viewers.values().forEach(viewer -> {
            viewer.lease = null;
            viewer.ready = false;
        });
    }

    /** Leases silent for two minutes end, and their tasks go to the next ready viewer. */
    public static synchronized void expire(MinecraftServer server) {
        var state = state(server);
        var now = clock.getAsLong();
        var expired = state.leases.values().stream().filter(lease -> now - lease.heard >= LEASE_MILLIS).toList();
        for (var lease : expired) {
            LOGGER.warn("A recipe viewer task of {} expired", lease.category);
            var viewer = state.viewers.get(lease.player);
            if (viewer != null) {
                viewer.ready = false;
                end(state, viewer);
            } else {
                giveBack(state, lease);
            }
        }
        if (!expired.isEmpty()) {
            assign(server, state);
        }
    }

    private static @Nullable Lease lease(State state, ServerPlayer player, int id) {
        var lease = state.leases.get(id);
        return lease != null && lease.player.equals(player.getUUID()) ? lease : null;
    }

    /** What a viewer listed: machines saved, categories gone with their mods forgotten, new ones to check. */
    private static void listed(MinecraftServer server, State state, Viewer viewer) {
        var recipes = ViewerRecipes.get(server);
        var machines = new LinkedHashMap<String, List<String>>();
        viewer.listed.values().forEach(listed -> machines.put(listed.category(), listed.machines()));
        recipes.machines(machines);
        for (var category : recipes.stored()) {
            if (!viewer.listed.containsKey(category) && !modLoaded.test(namespace(category))) {
                LOGGER.info("Forgot the viewer recipes of {}: the viewer does not list it and its mod is gone",
                        category);
                state.work.remove(category);
                for (var lease : List.copyOf(state.leases.values())) {
                    if (lease.category.equals(category)) {
                        state.leases.remove(lease.id);
                        var holder = state.viewers.get(lease.player);
                        if (holder != null && holder.lease == lease) {
                            holder.lease = null;
                        }
                    }
                }
                recipes.remove(category);
            }
        }
        for (var category : viewer.listed.keySet()) {
            if (state.checking.add(category)) {
                state.work.put(category, new Work(category));
            }
        }
    }

    private static String namespace(String category) {
        var id = ResourceLocation.tryParse(category);
        return id == null ? category : id.getNamespace();
    }

    /** Each ready viewer without a task gets the next one it can answer. */
    private static void assign(MinecraftServer server, State state) {
        for (var each : List.copyOf(state.viewers.entrySet())) {
            var viewer = each.getValue();
            if (!viewer.ready || viewer.lease != null) {
                continue;
            }
            var player = server.getPlayerList().getPlayer(each.getKey());
            if (player != null) {
                next(server, state, each.getKey(), viewer, player);
            }
        }
    }

    private static void next(MinecraftServer server, State state, UUID id, Viewer viewer, ServerPlayer player) {
        var recipes = ViewerRecipes.get(server);
        for (var work : List.copyOf(state.work.values())) {
            var listed = viewer.listed.get(work.category);
            if (listed == null) {
                continue;
            }
            if (work.bits < 0) {
                if (recipes.category(work.category) == null) {
                    if (listed.count() == 0) {
                        state.work.remove(work.category);
                        continue;
                    }
                    recipes.create(work.category, RecipeBuckets.bitsFor(listed.count()));
                }
                work.bits = recipes.split(work.category);
            }
            int first;
            if (!work.retry.isEmpty()) {
                first = work.retry.poll();
            } else if (work.next < 1 << work.bits) {
                first = work.next;
                work.next += BUCKETS_PER_TASK;
            } else {
                continue;
            }
            var count = Math.min(BUCKETS_PER_TASK, (1 << work.bits) - first);
            var lease = new Lease(state.nextLease++, id, work.category, work.bits, first, count);
            lease.heard = clock.getAsLong();
            work.out++;
            state.leases.put(lease.id, lease);
            viewer.lease = lease;
            var stored = recipes.category(work.category).recipes();
            var digests = new ArrayList<Long>();
            for (var bucket = first; bucket < first + count; bucket++) {
                digests.add(stored.digest(bucket));
            }
            sender.task(player, new Task(lease.id, work.category, work.bits, first, List.copyOf(digests)));
            return;
        }
    }

    /** Each differing bucket of the task saved: what the viewer no longer has dropped, what it read added. */
    private static void commit(MinecraftServer server, State state, Lease lease) {
        var recipes = ViewerRecipes.get(server);
        var added = new TreeMap<Integer, List<BucketedRecipes.Entry<ViewerRecipes.Recipe>>>();
        lease.received.forEach(entry -> added.computeIfAbsent(RecipeBuckets.bucket(
                entry.fingerprint().partition(), lease.bits), bucket -> new ArrayList<>()).add(entry));
        lease.drops.forEach((bucket, drop) -> {
            var add = added.getOrDefault(bucket, List.of());
            if (!drop.isEmpty() || !add.isEmpty()) {
                recipes.apply(lease.category, bucket, drop, add);
            }
        });
        state.saved += (int) lease.received.stream().filter(entry -> entry.record() != null).count();
        state.leases.remove(lease.id);
        var viewer = state.viewers.get(lease.player);
        if (viewer != null && viewer.lease == lease) {
            viewer.lease = null;
        }
        var work = state.work.get(lease.category);
        if (work != null) {
            work.out--;
            if (work.finished()) {
                state.work.remove(lease.category);
            }
        }
        if (state.work.isEmpty() && state.leases.isEmpty()) {
            LOGGER.info("Checked every recipe viewer category: {} recipes read, {} saved in {} categories",
                    state.saved, recipes.size(), recipes.stored().size());
        }
        assign(server, state);
    }

    private static void refuse(MinecraftServer server, State state, Lease lease, String why) {
        LOGGER.warn("Dropped a recipe viewer task of {}: {}", lease.category, why);
        var viewer = state.viewers.get(lease.player);
        if (viewer != null) {
            viewer.ready = false;
            end(state, viewer);
        } else {
            giveBack(state, lease);
        }
        assign(server, state);
    }

    /** The viewer's lease, if any, ended, its task handed out again. */
    private static void end(State state, Viewer viewer) {
        if (viewer.lease != null) {
            giveBack(state, viewer.lease);
            viewer.lease = null;
        }
    }

    private static void giveBack(State state, Lease lease) {
        if (state.leases.remove(lease.id) == null) {
            return;
        }
        var work = state.work.get(lease.category);
        if (work != null) {
            work.out--;
            work.retry.add(lease.first);
        }
    }
}
