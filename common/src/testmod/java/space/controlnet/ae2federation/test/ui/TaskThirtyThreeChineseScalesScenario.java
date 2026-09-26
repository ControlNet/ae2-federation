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
                .waitForText("#domain_title", "ME联邦域管理")
                .typeInto("#graph_search", "10, -57, 10")
                .click(".graph-object-row")
                .checkTextContains("#graph_selection", "最近目标检查：策略未允许访问")
                .check("localized provider observation fits compact inspector", context ->
                        TaskThirtyThreeScenarioSupport.wrappedTextFits(context, "#graph_selection"))
                .hover("#domain_title").frames(3).screenshot("ui-chinese-provider-observation")
                .typeInto("#graph_search", "端点")
                .click(".graph-object-row")
                .checkTextContains("#graph_selection", "端点")
                .check("compact graph detail fits its bounds", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(context, "#graph_selection"))
                .check("compact graph inspector action remains inside workspace", context -> {
                    var button = context.el("#graph_open").bounds();
                    var root = context.el("#domain_root").bounds();
                    return button.y() + button.height() <= root.y() + root.height();
                })
                .hover("#domain_title").frames(3).screenshot("ui-chinese-graph-scale-4")
                .click("#tab_diagnostics")
                .waitForTextContains("#endpoint_detail", "配置模式：联邦")
                .checkTextContains("#endpoint_identity", "归属世代：1")
                .check("compact diagnostics text fits its bounds", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(
                        context, "#endpoint_detail", "#endpoint_identity"))
                .hover("#domain_title").frames(3).screenshot("ui-chinese-diagnostics-scale-4")
                .click("#tab_mapping").frames(3)
                .check("Chinese mapping action labels fit", context ->
                        TaskThirtyThreeScenarioSupport.singleLineButtonTextFits(context, "#mapping_toggle", "#mapping_release"))
                .checkBounds("#pattern_list", bounds -> bounds.width() > 100 && bounds.height() > 40)
                .check("rendered Pattern row contains native 64-bit quantity", context ->
                        TaskThirtyThreeScenarioSupport.renderedVirtualRows(context).contains("4,000,000,000"))
                .step("record Chinese scale evidence", context -> {
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.chinese-scales");
                    context.attach("largeQuantity", TaskThirtyThreeScenarioSupport.renderedVirtualRows(context));
                })
                .screenshot("ui-chinese-scale-4")
                .waitUntilServer("native lane sends a real processing input", TaskThirtyThreeWorldFixture::dispatchRealWork)
                .click("#pattern_slot_1")
                .waitForTextContains("#mapping_selection_value", "样板槽位 1")
                .click("#mapping_toggle")
                .waitUntilServer("Chinese scene retains unmapped endpoint", TaskThirtyThreeWorldFixture::endpointRetained)
                .hover("#mapping_release")
                .step("open Chinese release confirmation", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#mapping_release"))
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
                .waitForTextContains("#mapping_status", "选择样板和目标端点")
                .checkServer("Chinese cancellation keeps endpoint owned", TaskThirtyThreeWorldFixture::endpointRetained)
                .step("resize to minimum supported logical width", context ->
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(context.mc().getWindow().getWindow(), 1280, 960))
                .waitUntil("320 by 240 logical viewport", context -> context.mc().getWindow().getGuiScaledWidth() == 320
                        && context.mc().getWindow().getGuiScaledHeight() == 240)
                .frames(5)
                .check("narrow mapping action labels fit", context -> TaskThirtyThreeScenarioSupport.singleLineButtonTextFits(
                        context, "#mapping_toggle", "#mapping_release"))
                .check("narrow mapping feedback fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(
                        context, "#mapping_status", "#mapping_selection_value"))
                .screenshot("ui-chinese-narrow-mapping")
                .hover("#mapping_release")
                .step("open narrow Chinese confirmation", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#mapping_release"))
                .awaitElement("#release_confirm")
                .check("narrow Chinese release text fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(context, "#release_consequence"))
                .check("narrow Chinese release controls stay on screen", context -> TaskThirtyThreeScenarioSupport.withinWorkspace(
                        context, "#release_consequence", "#release_cancel", "#release_confirm"))
                .screenshot("ui-chinese-narrow-release")
                .hover("#release_cancel")
                .step("cancel narrow Chinese confirmation", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#release_cancel"))
                .waitForTextContains("#mapping_status", "选择样板和目标端点")
                .checkServer("narrow Chinese cancellation preserves ownership", TaskThirtyThreeWorldFixture::endpointRetained)
                .click("#tab_diagnostics").frames(3)
                .check("narrow diagnostics fit", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(
                        context, "#endpoint_detail", "#endpoint_identity"))
                .repeat(12, steps -> steps.scroll("#endpoint_operation_scroll", -1)).frames(3)
                .check("narrow diagnostics can expose native network details", context -> {
                    var scroller = context.el("#endpoint_operation_scroll").as(com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView.class);
                    var text = context.el("#endpoint_detail").as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class);
                    return text.getPositionY() + text.getSizeHeight()
                            <= scroller.viewPort.getContentY() + scroller.viewPort.getContentHeight() + 0.01f;
                })
                .repeat(12, steps -> steps.scroll("#endpoint_identity_scroll", -1)).frames(3)
                .check("narrow diagnostics can expose the final identity line", context -> {
                    var scroller = context.el("#endpoint_identity_scroll").as(com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView.class);
                    var text = context.el("#endpoint_identity").as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class);
                    return text.getPositionY() + text.getSizeHeight()
                            <= scroller.viewPort.getContentY() + scroller.viewPort.getContentHeight() + 0.01f;
                })
                .check("narrow diagnostic navigation controls fit", context -> TaskThirtyThreeScenarioSupport.withinWorkspace(context,
                        "#endpoint_next", "#endpoint_browse", "#endpoint_locate", "#endpoint_mapping", "#endpoint_policy")
                        && TaskThirtyThreeScenarioSupport.singleLineButtonTextFits(context, "#endpoint_browse", "#endpoint_locate", "#endpoint_mapping", "#endpoint_policy"))
                .screenshot("ui-chinese-narrow-diagnostics")
                .hover("#endpoint_browse")
                .step("open narrow Chinese endpoint table", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#endpoint_browse"))
                .awaitElement("#endpoint_table_search")
                .typeInto("#endpoint_table_search", "联邦")
                .check("localized mode search finds the endpoint", context -> context.all(".endpoint-table-row").size() == 1)
                .check("narrow endpoint overview fits", context -> TaskThirtyThreeScenarioSupport.withinWorkspace(
                        context, "#endpoint_table_search", "#endpoint_table", "#endpoint_browser_close"))
                .check("narrow table columns and reason fit", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(context,
                        ".endpoint-table-row .endpoint-table-position", ".endpoint-table-row .endpoint-table-modes",
                        ".endpoint-table-row .endpoint-table-owner", ".endpoint-table-reason"))
                .screenshot("ui-chinese-narrow-endpoint-table")
                .hover("#endpoint_browser_close")
                .step("close endpoint overview", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#endpoint_browser_close"))
                .click("#tab_policy").frames(3)
                .check("narrow policy detail fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(
                        context, "#rule_value", "#policy_direction"))
                .screenshot("ui-chinese-narrow-policy")
                .hover("#policy_browse")
                .step("browse rules in narrow Chinese layout", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#policy_browse"))
                .awaitElement("#policy_rule_search")
                .typeInto("#policy_rule_search", "加工")
                .waitUntil("localized rule search finds processing", context -> context.all(".policy-rule-row").size() == 1)
                .check("narrow rule browser controls fit", context -> TaskThirtyThreeScenarioSupport.withinWorkspace(
                        context, "#policy_rule_search", "#policy_rule_list", "#policy_browser_close"))
                .check("narrow browser close label fits", context -> TaskThirtyThreeScenarioSupport.singleLineButtonTextFits(context, "#policy_browser_close"))
                .screenshot("ui-chinese-narrow-rules")
                .hover("#policy_browser_close")
                .step("close narrow rule browser", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#policy_browser_close"))
                .click("#tab_overview").frames(3)
                .check("narrow graph inspector fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(context, "#graph_selection"))
                .screenshot("ui-chinese-narrow-overview")
                .closeScreen()
                .server("open local Endpoint in compact Chinese viewport", TaskThirtyThreeWorldFixture::openEndpoint)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().frames(5)
                .waitForTextContains("#ack_status", "有多个候选联邦域")
                .checkTextContains("#endpoint_local", "本地端点 · 10, -57, 13")
                .check("compact Chinese local identity and explanation fit", context ->
                        TaskThirtyThreeScenarioSupport.wrappedTextFits(context, "#endpoint_local", "#diagnostics_description", "#ack_status"))
                .check("compact Chinese local label replaces the selector", context ->
                        context.el("#endpoint_local").isVisible() && !context.el("#endpoint_next").isVisible())
                .screenshot("ui-chinese-narrow-local-endpoint")
                .step("restore normal viewport", context ->
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(context.mc().getWindow().getWindow(), 1600, 960))
                .waitUntil("normal viewport restored", context -> context.mc().getWindow().getGuiScaledWidth() == 400)
                .closeScreen();
    }
}
