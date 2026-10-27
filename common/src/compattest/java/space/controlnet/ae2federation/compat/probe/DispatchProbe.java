package space.controlnet.ae2federation.compat.probe;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.me.service.CraftingService;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

/**
 * Development-only (P0 of the CPU route, {@code .omo/knowledges/remote-crafting-cpu-route-plan-2026-10-26.md}): records
 * where crafting dispatches happen, without changing them. Executor probes wrap the call through which a CPU hands a
 * pattern to a provider and say which crafting service dispatched and how many runs; witness probes sit on the
 * provider side and count every push that arrives, with the call stack of each push no executor probe saw. Witnesses
 * also check the cheaper alternative to executor probes: whether a crafting service's dispatch scope (its end-of-tick
 * work, or an addon's deferred dispatch) was open, and whether that service holds the provider, with the call stack of
 * each push outside every scope. Enabled by {@code -Dae2federation.dispatchProbe=true}; {@link #write} puts the counts
 * beside the compat report.
 *
 * <p>Gap measurement for option 1 of the CPU route (real providers, a minimal projection only as fallback): the known
 * protocols are the provider-side hooks that option would keep, namely {@code PatternProviderLogic.pushPattern}, Data
 * Energistics' expanded pushes, Neo ECO's batch and FastPath pushes and Thunderbolt's {@code pushBatch}. A pattern
 * provider whose push succeeds, or whose send list grows, while none of them is open was driven by a protocol that
 * option could not account; so was an accepted executor hand-over that opened none of them.
 */
public final class DispatchProbe {
    public static final boolean ENABLED = Boolean.getBoolean("ae2federation.dispatchProbe");
    /** Runs not known at the executor, such as a single push's pattern before a provider scales it. */
    public static final long UNKNOWN = -1;
    private static final int STACK_FRAMES = 14;
    private static final int STACKS_PER_SITE = 12;

    private static final class Frame {
        final String site;
        final @Nullable CraftingService service;
        final @Nullable Object provider;
        boolean known;

        Frame(String site, @Nullable CraftingService service, @Nullable Object provider) {
            this.site = site;
            this.service = service;
            this.provider = provider;
        }
    }

    private static final ThreadLocal<ArrayDeque<Frame>> FRAMES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<ArrayDeque<CraftingService>> SCOPES = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Map<String, Executor> EXECUTORS = new LinkedHashMap<>();
    private static final Map<String, Witness> WITNESSES = new LinkedHashMap<>();
    /** Open known protocols, and open pattern provider restores from NBT, on this thread. */
    private static final ThreadLocal<int[]> KNOWN = ThreadLocal.withInitial(() -> new int[1]);
    private static final ThreadLocal<int[]> LOADING = ThreadLocal.withInitial(() -> new int[1]);
    private static final Map<String, Gap> UNHOOKED_DISPATCHES = new LinkedHashMap<>();
    private static final Map<String, Gap> UNRECORDED_SUCCESSES = new LinkedHashMap<>();
    private static final Map<String, Gap> UNRECORDED_SENDS = new LinkedHashMap<>();
    /** Executor site, then provider class, then accepted hand-overs and those that opened a known protocol. */
    private static final Map<String, Map<String, long[]>> PROVIDER_CLASSES = new LinkedHashMap<>();
    /** Every successful pattern provider push and every send list growth, so a zero gap is known to be measured. */
    private static long pushSuccesses, sendListGrowths;

    private static final class Executor {
        long calls, accepted, runsAccepted, runsUnknown, nested;
        long argumentService, scopeService, noService, argumentDiffersFromScope;
    }

    private static final class Witness {
        long calls, covered, uncovered, runs;
        long attributionChecked, attributionCorrect;
        long inScope, outOfScope, scopeAttributionChecked, scopeAttributionCorrect;
        final Map<String, Long> uncoveredStacks = new LinkedHashMap<>();
        final Map<String, Long> outOfScopeStacks = new LinkedHashMap<>();
        final Map<String, Long> coveredBy = new LinkedHashMap<>();
    }

