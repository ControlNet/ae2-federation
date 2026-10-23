package space.controlnet.ae2federation.client.automation;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import space.controlnet.ae2federation.script.Canvas;
import space.controlnet.ae2federation.script.Catalog;
import space.controlnet.ae2federation.script.Checker;
import space.controlnet.ae2federation.script.Completion;
import space.controlnet.ae2federation.script.Completions;
import space.controlnet.ae2federation.script.GraphCards;
import space.controlnet.ae2federation.script.Program;
import space.controlnet.ae2federation.script.ScriptParser;
import space.controlnet.ae2federation.script.ScriptProblems;
import space.controlnet.ae2federation.script.Workspace;

/**
 * Checks and completes the shown program off the client thread, so typing never waits for it: one worker thread,
 * and only the latest request of each kind is served when several come while one runs. The caller hands over a
 * snapshot (the text, the other programs' texts and a catalog over fixed lists); the answer comes back on the client
 * thread.
 */
@OnlyIn(Dist.CLIENT)
public final class ScriptChecker {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "AE2 Federation script checker");
        thread.setDaemon(true);
        return thread;
    });
    private static final AtomicReference<Job> PENDING = new AtomicReference<>();
    private static final AtomicReference<Query> QUERY = new AtomicReference<>();
    private static final AtomicReference<GraphJob> GRAPH = new AtomicReference<>();

    private record Job(String text, Map<String, String> others, Catalog catalog,
            Consumer<List<ScriptProblems.Mark>> done) {
    }

    private record Query(String text, int line, int column, Map<String, String> others, Catalog catalog,
            Consumer<Completions> done) {
    }

    private record GraphJob(String text, Map<String, String> others, Catalog catalog,
            Consumer<Optional<Map<String, Graph>>> done) {
    }

    /**
     * A function as the graph view draws it: its canvas, its cards and its variables, and the other programs it was
     * checked against.
     */
    public record Graph(Canvas canvas, List<GraphCards.Card> cards, List<GraphCards.Variable> variables,
            Workspace workspace) {
    }

    private ScriptChecker() {
    }

    /** Checks {@code text} against the other programs, as the server does; {@code done} runs on the client thread. */
    public static void check(String text, Map<String, String> others, Catalog catalog,
            Consumer<List<ScriptProblems.Mark>> done) {
        if (PENDING.getAndSet(new Job(text, Map.copyOf(others), catalog, done)) == null) {
            WORKER.execute(ScriptChecker::checkPending);
        }
    }

    /**
     * Completes at {@code line} and {@code column} (from 1) of {@code text}; {@code done} runs on the client thread.
     */
    public static void complete(String text, int line, int column, Map<String, String> others, Catalog catalog,
            Consumer<Completions> done) {
        if (QUERY.getAndSet(new Query(text, line, column, Map.copyOf(others), catalog, done)) == null) {
            WORKER.execute(ScriptChecker::completePending);
        }
    }

    /**
     * The graph of every function of {@code text}, by name in the file's order, checked against the other programs;
     * {@code done} runs on the client thread, and gets nothing for a text that does not parse.
     */
    public static void graph(String text, Map<String, String> others, Catalog catalog,
            Consumer<Optional<Map<String, Graph>>> done) {
        if (GRAPH.getAndSet(new GraphJob(text, Map.copyOf(others), catalog, done)) == null) {
            WORKER.execute(ScriptChecker::graphPending);
        }
    }

    /** Drops requests not yet started; one already running still answers. */
    public static void cancel() {
        PENDING.set(null);
        QUERY.set(null);
        GRAPH.set(null);
    }

    private static Workspace workspace(Map<String, String> others, Catalog catalog) {
        var files = new LinkedHashMap<String, Program>();
        others.forEach((name, text) -> files.put(name, ScriptParser.parse(text).program()));
        return new Workspace(files, catalog);
    }

    private static void completePending() {
        var query = QUERY.getAndSet(null);
        if (query == null) return;
        Completions completions;
        try {
            completions = Completion.complete(query.text(), query.line(), query.column(),
                    workspace(query.others(), query.catalog()));
        } catch (RuntimeException e) {
            LOGGER.error("Completing a program in the Automation tab failed", e);
            return;
        }
        Minecraft.getInstance().execute(() -> query.done().accept(completions));
    }

    private static void checkPending() {
        var job = PENDING.getAndSet(null);
        if (job == null) return;
        List<ScriptProblems.Mark> marks;
        try {
            marks = ScriptProblems.check(job.text(), workspace(job.others(), job.catalog()));
        } catch (RuntimeException e) {
            LOGGER.error("Checking a program in the Automation tab failed", e);
            return;
        }
        Minecraft.getInstance().execute(() -> job.done().accept(marks));
    }

    /** The graph of every function of {@code text}, by name in the file's order; nothing when it does not parse. */
    public static Optional<Map<String, Graph>> graphs(String text, Workspace workspace) {
        var parsed = ScriptParser.parse(text);
        if (!parsed.ok()) return Optional.empty();
        var result = Checker.check(parsed.program(), workspace);
        var functions = new LinkedHashMap<String, Graph>();
        for (var function : parsed.program().functions()) {
            var canvas = Canvas.of(function, result);
            functions.put(function.name(), new Graph(canvas, GraphCards.of(canvas, parsed.program(), workspace),
                    GraphCards.variables(canvas), workspace));
        }
        return Optional.of(functions);
    }

    private static void graphPending() {
        var job = GRAPH.getAndSet(null);
        if (job == null) return;
        Optional<Map<String, Graph>> graphs;
        try {
            graphs = graphs(job.text(), workspace(job.others(), job.catalog()));
        } catch (RuntimeException e) {
            LOGGER.error("Drawing a program's graph in the Automation tab failed", e);
            return;
        }
        var answer = graphs;
        Minecraft.getInstance().execute(() -> job.done().accept(answer));
    }
}
