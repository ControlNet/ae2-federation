package space.controlnet.ae2federation.client.automation;

import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.automation.BucketedRecipes;
import space.controlnet.ae2federation.automation.RecipeBuckets;
import space.controlnet.ae2federation.automation.ViewerRecipes;
import space.controlnet.ae2federation.automation.ViewerScans;

/**
 * The client's side of {@link ViewerScans}: says hello once the recipe viewer's categories stop changing, and works
 * on the tasks the server leases it a few milliseconds a tick, sending what it finds in parts small enough for one
 * packet each. A viewer plugin hands its {@link ViewerScanner} over when its recipes are ready.
 *
 * <p>2.5 ms a tick, the budget the AE All Pattern mod settled on for the same JEI reads (owner decision, 2026-11-01):
 * building a recipe's JEI layout runs the mod's own category code on the render thread, so it cannot move to another
 * thread. Assistant choices: hello once the set of categories is unchanged for 30 s (mods such as MineColonies add
 * theirs about 20 s after JEI has its recipes), and again whenever it changes; parts of at most 30,000 bytes, under
 * the 32,767 bytes of a packet a client sends, measured by encoding each entry, a recipe larger than a part alone sent
 * as skipped; a part at least every 30 s while a task runs, so the server's two-minute lease does not end.
 */
