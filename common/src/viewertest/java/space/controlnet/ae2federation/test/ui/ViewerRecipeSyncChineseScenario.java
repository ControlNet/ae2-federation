package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import space.controlnet.ae2federation.automation.ViewerScans;

/**
 * The Recipe sync page in Simplified Chinese, the language chosen before the screen opens: during the first sync and
 * once it is done ({@link ViewerRecipeSyncScenario} has the English page). Runs only on the viewer client
 * ({@code viewerUiTestClient}).
 */
@LDLRegisterClient(name = "ui.viewer-recipe-sync-zh", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class ViewerRecipeSyncChineseScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 3);
        options.scenarioTimeoutMs(480_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        scenario.step("select Simplified Chinese", context -> {
            if (context.firstAttempt()) {
                context.mc().getLanguageManager().setSelected("zh_cn");
                context.mc().options.languageCode = "zh_cn";
                context.put("languageReload", context.mc().reloadResourcePacks());
            }
            java.util.concurrent.CompletableFuture<Void> reload = context.get("languageReload");
            if (!reload.isDone()) context.repeat("Chinese resource reload");
            else reload.join();
        }).timeoutMs(45_000).waitUntil("Chinese resources finish loading", context -> context.mc().getOverlay() == null)
                .teardown("restore English even after failure", context -> {
                    if (context.firstAttempt()) {
                        context.mc().getLanguageManager().setSelected("en_us");
                        context.mc().options.languageCode = "en_us";
                        context.put("languageRestore", context.mc().reloadResourcePacks());
                    }
                    java.util.concurrent.CompletableFuture<Void> reload = context.get("languageRestore");
                    if (!reload.isDone()) context.repeat("English resource reload");
                    else reload.join();
                });
        TaskFifteenScenarioSupport.open(scenario, TaskFifteenScenarioSupport.Entrance.ROUTER)
                .click("#tab_sync")
                .timeoutMs(240_000)
                .waitUntilServer("a viewer holds a task", context -> ViewerScans.status(context.server()).categories()
                        .stream().anyMatch(row -> !row.leases().isEmpty()))
                .frames(40)
                .check("the header counts the first sync in Chinese", context ->
                        context.el("#sync_summary").text().startsWith("首次同步"))
                .screenshot("ui-recipe-sync-zh-first")
                .timeoutMs(240_000)
                .waitUntilServer("the first round is done", context -> ViewerScans.status(context.server())
                        .checkedAt() != 0 && ViewerScans.progress(context.server()).done())
                .waitUntil("the page shows the finished round", context ->
                        context.el("#sync_summary").text().startsWith("配方 "))
                .frames(4)
                .screenshot("ui-recipe-sync-zh-done");
    }
}
