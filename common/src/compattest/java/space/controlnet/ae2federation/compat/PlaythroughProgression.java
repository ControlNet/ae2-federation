package space.controlnet.ae2federation.compat;

import appeng.api.stacks.AEItemKey;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;

/**
 * Phase 4: one plain AE2 base and one federated base grow through the quest book in lockstep. Each order runs on the
 * baseline, then on the federated base, against the stock their earlier orders left. A machine stays a stand-in until
 * the base owns its equipment, taken out of its own storage or ordered first. Between orders the run can stop at a
 * checkpoint: the bases stay in the saved world and a new server process continues them.
 */
final class PlaythroughProgression {
    static final String PREFIX = "ae2federation.playthrough.progression.";
    static final String STATE = "playthrough-progression-state.json";
    /** Test-relative origins, clear of the 36-block template and the margin GameTest clears around it. */
    private static final BlockPos BASELINE = new BlockPos(48, 0, 0);
    private static final BlockPos FEDERATED = new BlockPos(96, 0, 0);
    private static final BlockPos SURVEY = new BlockPos(0, 0, 64);
    static final List<String> DISTURBANCES = List.of("cable", "router", "STORAGE", "CRAFTING", "ME_POWER");
    private static final List<String> CLASSES = List.of("both-pass", "baseline-only", "both-fail", "federated-only",
            "not-bootstrappable", "nondeterministic", "machine-unobtainable", "unobtainable", "deferred", "craft-limit",
            "fixture-limit", "harness-error", "diverged");
    /** A base that is not ready this many ticks after the process started never re-attached or built. */
    private static final int ATTACH_TICKS = 1200;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    record Target(String chapter, String chapterId, String quest, String questId, String taskId, String item, long count,
                  boolean consume, String stack) { }

    /** Settings a resumed process must share with the process that started the run. */
    record Settings(String chapter, List<String> items, int maxTargets, long seed, int bankCells, int machineCopies,
                    int targetTicks, String machinePath, List<String> disturbances, long disturbanceSeed, int disturbanceEvery,
                    int disturbanceTicks, List<String> adapters, long maxCrafts, List<String> exclude) { }

    /** Everything a checkpoint saves beside the world. Rows are kept as JSON. */
    static final class State {
        int schemaVersion = 2;
        String checkpointId = "";
        Settings settings;
        PlaythroughTopology.Layout layout;
        /** The selected quest task IDs, in order; a resumed process must select the same. */
        List<String> targetIds = new ArrayList<>();
        int nextTarget;
        int orders;
        List<JsonObject> rows = new ArrayList<>();
        /** Equipment the bases own and keep outside ME storage, by item key; both bases own the same. */
        Map<String, Long> pool = new TreeMap<>();
        Map<String, String> stacks = new TreeMap<>();
        Set<String> available = new TreeSet<>();
        /** A machine recipe blocked while the base owned this many machines; owning another lets it try again. */
        Map<String, Integer> blocked = new TreeMap<>();
        Map<String, String> blockedReasons = new TreeMap<>();
        Map<String, Integer> failedEquipment = new TreeMap<>();
        Map<String, JsonObject> timeline = new TreeMap<>();
        Map<String, List<String>> waitedBy = new TreeMap<>();
        /** Survey results: recipe ID to equipment item keys and counts. */
        Map<String, Map<String, Long>> equipment = new TreeMap<>();
        /**
         * The target whose start was noted, the machines its first proof needed but the base lacked, and the machines
         * the base owned when it was first considered.
         */
        int considering = -1;
        List<String> missing = new ArrayList<>();
        List<String> ownedAtStart = new ArrayList<>();
        /** Equipment orders and takes since the last quest order. */
        List<String> obtained = new ArrayList<>();
        Map<String, Long> taken = new TreeMap<>();
        /** Where owned equipment came from, by item key: "stock", "crafted" (an equipment order) or "provided". */
        Map<String, Map<String, Long>> sources = new TreeMap<>();
        /** Equipment that passing equipment orders put into stock and that no take has used yet. */
        Map<String, Long> crafted = new TreeMap<>();
        PlaythroughFactory.BaseState baseline;
        PlaythroughFactory.BaseState federated;
        /** Stock per member at the checkpoint (see {@link PlaythroughFactory#members}). */
        Map<String, Map<String, Long>> baselineStock = Map.of();
        Map<String, Map<String, Long>> federatedStock = Map.of();
        List<JsonObject> processes = new ArrayList<>();
        boolean completed;
        String stopReason = "";
    }

    private final GameTestHelper helper;
    private final Path directory = Path.of(System.getProperty("ae2federation.compat.report", "compat-report.json")).toAbsolutePath().getParent();
    private final long started = System.nanoTime();
    private final int wallSeconds = Integer.getInteger("ae2federation.playthrough.wallSeconds", 1400);
    private final int stopAfterSeconds = Integer.getInteger(PREFIX + "stopAfterSeconds", 900);
    private final int maxOrders = Integer.getInteger(PREFIX + "maxOrders", 0);
    private final boolean resume = Boolean.getBoolean(PREFIX + "resume");
    private boolean deferNext = Boolean.getBoolean(PREFIX + "deferNext");
    private final boolean sprint = Boolean.parseBoolean(System.getProperty("ae2federation.playthrough.sprint", "true"));
    private boolean sprintOwned;
    private QuestCoverageCompatGameTests.Probe probe;
    private PlaythroughRecipes recipes;
    private final List<Target> targets = new ArrayList<>();
    private final List<JsonObject> excluded = new ArrayList<>();
    private State state;
    private PlaythroughFactory baseline;
    private PlaythroughFactory federated;
    private GameTestHelper scratch;
    private GameTestHelper baselineSite;
    private GameTestHelper federatedSite;
    private boolean ticketsHeld;
    private PlaythroughLogWatch log;
    /** Log events before this mark belong to an order that has ended, or to the bases' attach. */
    private int logMark;
    private int attachTicks;
    private Order order;
    private boolean ready;
    private int ordersThisProcess;
    private int settleTicks;
    private String harnessError = "";
    private JsonObject process = new JsonObject();
    private boolean finished;

    PlaythroughProgression(GameTestHelper helper) { this.helper = helper; }

    /** One order, run on the baseline and then on the federated base. */
    private static final class Order {
        String kind; Target target; String item; AEItemKey key; long amount; PlaythroughRecipes.Proof proof;
        String purpose; String perturbation = "none"; List<String> waited = List.of(); List<String> unobtained = List.of(); String adapter;
        int phase; boolean begun; int mark;
        PlaythroughFactory.Result baseline; PlaythroughFactory.Result federated;
        /** Disturbances that ended inside the order and were restored by {@code endOrder}. */
        List<String> restoredAtEnd = new ArrayList<>();
    }