    private static final class Gap {
        long count, inScope, outOfScope;
        final Map<String, Long> stacks = new LinkedHashMap<>();
    }

    private DispatchProbe() {}

    /** Opens a known protocol on this thread, marking every open executor frame; pair with {@link #exitKnown}. */
    public static void enterKnown() {
        KNOWN.get()[0]++;
        for (var frame : FRAMES.get()) frame.known = true;
    }

    public static void exitKnown() {
        var depth = KNOWN.get();
        if (depth[0] > 0) depth[0]--;
    }

    /** Brackets a pattern provider's restore from NBT, which refills its send list without any push. */
    public static void enterLoading() {
        LOADING.get()[0]++;
    }

    public static void exitLoading() {
        var depth = LOADING.get();
        if (depth[0] > 0) depth[0]--;
    }

    /** A pattern provider's push succeeded; a gap when no known protocol is open. */
    public static void pushSucceeded(Object logic) {
        synchronized (DispatchProbe.class) {
            pushSuccesses++;
        }
        if (KNOWN.get()[0] == 0) gap(UNRECORDED_SUCCESSES, logic.getClass().getName());
    }

    /** {@code stacks} were added to a pattern provider's send list; a gap outside known protocols and restores. */
    public static void sendListGrew(Object logic, int stacks) {
        if (stacks > 0 && LOADING.get()[0] == 0) {
            synchronized (DispatchProbe.class) {
                sendListGrowths++;
            }
        }
        if (stacks > 0 && KNOWN.get()[0] == 0 && LOADING.get()[0] == 0) {
            gap(UNRECORDED_SENDS, logic.getClass().getName());
        }
    }

    private static void gap(Map<String, Gap> gaps, String key) {
        var stack = stack();
        boolean inScope = SCOPES.get().peek() != null;
        synchronized (DispatchProbe.class) {
            var gap = gaps.computeIfAbsent(key, ignored -> new Gap());
            gap.count++;
            if (inScope) gap.inScope++;
            else gap.outOfScope++;
            keep(gap.stacks, stack);
        }
    }

    private static String describe(Object provider) {
        if (java.lang.reflect.Proxy.isProxyClass(provider.getClass())) {
            return "proxy(" + java.util.Arrays.stream(provider.getClass().getInterfaces()).map(Class::getName)
                    .collect(Collectors.joining(", ")) + ")";
        }
        return provider.getClass().getName();
    }

    /** Opens a crafting service's dispatch scope, such as its end-of-tick CPU work; pair with {@link #exitScope}. */
    public static void enterScope(@Nullable CraftingService service) {
        if (service != null) SCOPES.get().push(service);
    }

    public static void exitScope(@Nullable CraftingService service) {
        var scopes = SCOPES.get();
        if (service != null && !scopes.isEmpty() && scopes.peek() == service) scopes.pop();
        // A known protocol bracketed by HEAD and RETURN injections stays open when it throws; no push outlives a tick.
        if (scopes.isEmpty() && FRAMES.get().isEmpty()) KNOWN.get()[0] = 0;
    }

    /**
     * Records one executor's hand-over of {@code runs} runs ({@link #UNKNOWN} when it cannot tell) to {@code provider},
     * dispatched by {@code service} when the executor has it at hand, and runs {@code call}.
     */
    public static boolean dispatch(String site, @Nullable CraftingService service, @Nullable Object provider, long runs,
            BooleanSupplier call) {
        var frame = open(site, service, provider);
        boolean accepted = false;
        try {
            accepted = call.getAsBoolean();
            return accepted;
        } finally {
            close(frame, site, accepted, runs);
        }
    }

    /** As {@link #dispatch}, for a batch call that answers how many runs it took. */
    public static long dispatchCount(String site, @Nullable CraftingService service, @Nullable Object provider,
            LongSupplier call) {
        var frame = open(site, service, provider);
        long taken = 0;
        try {
            taken = call.getAsLong();
            return taken;
        } finally {
            close(frame, site, taken > 0, taken);
        }
    }

