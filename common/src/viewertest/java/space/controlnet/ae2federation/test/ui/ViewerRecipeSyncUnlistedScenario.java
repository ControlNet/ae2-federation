package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import java.util.Comparator;
import java.util.List;
import space.controlnet.ae2federation.automation.BucketedRecipes;
import space.controlnet.ae2federation.automation.RecipeBuckets;
import space.controlnet.ae2federation.automation.ViewerRecipes;
import space.controlnet.ae2federation.automation.ViewerScans;
import space.controlnet.ae2federation.automation.ViewerSyncStatus.CategoryRow;

/**
 * The Recipe sync page with a saved category the client's JEI does not list (a machine a pack update removed, its mod
 * still loaded): its row opened shows the recipe that failed, and no Rescan, which no viewer could answer (review of
 * 2026-11-15). The category is test data the server step saves, with one failed recipe so the row shows a line when
 * opened. Runs only on the viewer client ({@code viewerUiTestClient}).
 */
@LDLRegisterClient(name = "ui.viewer-recipe-sync-unlisted", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class ViewerRecipeSyncUnlistedScenario implements UIScenario {
    /** A category in a loaded mod's namespace, so the round keeps it, that no JEI category has. */
    private static final String CATEGORY = "minecraft:retired_machine";
    /** The page's layout, as {@code FederationRecipeSyncView} draws it (see {@link ViewerRecipeSyncScenario}). */
    private static final int TOP = 68;
    private static final int LIST = TOP + 6 + 39 + 2;
    private static final int ROW = 13;
    private static volatile int index = -1;

    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 3);
        options.scenarioTimeoutMs(480_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskFifteenScenarioSupport.open(scenario, TaskFifteenScenarioSupport.Entrance.ROUTER)
                .server("save a category no viewer lists", ViewerRecipeSyncUnlistedScenario::save)
                .click("#tab_sync")
                .timeoutMs(240_000)
                .waitUntilServer("the first round is done", context -> ViewerScans.status(context.server())
                        .checkedAt() != 0 && ViewerScans.progress(context.server()).done())
                .checkServer("the saved category is listed by no viewer", context -> row(context).listed() < 0)
                .server("find its row", ViewerRecipeSyncUnlistedScenario::find)
                .waitUntil("the page shows the finished round", context ->
                        context.el("#sync_summary").text().startsWith("Recipes "))
                .frames(20)
                .screenshot("ui-recipe-sync-unlisted-closed")
                .step("open its row", context -> click(context, 40, LIST + 5 + index * ROW))
                .frames(4)
                .screenshot("ui-recipe-sync-unlisted-open");
    }

    private static void save(ServerContext context) {
        var recipes = ViewerRecipes.get(context.server());
        recipes.create(CATEGORY, 0);
        var failed = new RecipeBuckets.Fingerprint(RecipeBuckets.hash(CATEGORY + "/smelter"), 1);
        recipes.apply(CATEGORY, 0, List.of(), List.of(new BucketedRecipes.Entry<>(failed,
                BucketedRecipes.Status.FAILED, null, CATEGORY + "/smelter")));
    }

    private static CategoryRow row(ServerContext context) {
        return ViewerScans.status(context.server()).categories().stream().filter(each -> each.id().equals(CATEGORY))
                .findFirst().orElseThrow();
    }

    /** Its place in the page's order once the round is done: failures first, then the largest. */
    private static void find(ServerContext context) {
        var rows = ViewerScans.status(context.server()).categories().stream()
                .sorted(Comparator.comparing((CategoryRow row) -> row.failed() == 0)
                        .thenComparing(Comparator.comparingInt((CategoryRow row) ->
                                Math.max(row.listed(), row.recipes())).reversed()))
                .map(CategoryRow::id).toList();
        index = rows.indexOf(CATEGORY);
        if (index < 0 || index > 15) {
            throw new IllegalStateException("The unlisted row is at " + index + " of " + rows);
        }
    }

    /** Clicks the categories panel at {@code dx} from its list's left and {@code dy} below the board's top. */
    private static void click(TestContext context, float dx, float dy) {
        var board = context.el("#sync_board").bounds();
        float viewers = Math.max(110, Math.min(264, Math.round(board.width() * 0.35f)));
        float x = board.x() + viewers + 6 + 10 + dx;
        float y = board.y() + dy;
        context.input().mouseDown(x, y, 0);
        context.input().mouseUp(x, y, 0);
    }
}
