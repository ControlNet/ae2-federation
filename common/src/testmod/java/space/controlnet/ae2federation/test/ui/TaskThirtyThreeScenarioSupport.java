package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import java.util.Objects;
import org.lwjgl.opengl.GL11;

final class TaskThirtyThreeScenarioSupport {
    enum Entrance {
        ROUTER,
        BRIDGE
    }

    private TaskThirtyThreeScenarioSupport() {
    }

    static ScenarioBuilder open(ScenarioBuilder scenario, Entrance entrance) {
        return TaskThirtyThreeWorldFixture.arrange(scenario)
                .server("open Task 33 production workspace", context -> {
                    if (entrance == Entrance.ROUTER) {
                        TaskThirtyThreeWorldFixture.openRouter(context);
                    } else {
                        TaskThirtyThreeWorldFixture.openBridge(context);
                    }
                })
                .awaitScreen(ModularUIContainerScreen.class)
                .awaitModularUI()
                .awaitElement("#domain_graph")
                .click("#graph_fit")
                .hover("#domain_graph")
                .step("record visible overview", context -> context.put("task33.graphVisited", Boolean.toString(context.el("#domain_graph").isVisible())));
    }

    /** Clicks the middle of the wire from a pattern port to the only Endpoint card of the processing view. */
    static void clickWire(com.lowdragmc.lowdraglib2.uitest.TestContext context, String slot) {
        var port = context.el("#processing_port_" + slot).bounds();
        var card = context.el(".processing-endpoint").bounds();
        float x = (port.x() + port.width() - 2 + card.x()) / 2;
        float y = (port.centerY() + card.centerY()) / 2;
        context.input().mouseDown(x, y, 0);
        context.input().mouseUp(x, y, 0);
    }

    static void activateNavigation(com.lowdragmc.lowdraglib2.uitest.TestContext context, String selector) {
        var bounds = context.el(selector).bounds();
        context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
        context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
    }

    /** The workspace names an unnamed network by the first four hex digits of its identity, its card badge. */
    static String networkTag(String networkUuid) {
        return networkUuid.substring(0, 4).toUpperCase(java.util.Locale.ROOT);
    }

    static String networkName(String networkUuid) {
        return "Network " + networkTag(networkUuid);
    }

