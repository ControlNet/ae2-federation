package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "ui.shared-policy", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class TaskFifteenSharedPolicyScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        options.tags("actual-client", "task-15").guiScale(3).defaultTimeoutMs(15_000).scenarioTimeoutMs(90_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskFifteenScenarioSupport.open(scenario, TaskFifteenScenarioSupport.Entrance.ROUTER)
                // Press and look in one frame, before the reply can land: locking every switch while the edit was
                // in flight made them all flash.
                .hover(TaskFifteenScenarioSupport.STORAGE_SWITCH)
                .step("other switches keep their look while the edit is in flight", context -> {
                    var bounds = context.el(TaskFifteenScenarioSupport.STORAGE_SWITCH).bounds();
                    context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
                    context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
                    if (!context.el("#ack_status").hasClass("pending")) {
                        throw new AssertionError("the edit is not in flight right after the press");
                    }
                    if (!context.el("#policy_switch_0_crafting").isActive()) {
                        throw new AssertionError("another switch locked while the edit was in flight");
                    }
                })
                .waitUntilServer("Router edit accepted", TaskFifteenWorldFixture::policyConfigured)
                .closeScreen()
                .server("open the same policy from the real Bridge", TaskFifteenWorldFixture::openBridge)
                .awaitScreen(ModularUIContainerScreen.class)
                .awaitModularUI()
                .waitForTextContains("#pair_title", "Via the Bridge at ")
                .waitUntil("Bridge shows the Router's switch on", context -> context.elOpt(TaskFifteenScenarioSupport.STORAGE_SWITCH)
                        .map(element -> element.as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).hasClass("on"))
                        .orElse(false))
                .checkServer("both entrances retain one directional record", context ->
                        TaskFifteenWorldFixture.policyEnabled(context))
                .server("observe shared production policy", context ->
                        TaskFifteenWorldFixture.observe(context, "SHARED"))
                .step("record shared policy identity", context ->
                        TaskFifteenScenarioSupport.attachPolicy(context, "ui.shared-policy"))
                .screenshot("ui-shared-policy")
                .closeScreen();
    }
}
