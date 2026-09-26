package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "ui-harness.server-ack", group = "ae2federation",
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class FederationServerAckScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        options.tags("actual-client", "server-ack").guiScale(3)
                .defaultTimeoutMs(15_000).scenarioTimeoutMs(90_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        var runId = System.getProperty("ae2federation.ui.runId", "");
        scenario.server("reset authoritative acknowledgment", context -> FederationUiFixture.resetServerAck())
                .server("open menu-backed fixture", context -> {
                    if (!FederationUiFixture.open(context.player())) {
                        throw new IllegalStateException("Dev-only LDLib2 player UI fixture was not registered");
                    }
                })
                .awaitScreen(ModularUIContainerScreen.class)
                .awaitModularUI()
                .awaitElement("#ack_control")
                .checkText("#ack_status", "Server acknowledgment pending")
                .click("#ack_control")
                .waitUntilServer("server acknowledgment " + runId,
                        context -> runId.equals(FederationUiFixture.serverAck()))
                .waitForText("#ack_status", "Server acknowledged " + runId)
                .checkServer("server retained matching run/correlation ID",
                        context -> runId.equals(FederationUiFixture.serverAck()))
                .checkText("#ack_status", "Server acknowledged " + runId)
                .checkBounds("#ack_control", bounds -> bounds.width() > 40 && bounds.height() > 10)
                .step("record server acknowledgment control", context -> {
                    var control = context.el("#ack_control");
                    var status = context.el("#ack_status");
                    context.attach("controlId", "ack_control");
                    context.attach("controlText", control.text());
                    context.attach("controlBounds", control.bounds().toString());
                    context.attach("visibleText", status.text());
                    context.attach("acknowledgmentId", runId);
                })
                .screenshot("server-acknowledged")
                .closeScreen();
    }
}
