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
            context.mc().getLanguageManager().setSelected("zh_cn");
            context.mc().options.languageCode = "zh_cn";
            context.mc().reloadResourcePacks();
        });
        TaskThirtyThreeScenarioSupport.open(scenario, TaskThirtyThreeScenarioSupport.Entrance.HUB)
                .waitForText("#fabric_title", "联邦关系图与加工诊断")
                .check("scale-4 Chinese labels stay inside buttons", context ->
                        TaskThirtyThreeScenarioSupport.buttonTextContained(context, "#consumer_next", "#provider_next",
                                "#capability_next", "#policy_toggle", "#mapping_provider_next",
                                "#mapping_slot_next", "#mapping_lane_next", "#mapping_toggle", "#endpoint_next"))
                .check("scale-4 paired Chinese labels fit at rendered glyph width", context ->
                        TaskThirtyThreeScenarioSupport.singleLineButtonTextFits(context,
                                "#mapping_slot_next", "#mapping_lane_next"))
                .checkBounds("#endpoint_detail", bounds -> bounds.width() > 100 && bounds.height() > 30)
                .checkBounds("#pattern_list", bounds -> bounds.width() > 100 && bounds.height() > 40)
                .check("rendered Pattern row contains native 64-bit quantity", context ->
                        TaskThirtyThreeScenarioSupport.renderedVirtualRows(context).contains("4000000000"))
                .step("record Chinese scale evidence", context -> {
                    TaskThirtyThreeScenarioSupport.attach(context, "ui.chinese-scales");
                    context.attach("largeQuantity", TaskThirtyThreeScenarioSupport.renderedVirtualRows(context));
                })
                .screenshot("ui-chinese-scale-4").closeScreen()
                .step("restore language", context -> {
                    context.mc().getLanguageManager().setSelected("en_us");
                    context.mc().options.languageCode = "en_us";
                    context.mc().reloadResourcePacks();
                });
    }
}
