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

    static void activateNavigation(com.lowdragmc.lowdraglib2.uitest.TestContext context, String selector) {
        var bounds = context.el(selector).bounds();
        context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
        context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
    }

    static void configure(com.lowdragmc.lowdraglib2.uitest.ScenarioOptions options, int guiScale) {
        options.tags("actual-client", "task-33").guiScale(guiScale)
                .defaultTimeoutMs(15_000).scenarioTimeoutMs(120_000);
    }

    static void attach(com.lowdragmc.lowdraglib2.uitest.TestContext context, String caseId) {
        context.attach("caseId", caseId);
        context.attach("mappingAck", context.el("#mapping_status").text());
        context.attach("mappingAckCode", context.el("#mapping_status").as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class)
                .getStyle().tooltips().asList().getFirst().getString());
        context.attach("endpointDetail", context.el("#endpoint_detail").text());
        context.attach("endpointIdentity", context.el("#endpoint_identity").text());
        context.attach("visibleStatus", context.el("#ack_status").text());
        context.attach("graphVisited", context.get("task33.graphVisited"));
        context.attach("workspaceVisible", Boolean.toString(context.el("#domain_root").isVisible()));
        context.attach("guiScale", Double.toString(context.mc().getWindow().getGuiScale()));
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

    static String renderedVirtualRows(com.lowdragmc.lowdraglib2.uitest.TestContext context) {
        return context.all(".virtual-row").stream().map(com.lowdragmc.lowdraglib2.uitest.ElementRef::text)
                .collect(java.util.stream.Collectors.joining("\n"));
    }
}