    private static Frame open(String site, @Nullable CraftingService service, @Nullable Object provider) {
        var frames = FRAMES.get();
        var scope = SCOPES.get().peek();
        synchronized (DispatchProbe.class) {
            var executor = EXECUTORS.computeIfAbsent(site, key -> new Executor());
            executor.calls++;
            if (!frames.isEmpty()) executor.nested++;
            if (service != null) {
                executor.argumentService++;
                if (scope != null && scope != service) executor.argumentDiffersFromScope++;
            } else if (scope != null) {
                executor.scopeService++;
            } else {
                executor.noService++;
            }
        }
        var frame = new Frame(site, service != null ? service : scope, provider);
        frames.push(frame);
        return frame;
    }

    private static void close(Frame frame, String site, boolean accepted, long runs) {
        var frames = FRAMES.get();
        if (!frames.isEmpty() && frames.peek() == frame) frames.pop();
        if (!accepted) return;
        var providerClass = frame.provider == null ? "(not passed)" : describe(frame.provider);
        synchronized (DispatchProbe.class) {
            var executor = EXECUTORS.get(site);
            executor.accepted++;
            if (runs >= 0) executor.runsAccepted += runs;
            else executor.runsUnknown++;
            var counts = PROVIDER_CLASSES.computeIfAbsent(site, key -> new LinkedHashMap<>())
                    .computeIfAbsent(providerClass, key -> new long[2]);
            counts[0]++;
            if (frame.known) counts[1]++;
        }
        if (!frame.known) gap(UNHOOKED_DISPATCHES, site + " -> " + providerClass);
    }

    /**
     * Records a push arriving at a provider-side {@code site}: covered when an executor probe is open on this thread,
     * otherwise with its call stack. {@code registered}, when given, is the object a crafting service holds for this
     * provider, checked against the open executor's service and, separately, against the innermost open scope's.
     */
    public static void witness(String site, @Nullable ICraftingProvider registered, @Nullable IPatternDetails pattern,
            long runs) {
        var frame = FRAMES.get().peek();
        var scope = SCOPES.get().peek();
        String stack = frame == null || scope == null ? stack() : null;
        Boolean attributed = frame == null ? null : holds(frame.service, registered, pattern);
        Boolean scopeAttributed = holds(scope, registered, pattern);
        synchronized (DispatchProbe.class) {
            var witness = WITNESSES.computeIfAbsent(site, key -> new Witness());
            witness.calls++;
            if (runs > 0) witness.runs += runs;
            if (frame != null) {
                witness.covered++;
                witness.coveredBy.merge(frame.site, 1L, Long::sum);
            } else {
                witness.uncovered++;
                keep(witness.uncoveredStacks, stack);
            }
            if (scope != null) {
                witness.inScope++;
            } else {
                witness.outOfScope++;
                keep(witness.outOfScopeStacks, stack);
            }
            if (attributed != null) {
                witness.attributionChecked++;
                if (attributed) witness.attributionCorrect++;
            }
            if (scopeAttributed != null) {
                witness.scopeAttributionChecked++;
                if (scopeAttributed) witness.scopeAttributionCorrect++;
            }
        }
    }

    /** Whether {@code service} holds {@code registered} for {@code pattern}; null when any of them is unknown. */
    private static @Nullable Boolean holds(@Nullable CraftingService service, @Nullable ICraftingProvider registered,
            @Nullable IPatternDetails pattern) {
        if (service == null || registered == null || pattern == null) return null;
        for (var provider : service.getProviders(pattern)) {
            if (provider == registered) return true;
        }
        return false;
    }

    private static void keep(Map<String, Long> stacks, String stack) {
        if (stacks.containsKey(stack) || stacks.size() < STACKS_PER_SITE) stacks.merge(stack, 1L, Long::sum);
    }

    private static String stack() {
        return StackWalker.getInstance().walk(frames -> frames
                .filter(frame -> !frame.getClassName().startsWith(DispatchProbe.class.getPackageName()))
                .limit(STACK_FRAMES)
                .map(frame -> frame.getClassName() + "." + frame.getMethodName())
                .collect(Collectors.joining(" < ")));
    }

