package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "ui.mapping", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class TaskThirtyThreeMappingScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 3);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskThirtyThreeScenarioSupport.open(scenario, TaskThirtyThreeScenarioSupport.Entrance.ROUTER)
                .checkTextContains("#mapping_selection_value", "Pattern slot 0 / Endpoint ")
                .click("#mapping_toggle")
                .waitUntilServer("real Provider mapping accepted", TaskThirtyThreeWorldFixture::mappingAccepted)
                .waitForTextContains("#mapping_status", "accepted-0-0")
                .server("record authoritative mapping identity", context -> {
                    context.put("task33.providerId", TaskThirtyThreeWorldFixture.providerId(context));
                    context.put("task33.mappingLanes", TaskThirtyThreeWorldFixture.mappingLanes(context));
                })
                .step("record mapping evidence", context -> {
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.mapping");
                    context.attach("providerId", context.get("task33.providerId"));
                    context.attach("authoritativeMappingLanes", context.get("task33.mappingLanes"));
                })
                .screenshot("ui-mapping-accepted").closeScreen();
    }
}
