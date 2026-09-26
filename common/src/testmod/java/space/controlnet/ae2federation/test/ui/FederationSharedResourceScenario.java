package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.UI;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import com.lowdragmc.lowdraglib2.utils.XmlUtils;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.lwjgl.opengl.GL11;

@LDLRegisterClient(name = "ui-harness.shared-resource", group = "ae2federation",
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class FederationSharedResourceScenario implements UIScenario {
    private static final String CHANGED_TEXT = "Shared resource rendered after reload";

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("actual-client", "resource-reload").guiScale(3)
                .defaultTimeoutMs(15_000).scenarioTimeoutMs(90_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        scenario.openModularUI("shared resource before reload", FederationSharedResourceScenario::loadUi)
                .awaitModularUI()
                .waitForText("#shared_resource_text", "Shared resource baseline")
                .step("write shared resource override", context -> FederationUiResourceOverride.write(CHANGED_TEXT))
                .step("reload client resources", context -> {
                    if (context.firstAttempt()) {
                        context.put("resourceReload", context.mc().reloadResourcePacks());
                    }
                    CompletableFuture<Void> reload = context.get("resourceReload");
                    if (!reload.isDone()) {
                        context.repeat("client resource reload");
                    }
                })
                .openModularUI("shared resource after reload", FederationSharedResourceScenario::loadUi)
                .awaitModularUI()
                .waitForText("#shared_resource_text", CHANGED_TEXT)
                .checkText("#shared_resource_text", CHANGED_TEXT)
                .checkBounds("#shared_resource_text", bounds -> bounds.width() > 40 && bounds.height() > 0)
                .step("record shared resource control", context -> {
                    var element = context.el("#shared_resource_text");
                    context.attach("controlId", "shared_resource_text");
                    context.attach("visibleText", element.text());
                    context.attach("bounds", element.bounds().toString());
                    context.attach("guiScale", Double.toString(context.mc().getWindow().getGuiScale()));
                    context.attach("windowWidth", Integer.toString(context.mc().getWindow().getScreenWidth()));
                    context.attach("windowHeight", Integer.toString(context.mc().getWindow().getScreenHeight()));
                    context.attach("framebufferWidth", Integer.toString(context.mc().getWindow().getWidth()));
                    context.attach("framebufferHeight", Integer.toString(context.mc().getWindow().getHeight()));
                    context.attach("language", context.mc().getLanguageManager().getSelected());
                    context.attach("glVendor", Objects.toString(GL11.glGetString(GL11.GL_VENDOR), ""));
                    context.attach("glRenderer", Objects.toString(GL11.glGetString(GL11.GL_RENDERER), ""));
                    context.attach("glVersion", Objects.toString(GL11.glGetString(GL11.GL_VERSION), ""));
                })
                .screenshot("shared-resource-rendered")
                .teardown("remove resource override", context -> FederationUiResourceOverride.remove());
    }

    private static ModularUI loadUi(com.lowdragmc.lowdraglib2.uitest.TestContext context) {
        var document = Objects.requireNonNull(XmlUtils.loadXml(FederationUiFixture.XML),
                "Missing actual LDLib2 XML fixture " + FederationUiFixture.XML);
        return ModularUI.of(UI.of(document), context.requirePlayer());
    }
}
