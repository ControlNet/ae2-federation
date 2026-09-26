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
        scenario.teardown("restore English viewport", context ->
                org.lwjgl.glfw.GLFW.glfwSetWindowSize(context.mc().getWindow().getWindow(), 1600, 960));
        TaskThirtyThreeScenarioSupport.open(scenario, TaskThirtyThreeScenarioSupport.Entrance.ROUTER)
                .click("#tab_mapping").frames(3)
                .waitForTextContains("#mapping_lane_next .__selector_preview__ .choice-label", "Owned")
                .step("remember target identity", context -> context.put("target.initialId", context.el("#mapping_lane_next").value()))
                .click("#mapping_provider_next")
                .typeInto("#mapping_provider_next_search", "no such device")
                .check("device search exposes its empty state", context -> context.el("#mapping_provider_next_empty").isVisible())
                .typeInto("#mapping_provider_next_search", "10, -57, 10")
                .check("coordinate search finds provider", context -> !context.el("#mapping_provider_next_empty").isVisible())
                .screenshot("ui-provider-search")
                .click("#tab_mapping")
                .check("empty slots are hidden by default", context -> context.all("#pattern_slot_2").isEmpty())
                .typeInto("#pattern_search", "no matching pattern")
                .waitForTextContains("#pattern_empty", "No patterns match")
                .check("no-result state is visible", context -> context.el("#pattern_empty").isVisible())
                .typeInto("#pattern_search", "Gold")
                .waitUntil("search shows only matching encoded pattern", context ->
                        context.all("#pattern_slot_0").isEmpty() && !context.all("#pattern_slot_1").isEmpty())
                .typeInto("#pattern_search", "").blur()
                .click("#pattern_show_empty")
                .awaitElement("#pattern_slot_2")
                .click("#pattern_show_empty")
                .check("empty slot toggle restores encoded list", context -> context.all("#pattern_slot_2").isEmpty())
                .click("#pattern_slot_1")
                .waitForTextContains("#mapping_selection_value", "Pattern slot 1 / Endpoint ")
                .waitForTextContains("#mapping_lane_next .__selector_preview__ .choice-label", "Mapped")
                .click("#mapping_slot_next")
                .typeInto("#mapping_slot_next_search", "Diamond")
                .hover(".__selector_dialog__ .choice-slot-0 button")
                .step("choose pattern through filtered popup", context -> TaskThirtyThreeScenarioSupport.activateNavigation(
                        context, ".__selector_dialog__ .choice-slot-0 button"))
                .waitForTextContains("#mapping_selection_value", "Pattern slot 0 / Endpoint ")
                .hover("#mapping_toggle")
                .step("rapid mapping clicks show waiting and submit once", context -> {
                    var bounds = context.el("#mapping_toggle").bounds();
                    context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
                    context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
                    var waiting = context.el("#request_status").text();
                    if (!waiting.contains("Waiting for server confirmation") || context.el("#mapping_toggle").isActive()) {
                        throw new IllegalStateException("Missing request waiting text or duplicate-click guard");
                    }
                    context.attach("requestWaitingText", waiting);
                    context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
                    context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
                })
                .waitUntilServer("real Provider mapping accepted", TaskThirtyThreeWorldFixture::mappingAccepted)
                .waitForTextContains("#mapping_status", "Mapping updated for pattern slot 0.")
                .server("record authoritative mapping identity", context -> {
                    context.put("task33.providerId", TaskThirtyThreeWorldFixture.providerId(context));
                    context.put("task33.mappingLanes", TaskThirtyThreeWorldFixture.mappingLanes(context));
                })
                .step("record mapping evidence", context -> {
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.mapping");
                    context.attach("providerId", context.get("task33.providerId"));
                    context.attach("authoritativeMappingLanes", context.get("task33.mappingLanes"));
                })
                .screenshot("ui-mapping-accepted")
                .server("install component-rich and fluid processing patterns", TaskThirtyThreeWorldFixture::installRichPatterns)
                .typeInto("#pattern_search", "Calibrated Diamond")
                .awaitElement("#pattern_slot_2")
                .check("component name and long output amount survive projection", context -> {
                    var row = context.el("#pattern_slot_2").as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class);
                    var lines = row.getStyle().tooltips().asList().stream().map(net.minecraft.network.chat.Component::getString).toList();
                    return lines.stream().anyMatch(line -> line.contains("Calibrated Diamond") && line.contains("4,000,000,000"))
                            && lines.stream().anyMatch(line -> line.contains("Water") && line.contains("1.5 B"));
                })
                .typeInto("#pattern_search", "Water")
                .waitUntil("search includes secondary and primary fluid outputs", context ->
                        !context.all("#pattern_slot_2").isEmpty() && !context.all("#pattern_slot_3").isEmpty()
                                && context.all("#pattern_slot_0").isEmpty())
                .check("fluid icon retains its AE resource and exact amount", context -> {
                    var row = context.el("#pattern_slot_3").as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class);
                    var texture = row.getChildren().stream().map(child -> child.getStyle().backgroundTexture())
                            .filter(com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture.class::isInstance)
                            .map(com.lowdragmc.lowdraglib2.gui.texture.ItemStackTexture.class::cast).findFirst().orElseThrow();
                    var resource = appeng.api.stacks.GenericStack.unwrapItemStack(texture.items[0]);
                    return resource != null && resource.amount() == 2500
                            && resource.what().equals(appeng.api.stacks.AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER));
                })
                .hover("#domain_title").frames(3)
                .screenshot("ui-generic-patterns")
                .closeScreen()
                .server("open Provider mapping entrance", TaskThirtyThreeWorldFixture::openProviderMapping)
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().frames(5)
                .check("device entrance selects mapping", context -> context.el("#page_mapping").isVisible())
                .hover("#return_provider")
                .step("press Provider return navigation", context -> {
                    var bounds = context.el("#return_provider").bounds();
                    context.put("task33.returnPoint", new float[] { bounds.centerX(), bounds.centerY() });
                    context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
                })
                .step("release after navigation replaces the screen", context -> {
                    var point = context.<float[]>get("task33.returnPoint");
                    context.input().mouseUp(point[0], point[1], 0);
                })
                .awaitScreen(appeng.client.gui.implementations.PatternProviderScreen.class)
                .screenshot("ui-provider-return")
                .step("use native Provider mapping navigation", context -> {
                    var button = context.mc().screen.children().stream()
                            .filter(net.minecraft.client.gui.components.Button.class::isInstance)
                            .map(net.minecraft.client.gui.components.Button.class::cast)
                            .filter(candidate -> candidate.getMessage().getString().equals("Federation mapping"))
                            .findFirst().orElseThrow();
                    var point = new float[] {button.getX() + button.getWidth() / 2f, button.getY() + button.getHeight() / 2f};
                    context.put("task33.nativePoint", point);
                    context.input().moveTo(point[0], point[1]);
                })
                .step("press native Provider navigation", context -> {
                    var point = context.<float[]>get("task33.nativePoint");
                    context.input().mouseDown(point[0], point[1], 0);
                })
                .step("release native navigation", context -> {
                    var point = context.<float[]>get("task33.nativePoint");
                    context.input().mouseUp(point[0], point[1], 0);
                })
                .awaitScreen(com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen.class)
                .awaitModularUI().frames(5)
                .check("native navigation returns to mapping", context -> context.el("#page_mapping").isVisible())
                .screenshot("ui-provider-mapping")
                .waitUntilServer("native lane sends a real processing input", TaskThirtyThreeWorldFixture::dispatchRealWork)
                .click("#pattern_slot_1")
                .waitForTextContains("#mapping_selection_value", "Pattern slot 1 / Endpoint ")
                .click("#mapping_toggle")
                .waitUntilServer("slot one is unmapped", TaskThirtyThreeWorldFixture::slotOneUnmapped)
                .click("#pattern_slot_0")
                .waitForTextContains("#mapping_selection_value", "Pattern slot 0 / Endpoint ")
                .click("#mapping_toggle")
                .waitUntilServer("last unmap retains the claim", TaskThirtyThreeWorldFixture::endpointRetained)
                .waitForTextContains("#mapping_lane_next .__selector_preview__ .choice-label", "Retained")
                .check("target identity survives mapping state changes", context -> context.el("#mapping_lane_next").value().equals(context.get("target.initialId")))
                .step("resize English workspace", context ->
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(context.mc().getWindow().getWindow(), 960, 720))
                .waitUntil("narrow English viewport", context -> context.mc().getWindow().getGuiScaledWidth() == 320
                        && context.mc().getWindow().getGuiScaledHeight() == 240)
                .frames(5)
                .check("narrow English mapping buttons fit", context -> TaskThirtyThreeScenarioSupport.singleLineButtonTextFits(
                        context, "#mapping_toggle", "#mapping_release", "#return_provider"))
                .check("narrow English mapping text fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(
                        context, "#mapping_selection_value", "#mapping_status"))
                .check("narrow English mapping controls stay in workspace", context -> TaskThirtyThreeScenarioSupport.withinWorkspace(
                        context, "#mapping_toggle", "#mapping_release", "#mapping_feedback_scroll"))
                .screenshot("ui-english-narrow-mapping")
                .click("#tab_policy").frames(3)
                .check("narrow English policy text fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(context, "#rule_value", "#policy_direction"))
                .screenshot("ui-english-narrow-policy")
                .click("#tab_diagnostics").frames(3)
                .check("narrow English diagnostic text fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(context, "#endpoint_detail", "#endpoint_identity"))
                .screenshot("ui-english-narrow-diagnostics")
                .click("#tab_overview").frames(3)
                .check("narrow English graph action fits", context -> TaskThirtyThreeScenarioSupport.singleLineButtonTextFits(context, "#graph_open"))
                .screenshot("ui-english-narrow-overview")
                .click("#tab_mapping").frames(3)
                .hover("#mapping_release")
                .step("prepare release", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#mapping_release"))
                .awaitElement("#release_confirm")
                .checkTextContains("#release_consequence", "closes the return path")
                .checkServer("opening confirmation does not release ownership", TaskThirtyThreeWorldFixture::endpointRetained)
                .check("narrow English release text fits", context -> TaskThirtyThreeScenarioSupport.wrappedTextFits(context, "#release_consequence"))
                .check("narrow English release controls stay on screen", context -> TaskThirtyThreeScenarioSupport.withinWorkspace(
                        context, "#release_consequence", "#release_cancel", "#release_confirm"))
                .screenshot("ui-release-confirmation")
                .hover("#release_cancel")
                .step("cancel release", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#release_cancel"))
                .waitForTextContains("#mapping_status", "Choose a pattern")
                .checkServer("cancellation preserves ownership and return lane", TaskThirtyThreeWorldFixture::endpointRetained)
                .hover("#mapping_release")
                .step("prepare release again", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#mapping_release"))
                .awaitElement("#release_confirm")
                .server("external mapping edit invalidates prepared release", TaskThirtyThreeWorldFixture::toggleExternalMapping)
                .waitUntil("stale confirmation disappears", context -> context.all("#release_confirm").isEmpty())
                .checkServer("external mapping remains installed", TaskThirtyThreeWorldFixture::mappingAccepted)
                .server("remove external mapping", TaskThirtyThreeWorldFixture::toggleExternalMapping)
                .waitUntilServer("external unmap preserves dispatched work", TaskThirtyThreeWorldFixture::endpointRetained)
                .frames(5)
                .check("old confirmation does not reopen", context -> context.all("#release_confirm").isEmpty())
                .hover("#mapping_release")
                .step("prepare fresh release after external change", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#mapping_release"))
                .awaitElement("#release_confirm")
                .hover("#release_confirm")
                .step("confirm release", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#release_confirm"))
                .waitUntilServer("confirmed release removes retained ownership", TaskThirtyThreeWorldFixture::endpointReleased)
                .waitForTextContains("#mapping_status", "Endpoint released. Its return path is closed.")
                .waitForTextContains("#mapping_lane_next .__selector_preview__ .choice-label", "Unclaimed")
                .repeat(8, steps -> steps.scroll("#mapping_feedback_scroll", -1)).frames(3)
                .check("narrow English feedback can expose its final line", context -> {
                    var scroller = context.el("#mapping_feedback_scroll").as(com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView.class);
                    var text = context.el("#mapping_status").as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class);
                    return text.getPositionY() + text.getSizeHeight()
                            <= scroller.viewPort.getContentY() + scroller.viewPort.getContentHeight() + 0.01f;
                })
                .screenshot("ui-release-complete")
                .server("change selected policy outside this menu", TaskThirtyThreeWorldFixture::changeStoragePolicyExternally)
                .hover("#mapping_toggle")
                .step("submit against externally changed policy", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#mapping_toggle"))
                .waitForTextContains("#request_status", "policy changed elsewhere")
                .check("rejected request replaces waiting with its reason", context -> context.el("#request_status").isVisible())
                .checkServer("rejected stale edit leaves endpoint unclaimed", TaskThirtyThreeWorldFixture::endpointReleased)
                .screenshot("ui-request-rejected")
                .step("restore English window", context ->
                        org.lwjgl.glfw.GLFW.glfwSetWindowSize(context.mc().getWindow().getWindow(), 1600, 960))
                .waitUntil("English window restored", context -> context.mc().getWindow().getWidth() == 1600)
                .closeScreen();
    }
}
