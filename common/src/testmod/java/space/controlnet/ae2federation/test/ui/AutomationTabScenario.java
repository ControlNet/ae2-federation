package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import java.io.IOException;
import java.io.UncheckedIOException;
import space.controlnet.ae2federation.automation.ProgramStore;
import space.controlnet.ae2federation.automation.ProgramSync;
import space.controlnet.ae2federation.client.automation.ScriptEditor;

/**
 * The Automation tab's edit view (G1a): the programs the server holds, one opened in the script editor with its
 * functions listed, and the index subscription ending when the screen closes. The two programs are sample content
 * written for this scenario; {@code smelting} misspells a function so the list shows its problem count.
 */
@LDLRegisterClient(name = "ui.automation", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class AutomationTabScenario implements UIScenario {
    private static final String IRON_TOOLS = """
            # Keep the workshop stocked.
            def _low(name: Item, min: int) -> bool:
                return ae2.amount(name) < min

            def refill(name: Item, min: int, lamp: Interface) -> None:
                \"""Craft more when the stock runs low.\"""
                if _low(name, min):
                    ae2.craft(name, min - ae2.amount(name))
                    mc.set_redstone(lamp, 15)
                else:
                    mc.set_redstone(lamp, 0)

            def low_iron_alarm(lamp: Interface) -> None:
                iron: Item = "minecraft:iron_ingot"
                now = ae2.amount(iron)
                if now < 64:
                    ae2.craft(iron, 64 - now)
                    mc.set_redstone(lamp, 15)
                else:
                    mc.set_redstone(lamp, 0)
            """;
    private static final String SMELTING = """
            # Keep a stack of raw iron ready.
            def raw_iron_low() -> bool:
                return ae2.amout("minecraft:raw_iron") < 64
            """;

    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 3);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskFifteenScenarioSupport.open(scenario, TaskFifteenScenarioSupport.Entrance.ROUTER)
                .server("write two sample programs", AutomationTabScenario::writePrograms)
                .teardownServer("remove the sample programs", AutomationTabScenario::removePrograms)
                .click("#tab_automation")
                .waitUntil("the programs are listed", context -> context.all("#auto_program_iron_tools").size() == 1
                        && context.all("#auto_program_smelting").size() == 1)
                .checkServer("the open tab is subscribed", context ->
                        ProgramSync.get(context.server()).subscribed(context.player().getUUID()))
                .check("the header counts the programs", context ->
                        context.el("#automation_summary").text().startsWith("Programs 2 "))
                .check("the misspelt program shows its problem count", context ->
                        context.el("#auto_program_smelting .program-problems").text().equals("1"))
                .check("no script is shown before a program is opened", context ->
                        !context.el(".script-editor").isVisible())
                .click("#auto_program_iron_tools")
                .waitUntil("the script arrives", context -> editor(context).getLines().size() > 10)
                .check("the script is the server's text", context ->
                        String.join("\n", editor(context).getLines()).equals(IRON_TOOLS))
                .check("the open file lists its functions, the private one too", context -> {
                    var names = context.all(".function-row .program-name").stream().map(row -> row.text()).toList();
                    return names.equals(java.util.List.of("_low", "refill", "low_iron_alarm"));
                })
                .check("the opened file has a tab", context -> context.all(".file-tab").size() == 1)
                .frames(2).screenshot("ui-automation-script")
                .closeScreen()
                .waitUntilServer("closing the screen unsubscribes", context ->
                        !ProgramSync.get(context.server()).subscribed(context.player().getUUID()));
    }

    private static ScriptEditor editor(com.lowdragmc.lowdraglib2.uitest.TestContext context) {
        return context.el(".script-editor").as(ScriptEditor.class);
    }

    private static void writePrograms(ServerContext context) {
        var library = ProgramStore.get(context.server()).library();
        try {
            library.save("iron_tools", IRON_TOOLS);
            library.save("smelting", SMELTING);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        ProgramSync.get(context.server()).changed();
    }

    private static void removePrograms(ServerContext context) {
        var library = ProgramStore.get(context.server()).library();
        try {
            for (var name : new String[] {"iron_tools", "smelting"}) {
                if (library.names().contains(name)) library.delete(name);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
