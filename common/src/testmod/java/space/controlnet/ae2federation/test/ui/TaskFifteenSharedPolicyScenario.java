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
                .click("#policy_toggle")
                .waitUntilServer("Router edit accepted", TaskFifteenWorldFixture::policyConfigured)
                .closeScreen()
                .server("open the same policy from the real Bridge", TaskFifteenWorldFixture::openBridge)
                .awaitScreen(ModularUIContainerScreen.class)
                .awaitModularUI()
                .waitForTextContains("#entrance_value", "ME Federation Bridge - side ")
                .waitForTextContains("#rule_value", "STORAGE: enabled (revision")
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
