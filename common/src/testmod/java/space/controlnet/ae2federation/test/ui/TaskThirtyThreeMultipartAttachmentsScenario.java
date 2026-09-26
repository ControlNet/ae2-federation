package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "ui.multipart-attachments", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class TaskThirtyThreeMultipartAttachmentsScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 3);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskThirtyThreeWorldFixture.arrange(scenario)
                .server("record actual host network for initial focus", TaskThirtyThreeWorldFixture::recordBridgeFocus)
                .server("open production Bridge workspace", TaskThirtyThreeWorldFixture::openBridge)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().awaitElement("#domain_graph").frames(5)
                .step("record visible overview", context -> context.put("task33.graphVisited", Boolean.toString(context.el("#domain_graph").isVisible())))
                .waitUntil("Bridge initially selects its actual host network", context -> {
                    var label = context.el("#graph_selection").as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class);
                    return label.collectHoverTooltips().tooltipTexts().stream().anyMatch(line ->
                            line.getString().replace("\n", "").equals(context.<String>get("bridge.focus")));
                })
                .checkText("#entrance_value", "ME Federation Bridge - side north / type bridge / cable extension 5.0")
                .checkTextContains("#members_value", "2 members")
                .server("record real multipart attachment", context ->
                        context.put("task33.multipart", TaskThirtyThreeWorldFixture.multipartAttachment(context)))
                .step("record multipart evidence", context -> {
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.multipart-attachments");
                    context.attach("multipartAttachment", context.get("task33.multipart"));
                    context.attach("visibleAttachment", context.el("#entrance_value").text());
                })
                .screenshot("ui-multipart-attachments")
                .click(".graph-node-provider").click("#graph_zoom_in")
                .step("record user-selected graph view", context -> {
                    context.put("bridge.userSelection", context.el("#graph_selection").text());
                    context.put("bridge.userZoom", context.el("#domain_graph")
                            .as(com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView.class).getScale());
                })
                .serverTicks(3).frames(5)
                .check("Bridge refresh preserves user's selection and zoom", context ->
                        context.el("#graph_selection").text().equals(context.get("bridge.userSelection"))
                                && Math.abs(context.el("#domain_graph").as(com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView.class).getScale()
                                        - context.<Float>get("bridge.userZoom")) < 0.0001f)
                .closeScreen()
                .server("remove real Bridge outer attachment", TaskThirtyThreeWorldFixture::disconnectBridgeOuterSide)
                .waitUntilServer("Bridge reports missing outer attachment", TaskThirtyThreeWorldFixture::bridgeOuterSideMissing)
                .server("reopen disconnected Bridge", TaskThirtyThreeWorldFixture::openBridge)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI()
                .waitForTextContains("#ack_status", "Connect a network to the bridge outer side.")
                .check("disconnected Bridge presents diagnostics instead of empty editors", context ->
                        context.el("#bridge_unavailable").isVisible() && !context.el("#workspace_tabs").isVisible()
                                && !context.el("#page_overview").isVisible() && !context.el("#page_policy").isVisible())
                .check("Bridge diagnostic is compact", context -> context.el("#domain_root").bounds().width() <= 360
                        && context.el("#domain_root").bounds().height() <= 160)
                .check("disconnected Bridge cannot edit policies", context -> !context.el("#policy_toggle").isActive())
                .check("unavailable explanation fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(context, "#ack_status"))
                .screenshot("ui-bridge-disconnected").closeScreen();
    }
}