    void start() throws Exception {
        if (wallSeconds < 1 || wallSeconds > 1400 || stopAfterSeconds < 1) throw new IllegalArgumentException("Invalid progression time limits");
        var settings = settings();
        probe = new QuestCoverageCompatGameTests.Probe();
        probe.readQuests(Path.of("config/ftbquests/quests"));
        if (!probe.questDataStatus().equals("available")) throw new IllegalStateException(probe.questDataStatus());
        probe.readRecipes(helper); probe.readResources(helper);
        recipes = new PlaythroughRecipes(helper, probe, true);
        select(settings);
        scratch = new PlaythroughOffsetHelper(helper, SURVEY);
        var targetIds = targets.stream().map(Target::taskId).toList();
        if (resume) {
            state = GSON.fromJson(Files.readString(directory.resolve(STATE)), State.class);
            if (!state.settings.equals(settings)) throw new IllegalStateException("Resumed settings differ: saved " + state.settings + ", now " + settings);
            if (state.completed || !state.stopReason.isEmpty()) throw new IllegalStateException("The saved progression already ended: " + state.stopReason);
            // The quest book or a mod could select other tasks now; the next target must be the one the run stopped before.
            if (!state.targetIds.equals(targetIds)) throw new IllegalStateException("Selected targets changed across processes: saved "
                    + state.targetIds.size() + " (next " + (state.nextTarget < state.targetIds.size() ? state.targetIds.get(state.nextTarget) : "none")
                    + "), now " + targetIds.size() + " (next " + (state.nextTarget < targetIds.size() ? targetIds.get(state.nextTarget) : "none") + ")");
            baselineSite = PlaythroughOffsetHelper.at(helper, BlockPos.of(state.baseline.origin()));
            federatedSite = PlaythroughOffsetHelper.at(helper, BlockPos.of(state.federated.origin()));
            // The saved tickets come back with the world; forcing again also covers a world whose run had ended.
            tickets(true);
            baseline = PlaythroughFactory.attach(baselineSite, state.baseline, settings.machineCopies());
            federated = PlaythroughFactory.attach(federatedSite, state.federated, settings.machineCopies());
        } else {
            if (Files.exists(directory.resolve(STATE))) throw new IllegalStateException("A saved progression exists; resume it or use a new run directory");
            state = new State();
            state.settings = settings;
            state.targetIds = targetIds;
            state.layout = PlaythroughTopology.base(settings.seed());
            for (var adapter : PlaythroughMachineAdapters.ENABLED) if (adapter.assembler()) state.available.add(adapter.id());
            baselineSite = new PlaythroughOffsetHelper(helper, BASELINE);
            federatedSite = new PlaythroughOffsetHelper(helper, FEDERATED);
            tickets(true);
            try {
                baseline = PlaythroughFactory.base(baselineSite, null, settings.bankCells(), settings.machineCopies());
                federated = PlaythroughFactory.base(federatedSite, state.layout, settings.bankCells(), settings.machineCopies());
            } catch (RuntimeException exception) {
                tickets(false);
                throw exception;
            }
        }
        process.addProperty("index", state.processes.size());
        process.addProperty("resumed", resume);
        process.addProperty("startOrder", state.orders);
        process.addProperty("startTarget", state.nextTarget);
        process.addProperty("originX", Integer.getInteger("ae2federation.compat.originX", 0));
        log = PlaythroughLogWatch.attach();
        if (sprint) {
            var manager = helper.getLevel().getServer().tickRateManager();
            if (manager.isSprinting()) throw new IllegalStateException("Playthrough requires ownership of tick sprint");
            manager.requestGameToSprint(PlaythroughCraftingGameTests.MAX_TICKS); sprintOwned = true;
        }
        writeReport(false, null);
        helper.runAfterDelay(1, this::tick);
    }

    private static Settings settings() {
        var items = java.util.Arrays.stream(System.getProperty("ae2federation.playthrough.items", "").split(","))
                .map(String::trim).filter(item -> !item.isEmpty()).sorted().toList();
        var disturbances = java.util.Arrays.stream(System.getProperty(PREFIX + "disturbances", "").split(","))
                .map(String::trim).filter(value -> !value.isEmpty()).toList();
        if (!DISTURBANCES.containsAll(disturbances)) throw new IllegalArgumentException("Disturbances must be among " + DISTURBANCES);
        long seed = Long.getLong(PREFIX + "seed", 0);
        var settings = new Settings(System.getProperty("ae2federation.playthrough.chapter", "ae2"), items,
                Integer.getInteger(PREFIX + "maxTargets", 0), seed, Integer.getInteger(PREFIX + "bankCells", 8),
                PlaythroughMachines.requestedCopies(1), Integer.getInteger("ae2federation.playthrough.targetTicks", 1200),
                PlaythroughMachines.PATH, disturbances, Long.getLong(PREFIX + "disturbanceSeed", seed),
                Integer.getInteger(PREFIX + "disturbanceEvery", 4), Integer.getInteger(PREFIX + "disturbanceTicks", 40),
                PlaythroughMachineAdapters.ENABLED.stream().map(PlaythroughMachineAdapter::id).toList(),
                // About what one 1400-second process finishes in both bases (appflux:core_256m plans 56,115 crafts).
                Long.getLong(PREFIX + "maxCrafts", 60_000),
                java.util.Arrays.stream(System.getProperty(PREFIX + "exclude", "").split(",")).map(String::trim)
                        .filter(item -> !item.isEmpty()).sorted().toList());
        if (settings.maxTargets() < 0 || settings.bankCells() < 0 || settings.bankCells() > 10 || settings.targetTicks() < 1 || settings.disturbanceEvery() < 1 || settings.disturbanceTicks() < 1 || settings.maxCrafts() < 0)
            throw new IllegalArgumentException("Invalid progression settings: " + settings);
        return settings;
    }

