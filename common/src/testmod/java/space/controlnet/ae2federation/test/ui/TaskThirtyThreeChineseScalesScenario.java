package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;

@LDLRegisterClient(name = "ui.chinese-scales", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class TaskThirtyThreeChineseScalesScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 4);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        scenario.step("select Simplified Chinese", context -> {
            if (context.firstAttempt()) {
                context.mc().getLanguageManager().setSelected("zh_cn");
                context.mc().options.languageCode = "zh_cn";
                context.put("languageReload", context.mc().reloadResourcePacks());
            }
            java.util.concurrent.CompletableFuture<Void> reload = context.get("languageReload");
            if (!reload.isDone()) context.repeat("Chinese resource reload");
            else reload.join();
        }).timeoutMs(45_000).waitUntil("Chinese resources finish loading", context -> context.mc().getOverlay() == null)
                .teardown("restore language even after failure", context -> {
                    if (context.firstAttempt()) {
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(context.mc().getWindow().getWindow(), 1600, 960);
                        context.mc().getLanguageManager().setSelected("en_us");
                        context.mc().options.languageCode = "en_us";
                        context.put("languageRestore", context.mc().reloadResourcePacks());
                    }
                    java.util.concurrent.CompletableFuture<Void> reload = context.get("languageRestore");
                    if (!reload.isDone()) context.repeat("English resource reload");
                    else reload.join();
                });
        TaskThirtyThreeScenarioSupport.open(scenario, TaskThirtyThreeScenarioSupport.Entrance.ROUTER)
                .waitForText("#domain_title", "ME 联邦域")
                .server("record the fixture's networks", TaskThirtyThreeWorldFixture::recordNetworks)
                .check("localized pair editor fits compact aside", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(
                        context, "#pair_title", "#policy_section_title_0", "#policy_state_0_storage"))
                .hover("#domain_title").frames(3).screenshot("ui-chinese-pair-editor")
                .typeInto("#graph_search", "网络")
                .check("localized network search matches", context -> !context.el("#graph_search_empty").isVisible())
                .typeInto("#graph_search", "").blur()
                .step("select the Provider host network", context -> TaskThirtyThreeScenarioSupport.selectNetworkCard(
                        context, context.get("net.providerHost")))
                .waitUntil("localized network detail is shown", context -> context.el("#network_detail").isVisible())
                .check("localized device counts are on the devices button", context ->
                        TaskThirtyThreeScenarioSupport.tooltipContains(context, "#graph_open", "Pattern Provider：1"))
                .check("localized network detail fits compact inspector", context ->
                        TaskThirtyThreeScenarioSupport.wrappedTextFits(context, "#network_identity", "#network_stat_energy")
                                && TaskThirtyThreeScenarioSupport.singleLineButtonTextFits(context, "#graph_open", "#network_highlight"))
                .hover("#domain_title").frames(3).screenshot("ui-chinese-provider-observation")
                .step("select the Endpoint network", context -> TaskThirtyThreeScenarioSupport.selectNetworkCard(
                        context, context.get("net.endpoint")))
                .waitUntil("endpoint network detail is shown", context -> context.el("#network_title").text()
                        .contains(TaskThirtyThreeScenarioSupport.networkTag(context.get("net.endpoint"))))
                .check("localized endpoint count is on the devices button", context ->
                        TaskThirtyThreeScenarioSupport.tooltipContains(context, "#graph_open", "处理端点："))
                .check("compact graph detail fits its bounds", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(context,
                        "#network_identity", "#network_stat_cpus", "#network_stat_channels"))
                .check("compact graph inspector action remains inside workspace", context -> {
                    var button = context.el("#graph_open").bounds();
                    var root = context.el("#domain_root").bounds();
                    return button.y() + button.height() <= root.y() + root.height();
                })
                .hover("#domain_title").frames(3).screenshot("ui-chinese-graph-scale-4")
                .step("select the Endpoint node", context -> TaskThirtyThreeScenarioSupport.selectEndpointNode(context, "10, -57, 13"))
                .waitForTextContains("#endpoint_fact_mode", "联邦")
                .checkTextContains("#network_links_heading", "生效的样板")
                .check("compact Endpoint panel text fits its bounds", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(
                        context, "#endpoint_fact_mode", "#endpoint_fact_owner", "#network_links_heading"))
                .hover("#domain_title").frames(3).screenshot("ui-chinese-endpoint-scale-4")
                .click("#tab_mapping").frames(3)
                .waitUntil("the wires view lists the Provider's patterns", context -> !context.all("#processing_pattern_0").isEmpty())
                .checkBounds("#processing_scroll", bounds -> bounds.width() > 100 && bounds.height() > 40)
                .click("#processing_pattern_0")
                .waitUntil("the chosen pattern's detail shows its native 64-bit input", context ->
                        context.el("#processing_detail_text").text().contains("4,000,000,000"))
                .step("record Chinese scale evidence", context -> {
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.chinese-scales");
                    context.attach("largeQuantity", context.el("#processing_detail_text").text());
                })
                .hover("#domain_title").frames(3)
                .screenshot("ui-chinese-scale-4")
                .waitUntilServer("native lane sends a real processing input", TaskThirtyThreeWorldFixture::dispatchRealWork)
                .click("#processing_pattern_1")
                .click(".processing-endpoint")
                .waitForText("#mapping_toggle", "断开 #1")
                .waitUntil("the unmap action is ready", context -> context.el("#mapping_toggle").isActive()).frames(2)
                .check("Chinese mapping action labels fit", context ->
                        TaskThirtyThreeScenarioSupport.singleLineButtonTextFits(context, "#mapping_toggle", "#processing_highlight"))
                .click("#mapping_toggle")
                .waitUntilServer("Chinese scene retains unmapped endpoint", TaskThirtyThreeWorldFixture::endpointRetained)
                .waitUntil("a retained Endpoint offers release", context -> context.el("#processing_release").isActive())
                .hover("#processing_release")
                .step("open Chinese release confirmation", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#processing_release"))
                .awaitElement("#release_confirm")
                .checkTextContains("#release_consequence", "关闭返回此供应器的通道")
                .checkBounds("#release_confirm", bounds -> bounds.height() >= 18 && bounds.width() > 60)
                .check("Chinese modal content remains inside the workspace", context -> {
                    var body = context.el("#release_consequence").bounds();
                    var root = context.el("#domain_root").bounds();
                    return body.x() >= root.x() && body.y() >= root.y()
                            && body.x() + body.width() <= root.x() + root.width()
                            && body.y() + body.height() <= root.y() + root.height();
                })
                .screenshot("ui-chinese-release-scale-4")
                .hover("#release_cancel")
                .step("cancel Chinese release confirmation", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#release_cancel"))
                .waitForTextContains("#processing_status", "选择样板和目标端点")
                .checkServer("Chinese cancellation keeps endpoint owned", TaskThirtyThreeWorldFixture::endpointRetained)
                .step("resize to minimum supported logical width", context ->
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(context.mc().getWindow().getWindow(), 320, 240))
                .waitUntil("320 by 240 logical viewport", context -> context.mc().getWindow().getGuiScaledWidth() == 320
                        && context.mc().getWindow().getGuiScaledHeight() == 240)
                .frames(5)
                .repeat(30, steps -> steps.scroll("#processing_detail", -1)).frames(3)
                .check("narrow mapping action labels fit", context -> TaskThirtyThreeScenarioSupport.singleLineButtonTextFits(
                        context, "#mapping_toggle", "#processing_release"))
                .check("narrow mapping detail fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(
                        context, "#processing_detail_title", "#processing_detail_text"))
                .check("narrow mapping controls stay in the workspace", context -> TaskThirtyThreeScenarioSupport.withinWorkspace(
                        context, "#mapping_toggle", "#processing_release"))
                .screenshot("ui-chinese-narrow-mapping")
                .hover("#processing_release")
                .step("open narrow Chinese confirmation", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#processing_release"))
                .awaitElement("#release_confirm")
                .check("narrow Chinese release text fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(context, "#release_consequence"))
                .check("narrow Chinese release controls stay on screen", context -> TaskThirtyThreeScenarioSupport.withinWorkspace(
                        context, "#release_consequence", "#release_cancel", "#release_confirm"))
                .screenshot("ui-chinese-narrow-release")
                .hover("#release_cancel")
                .step("cancel narrow Chinese confirmation", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#release_cancel"))
                .waitForTextContains("#processing_status", "选择样板和目标端点")
                .checkServer("narrow Chinese cancellation preserves ownership", TaskThirtyThreeWorldFixture::endpointRetained)
                .click("#tab_overview").frames(3)
                .step("select the Endpoint node", context -> TaskThirtyThreeScenarioSupport.selectEndpointNode(context, "10, -57, 13"))
                .waitUntil("narrow Endpoint panel is shown", context -> context.el("#endpoint_detail").isVisible())
                .step("reveal the Endpoint's patterns", context -> TaskThirtyThreeScenarioSupport.revealInAside(context, "#network_links_heading"))
                .frames(2)
                .check("narrow Endpoint panel text fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(context,
                        "#endpoint_fact_mode", "#endpoint_fact_owner", "#endpoint_fact_native", "#network_links_heading"))
                .check("narrow Endpoint owner navigation fits", context -> TaskThirtyThreeScenarioSupport.singleLineButtonTextFits(
                        context, "#graph_open"))
                .screenshot("ui-chinese-narrow-endpoint")
                .step("select the Endpoint's network again", context -> TaskThirtyThreeScenarioSupport.selectNetworkCard(
                        context, context.get("net.endpoint")))
                .waitUntil("the network panel is back", context -> !context.el("#endpoint_detail").isVisible())
                .click("#tab_overview").frames(3)
                .step("reveal the network's links", context -> TaskThirtyThreeScenarioSupport.revealInAside(context, ".network-link"))
                .frames(2)
                .hover(".network-link")
                .step("open the pair in the narrow layout", context ->
                        TaskThirtyThreeScenarioSupport.activateNavigation(context, ".network-link"))
                .waitUntil("narrow pair editor is shown", context -> context.el("#pair_editor").isVisible())
                .step("return the aside to the top", context -> TaskThirtyThreeScenarioSupport.revealInAside(context, "#pair_title"))
                .frames(2)
                .check("narrow pair editor fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(
                        context, "#pair_title", "#policy_section_title_0", "#policy_state_0_storage", "#policy_state_energy"))
                .check("narrow pair editor stays in workspace", context -> TaskThirtyThreeScenarioSupport.withinWorkspace(
                        context, "#topology_aside", "#policy_switch_0_storage"))
                .screenshot("ui-chinese-narrow-policy")
                .step("select a network card", TaskThirtyThreeScenarioSupport::selectFirstNetworkCard).frames(3)
                .check("narrow graph inspector fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(context,
                        "#network_identity", "#network_stat_energy", "#network_stat_types"))
                .screenshot("ui-chinese-narrow-overview")
                .closeScreen()
                // The Endpoint's own entrance opens the domain its Federation face joins.
                .server("open the Endpoint in compact Chinese viewport", TaskThirtyThreeWorldFixture::openEndpoint)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().frames(5)
                .waitForTextContains("#endpoint_fact_mode", "联邦")
                .check("compact Chinese endpoint explanation fits", context ->
                        TaskThirtyThreeScenarioSupport.wrappedTextFits(context, "#graph_selection", "#ack_status"))
                .check("compact Chinese device entrance selects its Endpoint on the topology", context ->
                        context.el("#page_overview").isVisible() && context.el("#endpoint_detail").isVisible())
                .screenshot("ui-chinese-narrow-device-endpoint")
                .closeScreen()
                .server("disconnect real Bridge in Chinese viewport", TaskThirtyThreeWorldFixture::disconnectBridgeOuterSide)
                .waitUntilServer("Chinese fixture loses outer attachment", TaskThirtyThreeWorldFixture::bridgeOuterSideMissing)
                .server("open compact Chinese Bridge diagnostics", TaskThirtyThreeWorldFixture::openBridge)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().frames(5)
                .waitForTextContains("#ack_status", "桥接器外侧")
                .checkText("#bridge_diagnostic_title", "桥接器联邦域不可用")
                .check("Chinese Bridge diagnostics retain readable text", context ->
                        TaskThirtyThreeScenarioSupport.wrappedTextFits(context, "#bridge_diagnostic_title",
                                "#bridge_diagnostic_help", "#ack_status"))
                .check("Chinese unavailable Bridge has no empty editor", context ->
                        !context.el("#workspace_tabs").isVisible() && context.el("#bridge_unavailable").isVisible()
                                && !context.el("#page_mapping").isVisible())
                .check("Chinese diagnostic contents fit the small workspace", context ->
                        TaskThirtyThreeScenarioSupport.withinWorkspace(context, "#bridge_diagnostic_title",
                                "#bridge_diagnostic_help", "#ack_status"))
                .screenshot("ui-chinese-narrow-bridge-unavailable")
                .step("restore normal viewport", context ->
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(context.mc().getWindow().getWindow(), 1600, 960))
                .waitUntil("normal viewport restored at the fixed scale", context -> context.mc().getWindow().getGuiScaledWidth() == 800)
                .closeScreen();
    }
}
