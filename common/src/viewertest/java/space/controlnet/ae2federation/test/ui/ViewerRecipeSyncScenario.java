package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import java.util.Comparator;
import space.controlnet.ae2federation.automation.ViewerScans;
import space.controlnet.ae2federation.automation.ViewerSyncStatus;
import space.controlnet.ae2federation.automation.ViewerSyncStatus.CategoryRow;

/**
 * The Recipe sync page while the client's JEI sends its recipes for the first time, once the round is done, after
 * Rescan all (every bucket matches, nothing read again), and after the largest category's row is opened and rescanned
 * (every recipe in it read again, none dropped or changed), then in Simplified Chinese. Runs only on the viewer client
 * ({@code viewerUiTestClient}).
 */
@LDLRegisterClient(name = "ui.viewer-recipe-sync", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class ViewerRecipeSyncScenario implements UIScenario {
    /** The page's layout, as {@code FederationRecipeSyncView} draws it: the buckets' height and the list's top. */
    private static final int TOP = 68;
    private static final int LIST = TOP + 6 + 39 + 2;
    private static final int ROW = 13;
    /** What the server steps found, for the client steps: the round's end and the row opened. */
    private static volatile long checkedAt;
    private static volatile String opened = "";
    private static volatile int openedFailed;
    private static volatile int openedEntries;

    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 3);
        // A pack with Create sends its recipes in about twenty seconds under xvfb, after thirty seconds of quiet.
        options.scenarioTimeoutMs(480_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskFifteenScenarioSupport.open(scenario, TaskFifteenScenarioSupport.Entrance.ROUTER)
                .click("#tab_sync")
                .timeoutMs(240_000)
                .waitUntilServer("a viewer holds a task", ViewerRecipeSyncScenario::leased)
                .frames(40)
                .check("the header counts the first sync", context ->
                        context.el("#sync_summary").text().startsWith("First sync "))
                .screenshot("ui-recipe-sync-first")
                .timeoutMs(240_000)
                .waitUntilServer("the first round is done", ViewerRecipeSyncScenario::finished)
                .waitUntil("the page shows the finished round", context ->
                        context.el("#sync_summary").text().startsWith("Recipes "))
                .frames(4)
                .screenshot("ui-recipe-sync-done")
                .click("#sync_rescan_all")
                .timeoutMs(240_000)
                .waitUntilServer("the rescan's round is done", ViewerRecipeSyncScenario::finishedAgain)
                .checkServer("Rescan all read nothing: every bucket matches", context -> {
                    var status = ViewerScans.status(context.server());
                    return status.categories().stream().allMatch(row -> row.read() == 0 && row.dropped() == 0);
                })
                .waitUntil("the page says nothing changed", context ->
                        context.el("#sync_summary").text().endsWith("no changes"))
                .frames(4)
                .screenshot("ui-recipe-sync-rescanned")
                .server("find the first row", ViewerRecipeSyncScenario::firstRow)
                .step("open the first row", context -> click(context, 40, LIST + 5))
                .frames(4)
                .screenshot("ui-recipe-sync-row-open")
                .step("press the row's Rescan", context -> click(context, -18,
                        LIST + ROW + 4 + ROW * (openedFailed + 1) + 6))
                .timeoutMs(240_000)
                .waitUntilServer("the row's rescan is done", ViewerRecipeSyncScenario::finishedAgain)
                .checkServer("the row's category was read again in full, nothing dropped", context -> {
                    var row = row(context, opened);
                    return row.read() == openedEntries && row.dropped() == 0;
                })
                .frames(30)
                .check("a forced rescan that changed nothing says so", context ->
                        context.el("#sync_summary").text().endsWith("no changes"))
                .screenshot("ui-recipe-sync-row-rescanned")
                .teardown("restore English even after failure", context -> {
                    if (context.firstAttempt()) {
                        context.mc().getLanguageManager().setSelected("en_us");
                        context.mc().options.languageCode = "en_us";
                        context.put("languageRestore", context.mc().reloadResourcePacks());
                    }
                    java.util.concurrent.CompletableFuture<Void> reload = context.get("languageRestore");
                    if (!reload.isDone()) context.repeat("English resource reload");
                    else reload.join();
                })
                .step("select Simplified Chinese", context -> {
                    if (context.firstAttempt()) {
                        context.mc().getLanguageManager().setSelected("zh_cn");
                        context.mc().options.languageCode = "zh_cn";
                        context.put("languageReload", context.mc().reloadResourcePacks());
                    }
                    java.util.concurrent.CompletableFuture<Void> reload = context.get("languageReload");
                    if (!reload.isDone()) context.repeat("Chinese resource reload");
                    else reload.join();
                }).timeoutMs(45_000)
                .waitUntil("Chinese resources finish loading", context -> context.mc().getOverlay() == null)
                .frames(10)
                .screenshot("ui-recipe-sync-zh");
    }

    private static boolean leased(ServerContext context) {
        return ViewerScans.status(context.server()).categories().stream().anyMatch(row -> !row.leases().isEmpty());
    }

    private static boolean finished(ServerContext context) {
        var status = ViewerScans.status(context.server());
        if (status.checkedAt() == 0 || !ViewerScans.progress(context.server()).done()) return false;
        checkedAt = status.checkedAt();
        return true;
    }

    /** A round that began after the last one finished is done. */
    private static boolean finishedAgain(ServerContext context) {
        var previous = checkedAt;
        var status = ViewerScans.status(context.server());
        if (status.checkedAt() == previous || !ViewerScans.progress(context.server()).done()) return false;
        checkedAt = status.checkedAt();
        return true;
    }

    /** The page's first row once the round is done: categories with failures first, then the largest. */
    private static void firstRow(ServerContext context) {
        var row = ViewerScans.status(context.server()).categories().stream()
                .sorted(Comparator.comparing((CategoryRow each) -> each.failed() == 0)
                        .thenComparing(Comparator.comparingInt((CategoryRow each) ->
                                Math.max(each.listed(), each.recipes())).reversed()))
                .findFirst().orElseThrow();
        opened = row.id();
        openedFailed = row.failedIds().size();
        openedEntries = row.recipes() + row.skipped() + row.failed();
    }

    private static CategoryRow row(ServerContext context, String id) {
        ViewerSyncStatus status = ViewerScans.status(context.server());
        return status.categories().stream().filter(each -> each.id().equals(id)).findFirst().orElseThrow();
    }

    /**
     * Clicks the categories panel at {@code dx} from its list's left (from its right edge when negative) and
     * {@code dy} below the board's top.
     */
    private static void click(TestContext context, float dx, float dy) {
        var board = context.el("#sync_board").bounds();
        float viewers = Math.max(110, Math.min(264, Math.round(board.width() * 0.35f)));
        float x = dx >= 0 ? board.x() + viewers + 6 + 10 + dx : board.x() + board.width() - 10 + dx;
        float y = board.y() + dy;
        context.input().mouseDown(x, y, 0);
        context.input().mouseUp(x, y, 0);
    }
}