    /** Phase 1's selection: quest item tasks in dependency order, through the chapter and item filters. */
    private void select(Settings settings) throws Exception {
        var chapters = new java.util.HashMap<String, QuestCoverageCompatGameTests.Chapter>();
        var quests = new LinkedHashMap<String, QuestCoverageCompatGameTests.Quest>();
        for (var ch : probe.chapters()) for (var quest : ch.quests()) { quests.put(quest.id(), quest); chapters.put(quest.id(), ch); }
        var ordered = new ArrayList<QuestCoverageCompatGameTests.Quest>();
        var seen = new HashSet<String>();
        for (var quest : quests.values()) PlaythroughCraftingGameTests.visit(quest, quests, seen, new HashSet<>(), ordered);
        String chapter = settings.chapter();
        for (var quest : ordered) {
            var ch = chapters.get(quest.id());
            boolean selected = chapter.isEmpty() || ch.id().equalsIgnoreCase(chapter) || ch.title().contains(chapter)
                    || chapter.equals("ae2") && ch.title().contains("应用能源2");
            if (!selected) continue;
            for (var task : quest.tasks()) {
                if (!task.classification().equals("plain-item") || !settings.items().isEmpty() && !settings.items().contains(task.item())) continue;
                var tag = QuestCoverageCompatGameTests.parse(task.snbt());
                long count = tag.contains("count") ? tag.getLong("count") : 1;
                if (count <= 0) throw new IllegalStateException("Invalid quest count: " + task.id());
                var stack = task.hasComponents() ? ItemStack.parseOptional(helper.getLevel().registryAccess(), tag.getCompound("item"))
                        : PlaythroughRecipes.key(task.item()).toStack();
                if (stack.isEmpty()) throw new IllegalStateException("Cannot decode quest stack: " + task.id());
                var target = new Target(ch.title(), ch.id(), quest.title(), quest.id(), task.id(), task.item(), count,
                        tag.getBoolean("consume_items"), stack.copyWithCount(1).save(helper.getLevel().registryAccess()).toString());
                if (!recipes.hasCraftingRecipe(task.item())) continue;
                if (settings.exclude().contains(task.item())) {
                    var row = new JsonObject();
                    row.addProperty("taskId", task.id()); row.addProperty("item", task.item()); row.addProperty("chapter", ch.title());
                    row.addProperty("reason", "excluded by " + PREFIX + "exclude");
                    excluded.add(row); continue;
                }
                if (settings.maxTargets() == 0 || targets.size() < settings.maxTargets()) targets.add(target);
            }
        }
        if (targets.isEmpty()) throw new IllegalStateException("Chapter filter selected no targets: " + chapter);
    }

    /**
     * Forces or releases both bases' chunks. The tickets save with the world, so they stay through a checkpoint for the
     * next process, and are released on every exit that ends the run.
     */
    private void tickets(boolean add) {
        if (add == ticketsHeld) return;
        for (var base : List.of(baselineSite, federatedSite)) {
            var origin = base.absolutePos(BlockPos.ZERO);
            var low = new ChunkPos(origin.offset(-16, 0, -16));
            var high = new ChunkPos(origin.offset(48, 0, 48));
            var chunks = new ArrayList<ChunkPos>();
            for (int x = low.x; x <= high.x; x++) for (int z = low.z; z <= high.z; z++) chunks.add(new ChunkPos(x, z));
            space.controlnet.ae2federation.test.world.RestartChunkTickets.force(base.getLevel(), origin, chunks, add);
        }
        ticketsHeld = add;
    }

    private void tick() {
        if (finished) return;
        try {
            if (!ready) {
                String failure = "";
                try {
                    var built = baseline.tick();
                    var linked = federated.tick();
                    if (built != null || linked != null) failure = "a base returned a result instead of becoming idle";
                } catch (Exception exception) {
                    failure = exception.toString();
                }
                if (!baseline.ready() || !federated.ready()) {
                    if (failure.isEmpty() && deadlineReached()) failure = "the process deadline arrived";
                    if (failure.isEmpty() && ++attachTicks > ATTACH_TICKS) failure = "not ready after " + ATTACH_TICKS + " ticks";
                    if (!failure.isEmpty()) { attachFailed(failure); return; }
                    helper.runAfterDelay(1, this::tick); return;
                }
                ready = true;
                // Errors while the bases attach belong to no order; they stay visible in this process's entry.
                process.add("loggedWhileAttaching", GSON.toJsonTree(log.since(logMark)));
                logMark = log.mark();
                if (resume) verifyRestart();
                if (!state.stopReason.isEmpty()) { stop(true); return; }
            }
            if (order != null) {
                if (deadlineReached()) { interrupted(); return; }
                drive();
            } else if (state.completed || !state.stopReason.isEmpty() || state.nextTarget >= targets.size()) {
                if (state.nextTarget >= targets.size()) state.completed = true;
                stop(true); return;
            } else if (progressed() && (System.nanoTime() - started) / 1_000_000_000L >= stopAfterSeconds || deadlineReached()
                    || maxOrders > 0 && ordersThisProcess >= maxOrders) {
                stop(true); return;
            } else {
                next();
            }
            helper.runAfterDelay(1, this::tick);
        } catch (net.minecraft.gametest.framework.GameTestAssertException failure) {
            // A failure stop has already written its report.
            throw failure;
        } catch (Exception exception) {
            harnessError = exception.toString();
            com.mojang.logging.LogUtils.getLogger().error("AE2F_PROGRESSION_HARNESS", exception);
            restoreTicks();
            try { tickets(false); } catch (Exception releasing) { exception.addSuppressed(releasing); }
            try { writeReport(false, null); } catch (Exception writing) { exception.addSuppressed(writing); }
            if (log != null) log.close();
            finished = true;
            throw new IllegalStateException("Playthrough progression failed at target " + state.nextTarget, exception);
        }
    }

    /**
     * A base that never became idle: a resumed base that did not re-attach, or a new one that did not build. That is a
     * failure of the run, not an order to defer; the saved state stays as the last checkpoint wrote it.
     */
    private void attachFailed(String failure) throws Exception {
        state.stopReason = (resume ? "Bases did not re-attach: " : "Bases did not build: ") + failure
                + "; baseline " + baseline.readiness() + ", federated " + federated.readiness();
        process.addProperty("attachFailure", state.stopReason);
        process.add("loggedWhileAttaching", GSON.toJsonTree(log.since(logMark)));
        restoreTicks();
        tickets(false);
        writeReport(false, null);
        log.close();
        finished = true;
        helper.fail("Playthrough progression stopped: " + state.stopReason);
    }

    /** Every process completes at least one order or target before its time limit may stop it. */
    private boolean progressed() {
        return ordersThisProcess > 0 || state.nextTarget > process.get("startTarget").getAsInt();
    }

    /** A resumed base must come back with exactly the stock per member, idle CPUs and settled ledger it was saved with. */
    private void verifyRestart() {
        var checks = new JsonObject();
        for (var entry : List.of(Map.entry("baseline", baseline), Map.entry("federated", federated))) {
            var saved = entry.getKey().equals("baseline") ? state.baselineStock : state.federatedStock;
            String changed = memberDifference(saved, entry.getValue().members());
            String unsettled = entry.getValue().unsettled();
            String result = !changed.isEmpty() ? "Stock changed across the restart: " + changed
                    : !unsettled.isEmpty() ? "Unsettled after the restart: " + unsettled : "PASS";
            checks.addProperty(entry.getKey(), result);
            if (!result.equals("PASS") && state.stopReason.isEmpty()) state.stopReason = entry.getKey() + " base after restart: " + result;
        }
        process.add("restartChecks", checks);
    }

