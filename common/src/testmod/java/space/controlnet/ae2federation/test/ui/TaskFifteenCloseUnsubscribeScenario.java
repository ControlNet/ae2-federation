package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "ui.close-unsubscribe", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class TaskFifteenCloseUnsubscribeScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        options.tags("actual-client", "task-15").guiScale(3).defaultTimeoutMs(15_000).scenarioTimeoutMs(90_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskFifteenScenarioSupport.open(scenario, TaskFifteenScenarioSupport.Entrance.ROUTER)
                .click("#policy_toggle")
                .waitUntilServer("initial edit accepted", TaskFifteenWorldFixture::policyConfigured)
                .closeScreen()
                .waitUntilServer("production menu closed", TaskFifteenWorldFixture::menuClosed)
                .server("mutate shared policy after menu close", context -> {
                    TaskFifteenWorldFixture.mutateAfterClose(context);
                    TaskFifteenWorldFixture.openBridge(context);
                })
                .awaitScreen(ModularUIContainerScreen.class)
                .awaitModularUI()
                .waitForTextContains("#rule_value", "STORAGE: disabled (revision")
                .checkServer("menu close did not stop policy sharing", context ->
                        !TaskFifteenWorldFixture.policyEnabled(context))
                .server("observe post-close production mutation", context ->
                        TaskFifteenWorldFixture.observe(context, "POST_CLOSE_ACCEPTED"))
                .step("record closed-menu lifecycle", context -> {
                    TaskFifteenScenarioSupport.attachPolicy(context, "ui.close-unsubscribe");
                    context.attach("menuClosedBeforeMutation", "true");
                    context.attach("sharingContinues", "true");
                })
                .screenshot("ui-close-unsubscribe")
                .closeScreen();
    }
}
