package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "ui.router", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class TaskFifteenRouterScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        options.tags("actual-client", "task-15").guiScale(3).defaultTimeoutMs(15_000).scenarioTimeoutMs(90_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskFifteenScenarioSupport.open(scenario, TaskFifteenScenarioSupport.Entrance.ROUTER)
                .checkTextContains("#members_value", "2 networks · ")
                .click(TaskFifteenScenarioSupport.STORAGE_SWITCH)
                .waitUntilServer("server policy acknowledgment", context ->
                        TaskFifteenWorldFixture.policyConfigured(context))
                .waitUntil("the switch shows the accepted rule and the footer stays hidden", context -> context
                        .el(TaskFifteenScenarioSupport.STORAGE_SWITCH).as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class)
                        .hasClass("on") && !context.el("#domain_footer").as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class)
                        .isDisplayed())
                .server("observe production Router mutation", context ->
                        TaskFifteenWorldFixture.observe(context, "ACCEPTED"))
                .step("record Router policy outcome", context -> TaskFifteenScenarioSupport.attachPolicy(context, "ui.router"))
                .screenshot("ui-router-accepted")
                .closeScreen();
    }
}
