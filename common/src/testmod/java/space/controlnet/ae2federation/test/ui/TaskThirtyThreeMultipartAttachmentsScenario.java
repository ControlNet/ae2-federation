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
        TaskThirtyThreeScenarioSupport.open(scenario, TaskThirtyThreeScenarioSupport.Entrance.BRIDGE)
                .checkText("#entrance_value", "Multipart Bridge - side north / type bridge / cable extension 5.0")
                .checkTextContains("#members_value", "2 members")
                .server("record real multipart attachment", context ->
                        context.put("task33.multipart", TaskThirtyThreeWorldFixture.multipartAttachment(context)))
                .step("record multipart evidence", context -> {
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.multipart-attachments");
                    context.attach("multipartAttachment", context.get("task33.multipart"));
                    context.attach("visibleAttachment", context.el("#entrance_value").text());
                })
                .screenshot("ui-multipart-attachments").closeScreen();
    }
}