    static com.lowdragmc.lowdraglib2.uitest.ElementRef networkCard(com.lowdragmc.lowdraglib2.uitest.TestContext context,
            String networkUuid) {
        var tag = networkTag(networkUuid);
        // A card carries its network's identity as an undrawn class, which holds after a rename.
        return context.all(".graph-node-member").stream()
                .filter(candidate -> candidate.as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).hasClass("network-" + networkUuid))
                .findFirst().orElseThrow(() -> new IllegalStateException("No topology card for network " + tag));
    }

    /** The texts a network's card shows, such as its name, position and state line. */
    static java.util.List<String> cardTexts(com.lowdragmc.lowdraglib2.uitest.TestContext context, String networkUuid) {
        return networkCard(context, networkUuid).as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).allChildrenStream()
                .filter(com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.class::isInstance)
                .map(child -> ((com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement) child).getText().getString())
                .toList();
    }

    /** The energy figure a network's card shows, such as "76%", or null when it shows none. */
    static String cardPercent(com.lowdragmc.lowdraglib2.uitest.TestContext context, String networkUuid) {
        return cardTexts(context, networkUuid).stream().filter(text -> text.matches("\\d+%")).findFirst().orElse(null);
    }

    /** Selects the topology card of one network through its rendered name. */
    static void selectNetworkCard(com.lowdragmc.lowdraglib2.uitest.TestContext context, String networkUuid) {
        var bounds = networkCard(context, networkUuid).bounds();
        context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
        context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
    }

    /** Selects the topology's Endpoint node at {@code position} ("x, y, z"), as a player's click does. */
    static void selectEndpointNode(com.lowdragmc.lowdraglib2.uitest.TestContext context, String position) {
        var node = context.all(".graph-node-endpoint").stream()
                .filter(candidate -> candidate.as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).allChildrenStream()
                        .filter(com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.class::isInstance)
                        .anyMatch(text -> ((com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement) text).getText().getString()
                                .contains(position))).findFirst()
                .orElseThrow(() -> new IllegalStateException("No Endpoint node at " + position));
        var bounds = node.bounds();
        context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
        context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
    }

    /** The Endpoint panel's facts, one "name: value" line per row, as the Task 33 evidence records them. */
    static String endpointFacts(com.lowdragmc.lowdraglib2.uitest.TestContext context) {
        var lines = new java.util.ArrayList<String>();
        for (var row : context.all(".endpoint-fact")) {
            var texts = row.as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).getChildren().stream()
                    .filter(com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.class::isInstance)
                    .map(text -> ((com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement) text).getText().getString()).toList();
            if (texts.size() == 2) lines.add(texts.get(0) + ": " + texts.get(1));
        }
        return String.join("\n", lines);
    }

    /** Records the selected Endpoint's panel: its facts and the full identity its caption's tooltip names. */
    static void attachEndpoint(com.lowdragmc.lowdraglib2.uitest.TestContext context) {
        context.attach("endpointDetail", endpointFacts(context));
        context.attach("endpointIdentity", endpointIdentity(context));
    }

    /** The selected Endpoint's full identity, which its caption shows shortened and its tooltip in full. */
    static String endpointIdentity(com.lowdragmc.lowdraglib2.uitest.TestContext context) {
        return String.join("\n", tooltipLines(context, "#network_identity"));
    }

    /** The element's tooltip, one string per line. */
    static java.util.List<String> tooltipLines(com.lowdragmc.lowdraglib2.uitest.TestContext context, String selector) {
        return context.el(selector).as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).getStyle().tooltips().asList()
                .stream().map(net.minecraft.network.chat.Component::getString).toList();
    }

    /** Whether the element has no tooltip at all. */
    static boolean noTooltip(com.lowdragmc.lowdraglib2.uitest.TestContext context, String selector) {
        return context.el(selector).as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).getStyle().tooltips().asList().isEmpty();
    }

    static void selectFirstNetworkCard(com.lowdragmc.lowdraglib2.uitest.TestContext context) {
        var bounds = context.all(".graph-node-member").getFirst().bounds();
        context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
        context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
    }

    static boolean networkCardCentered(com.lowdragmc.lowdraglib2.uitest.TestContext context, String networkUuid) {
        var viewport = context.el("#domain_graph").bounds();
        var card = networkCard(context, networkUuid).bounds();
        return viewport.width() > 0 && viewport.height() > 0
                && Math.abs(viewport.centerX() - card.centerX()) < 1
                && Math.abs(viewport.centerY() - card.centerY()) < 1;
    }

    /**
     * A pair-editor control for the rule {@code consumer uses the other network's capability}. Section titles name the
     * consumer first in every language, so its tag sits right after the short localized "Network" prefix.
     */
    static String ruleControl(com.lowdragmc.lowdraglib2.uitest.TestContext context, String kind, String consumerUuid,
            String capability) {
        // Energy is one switch per pair, in its own section.
        if (capability.equals("me_power")) return "#policy_" + kind + "_energy";
        var index = context.el("#policy_section_title_0").text().indexOf(networkTag(consumerUuid));
        var section = index >= 0 && index <= 12 ? 0 : 1;
        return "#policy_" + kind + "_" + section + "_" + capability;
    }

    /** Whether any tooltip line of the element contains {@code text}, such as the device counts on "Devices (N)". */
    static boolean tooltipContains(com.lowdragmc.lowdraglib2.uitest.TestContext context, String selector, String text) {
        return context.el(selector).as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).getStyle().tooltips().asList()
                .stream().anyMatch(line -> line.getString().contains(text));
    }

    static String ruleState(com.lowdragmc.lowdraglib2.uitest.TestContext context, String capability) {
        return context.el(ruleControl(context, "state", context.get("net.providerHost"), capability)).text();
    }

    /** Scrolls the topology aside so a pair-editor row is in view for screenshots. */
    static void revealRule(com.lowdragmc.lowdraglib2.uitest.TestContext context, String capability) {
        revealInAside(context, ruleControl(context, "row", context.get("net.providerHost"), capability));
    }

    /** Scrolls the topology aside so the element matching {@code selector} is at the top of the viewport. */
    static void revealInAside(com.lowdragmc.lowdraglib2.uitest.TestContext context, String selector) {
        var row = context.el(selector).as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class);
        var aside = context.el("#topology_aside").as(com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView.class);
        // The vertical scroller is normalized over the overflow between the content and its viewport.
        float overflow = aside.viewContainer.getSizeHeight() - aside.viewPort.getContentHeight();
        float top = row.getPositionY() - aside.viewContainer.getPositionY();
        aside.verticalScroller.setValue(overflow <= 0 ? 0f : Math.min(1f, Math.max(0f, top / overflow)));
    }

    static void configure(com.lowdragmc.lowdraglib2.uitest.ScenarioOptions options, int guiScale) {
        options.tags("actual-client", "task-33").guiScale(guiScale)
                .defaultTimeoutMs(15_000).scenarioTimeoutMs(120_000);
    }

    static void attach(com.lowdragmc.lowdraglib2.uitest.TestContext context, String caseId) {
        context.attach("caseId", caseId);
        context.attach("mappingAck", context.el("#processing_status").text());
        context.attach("mappingAckCode", context.el("#processing_status").as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class)
                .getStyle().tooltips().asList().getFirst().getString());
        context.attach("visibleStatus", context.el("#ack_status").text());
        context.attach("graphVisited", context.get("task33.graphVisited"));
        context.attach("workspaceVisible", Boolean.toString(context.el("#domain_root").isVisible()));
        // The player's option, and the scale the Federation screen actually uses (fixed by window size).
        context.attach("guiScale", Double.toString(context.mc().options.guiScale().get()));
        context.attach("effectiveGuiScale", Double.toString(context.mc().getWindow().getGuiScale()));
        context.attach("windowWidth", Integer.toString(context.mc().getWindow().getScreenWidth()));
        context.attach("windowHeight", Integer.toString(context.mc().getWindow().getScreenHeight()));
        context.attach("framebufferWidth", Integer.toString(context.mc().getWindow().getWidth()));
        context.attach("framebufferHeight", Integer.toString(context.mc().getWindow().getHeight()));
        context.attach("language", context.mc().getLanguageManager().getSelected());
        context.attach("glVendor", Objects.toString(GL11.glGetString(GL11.GL_VENDOR), ""));
        context.attach("glRenderer", Objects.toString(GL11.glGetString(GL11.GL_RENDERER), ""));
        context.attach("glVersion", Objects.toString(GL11.glGetString(GL11.GL_VERSION), ""));
    }

    static boolean wrappedTextFits(com.lowdragmc.lowdraglib2.uitest.TestContext context, String... selectors) {
        for (var selector : selectors) {
            var text = context.el(selector).as(com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement.class);
            var style = text.getTextStyle();
            var lines = com.lowdragmc.lowdraglib2.utils.TextUtilities.computeFormattedLines(text.getFont(),
                    com.lowdragmc.lowdraglib2.utils.TextUtilities.withFont(text.getText(), style.font()),
                    style.fontSize(), text.getContentWidth());
            float height = lines.size() * (style.fontSize() + style.lineSpacing()) - style.lineSpacing();
            if (height > text.getContentHeight() + 0.01f
                    || lines.stream().anyMatch(line -> line.getB() > text.getContentWidth() + 0.01f)) return false;
        }
        return true;
    }

    static boolean graphNodeCentered(com.lowdragmc.lowdraglib2.uitest.TestContext context, String id) {
        var viewport = context.el("#domain_graph").bounds();
        var node = context.el("#graph_node_" + id.replaceAll("[^a-zA-Z0-9_-]", "_")).bounds();
        return viewport.width() > 0 && viewport.height() > 0
                && Math.abs(viewport.centerX() - node.centerX()) < 1
                && Math.abs(viewport.centerY() - node.centerY()) < 1;
    }

    static boolean withinWorkspace(com.lowdragmc.lowdraglib2.uitest.TestContext context, String... selectors) {
        var root = context.el("#domain_root").bounds();
        for (var selector : selectors) {
            var bounds = context.el(selector).bounds();
            if (bounds.x() < root.x() || bounds.y() < root.y()
                    || bounds.x() + bounds.width() > root.x() + root.width() + 0.01f
                    || bounds.y() + bounds.height() > root.y() + root.height() + 0.01f) return false;
        }
        return true;
    }

    static boolean buttonTextContained(com.lowdragmc.lowdraglib2.uitest.TestContext context, String... selectors) {
        for (var selector : selectors) {
            var button = context.el(selector).as(Button.class);
            var text = button.text;
            if (text.getPositionX() < button.getContentX()
                    || text.getPositionX() + text.getSizeWidth() > button.getContentX() + button.getContentWidth()
                    || text.getPositionY() < button.getContentY()
                    || text.getPositionY() + text.getSizeHeight() > button.getContentY() + button.getContentHeight()) {
                return false;
            }
        }
        return true;
    }

    static boolean singleLineButtonTextFits(com.lowdragmc.lowdraglib2.uitest.TestContext context, String... selectors) {
        for (var selector : selectors) {
            var text = context.el(selector).as(Button.class).text;
            var renderedWidth = text.getFont().width(text.getText()) * text.getTextStyle().fontSize()
                    / text.getFont().lineHeight;
            if (renderedWidth > text.getContentWidth()) {
                return false;
            }
        }
        return true;
    }
}