    /**
     * Decides and starts the next order of the current quest target, or records a target that cannot run. The proof
     * may use every enabled machine whose parts the base can obtain; machines it uses that the base does not own yet
     * are earned first, and once all are owned that proof runs.
     */
    private void next() throws Exception {
        var target = targets.get(state.nextTarget);
        PlaythroughRecipes.Proof proof;
        try {
            proof = recipes.target(target.item(), enabled(), blocked());
        } catch (PlaythroughRecipes.NotBootstrappable exception) {
            unexecuted(target, "not-bootstrappable", exception.getMessage()); return;
        } catch (PlaythroughRecipes.Nondeterministic exception) {
            unexecuted(target, "nondeterministic", exception.getMessage()); return;
        } catch (IllegalArgumentException exception) {
            if (!blocked().isEmpty() && producibleWithoutBlocks(target.item())) {
                // Every remaining proof needs a machine the bases cannot earn; neither base is at fault.
                unexecuted(target, "machine-unobtainable", exception.getMessage() + "; blocked machine recipes: " + blocked()); return;
            }
            var failure = PlaythroughFactory.Result.unexecuted("pattern-encoding", exception.getMessage());
            var row = row("quest", target, target.item(), target.count(), null, failure, failure, "both-fail");
            finishTarget(row); return;
        }
        if (state.considering != state.nextTarget) {
            state.considering = state.nextTarget;
            state.missing = new ArrayList<>(adapters(proof));
            state.missing.removeAll(state.available);
            state.ownedAtStart = new ArrayList<>(state.available);
        }
        for (var recipe : proof.shelf().values()) {
            var adapter = recipe.adapter();
            if (state.available.contains(adapter)) continue;
            var outcome = acquire(equipment(recipe), "machine " + adapter + " for " + target.item(), adapter);
            if (outcome instanceof Order pending) { begin(pending); return; }
            // Another recipe of this machine, or a stand-in, takes its place in the next proof.
            if (outcome instanceof String failure) { block(recipe.id(), "equipment " + failure + " unobtainable"); return; }
            state.available.add(adapter);
            var entry = new JsonObject();
            entry.addProperty("availableFromOrder", state.orders);
            entry.addProperty("beforeTask", target.taskId());
            entry.addProperty("beforeItem", target.item());
            entry.addProperty("chapter", target.chapter());
            entry.add("equipment", GSON.toJsonTree(equipment(recipe)));
            // Where each block and part came from when the machine became available: stock, crafted, or provided.
            var sources = new TreeMap<String, Map<String, Long>>();
            for (var name : equipment(recipe).keySet()) sources.put(name, state.sources.getOrDefault(name, Map.of()));
            entry.add("equipmentSources", GSON.toJsonTree(sources));
            state.timeline.put(adapter, entry);
        }
        int copies = siteCopies(proof);
        var required = new TreeMap<String, Long>();
        for (var recipe : proof.shelf().values()) equipment(recipe).forEach((key, count) -> required.merge(key, count * copies, Math::addExact));
        var outcome = acquire(required, "equipment for " + target.item(), null);
        if (outcome instanceof Order pending) { begin(pending); return; }
        if (outcome instanceof String failure) {
            // A part no site can do without: those recipes stay stand-ins until the base owns another machine.
            for (var recipe : proof.shelf().values()) if (equipment(recipe).containsKey(failure)) block(recipe.id(), "equipment " + failure + " unobtainable");
            return;
        }
        var pending = new Order();
        pending.kind = "quest"; pending.target = target; pending.item = target.item(); pending.amount = target.count();
        pending.key = AEItemKey.of(stack(target.stack())); pending.proof = proof; pending.purpose = "quest task";
        // Machines this proof runs that the base earned while the target waited.
        var waited = adapters(proof);
        waited.removeAll(state.ownedAtStart);
        pending.waited = List.copyOf(waited);
        var unobtained = new TreeSet<>(state.missing);
        unobtained.removeAll(adapters(proof));
        pending.unobtained = List.copyOf(unobtained);
        begin(pending);
    }

    /** Whether the item would have a proof if every enabled machine could be earned. */
    private boolean producibleWithoutBlocks(String item) {
        try {
            recipes.target(item, enabled(), Set.of());
            return true;
        } catch (IllegalArgumentException | PlaythroughRecipes.Nondeterministic exception) {
            return false;
        }
    }

    private static Set<String> enabled() {
        var result = new TreeSet<String>();
        PlaythroughMachineAdapters.ENABLED.forEach(adapter -> result.add(adapter.id()));
        return result;
    }

    /** Machine recipes blocked since the base last gained a machine. */
    private Set<String> blocked() {
        var result = new TreeSet<String>();
        state.blocked.forEach((recipe, owned) -> { if (owned == state.available.size()) result.add(recipe); });
        return result;
    }

    private void block(String recipe, String reason) {
        if (blocked().contains(recipe)) throw new IllegalStateException("Machine recipe blocked twice: " + recipe);
        state.blocked.put(recipe, state.available.size());
        state.blockedReasons.put(recipe, reason);
    }

    private static Set<String> adapters(PlaythroughRecipes.Proof proof) {
        var result = new TreeSet<String>();
        proof.shelf().values().forEach(recipe -> result.add(recipe.adapter()));
        return result;
    }

    private int siteCopies(PlaythroughRecipes.Proof proof) {
        return PlaythroughMachines.siteCopies(state.settings.machineCopies(), proof);
    }

    /** A recipe's equipment, surveyed once from what its adapter places. */
    private Map<String, Long> equipment(PlaythroughMachineAdapter.Recipe recipe) {
        var known = state.equipment.get(recipe.id());
        if (known != null) return known;
        var result = new TreeMap<String, Long>();
        for (var stack : PlaythroughMachines.survey(scratch, recipe)) {
            var key = AEItemKey.of(stack);
            String name = PlaythroughAccounting.name(key, helper.getLevel());
            result.merge(name, (long) stack.getCount(), Math::addExact);
            state.stacks.put(name, stack.copyWithCount(1).save(helper.getLevel().registryAccess()).toString());
        }
        state.equipment.put(recipe.id(), result);
        return result;
    }

    private ItemStack stack(String snbt) {
        try {
            return ItemStack.parseOptional(helper.getLevel().registryAccess(), QuestCoverageCompatGameTests.parse(snbt));
        } catch (Exception exception) {
            throw new IllegalStateException("Unreadable saved stack: " + snbt, exception);
        }
    }

    /**
     * Makes the bases own {@code required}: what they lack is taken out of their storage, or supplied as a Phase 0
     * provided resource. Returns null when owned, an Order for the first item that must be crafted first, or the
     * name of an item that cannot be obtained with the machines the bases own now.
     */
    private Object acquire(Map<String, Long> required, String purpose, String adapter) throws Exception {
        return acquire(required, purpose, adapter, Set.of());
    }

