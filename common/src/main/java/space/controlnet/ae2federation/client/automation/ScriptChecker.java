package space.controlnet.ae2federation.client.automation;

import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.slf4j.Logger;
import space.controlnet.ae2federation.function.Catalog;
import space.controlnet.ae2federation.function.Program;
import space.controlnet.ae2federation.function.ScriptParser;
import space.controlnet.ae2federation.function.ScriptProblems;
import space.controlnet.ae2federation.function.Workspace;

/**
 * Checks the shown program off the client thread, so typing never waits for it: one worker thread, and only the
 * latest request is checked when several come while one runs. The caller hands over a snapshot (the text, the other
 * programs' texts and a catalog over fixed lists); the marks come back on the client thread.
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

    private record Job(String text, Map<String, String> others, Catalog catalog,
            Consumer<List<ScriptProblems.Mark>> done) {
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

    /** Drops a request not yet started; one already running still answers. */
    public static void cancel() {
        PENDING.set(null);
    }

    private static void checkPending() {
        var job = PENDING.getAndSet(null);
        if (job == null) return;
        List<ScriptProblems.Mark> marks;
        try {
            var files = new LinkedHashMap<String, Program>();
            job.others().forEach((name, text) -> files.put(name, ScriptParser.parse(text).program()));
            marks = ScriptProblems.check(job.text(), new Workspace(files, job.catalog()));
        } catch (RuntimeException e) {
            LOGGER.error("Checking a program in the Automation tab failed", e);
            return;
        }
        Minecraft.getInstance().execute(() -> job.done().accept(marks));
    }
}
