package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import java.util.Objects;
import org.lwjgl.opengl.GL11;

final class TaskFifteenScenarioSupport {
    enum Entrance {
        HUB,
        BRIDGE
    }

    private TaskFifteenScenarioSupport() {
    }

    static ScenarioBuilder open(ScenarioBuilder scenario, Entrance entrance) {
        return TaskFifteenWorldFixture.arrange(scenario)
                .server("open production Fabric policy menu", context -> {
                    if (entrance == Entrance.HUB) {
                        TaskFifteenWorldFixture.openHub(context);
                    } else {
                        TaskFifteenWorldFixture.openBridge(context);
                    }
                })
                .awaitScreen(ModularUIContainerScreen.class)
                .awaitModularUI()
                .awaitElement("#policy_toggle");
    }

    static void attachPolicy(com.lowdragmc.lowdraglib2.uitest.TestContext context, String caseId) {
        context.attach("caseId", caseId);
        var observation = TaskFifteenWorldFixture.observation(context);
        context.attach("recordIdentity", observation.hubRecordIdentity());
        context.attach("bridgeRecordIdentity", observation.bridgeRecordIdentity());
        context.attach("policyRevision", Long.toString(observation.policyRevision()));
        context.attach("policyEnabled", Boolean.toString(observation.enabled()));
        context.attach("mutationStatus", observation.mutationStatus());
        context.attach("previousRevision", Long.toString(observation.previousRevision()));
        context.attach("visibleStatus", context.el("#ack_status").text());
        context.attach("visibleRule", context.el("#rule_value").text());
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
}