    /** {@code chain} holds the equipment whose orders wait on this acquisition, so a part never waits on itself. */
    private Object acquire(Map<String, Long> required, String purpose, String adapter, Set<String> chain) throws Exception {
        for (var entry : required.entrySet()) {
            String name = entry.getKey();
            long missing = entry.getValue() - state.pool.getOrDefault(name, 0L);
            if (missing <= 0) continue;
            if (chain.contains(name)) return name;
            var key = AEItemKey.of(stack(state.stacks.get(name)));
            boolean provided = key.toStack().getComponentsPatch().isEmpty() && probe.providedItems().contains(name);
            long inBaseline = baseline.stock().getOrDefault(name, 0L);
            long inFederated = federated.stock().getOrDefault(name, 0L);
            if (inBaseline != inFederated) throw new IllegalStateException("Bases hold different stock of " + name);
            if (provided || inBaseline >= missing) {
                long fromStock = Math.min(missing, inBaseline);
                long takenBaseline = baseline.take(key, fromStock, false);
                long takenFederated = federated.take(key, fromStock, false);
                if (takenBaseline != fromStock || takenFederated != fromStock)
                    throw new IllegalStateException("Equipment take of " + name + " differs: " + takenBaseline + "/" + takenFederated + " of " + fromStock);
                state.pool.merge(name, missing, Math::addExact);
                state.taken.merge(name + (fromStock < missing ? " (provided)" : ""), missing, Math::addExact);
                // Stock that an equipment order made counts as crafted; the rest of the take was stock already.
                long crafted = Math.min(fromStock, state.crafted.getOrDefault(name, 0L));
                if (crafted > 0) PlaythroughAccounting.add(state.crafted, name, -crafted);
                var sources = state.sources.computeIfAbsent(name, ignored -> new TreeMap<>());
                for (var source : List.of(Map.entry("stock", fromStock - crafted), Map.entry("crafted", crafted), Map.entry("provided", missing - fromStock)))
                    if (source.getValue() > 0) sources.merge(source.getKey(), source.getValue(), Math::addExact);
                continue;
            }
            if (state.failedEquipment.getOrDefault(name, -1) == state.available.size()) return name;
            return equipmentOrder(name, key, missing - inBaseline, purpose, adapter, chain);
        }
        return null;
    }

    /**
     * An order for equipment the bases lack. Its own machine sites need their parts first; a recipe whose parts cannot
     * be obtained is left out of this order's proof, so its product becomes a stand-in.
     */
    private Object equipmentOrder(String name, AEItemKey key, long amount, String purpose, String adapter, Set<String> chain) throws Exception {
        var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(key.getItem()).toString();
        var pending = new Order();
        pending.kind = "equipment"; pending.item = item; pending.key = key; pending.amount = amount;
        pending.purpose = purpose; pending.adapter = adapter;
        var excluded = new TreeSet<>(blocked());
        var inner = new HashSet<>(chain); inner.add(name);
        while (true) {
            try {
                pending.proof = recipes.target(item, state.available, excluded);
            } catch (IllegalArgumentException | PlaythroughRecipes.Nondeterministic exception) {
                state.failedEquipment.put(name, state.available.size());
                var failure = PlaythroughFactory.Result.unexecuted("unobtainable", exception.getMessage());
                var row = row("equipment", targets.get(state.nextTarget), item, amount, pending, failure, failure, "unobtainable");
                state.rows.add(row); state.obtained.add("unobtainable " + item + " (" + purpose + ")");
                return name;
            }
            int copies = siteCopies(pending.proof);
            var parts = new TreeMap<String, Long>();
            for (var recipe : pending.proof.shelf().values()) equipment(recipe).forEach((part, count) -> parts.merge(part, count * copies, Math::addExact));
            var outcome = acquire(parts, "parts for " + item + " (" + purpose + ")", null, inner);
            if (outcome instanceof Order deeper) return deeper;
            if (outcome == null) return pending;
            for (var recipe : pending.proof.shelf().values()) if (equipment(recipe).containsKey((String) outcome)) excluded.add(recipe.id());
        }
    }

    private void begin(Order pending) throws Exception {
        if (deferNext) {
            // A fresh process could not finish this order before its deadline; record it and move on.
            deferNext = false;
            skip(pending, "deferred", "The order did not finish within one server process");
            return;
        }
        String channels = channelLimit(pending.proof);
        if (!channels.isEmpty()) { skip(pending, "fixture-limit", channels); return; }
        var settings = state.settings;
        if (!settings.disturbances().isEmpty()) {
            var random = new Random(settings.disturbanceSeed() * 1_000_003L + state.orders);
            if (random.nextInt(settings.disturbanceEvery()) == 0)
                pending.perturbation = settings.disturbances().get(random.nextInt(settings.disturbances().size()));
        }
        // The order's log window starts where the previous order's ended, so the steps between orders count too.
        pending.mark = logMark;
        order = pending;
        com.mojang.logging.LogUtils.getLogger().info("AE2F_PROGRESSION_ORDER order={} target={}/{} kind={} item={} amount={} perturbation={}",
                state.orders + 1, state.nextTarget + 1, targets.size(), pending.kind, pending.item, pending.amount, pending.perturbation);
    }

    /** Records an order that does not run in either base, as {@code classification}. */
    private void skip(Order pending, String classification, String reason) throws Exception {
        var failure = PlaythroughFactory.Result.unexecuted(classification, reason);
        var row = row(pending.kind, targets.get(state.nextTarget), pending.item, pending.amount, pending, failure, failure, classification);
        if (pending.kind.equals("quest")) finishTarget(row);
        else {
            state.rows.add(row);
            state.failedEquipment.put(PlaythroughAccounting.name(pending.key, helper.getLevel()), state.available.size());
            state.obtained.add(classification + " " + pending.item + " (" + pending.purpose + ")");
        }
        writeReport(false, null);
    }

    /**
     * Why the output cell ME Chests this proof adds would take a base's requester past its channels, or empty. A
     * requester without a controller is ad-hoc: one channel too many takes every device on it offline. Checked for
     * both bases before either runs, so neither order starts.
     */
    private String channelLimit(PlaythroughRecipes.Proof proof) {
        if (proof == null) return "";
        var problems = new ArrayList<String>();
        for (var entry : List.of(Map.entry("baseline", baseline), Map.entry("federated", federated))) {
            var added = new TreeSet<>(PlaythroughMachines.storageCells(proof));
            added.removeAll(entry.getValue().outputCellTypes());
            int[] channels = entry.getValue().requesterChannels();
            if (!added.isEmpty() && channels[1] > 0 && channels[0] + added.size() > channels[1])
                problems.add(entry.getKey() + " requester has no controller and uses " + channels[0] + " of " + channels[1]
                        + " channels; output cells " + added + " need " + added.size() + " more");
        }
        return problems.isEmpty() ? "" : "Fixture limit: " + String.join("; ", problems);
    }

