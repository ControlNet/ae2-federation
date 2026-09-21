package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "ui.endpoint", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class TaskThirtyThreeEndpointScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 2);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskThirtyThreeScenarioSupport.open(scenario, TaskThirtyThreeScenarioSupport.Entrance.HUB)
                .check("scale-2 policy labels stay inside buttons", context ->
                        TaskThirtyThreeScenarioSupport.buttonTextContained(context, "#consumer_next", "#provider_next",
                                "#capability_next", "#policy_toggle"))
                .hover("#endpoint_detail")
                .waitForTextContains("#endpoint_detail", "Mode FEDERATED")
                .checkTextContains("#endpoint_detail", "Claim epoch 1")
                .server("record live Endpoint identity", context ->
                        context.put("task33.endpointId", TaskThirtyThreeWorldFixture.endpointId(context)))
                .step("record Endpoint evidence", context -> {
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.endpoint");
                    context.attach("endpointId", context.get("task33.endpointId"));
                    context.attach("endpointMode", "FEDERATED");
                })
                .screenshot("ui-endpoint-detail").closeScreen();
    }
}
