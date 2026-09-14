package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "ui.bridge", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class TaskFifteenBridgeScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        options.tags("actual-client", "task-15").guiScale(3).defaultTimeoutMs(15_000).scenarioTimeoutMs(90_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskFifteenScenarioSupport.open(scenario, TaskFifteenScenarioSupport.Entrance.BRIDGE)
                .checkText("#entrance_value", "Opened from Multipart Bridge")
                .checkBounds("#policy_toggle", bounds -> bounds.width() > 40 && bounds.height() > 10)
                .click("#policy_toggle")
                .waitUntilServer("server policy acknowledgment", context ->
                        TaskFifteenWorldFixture.policyConfigured(context))
                .waitForTextContains("#ack_status", "Server accepted revision")
                .server("observe production Bridge mutation", context ->
                        TaskFifteenWorldFixture.observe(context, "ACCEPTED"))
                .step("record Bridge policy outcome", context -> TaskFifteenScenarioSupport.attachPolicy(context, "ui.bridge"))
                .screenshot("ui-bridge-accepted")
                .closeScreen()
                .server("remove real Bridge outer attachment", TaskFifteenWorldFixture::disableBridge)
                .waitUntilServer("Bridge becomes diagnostic only", TaskFifteenWorldFixture::bridgeDisabled)
                .server("open disabled production Bridge", TaskFifteenWorldFixture::openBridge)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI()
                .waitForTextContains("#ack_status", "Bridge diagnostic only:")
                .check("disabled Bridge submit is inactive", context -> !context.el("#policy_toggle").isActive())
                .step("record disabled Bridge proof", context -> context.attach("bridgeDiagnosticOnly", "true"))
                .screenshot("ui-bridge-disabled")
                .closeScreen();
    }
}