    /** Runs the active order on the baseline, then on the federated base, then lets both settle. */
    private void drive() throws Exception {
        var current = order;
        var base = current.phase == 0 ? baseline : federated;
        if (current.phase < 2) {
            PlaythroughFactory.Result result = null;
            if (!current.begun) {
                try {
                    base.order(current.proof, current.key, current.amount, current.phase == 0 ? "none" : current.perturbation,
                            state.settings.disturbanceTicks(), state.settings.targetTicks(), state.settings.maxCrafts());
                    current.begun = true;
                } catch (IllegalArgumentException exception) {
                    result = PlaythroughFactory.Result.unexecuted("pattern-encoding", exception.getMessage());
                } catch (Exception exception) {
                    result = harnessFailure(current, exception);
                }
            }
            if (result == null) {
                try {
                    result = base.tick();
                } catch (Exception exception) {
                    result = harnessFailure(current, exception);
                }
            }
            if (result == null) return;
            String restored = base.endOrder();
            if (!restored.isEmpty()) current.restoredAtEnd.add(restored);
            if (current.phase == 0) current.baseline = result;
            else current.federated = result;
            current.phase++; current.begun = false; settleTicks = 0;
            // The bases hold the same stock and providers, so the federated base would refuse this plan as well.
            if (current.phase == 1 && (result.invariant().equals("craft-limit") || result.invariant().equals("pattern-encoding"))) {
                current.federated = result; current.phase = 2;
            }
            return;
        }
        // Return state, also of a cancelled job, settles through the normal 20-tick sweep; wait for it, then compare.
        if (++settleTicks < 25) return;
        if (settleTicks < 400 && !(baseline.unsettled().isEmpty() && federated.unsettled().isEmpty())) return;
        complete(current);
    }

    /**
     * An exception inside one base's order fails that order only. An IllegalStateException is a fixture limit, such as
     * a plan larger than the CPU template; anything else is a harness failure. Neither blames a base: the order is
     * classified {@code fixture-limit} or {@code harness-error}, and the settle check decides whether the bases are
     * still comparable.
     */
    private PlaythroughFactory.Result harnessFailure(Order current, Exception exception) {
        boolean limit = exception instanceof IllegalStateException;
        com.mojang.logging.LogUtils.getLogger().warn("AE2F_PROGRESSION_{} item={} base={}", limit ? "FIXTURE_LIMIT" : "HARNESS_EXCEPTION",
                current.item, current.phase == 0 ? "baseline" : "federated", exception);
        return PlaythroughFactory.Result.unexecuted(limit ? "fixture-limit" : "harness-exception", exception.toString());
    }

    private static PlaythroughFactory.Result failed(PlaythroughFactory.Result result, String invariant, String reason) {
        return result.pass() ? result.failure(invariant, reason) : result;
    }

    private static List<String> texts(List<PlaythroughLogWatch.Event> events, String source) {
        return events.stream().filter(event -> event.source().equals(source)).map(PlaythroughLogWatch.Event::text).toList();
    }

    /**
     * Stock per member compared key by key; a member one side lacks counts as empty, so the federated base's extra
     * member must hold nothing. Empty when equal.
     */
    static String memberDifference(Map<String, Map<String, Long>> baseline, Map<String, Map<String, Long>> federated) {
        var names = new TreeSet<>(baseline.keySet()); names.addAll(federated.keySet());
        var problems = new ArrayList<String>();
        for (var name : names) {
            Map<String, Long> first = baseline.getOrDefault(name, Map.of()), second = federated.getOrDefault(name, Map.of());
            if (!first.equals(second)) problems.add(name + " " + PlaythroughAccounting.difference(first, second));
        }
        return String.join("; ", problems);
    }

    private void complete(Order current) throws Exception {
        var base = current.baseline;
        var fed = current.federated;
        // Every event since the previous order ended: both runs, the settle window with the federated return sweep,
        // endOrder, retire, and the equipment steps before this order.
        var events = log.since(current.mark);
        var federatedEvents = texts(events, PlaythroughLogWatch.FEDERATED);
        var unattributed = texts(events, PlaythroughLogWatch.UNATTRIBUTED);
        if (!unattributed.isEmpty()) {
            String reason = "unattributed, " + unattributed.size() + " events: " + unattributed.getFirst();
            base = failed(base, "exception-logged", reason);
            fed = failed(fed, "exception-logged", reason);
        }
        if (!federatedEvents.isEmpty()) fed = failed(fed, "exception-logged", federatedEvents.size() + " events: " + federatedEvents.getFirst());
        // The bases stay comparable only while both are settled and hold the same stock in each member.
        String baselineUnsettled = baseline.unsettled();
        String federatedUnsettled = federated.unsettled();
        var baselineMembers = baseline.members();
        var federatedMembers = federated.members();
        String divergence = !baselineUnsettled.isEmpty() || !federatedUnsettled.isEmpty()
                ? "unsettled" + (baselineUnsettled.isEmpty() ? "" : " baseline: " + baselineUnsettled) + (federatedUnsettled.isEmpty() ? "" : " federated: " + federatedUnsettled)
                : memberDifference(baselineMembers, federatedMembers);
        if (base.pass() && fed.pass()) {
            if (!base.extracted().equals(fed.extracted()))
                fed = fed.failure("cross-base-extractions", "Source extractions differ: baseline " + base.extracted() + ", federated " + fed.extracted());
            else if (!PlaythroughTopology.inventoriesEqual(base, fed))
                fed = fed.failure("cross-base-stock", "Stock per member differs from the baseline: " + memberDifference(base.inventories(), fed.inventories()));
        }
        var comparison = PlaythroughTopology.compare(base, fed, false);
        String classification = base.invariant().equals("craft-limit") ? "craft-limit"
                : base.invariant().equals("harness-exception") || fed.invariant().equals("harness-exception") ? "harness-error"
                : base.invariant().equals("fixture-limit") || fed.invariant().equals("fixture-limit") ? "fixture-limit"
                : comparison.classification();
        // A divergence found while settling outranks what the two runs reported.
        if (!divergence.isEmpty()) classification = "diverged";
        var row = row(current.kind, current.target == null ? targets.get(state.nextTarget) : current.target, current.item, current.amount,
                current, base, comparison.federated(), classification);
        row.add("loggedExceptions", GSON.toJsonTree(Map.of("federated", federatedEvents, "unattributed", unattributed,
                "harness", texts(events, PlaythroughLogWatch.HARNESS))));
        if (!current.restoredAtEnd.isEmpty()) row.add("disturbanceRestoredAtOrderEnd", GSON.toJsonTree(current.restoredAtEnd));
        if (!divergence.isEmpty()) {
            state.stopReason = "Bases diverged after order " + (state.orders + 1) + " (" + current.item + "): " + divergence;
            row.addProperty("divergence", state.stopReason);
            row.add("stockByCell", GSON.toJsonTree(Map.of("baseline", baseline.stockByCell(), "federated", federated.stockByCell())));
        }
        boolean passed = classification.equals("both-pass");
        order = null; ordersThisProcess++; logMark = log.mark();
        if (current.kind.equals("quest")) {
            if (passed && current.target.consume() && state.stopReason.isEmpty()) {
                // A quest submission takes the items out of the requester.
                long takenBaseline = baseline.take(current.key, current.target.count(), true);
                long takenFederated = federated.take(current.key, current.target.count(), true);
                row.addProperty("consumed", takenBaseline);
                if (takenBaseline != current.target.count() || takenFederated != current.target.count())
                    state.stopReason = "Quest submission of " + current.target.count() + " " + current.item + " took " + takenBaseline + "/" + takenFederated;
            }
            finishTarget(row);
        } else {
            state.rows.add(row);
            state.obtained.add((passed ? "ordered " : "failed ") + current.amount + " " + current.item + " (" + current.purpose + ")");
            if (passed) state.crafted.merge(PlaythroughAccounting.name(current.key, helper.getLevel()), current.amount, Math::addExact);
            else state.failedEquipment.put(PlaythroughAccounting.name(current.key, helper.getLevel()), state.available.size());
            state.orders++;
            writeReport(false, null);
        }
    }

