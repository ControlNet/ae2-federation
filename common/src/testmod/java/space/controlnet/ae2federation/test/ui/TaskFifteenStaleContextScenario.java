package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "ui.stale-context", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class TaskFifteenStaleContextScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        options.tags("actual-client", "task-15").guiScale(3).defaultTimeoutMs(15_000).scenarioTimeoutMs(90_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskFifteenScenarioSupport.open(scenario, TaskFifteenScenarioSupport.Entrance.HUB)
                .server("invalidate opened Fabric generation", TaskFifteenWorldFixture::invalidateHubContext)
                .waitUntilServer("real Fabric generation invalidated", TaskFifteenWorldFixture::hubContextInvalidated)
                .click("#policy_toggle")
                .waitUntilServer("stale generation rejected", context ->
                        TaskFifteenWorldFixture.policyRevision(context) == 0)
                .waitForText("#ack_status", "Rejected by server: Fabric topology changed; reopen this editor")
                .check("stale selector is inactive", context -> !context.el("#consumer_next").isActive())
                .click("#consumer_next")
                .checkText("#ack_status", "Rejected by server: Fabric topology changed; reopen this editor")
                .check("stale submit remains inactive", context -> !context.el("#policy_toggle").isActive())
                .checkServer("stale edit did not mutate policy", context ->
                        TaskFifteenWorldFixture.policyRevision(context) == 0
                                && !TaskFifteenWorldFixture.policyConfigured(context))
                .server("observe production stale rejection", context ->
                        TaskFifteenWorldFixture.observe(context, "STALE_CONTEXT"))
                .step("record stale context rejection", context -> {
                    TaskFifteenScenarioSupport.attachPolicy(context, "ui.stale-context");
                    context.attach("staleContext", "true");
                })
                .screenshot("ui-stale-context-rejected")
                .closeScreen();
    }
}