    /** Writes the counts to {@code dispatch-probe.json} in {@code directory}. */
    public static synchronized void write(Path directory) {
        if (!ENABLED) return;
        var root = new JsonObject();
        var executors = new JsonObject();
        EXECUTORS.forEach((site, executor) -> {
            var json = new JsonObject();
            json.addProperty("calls", executor.calls);
            json.addProperty("accepted", executor.accepted);
            json.addProperty("runsAccepted", executor.runsAccepted);
            json.addProperty("acceptedWithUnknownRuns", executor.runsUnknown);
            json.addProperty("nested", executor.nested);
            json.addProperty("serviceFromArgument", executor.argumentService);
            json.addProperty("serviceFromScope", executor.scopeService);
            json.addProperty("noService", executor.noService);
            json.addProperty("argumentDiffersFromScope", executor.argumentDiffersFromScope);
            executors.add(site, json);
        });
        root.add("executors", executors);
        var witnesses = new JsonObject();
        WITNESSES.forEach((site, witness) -> {
            var json = new JsonObject();
            json.addProperty("calls", witness.calls);
            json.addProperty("covered", witness.covered);
            json.addProperty("uncovered", witness.uncovered);
            json.addProperty("runs", witness.runs);
            json.addProperty("attributionChecked", witness.attributionChecked);
            json.addProperty("attributionCorrect", witness.attributionCorrect);
            json.addProperty("inScope", witness.inScope);
            json.addProperty("outOfScope", witness.outOfScope);
            json.addProperty("scopeAttributionChecked", witness.scopeAttributionChecked);
            json.addProperty("scopeAttributionCorrect", witness.scopeAttributionCorrect);
            var coveredBy = new JsonObject();
            witness.coveredBy.forEach(coveredBy::addProperty);
            json.add("coveredBy", coveredBy);
            json.add("uncoveredStacks", stacks(witness.uncoveredStacks));
            json.add("outOfScopeStacks", stacks(witness.outOfScopeStacks));
            witnesses.add(site, json);
        });
        root.add("witnesses", witnesses);
        root.addProperty("pushSuccesses", pushSuccesses);
        root.addProperty("sendListGrowths", sendListGrowths);
        root.add("unhookedDispatches", gaps(UNHOOKED_DISPATCHES));
        root.add("unrecordedSuccesses", gaps(UNRECORDED_SUCCESSES));
        root.add("unrecordedSends", gaps(UNRECORDED_SENDS));
        var providerClasses = new JsonObject();
        PROVIDER_CLASSES.forEach((site, classes) -> {
            var json = new JsonObject();
            classes.forEach((name, counts) -> {
                var entry = new JsonObject();
                entry.addProperty("accepted", counts[0]);
                entry.addProperty("known", counts[1]);
                json.add(name, entry);
            });
            providerClasses.add(site, json);
        });
        root.add("providerClasses", providerClasses);
        try {
            Files.writeString(directory.resolve("dispatch-probe.json"),
                    new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            com.mojang.logging.LogUtils.getLogger().error("AE2F_DISPATCH_PROBE_WRITE_FAILED", exception);
        }
    }

    private static JsonObject gaps(Map<String, Gap> gaps) {
        var json = new JsonObject();
        gaps.forEach((key, gap) -> {
            var entry = new JsonObject();
            entry.addProperty("count", gap.count);
            entry.addProperty("inScope", gap.inScope);
            entry.addProperty("outOfScope", gap.outOfScope);
            entry.add("stacks", stacks(gap.stacks));
            json.add(key, entry);
        });
        return json;
    }

    private static JsonArray stacks(Map<String, Long> stacks) {
        var array = new JsonArray();
        stacks.forEach((stack, count) -> {
            var entry = new JsonObject();
            entry.addProperty("count", count);
            entry.addProperty("stack", stack);
            array.add(entry);
        });
        return array;
    }
}