    private void unexecuted(Target target, String classification, String reason) throws Exception {
        var row = row("quest", target, target.item(), target.count(), null, null, null, classification);
        row.addProperty("reason", reason);
        finishTarget(row);
    }

    /** Closes the current quest target: its row carries what was obtained for it since the previous target. */
    private void finishTarget(JsonObject row) throws Exception {
        row.add("machinesObtainedForIt", GSON.toJsonTree(state.obtained));
        row.add("equipmentTaken", GSON.toJsonTree(state.taken));
        state.obtained = new ArrayList<>(); state.taken = new TreeMap<>();
        if (row.has("waitedFor")) for (var adapter : row.getAsJsonArray("waitedFor"))
            state.waitedBy.computeIfAbsent(adapter.getAsString(), ignored -> new ArrayList<>()).add(row.get("taskId").getAsString() + " " + row.get("item").getAsString());
        state.rows.add(row);
        if (row.has("baseline")) state.orders++;
        state.nextTarget++;
        com.mojang.logging.LogUtils.getLogger().info("AE2F_PROGRESSION_RESULT target={} item={} class={}",
                state.nextTarget, row.get("item").getAsString(), row.get("classification").getAsString());
        writeReport(false, null);
    }

    private JsonObject row(String kind, Target target, String item, long amount, Order source, PlaythroughFactory.Result base,
                           PlaythroughFactory.Result fed, String classification) {
        var row = new JsonObject();
        row.addProperty("order", state.orders + 1);
        row.addProperty("kind", kind);
        row.addProperty("chapter", target.chapter()); row.addProperty("chapterId", target.chapterId());
        row.addProperty("quest", target.quest()); row.addProperty("questId", target.questId()); row.addProperty("taskId", target.taskId());
        row.addProperty("item", item); row.addProperty("count", amount);
        if (kind.equals("quest")) row.addProperty("consumeItems", target.consume());
        else { row.addProperty("forTask", target.taskId()); row.addProperty("purpose", source == null ? "" : source.purpose); }
        row.addProperty("classification", classification);
        row.add("machinesAvailable", GSON.toJsonTree(state.available));
        if (source != null) {
            row.add("waitedFor", GSON.toJsonTree(source.waited));
            if (source.kind.equals("quest")) row.add("machinesNotObtained", GSON.toJsonTree(source.unobtained));
            row.addProperty("perturbation", source.perturbation);
            if (source.proof != null) {
                row.add("standIns", GSON.toJsonTree(source.proof.standIns()));
                row.add("recipes", GSON.toJsonTree(source.proof.patterns().keySet()));
                var steps = new LinkedHashMap<String, Object>();
                if (base != null && fed != null && base.cleanup().containsKey("machineRuns")) PlaythroughMachineReports.steps(steps, source.proof, helper.getLevel(), base, fed);
                row.add("machineSteps", GSON.toJsonTree(steps.getOrDefault("machineSteps", Map.of())));
                row.add("realMachineSteps", GSON.toJsonTree(steps.getOrDefault("realMachineSteps", List.of())));
            }
        }
        if (base != null) {
            // A plan that did not run took nothing from stock.
            row.add("stockReused", GSON.toJsonTree(base.pass() ? base.cleanup().getOrDefault("stockReused", Map.of()) : Map.of()));
            row.add("baseline", GSON.toJsonTree(slim(base)));
            row.add("federated", GSON.toJsonTree(slim(fed)));
        }
        return row;
    }

    /** Passing results drop their full stock maps; the checkpoint and the stock delta carry it. */
    private static PlaythroughFactory.Result slim(PlaythroughFactory.Result result) {
        if (!result.pass()) return result;
        var totals = new TreeMap<String, Map<String, Long>>();
        result.inventories().forEach((name, stock) -> totals.put(name, Map.of("keys", (long) stock.size(),
                "items", stock.values().stream().mapToLong(Long::longValue).sum())));
        return new PlaythroughFactory.Result(result.pass(), result.ticks(), result.invariant(), result.reason(), result.usedItems(),
                result.extracted(), result.arrived(), result.invariants(), result.storedByNetwork(), result.expectedAmount(),
                result.planBytes(), result.totalCrafts(), result.assemblerParallelism(), result.tickBudget(), result.cpuBytes(),
                Map.of(), totals, result.signatures(), result.ledger(), result.cleanup());
    }

    private boolean deadlineReached() {
        return (System.nanoTime() - started) / 1_000_000_000L >= wallSeconds
                || System.currentTimeMillis() >= Long.getLong("ae2federation.compat.deadlineEpochMillis", Long.MAX_VALUE) - 60000;
    }

    /**
     * The process deadline arrived inside an order. The saved world will hold that half-finished order, so this
     * process writes no checkpoint; its runner restores the last one.
     */
    private void interrupted() throws Exception {
        var incomplete = new JsonObject();
        incomplete.addProperty("item", order.item); incomplete.addProperty("kind", order.kind);
        incomplete.addProperty("phase", order.phase == 0 ? "baseline" : order.phase == 1 ? "federated" : "settling");
        incomplete.addProperty("ordersThisProcess", ordersThisProcess);
        restoreTicks();
        // The runner restores the checkpoint, whose world keeps the chunk tickets for the repeat.
        writeReport(false, incomplete);
        log.close();
        finished = true;
        helper.succeed();
    }

    /**
     * Ends this process at an order boundary: saves the bases' state beside the world the server saves on stop. A
     * stop for a divergence, an unsettled base or failed restart checks fails the GameTest after writing its report.
     */
    private void stop(boolean boundary) throws Exception {
        state.baseline = baseline.state(); state.federated = federated.state();
        state.baselineStock = baseline.members(); state.federatedStock = federated.members();
        process.addProperty("endOrder", state.orders);
        process.addProperty("endTarget", state.nextTarget);
        process.addProperty("ordersThisProcess", ordersThisProcess);
        process.addProperty("seconds", (System.nanoTime() - started) / 1_000_000_000.0);
        // Errors after the last order of this process belong to no order; they stay visible here.
        process.add("loggedAfterLastOrder", GSON.toJsonTree(log.since(logMark)));
        state.processes.add(process);
        state.checkpointId = java.util.UUID.randomUUID().toString();
        if (state.completed || !state.stopReason.isEmpty()) tickets(false);
        PlaythroughCheckpoint.write(directory.resolve(STATE), GSON.toJson(state));
        restoreTicks();
        writeReport(boundary, null);
        log.close();
        finished = true;
        if (!state.stopReason.isEmpty()) helper.fail("Playthrough progression stopped: " + state.stopReason);
        helper.succeed();
    }

