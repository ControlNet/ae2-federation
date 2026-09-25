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
                .hover("#domain_graph");
    }

    static void configure(com.lowdragmc.lowdraglib2.uitest.ScenarioOptions options, int guiScale) {
        options.tags("actual-client", "task-33").guiScale(guiScale)
                .defaultTimeoutMs(15_000).scenarioTimeoutMs(120_000);
    }

    static void attach(com.lowdragmc.lowdraglib2.uitest.TestContext context, String caseId) {
        context.attach("caseId", caseId);
        context.attach("mappingAck", context.el("#mapping_status").text());
        context.attach("endpointDetail", context.el("#endpoint_detail").text());
        context.attach("visibleStatus", context.el("#ack_status").text());
        context.attach("graphVisible", Boolean.toString(context.el("#domain_graph").isVisible()));
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
