package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "ui.hub", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class TaskFifteenHubScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        options.tags("actual-client", "task-15").guiScale(3).defaultTimeoutMs(15_000).scenarioTimeoutMs(90_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskFifteenScenarioSupport.open(scenario, TaskFifteenScenarioSupport.Entrance.HUB)
                .checkText("#entrance_value", "Opened from Federation Hub")
                .checkTextContains("#members_value", "2 members")
                .click("#policy_toggle")
                .waitUntilServer("server policy acknowledgment", context ->
                        TaskFifteenWorldFixture.policyConfigured(context))
                .waitForTextContains("#ack_status", "Server accepted revision")
                .server("observe production Hub mutation", context ->
                        TaskFifteenWorldFixture.observe(context, "ACCEPTED"))
                .step("record Hub policy outcome", context -> TaskFifteenScenarioSupport.attachPolicy(context, "ui.hub"))
                .screenshot("ui-hub-accepted")
                .closeScreen();
    }
}