    private void restoreTicks() {
        if (sprintOwned) { helper.getLevel().getServer().tickRateManager().stopSprinting(); sprintOwned = false; }
    }

    private void writeReport(boolean boundary, JsonObject incomplete) throws Exception {
        var report = new JsonObject();
        report.addProperty("schemaVersion", 2);
        report.addProperty("checkpointId", boundary ? state.checkpointId : "");
        report.addProperty("cleanBoundary", boundary);
        report.addProperty("completed", state.completed);
        report.addProperty("stopReason", state.stopReason);
        report.addProperty("harnessError", harnessError);
        report.add("incompleteOrder", incomplete == null ? new JsonObject() : incomplete);
        report.addProperty("ordersThisProcess", ordersThisProcess);
        report.addProperty("elapsedSeconds", (System.nanoTime() - started) / 1_000_000_000.0);
        report.add("settings", GSON.toJsonTree(state.settings));
        report.add("federatedLayout", GSON.toJsonTree(state.layout));
        report.addProperty("layoutSeed", state.layout.seed());
        report.addProperty("selectedTargets", targets.size());
        report.add("excludedTargets", GSON.toJsonTree(excluded));
        report.addProperty("nextTarget", state.nextTarget);
        report.addProperty("orders", state.orders);
        report.add("summary", GSON.toJsonTree(summary()));
        report.add("machineTimeline", GSON.toJsonTree(state.timeline));
        report.add("waitedBy", GSON.toJsonTree(state.waitedBy));
        report.add("blockedMachineRecipes", GSON.toJsonTree(state.blockedReasons));
        report.add("ownedEquipment", GSON.toJsonTree(state.pool));
        report.add("equipmentSources", GSON.toJsonTree(state.sources));
        report.add("availableMachines", GSON.toJsonTree(state.available));
        var processes = new ArrayList<>(state.processes);
        if (!boundary) processes.add(process);
        report.add("processes", GSON.toJsonTree(processes));
        report.add("rows", GSON.toJsonTree(state.rows));
        report.addProperty("materialSource", "Phase 0 provided set plus each proof's stand-ins, unlimited; stock in the bases' cells is used first.");
        Files.createDirectories(directory);
        PlaythroughCheckpoint.write(directory.resolve("playthrough-progression.md"), markdown(report));
        PlaythroughCheckpoint.write(directory.resolve("playthrough-progression.json"), GSON.toJson(report));
    }

    /** Per chapter and kind, counts of each class. */
    private Map<String, Map<String, Map<String, Integer>>> summary() {
        var result = new TreeMap<String, Map<String, Map<String, Integer>>>();
        for (var row : state.rows) {
            var kinds = result.computeIfAbsent(row.get("chapter").getAsString(), ignored -> new TreeMap<>());
            var counts = kinds.computeIfAbsent(row.get("kind").getAsString(), ignored -> {
                var map = new LinkedHashMap<String, Integer>(); CLASSES.forEach(name -> map.put(name, 0)); return map;
            });
            counts.merge(row.get("classification").getAsString(), 1, Integer::sum);
        }
        return result;
    }

    private String markdown(JsonObject report) {
        var text = new StringBuilder("# Quest progression\n\nChapter filter: ").append(state.settings.chapter())
                .append(". Federated layout seed ").append(state.layout.seed()).append(": ").append(state.layout.dimensions())
                .append(". Machine path: ").append(state.settings.machinePath()).append(". Completed: ").append(state.completed)
                .append(". Clean boundary: ").append(report.get("cleanBoundary").getAsBoolean())
                .append(". Checkpoint: ").append(report.get("checkpointId").getAsString())
                .append(". Stop reason: ").append(state.stopReason.isEmpty() ? "none" : escape(state.stopReason))
                .append(". Harness error: ").append(harnessError.isEmpty() ? "none" : escape(harnessError)).append(".\n\n")
                .append("Targets ").append(state.nextTarget).append(" of ").append(targets.size()).append(", orders ").append(state.orders)
                .append(", processes ").append(state.processes.size()).append(".\n\n## Summary\n\n| Chapter | Kind |");
        CLASSES.forEach(name -> text.append(' ').append(name).append(" |"));
        text.append("\n|---|---|").append("---:|".repeat(CLASSES.size())).append('\n');
        summary().forEach((chapter, kinds) -> kinds.forEach((kind, counts) -> {
            text.append('|').append(escape(chapter)).append('|').append(kind).append('|');
            counts.values().forEach(count -> text.append(count).append('|'));
            text.append('\n');
        }));
        text.append("\n## Machines\n\n| Adapter | Available from order | Before task | Waiting targets |\n|---|---:|---|---:|\n");
        var adapters = new TreeSet<String>(state.timeline.keySet()); adapters.addAll(state.waitedBy.keySet());
        for (var adapter : adapters) {
            var entry = state.timeline.get(adapter);
            text.append('|').append(adapter).append('|').append(entry == null ? "never" : entry.get("availableFromOrder").getAsString())
                    .append('|').append(entry == null ? "" : entry.get("beforeItem").getAsString())
                    .append('|').append(state.waitedBy.getOrDefault(adapter, List.of()).size()).append("|\n");
        }
        text.append("\nBlocked machine recipes: ").append(escape(state.blockedReasons.toString()))
                .append("\n\nOwned equipment: ").append(escape(state.pool.toString())).append("\n\n## Orders\n\n")
                .append("| # | Kind | Item | Count | Class | Baseline | Federated | Machines available | Stock reused | Obtained for it |\n|---:|---|---|---:|---|---|---|---|---|---|\n");
        for (var row : state.rows) {
            text.append('|').append(row.get("order")).append('|').append(row.get("kind").getAsString()).append('|')
                    .append(row.get("item").getAsString()).append('|').append(row.get("count")).append('|')
                    .append(row.get("classification").getAsString()).append('|');
            for (var layout : List.of("baseline", "federated")) {
                var result = row.getAsJsonObject(layout);
                if (result == null) text.append(escape(row.has("reason") ? row.get("reason").getAsString() : "")).append('|');
                else text.append(result.get("ticks")).append(" ticks / ").append(result.get("pass").getAsBoolean() ? "PASS"
                        : escape(result.get("invariant").getAsString() + ": " + result.get("reason").getAsString())).append('|');
            }
            text.append(escape(String.valueOf(row.get("machinesAvailable")))).append('|')
                    .append(escape(String.valueOf(row.get("stockReused")))).append('|')
                    .append(escape(String.valueOf(row.get("machinesObtainedForIt")))).append("|\n");
        }
        return text.toString();
    }

    private static String escape(String text) {
        var single = text.replace("|", "\\|").replace("\n", " ");
        return single.length() > 600 ? single.substring(0, 600) + "..." : single;
    }
}
