package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "ui.graph-controls", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class TaskThirtyThreeGraphControlsScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 3);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskThirtyThreeScenarioSupport.open(scenario, TaskThirtyThreeScenarioSupport.Entrance.HUB)
                .checkBounds("#fabric_graph", bounds -> bounds.width() > 180 && bounds.height() > 150)
                .check("scale-3 policy labels stay inside buttons", context ->
                        TaskThirtyThreeScenarioSupport.buttonTextContained(context, "#consumer_next", "#provider_next",
                                "#capability_next", "#policy_toggle"))
                .click("#graph_zoom_in").click("#graph_zoom_out").click("#graph_fit")
                .click("#physical_layer_toggle").click("#physical_layer_toggle")
                .click("#capability_layer_toggle").click("#capability_layer_toggle")
                .check("graph remains visible after controls", context -> context.el("#fabric_graph").isVisible())
                .serverGet("record current policy revision", "task33.policyBefore",
                        TaskFifteenWorldFixture::policyRevision)
                .click("#policy_toggle")
                .waitUntilServer("real policy revision advances", context ->
                        TaskFifteenWorldFixture.policyRevision(context)
                                > context.<Long>get("task33.policyBefore"))
                .waitForTextContains("#ack_status", "Server accepted revision")
                .server("record authoritative policy result", context -> {
                    context.put("task33.policyRevision", Long.toString(TaskFifteenWorldFixture.policyRevision(context)));
                    context.put("task33.policyEnabled", Boolean.toString(TaskFifteenWorldFixture.policyEnabled(context)));
                })
                .step("record graph control evidence", context -> {
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.graph-controls");
                    context.attach("policyRevision", context.get("task33.policyRevision"));
                    context.attach("policyEnabled", context.get("task33.policyEnabled"));
                    context.attach("worldCaptures", "hub-overview,bridge-north,provider-host,endpoint-faces");
                })
                .screenshot("ui-graph-controls").closeScreen()
                .server("position Hub overview camera", TaskThirtyThreeWorldFixture::positionHubOverviewCamera)
                .serverTicks(2).frames(2).screenshot("world-hub-overview")
                .server("position Multipart Bridge camera", TaskThirtyThreeWorldFixture::positionBridgeCamera)
                .serverTicks(2).frames(2).screenshot("world-multipart-bridge-north")
                .server("position Provider host camera", TaskThirtyThreeWorldFixture::positionProviderCamera)
                .serverTicks(2).frames(2).screenshot("world-provider-host")
                .server("position Endpoint face camera", TaskThirtyThreeWorldFixture::positionEndpointCamera)
                .serverTicks(2).frames(2).screenshot("world-endpoint-faces");
    }
}