public final class ViewerScanClient {
    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ViewerScanClient.class);
    static final int PAGE_BYTES = 30_000;
    private static final long BUDGET_NANOS = 2_500_000L;
    private static final long STABLE_NANOS = 30_000_000_000L;
    private static final long QUIET_NANOS = 30_000_000_000L;
    /** Sixteen bytes each, with room for the part's header. */
    private static final int PRINTS_PER_PART = (PAGE_BYTES - 64) / 16;

    private static @Nullable ViewerScanner scanner;
    private static int generation;
    private static Consumer<ViewerScans.Hello> sendHello = hello -> { };
    private static Consumer<ViewerScans.Prints> sendPrints = prints -> { };
    private static Consumer<ViewerScans.Records> sendRecords = records -> { };
    private static int ticks;
    private static @Nullable Set<String> watched;
    private static long stableSince;
    private static @Nullable Set<String> announced;
    private static @Nullable Listing listing;
    /** The categories fingerprinted since the viewer last loaded its recipes. */
    private static final Map<String, Printed> printed = new HashMap<>();
    private static @Nullable Working working;

    private record Printed(ViewerScanner.Prints prints, List<RecipeBuckets.Fingerprint> fingerprints) {
    }

    private ViewerScanClient() {
    }

    /** Once, on the client: how the client's messages reach the server. */
    public static void register(Consumer<ViewerScans.Hello> hello, Consumer<ViewerScans.Prints> prints,
            Consumer<ViewerScans.Records> records) {
        sendHello = hello;
        sendPrints = prints;
        sendRecords = records;
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> tick());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> reset());
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> reset());
    }

    /** A viewer plugin's scanner once its recipes are ready; null when they are gone, as during a reload. */
    public static void scanner(@Nullable ViewerScanner ready) {
        scanner = ready;
        generation++;
        printed.clear();
        reset();
    }

    private static void reset() {
        watched = null;
        announced = null;
        listing = null;
        working = null;
    }

    /** The server leases a task; it replaces any the server ended. */
    public static void task(ViewerScans.Task task) {
        if (scanner == null || Minecraft.getInstance().getConnection() == null) {
            sendRecords.accept(new ViewerScans.Records(task.lease(), -1, true, List.of()));
            return;
        }
        working = new Working(task);
    }

    /** The server asks for the recipes of these fingerprints. */
    public static void reads(ViewerScans.Reads reads) {
        var task = working;
        if (task != null && task.task.lease() == reads.lease()) {
            task.read(reads.fingerprints());
        }
    }

    private static void tick() {
        if (scanner == null || Minecraft.getInstance().getConnection() == null) {
            return;
        }
        var deadline = System.nanoTime() + BUDGET_NANOS;
        if (++ticks % 20 == 0) {
            watch();
        }
        var task = working;
        try {
            if (listing != null && listing.step(deadline)) {
                listing = null;
            }
            // While listing, a task still sends its keep-alive parts so its lease does not end.
            if (task != null && task.step(listing == null ? deadline : System.nanoTime()) && working == task) {
                working = null;
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("The recipe viewer scan failed", failure);
            listing = null;
            if (task != null && working == task) {
                sendRecords.accept(new ViewerScans.Records(task.task.lease(), -1, true, List.of()));
                working = null;
            }
        }
    }

    /** Hello once the categories are unchanged for 30 s, and again after each change. */
    private static void watch() {
        var ids = scanner.categoryIds();
        var now = System.nanoTime();
        if (!ids.equals(watched)) {
            watched = ids;
            stableSince = now;
        }
        if (listing == null && !ids.equals(announced) && now - stableSince >= STABLE_NANOS) {
            announced = ids;
            listing = new Listing(scanner.list(), measure());
        }
    }

    private static RegistryFriendlyByteBuf measure() {
        var connection = Minecraft.getInstance().getConnection();
        return new RegistryFriendlyByteBuf(Unpooled.buffer(), connection.registryAccess(),
                connection.getConnectionType());
    }

    /** The categories being listed for a hello, sent in parts. */
    private static final class Listing {
        final ViewerScanner.Job<ViewerScans.Listed> job;
        final RegistryFriendlyByteBuf measure;
        final List<ViewerScans.Listed> part = new ArrayList<>();
        int bytes;
        int index;
        int listed;

        Listing(ViewerScanner.Job<ViewerScans.Listed> job, RegistryFriendlyByteBuf measure) {
            this.job = job;
            this.measure = measure;
        }

        boolean step(long deadline) {
            if (!job.step(deadline, this::add)) {
                return false;
            }
            send(true);
            LOGGER.info("Told the server of {} recipe viewer categories", listed);
            return true;
        }

        void add(ViewerScans.Listed category) {
            measure.clear();
            ViewerScans.Listed.STREAM_CODEC.encode(measure, category);
            var size = measure.writerIndex();
            if (size > PAGE_BYTES - 64) {
                return;
            }
            if (bytes + size > PAGE_BYTES - 64 || part.size() >= ViewerScans.MAX_PART_ENTRIES) {
                send(false);
            }
            part.add(category);
            bytes += size;
            listed++;
        }

        void send(boolean last) {
            sendHello.accept(new ViewerScans.Hello(generation, index++, last, List.copyOf(part)));
            part.clear();
            bytes = 0;
        }
    }

    /**
     * A leased task: the category fingerprinted (once a load of the viewer's recipes), the fingerprints of the buckets
     * whose digest differs sent, then the recipes the server asks for read and sent.
     */
    private static final class Working {
        final ViewerScans.Task task;
        final ViewerScanner.Prints prints;
        final List<RecipeBuckets.Fingerprint> fingerprints;
        final RegistryFriendlyByteBuf measure = measure();
        final long started = System.nanoTime();
        boolean printing;
        boolean compared;
        int printPart;
        @Nullable ViewerScanner.Job<BucketedRecipes.Entry<ViewerRecipes.Recipe>> reading;
        final List<BucketedRecipes.Entry<ViewerRecipes.Recipe>> page = new ArrayList<>();
        int bytes;
        int recordPart;
        int sent;
        int tooLarge;
        long lastSent = System.nanoTime();

        Working(ViewerScans.Task task) {
            this.task = task;
            var done = printed.get(task.category());
            if (done != null) {
                prints = done.prints();
                fingerprints = done.fingerprints();
            } else {
                prints = scanner.fingerprint(task.category());
                fingerprints = new ArrayList<>();
                printing = true;
            }
        }

        /** True once the task is done on this side. */
        boolean step(long deadline) {
            if (printing) {
                if (!prints.step(deadline, fingerprints::add)) {
                    if (quiet()) {
                        sendPrints.accept(new ViewerScans.Prints(task.lease(), printPart++, false, List.of(),
                                List.of()));
                        lastSent = System.nanoTime();
                    }
                    return false;
                }
                printing = false;
                printed.put(task.category(), new Printed(prints, List.copyOf(fingerprints)));
            }
            if (!compared) {
                compared = true;
                compare();
                return false;
            }
            if (reading == null) {
                return false;
            }
            if (!reading.step(deadline, this::add)) {
                if (quiet()) {
                    send(false);
                }
                return false;
            }
            send(true);
            LOGGER.debug("Sent {} recipes of {} in {} ms, {} too large", sent, task.category(),
                    (System.nanoTime() - started) / 1_000_000L, tooLarge);
            return true;
        }

        private boolean quiet() {
            return System.nanoTime() - lastSent >= QUIET_NANOS;
        }

        /** The fingerprints of the buckets whose digest differs from the server's, or of all when forced, in parts. */
        private void compare() {
            var byBucket = new TreeMap<Integer, List<RecipeBuckets.Fingerprint>>();
            for (var print : fingerprints) {
                var bucket = RecipeBuckets.bucket(print.partition(), task.bits());
                if (bucket >= task.first() && bucket < task.first() + task.digests().size()) {
                    byBucket.computeIfAbsent(bucket, each -> new ArrayList<>()).add(print);
                }
            }
            var differing = new ArrayList<Integer>();
            var out = new ArrayList<RecipeBuckets.Fingerprint>();
            for (var index = 0; index < task.digests().size(); index++) {
                var bucket = task.first() + index;
                var mine = byBucket.getOrDefault(bucket, List.of());
                if (task.force() || RecipeBuckets.digest(mine) != task.digests().get(index)) {
                    differing.add(bucket);
                    out.addAll(mine);
                }
            }
            var from = 0;
            do {
                var to = Math.min(out.size(), from + PRINTS_PER_PART);
                sendPrints.accept(new ViewerScans.Prints(task.lease(), printPart++, to == out.size(),
                        from == 0 ? List.copyOf(differing) : List.of(), List.copyOf(out.subList(from, to))));
                from = to;
            } while (from < out.size());
            lastSent = System.nanoTime();
        }

        void read(List<RecipeBuckets.Fingerprint> wanted) {
            if (compared && reading == null) {
                reading = prints.read(wanted);
            }
        }

        private void add(BucketedRecipes.Entry<ViewerRecipes.Recipe> entry) {
            var size = 40;
            if (entry.record() != null) {
                measure.clear();
                ViewerRecipes.Recipe.STREAM_CODEC.encode(measure, entry.record());
                size += measure.writerIndex();
                if (size > PAGE_BYTES - 64) {
                    tooLarge++;
                    entry = new BucketedRecipes.Entry<>(entry.fingerprint(), BucketedRecipes.Status.SKIPPED, null,
                            entry.record().id().orElse(null));
                    size = 40;
                }
            }
            if (bytes + size > PAGE_BYTES - 64 || page.size() >= ViewerScans.MAX_PART_ENTRIES) {
                send(false);
            }
            page.add(entry);
            bytes += size;
            if (entry.record() != null) {
                sent++;
            }
        }

        private void send(boolean last) {
            sendRecords.accept(new ViewerScans.Records(task.lease(), recordPart++, last, List.copyOf(page)));
            page.clear();
            bytes = 0;
            lastSent = System.nanoTime();
        }
    }
}
