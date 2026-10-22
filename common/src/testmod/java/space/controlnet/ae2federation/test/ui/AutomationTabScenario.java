package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import org.lwjgl.glfw.GLFW;
import space.controlnet.ae2federation.automation.AutomationRegistration;
import space.controlnet.ae2federation.automation.ComputingDevices;
import space.controlnet.ae2federation.automation.Installation;
import space.controlnet.ae2federation.automation.LibraryRows;
import space.controlnet.ae2federation.automation.LocalCopies;
import space.controlnet.ae2federation.automation.NetworkShortIds;
import space.controlnet.ae2federation.automation.ProgramScheduler;
import space.controlnet.ae2federation.automation.ProgramStore;
import space.controlnet.ae2federation.automation.ProgramSync;
import space.controlnet.ae2federation.client.automation.ScriptEditor;
import space.controlnet.ae2federation.script.ScriptParser;
import space.controlnet.ae2federation.script.StandardLibrary;
import space.controlnet.ae2federation.script.Stmt;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.persistence.NetworkNames;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * The Automation tab's edit view (G1a, G1b): the programs the server holds, one opened in the script editor with its
 * functions listed, typing with Python's indentation and comments, the edit reaching the local copy and the server
 * without a save, Push and its refusal, a new program, edits on an older version kept beside the server's, problems
 * checked on the client and cleared by a fix, completion of functions and items, item icons in the script, the
 * Library panel (namespaces, a function's page with its example, search), the Install view (G1e: a device beside
 * the Router, a function installed on it, its row and aside, editing its arguments, gap, overlap mode and trigger
 * in place (Every, Condition, Event), Run now, Open in editor, Uninstall, a wire drawn to install another), and the
 * index
 * subscription ending when the screen closes. The programs, the device and its installation are sample content
 * written for this scenario;
 * {@code smelting} misspells a function so the list shows its problem count and Push refuses it.
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
    private static final String STOCK = """
            def keep(item: Item, min: int) -> None:
                if ae2.amount(item) < min:
                    ae2.craft(item, min - ae2.amount(item))

            def low(item: Item, min: int) -> bool:
                return ae2.amount(item) < min
            """;
    /** The sample device's ID, once it has claimed one. */
    private static String device = "";
    private static final String DOMAIN_NETWORKS = "automation.domainNetworks";
    private static final String DOMAIN_NETWORK_NAMES = "automation.domainNetworkNames";
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
                .step("clear the local copies", context -> clearLocalCopies())
                .server("write two sample programs", AutomationTabScenario::writePrograms)
                .teardownServer("remove the sample programs", AutomationTabScenario::removePrograms)
                .teardown("clear the local copies again", context -> clearLocalCopies())
                .click("#tab_programs")
                .waitUntil("the programs are listed", context -> context.all("#auto_program_iron_tools").size() == 1
                        && context.all("#auto_program_smelting").size() == 1)
                .checkServer("the open tab is subscribed", context ->
                        ProgramSync.get(context.server()).subscribed(context.player().getUUID()))
                .check("the header counts the programs", context ->
                        context.el("#automation_summary").text().startsWith("Programs 2 "))
                .check("the status line is empty before any answer", context ->
                        context.el("#automation_answer").text().isEmpty())
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
                // The graph view (G1f): a tab per function, the variables, and the cards as Layout places them.
                .waitUntil("Graph is offered once the script parses", context -> graphOffered(context))
                .click("#auto_view_graph")
                .waitUntil("the graph replaces the script, with a tab per function", context ->
                        !context.el("#auto_script").isVisible() && context.all(".function-tab").stream()
                                .map(tab -> tab.text()).toList().equals(java.util.List.of("_low", "refill", "low_iron_alarm"))
                                && context.all(".graph-card").size() > 0)
                .click("#auto_function_low_iron_alarm")
                .waitUntil("the alarm's cards: nine, three of them acting", context ->
                        context.all(".graph-card").size() == 9 && context.all(".graph-card.acts").size() == 3)
                .check("the strip lists the parameter and the variables with their types", context ->
                        context.all(".auto-variable").stream().map(chip -> chip.text()).toList()
                                .equals(java.util.List.of("lamp: Interface", "iron: Item", "now: int")))
                .frames(2).screenshot("ui-automation-graph")
                // The dry run (G1h): the server runs the shown function with its actions simulated, and each card
                // shows its value at its output. The lamp is left empty, so lighting it stops the run.
                .waitUntil("the dry run shows the stock read on its card", context ->
                        context.all(alarmDry("now")).size() == 1)
                .waitUntil("the run stops at the lamp, in red on its card and on the status line", context -> {
                    long now = Long.parseLong(context.el(alarmDry("now")).text());
                    var lamp = alarmDry(now < 64 ? "lamp on" : "lamp off");
                    return context.all(lamp).size() == 1 && context.el(lamp).text().equals("ValueError")
                            && context.all(lamp + ".error").size() == 1
                            && context.el("#automation_answer").text().equals("ValueError: no interface '' in this domain");
                })
                .check("the value taken from the stock is on its own card when that branch runs", context -> {
                    long now = Long.parseLong(context.el(alarmDry("now")).text());
                    return now >= 64 ? context.all(alarmDry("minus")).isEmpty()
                            : context.el(alarmDry("minus")).text().equals(String.valueOf(64 - now));
                })
                .check("the function's card has a row for its parameter", context ->
                        context.all("#auto_graph_param_lamp").size() == 1)
                .frames(2).screenshot("ui-automation-graph-dry")
                .click("#auto_graph_param_lamp")
                .type("lamp9")
                .key(GLFW.GLFW_KEY_ENTER)
                .waitUntil("a value typed for the parameter runs it again with that value", context ->
                        context.el("#automation_answer").text().equals("ValueError: no interface 'lamp9' in this domain"))
                .check("the script is untouched by a parameter's value", context ->
                        String.join("\n", editor(context).getLines()).equals(IRON_TOOLS))
                // Values are edited on the cards (G1f-ii) and written into the script.
                .click(alarmBound())
                .check("a click opens a field over the value, its text selected", context ->
                        context.el("#auto_graph_field").as(TextField.class).getText().equals("64"))
                .type("32")
                .frames(2).screenshot("ui-automation-graph-edit")
                .key(GLFW.GLFW_KEY_ENTER)
                .waitUntil("Enter writes the value into the script and the graph is drawn again", context ->
                        String.join("\n", editor(context).getLines()).equals(IRON_TOOLS.replace("now < 64", "now < 32"))
                                && context.all("#auto_graph_field").isEmpty() && context.all(".graph-card").size() == 9)
                .click(alarmBound())
                .type("1 +")
                .key(GLFW.GLFW_KEY_ENTER)
                .check("what is no value is refused on the status line, in red", context ->
                        context.all("#automation_answer.refused").size() == 1
                                && String.join("\n", editor(context).getLines())
                                        .equals(IRON_TOOLS.replace("now < 64", "now < 32")))
                .waitUntil("the bound is back after the next check", context -> context.all(alarmBound()).size() == 1)
                .click(alarmBound())
                .type("64")
                .key(GLFW.GLFW_KEY_ENTER)
                .waitUntil("the value goes back", context ->
                        String.join("\n", editor(context).getLines()).equals(IRON_TOOLS))
                .check("a written value clears the refusal", context ->
                        context.all("#automation_answer.refused").isEmpty())
                // A Library function dropped on the graph is a draft until it is wired (G1f-iii).
                .click("#auto_library_ns_ae2")
                .drag("#auto_library_fn_ae2_amount", "#auto_graph")
                .waitUntil("the dropped function is a dashed draft card", context ->
                        context.all(".graph-draft").size() == 1 && context.all("#auto_graph_draft_0_out").size() == 1
                                && context.all("#auto_graph_draft_0_in").isEmpty())
                .check("a draft is not in the script", context ->
                        String.join("\n", editor(context).getLines()).equals(IRON_TOOLS))
                .frames(2).screenshot("ui-automation-graph-draft")
                .drag("#auto_graph_draft_0_out", alarmPin("in"))
                .waitUntil("its value wired to a port writes the call there", context ->
                        String.join("\n", editor(context).getLines()).equals(IRON_TOOLS.replace(
                                "ae2.craft(iron, 64 - now)", "ae2.craft(iron, 64 - ae2.amount(\"\"))"))
                                && context.all(".graph-draft").isEmpty() && context.all(".graph-card").size() == 10)
                .frames(2).screenshot("ui-automation-graph-wired")
                .step("put the script back", context -> editor(context).setValue(IRON_TOOLS.split("\n", -1), true))
                .waitUntil("the alarm has nine cards again", context -> context.all(".graph-card").size() == 9)
                .click("#auto_library_ns_ae2")
                .click("#auto_library_ns_mc")
                .drag("#auto_library_fn_mc_set_redstone", "#auto_graph")
                .waitUntil("an acting draft has an exec pin", context ->
                        context.all("#auto_graph_draft_1_in").size() == 1)
                .drag("#auto_graph_draft_1_in", alarmPin("out"))
                .waitUntil("its exec pin wired to an output writes the call as a statement there", context ->
                        String.join("\n", editor(context).getLines()).equals(IRON_TOOLS.replace(
                                "    now = ae2.amount(iron)\n", "    now = ae2.amount(iron)\n    mc.set_redstone(None, 0)\n"))
                                && context.all(".graph-draft").isEmpty())
                .step("put the script back", context -> editor(context).setValue(IRON_TOOLS.split("\n", -1), true))
                .waitUntil("the alarm has nine cards again", context -> context.all(".graph-card").size() == 9)
                // Cards already in the script are wired too (G1f-iv): a statement moves, a value feeds another port.
                .drag(alarmPin("now"), alarmPin("after if"))
                .waitUntil("a statement's exec pin dropped after the if moves the statement there", context ->
                        String.join("\n", editor(context).getLines()).equals(
                                IRON_TOOLS.replace("    now = ae2.amount(iron)\n", "") + "    now = ae2.amount(iron)\n"))
                .step("put the script back", context -> editor(context).setValue(IRON_TOOLS.split("\n", -1), true))
                .waitUntil("the alarm has nine cards again", context -> context.all(".graph-card").size() == 9
                        && String.join("\n", editor(context).getLines()).equals(IRON_TOOLS))
                .drag(alarmPin("minus"), alarmPin("bound"))
                .waitUntil("a value wired to another port leaves a plain value behind", context ->
                        String.join("\n", editor(context).getLines()).equals(IRON_TOOLS
                                .replace("if now < 64:", "if now < 64 - now:")
                                .replace("ae2.craft(iron, 64 - now)", "ae2.craft(iron, 0)")))
                .frames(2).screenshot("ui-automation-graph-moved")
                .step("put the script back", context -> editor(context).setValue(IRON_TOOLS.split("\n", -1), true))
                .waitUntil("the alarm has nine cards again", context -> context.all(".graph-card").size() == 9
                        && String.join("\n", editor(context).getLines()).equals(IRON_TOOLS))
                // The × in a card's head removes it (G1f-v, user decision): a value leaves a plain value behind.
                .check("every card but the function's own has an ×", context ->
                        context.all(".graph-remove").size() == 8)
                .click(alarmPin("remove minus"))
                .waitUntil("a removed value leaves a plain value on its port", context ->
                        String.join("\n", editor(context).getLines()).equals(
                                IRON_TOOLS.replace("ae2.craft(iron, 64 - now)", "ae2.craft(iron, 0)")))
                .step("put the script back", context -> editor(context).setValue(IRON_TOOLS.split("\n", -1), true))
                .waitUntil("the alarm has nine cards again", context -> context.all(".graph-card").size() == 9
                        && String.join("\n", editor(context).getLines()).equals(IRON_TOOLS))
                .click(alarmPin("remove now"))
                .waitUntil("a removed statement goes and the rest closes up", context ->
                        String.join("\n", editor(context).getLines()).equals(
                                IRON_TOOLS.replace("    now = ae2.amount(iron)\n", "")))
                .frames(2).screenshot("ui-automation-graph-removed")
                .step("put the script back", context -> editor(context).setValue(IRON_TOOLS.split("\n", -1), true))
                .waitUntil("the alarm has nine cards again", context -> context.all(".graph-card").size() == 9
                        && String.join("\n", editor(context).getLines()).equals(IRON_TOOLS))
                .click("#auto_library_ns_ae2")
                .drag("#auto_library_fn_ae2_amount", "#auto_graph")
                .waitUntil("another draft", context -> context.all("#auto_graph_draft_2").size() == 1)
                .click("#auto_graph_draft_2_remove")
                .waitUntil("its × discards the draft", context -> context.all(".graph-draft").isEmpty())
                .check("and the script is as it was", context ->
                        String.join("\n", editor(context).getLines()).equals(IRON_TOOLS))
                .click("#auto_library_ns_ae2")
                .click("#auto_library_ns_mc")
                .click("#auto_function_refill")
                .waitUntil("another tab shows that function", context ->
                        context.all("#auto_function_refill.selected").size() == 1
                                && context.all(".graph-card").size() == 8)
                .click("#auto_view_script")
                .check("Script shows the script again", context ->
                        context.el("#auto_script").isVisible() && !context.el("#auto_graph_box").isVisible())
                // The script view shows the dry run of the function the caret is in after its lines (G1h-iv).
                .step("put the caret in the alarm", context -> editor(context).setCursor(alarmLine("now =") - 1, 0))
                .waitUntil("the alarm's lines show its dry run, the typed parameter on its def line", context -> {
                    var notes = editor(context).shownNotes();
                    var def = notes.get(alarmLine("def low_iron_alarm"));
                    return def != null && def.text().equals("lamp = 'lamp9'") && notes.values().stream()
                            .anyMatch(note -> note.error()
                                    && note.text().equals("ValueError: no interface 'lamp9' in this domain"));
                })
                .check("the value read from the stock is after its line", context ->
                        editor(context).shownNotes().get(alarmLine("now =")).text().matches("\\d+"))
                .frames(2).screenshot("ui-automation-script-dry")
                // Typing at the end: a colon indents the next line, Tab indents by four, Ctrl+/ comments with #.
                .step("put the caret on the last line", context -> {
                    var editor = editor(context);
                    editor.focus();
                    editor.setCursor(editor.getLines().size() - 1, 0);
                })
                .type("def ping() -> bool:\nreturn True\n")
                .key(GLFW.GLFW_KEY_TAB).type("x = 1")
                .key(GLFW.GLFW_KEY_SLASH, GLFW.GLFW_MOD_CONTROL)
                .check("the editor indents and comments as Python does", context -> {
                    var lines = editor(context).getLines();
                    return lines.subList(lines.size() - 3, lines.size())
                            .equals(java.util.List.of("def ping() -> bool:", "    return True", "        # x = 1"));
                })
                .check("an edited text hides the notes until it is run again", context ->
                        editor(context).shownNotes().isEmpty())
                .step("note the edited text", context -> context.put("edited", String.join("\n", editor(context).getLines())))
                .waitUntilServer("the edit reaches the server without a save", context -> {
                    var library = ProgramStore.get(context.server()).library();
                    return library.text("iron_tools").contains("def ping() -> bool:\n    return True\n");
                })
                .check("the local copy holds the edit", context ->
                        readLocal("iron_tools").equals(context.<String>get("edited")))
                .waitUntil("the new function is listed", context -> context.all(".function-row .program-name").stream()
                        .anyMatch(row -> row.text().equals("ping")))
                .click("#auto_push")
                .waitUntil("Push answers on the status line", context ->
                        context.el("#automation_answer").text().equals("pushed iron_tools"))
                .click("#auto_program_smelting")
                .waitUntil("smelting opens", context -> String.join("\n", editor(context).getLines()).equals(SMELTING))
                .click("#auto_push")
                .waitUntil("a refused push says why, in red", context -> {
                    var answer = context.el("#automation_answer");
                    return answer.text().equals("smelting line 3: ae2 has no function 'amout'")
                            && answer.as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).hasClass("refused");
                })
                .frames(2).screenshot("ui-automation-refused-push")
                .click("#auto_new")
                .typeInto("#auto_new_field", "ae2")
                .key(GLFW.GLFW_KEY_ENTER)
                .waitUntil("a reserved name is refused", context ->
                        context.el("#automation_answer").text().equals("'ae2' is a standard namespace"))
                .click("#auto_new")
                .typeInto("#auto_new_field", "scratch")
                .key(GLFW.GLFW_KEY_ENTER)
                .waitUntil("the new program opens empty", context -> context.all("#auto_program_scratch.selected").size() == 1
                        && editor(context).isVisible() && String.join("\n", editor(context).getLines()).isEmpty())
                .checkServer("the new program is saved", context ->
                        ProgramStore.get(context.server()).library().text("scratch").isEmpty())
                // Delete asks first; Cancel keeps the program.
                .step("press Delete", context -> pressAndRelease(context, "#auto_delete"))
                .waitUntil("Delete asks before deleting", context -> context.all("#auto_delete_dialog").size() == 1)
                .frames(2).screenshot("ui-automation-delete-dialog")
                .step("press cancel", context -> pressAndRelease(context, "#auto_delete_cancel"))
                .check("Cancel keeps the program", context -> context.all("#auto_delete_dialog").isEmpty()
                        && context.all("#auto_program_scratch").size() == 1)
                .step("press Delete", context -> pressAndRelease(context, "#auto_delete"))
                .waitUntil("the dialog opens again", context -> context.all("#auto_delete_confirm").size() == 1)
                .step("press confirm", context -> pressAndRelease(context, "#auto_delete_confirm"))
                .waitUntil("the confirmed delete removes the program, its tab and its local copy", context ->
                        context.all("#auto_program_scratch").isEmpty()
                                && context.el("#automation_answer").text().equals("deleted scratch")
                                && context.all(".file-tab").stream().noneMatch(tab -> tab.text().equals("scratch"))
                                && !java.nio.file.Files.exists(localFolder().resolve("scratch.ae2f")))
                .checkServer("the server no longer holds it", context ->
                        !ProgramStore.get(context.server()).library().names().contains("scratch"))
                // Edits made while the server's copy moved on are kept as a program of their own.
                .step("leave an edit on an older smelting in the local copy", context ->
                        new LocalCopies(localFolder()).edited("smelting", "# mine\n", ProgramSync.version("old\n")))
                .click("#auto_program_smelting")
                .waitUntil("the older edits become smelting_local", context ->
                        context.all("#auto_program_smelting_local").size() == 1 && context.el("#automation_answer").text()
                                .equals("Edits made on an older version are kept as smelting_local"))
                .checkServer("smelting_local holds them", context ->
                        ProgramStore.get(context.server()).library().text("smelting_local").equals("# mine\n"))
                .check("smelting shows the server's text", context ->
                        String.join("\n", editor(context).getLines()).equals(SMELTING))
                // An outside editor writes the same local files (G1g): the shown program takes its edits as if typed.
                .step("an outside editor saves smelting with CRLF line ends", context ->
                        writeLocal("smelting", SMELTING.replace("< 64", "< 32").replace("\n", "\r\n")))
                .waitUntil("the script takes the outside edit, with plain line ends", context ->
                        String.join("\n", editor(context).getLines()).equals(SMELTING.replace("< 64", "< 32")))
                .waitUntilServer("the outside edit reaches the server as if typed", context ->
                        ProgramStore.get(context.server()).library().text("smelting")
                                .equals(SMELTING.replace("< 64", "< 32")))
                .check("the local copy now has plain line ends", context ->
                        readLocal("smelting").equals(SMELTING.replace("< 64", "< 32")))
                .step("the outside editor puts smelting back", context -> writeLocal("smelting", SMELTING))
                .waitUntil("the script has it back", context ->
                        String.join("\n", editor(context).getLines()).equals(SMELTING))
                .waitUntilServer("and so has the server", context ->
                        ProgramStore.get(context.server()).library().text("smelting").equals(SMELTING))
                .step("an outside editor creates a program in the folder", context ->
                        writeLocal("pinger", "def ping() -> bool:\n    return True\n"))
                .waitUntil("the new file is listed", context -> context.all("#auto_program_pinger").size() == 1)
                .click("#auto_program_pinger")
                .waitUntil("opening it shows it", context -> context.all("#auto_program_pinger.selected").size() == 1
                        && String.join("\n", editor(context).getLines()).equals("def ping() -> bool:\n    return True\n"))
                .checkServer("and uploads it", context -> ProgramStore.get(context.server()).library().text("pinger")
                        .equals("def ping() -> bool:\n    return True\n"))
                .click("#auto_program_smelting")
                .waitUntil("smelting opens again", context ->
                        String.join("\n", editor(context).getLines()).equals(SMELTING))
                // Problems are checked on the client: marked in the editor and listed under it.
                .waitUntil("the misspelt call is listed under the editor", context ->
                        context.el("#auto_problems").isVisible()
                                && context.el("#auto_problems_title").text().equals("Problems 1")
                                && context.el("#auto_problem_list .problem-line").text().equals("3")
                                && context.el("#auto_problem_list .program-name").text()
                                        .equals("ae2 has no function 'amout'"))
                .frames(2).screenshot("ui-automation-problems")
                .step("put the caret inside the misspelt name", context -> {
                    var editor = editor(context);
                    editor.focus();
                    editor.setCursor(2, 19);
                })
                .type("n")
                .waitUntil("the fixed call clears the problems and the list's count", context ->
                        !context.el("#auto_problems").isVisible()
                                && context.all("#auto_program_smelting .program-problems").isEmpty())
                // Completion: the namespace's functions after a dot, then items inside a string.
                .step("put the caret on the empty last line", context -> {
                    var editor = editor(context);
                    editor.focus();
                    editor.setCursor(editor.getLines().size() - 1, 0);
                })
                .type("    n = ae2.")
                .waitUntil("the dot lists ae2's functions", context -> popupLabels(context).size() > 5
                        && popupLabels(context).get(0).equals("amount") && popupLabels(context).contains("craft"))
                .frames(2).screenshot("ui-automation-completion")
                .waitUntil("Graph is not offered while the script does not parse", context -> !graphOffered(context))
                .key(GLFW.GLFW_KEY_ESCAPE)
                .check("Escape closes the list and leaves the screen open", context ->
                        !editor(context).isCompleting() && editor(context).isVisible()
                                && context.all("#tab_automation").size() == 1)
                .type("cra")
                .waitUntil("typing narrows the list", context ->
                        popupLabels(context).equals(java.util.List.of("craft", "craftable", "crafting")))
                .key(GLFW.GLFW_KEY_DOWN)
                .key(GLFW.GLFW_KEY_ENTER)
                .check("Enter puts the chosen function in place of what is typed", context ->
                        lastLine(context, 0).equals("    n = ae2.craftable") && !editor(context).isCompleting())
                .key(GLFW.GLFW_KEY_END)
                .key(GLFW.GLFW_KEY_ENTER)
                .type("iron: Item = \"minecraft:iron_in")
                .check("the typed quote brought its closing one", context ->
                        lastLine(context, 0).equals("    iron: Item = \"minecraft:iron_in\""))
                .waitUntil("an item string lists items", context -> !popupLabels(context).isEmpty()
                        && popupLabels(context).get(0).equals("minecraft:iron_ingot"))
                .waitUntil("the chosen item's stock in the domain comes from the server", context ->
                        editor(context).completionPopup().footerText().matches("Item \u00b7 in stock: [0-9,]+"))
                .frames(2).screenshot("ui-automation-completion-item")
                .key(GLFW.GLFW_KEY_ENTER)
                .check("Enter puts the item in, with the caret after its string", context ->
                        lastLine(context, 0).equals("    iron: Item = \"minecraft:iron_ingot\"")
                                && editor(context).getCursorCol() == lastLine(context, 0).length())
                // The item's icon sits before its string; clicks still find each column after it.
                .frames(2).screenshot("ui-automation-item-icon")
                .check("a click finds each column of a line with an item's icon", context -> {
                    var editor = editor(context);
                    int line = editor.getLines().size() - 1;
                    while (editor.getLines().get(line).isBlank()) line--;
                    for (int column = 0; column <= editor.getLines().get(line).length(); column++) {
                        var at = editor.columnPosition(line, column);
                        var cursor = editor.getCursorUnderMouse(at.x + 0.5f, at.y + 4);
                        if (cursor.line() != line || cursor.col() != column) return false;
                    }
                    return true;
                })
                // Networks: only this domain's, each once, beside the name the topology shows for it.
                .server("note this domain's networks", AutomationTabScenario::noteDomainNetworks)
                .key(GLFW.GLFW_KEY_END)
                .key(GLFW.GLFW_KEY_ENTER)
                .type("net: Network = \"network-")
                .waitUntil("a network string lists this domain's networks, each once", context -> {
                    var labels = popupLabels(context);
                    return labels.size() == java.util.Set.copyOf(labels).size()
                            && java.util.Set.copyOf(labels).equals(context.get(DOMAIN_NETWORKS));
                })
                .check("an unnamed network's ID is the topology's \"Network 082A\"; a named one shows its name",
                        context -> {
                            var popup = editor(context).completionPopup();
                            for (int i = 0; i < popup.labels().size(); i++) {
                                var tag = popup.labels().get(i).substring("network-".length()).toUpperCase(java.util.Locale.ROOT);
                                var detail = popup.details().get(i);
                                if (!detail.equals("Network " + tag)
                                        && !context.<java.util.Set<String>>get(DOMAIN_NETWORK_NAMES).contains(detail)) {
                                    return false;
                                }
                            }
                            return true;
                        })
                .frames(2).screenshot("ui-automation-completion-network")
                .key(GLFW.GLFW_KEY_ESCAPE)
                .check("Escape closes the list", context -> !editor(context).isCompleting())
                // Ctrl+Space asks again; the space a platform may type with it stays out of the text.
                .step("press Ctrl+Space", context -> {
                    context.input().keyDown(GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_MOD_CONTROL);
                    context.input().keyDown(GLFW.GLFW_KEY_SPACE, GLFW.GLFW_MOD_CONTROL);
                    context.input().charTyped(' ', GLFW.GLFW_MOD_CONTROL);
                    context.input().keyUp(GLFW.GLFW_KEY_SPACE, GLFW.GLFW_MOD_CONTROL);
                    context.input().keyUp(GLFW.GLFW_KEY_LEFT_CONTROL, 0);
                })
                .waitUntil("Ctrl+Space lists the networks again", context ->
                        java.util.Set.copyOf(popupLabels(context)).equals(context.get(DOMAIN_NETWORKS)))
                .check("Ctrl+Space typed nothing", context -> lastLine(context, 0).equals("    net: Network = \"network-\""))
                .key(GLFW.GLFW_KEY_ENTER)
                // The Library: namespaces with their counts, a function's page and the way back, a search.
                .check("the library lists the namespaces with their counts", context ->
                        context.el("#auto_library_ns_ae2 .library-count").text().equals("12")
                                && context.el("#auto_library_ns_ae2f .library-count").text().equals("6")
                                && context.el("#auto_library_ns_mc .library-count").text().equals("13")
                                && context.all("#auto_library_tree .library-function").isEmpty())
                .click("#auto_library_ns_ae2")
                .check("opening a namespace lists its functions", context ->
                        context.all("#auto_library_tree .library-function").size() == 12
                                && context.all("#auto_library_fn_ae2_craft .library-acts").size() == 1
                                && context.all("#auto_library_fn_ae2_amount .library-acts").isEmpty())
                .frames(2).screenshot("ui-automation-library")
                .click("#auto_library_fn_ae2_craft")
                .check("a function's page shows its signature, that it acts, its summary and its example", context -> {
                    var craft = LibraryRows.find(StandardLibrary.NAMESPACES, "ae2", "craft");
                    var texts = context.all("#auto_library_doc .library-text").stream().map(text -> text.text())
                            .toList();
                    var example = context.el("#auto_library_doc .script-editor").as(ScriptEditor.class);
                    return context.el("#auto_library_doc").isVisible() && !context.el("#auto_library_tree").isVisible()
                            && context.el("#auto_library_back").text().equals("\u2039 ae2")
                            && texts.contains("ae2.craft") && texts.contains(craft.display())
                            && texts.contains("action: one per tick") && texts.contains(craft.summary())
                            && example.getLines().stream().map(String::stripTrailing).toList()
                                    .equals(craft.example().stripTrailing().lines().toList());
                })
                .frames(2).screenshot("ui-automation-library-doc")
                .click("#auto_library_back")
                .check("back returns to the list as it was", context ->
                        !context.el("#auto_library_doc").isVisible()
                                && context.all("#auto_library_tree .library-function").size() == 12)
                .step("search the library", context ->
                        context.el("#auto_library_search").as(TextField.class).setText("redstone", true))
                .check("a search lists only the matches, under their namespace", context ->
                        context.all("#auto_library_tree .library-row").size() == 3
                                && context.el("#auto_library_ns_mc .library-count").text().equals("2")
                                && context.all("#auto_library_fn_mc_redstone").size() == 1
                                && context.all("#auto_library_fn_mc_set_redstone").size() == 1)
                .frames(2).screenshot("ui-automation-library-search")
                // The Install view: a device in this domain, beside the Router, with a function installed on it.
                .click("#tab_automation")
                .check("the Automation tab shows the install view, without Push or Delete", context ->
                        context.el("#auto_install_view").isVisible() && !context.el("#auto_edit_view").isVisible()
                                && !context.el("#header_auto_tools").isVisible()
                                && context.el("#tab_automation").as(UIElement.class).hasClass("selected"))
                .check("without a device the view says so", context ->
                        context.all("#auto_install_view .install-note").size() == 1)
                .server("place a device beside the Router", AutomationTabScenario::placeDevice)
                .teardownServer("remove the device", AutomationTabScenario::removeDevice)
                .waitUntilServer("the device joins the Router's domain", context -> {
                    var devices = ComputingDevices.get(context.level());
                    device = devices.entries().entrySet().stream()
                            .filter(entry -> entry.getValue().position().equals(devicePosition(context)))
                            .map(java.util.Map.Entry::getKey).findFirst().orElse("");
                    return !device.isEmpty() && devices.domainOf(context.level(), device).isPresent();
                })
                .server("install keep on the device", context -> {
                    ProgramScheduler.get(context.server()).install(device, "stock", "keep",
                            java.util.List.of("minecraft:iron_ingot", "256"));
                    ProgramSync.get(context.server()).changed();
                })
                .waitUntil("the device and its installation are listed", context ->
                        context.all("#auto_install_device_" + device).size() == 1
                                && context.all("#auto_install_view .installation").size() == 1)
                .check("the program card counts the installation and the row names the item", context ->
                        context.el("#auto_install_program_stock .install-count").text().equals("\u2192 1")
                                && context.el("#auto_install_view .installation .install-function").text().equals("keep")
                                && context.el("#auto_install_view .install-summary").text().equals("Iron Ingot \u00b7 256"))
                .check("an unnamed device shows its ID and position", context ->
                        context.el("#auto_install_device_" + device + " .install-title").text().equals(device)
                                && context.el("#auto_install_device_" + device + " .install-device-lines .install-muted").text()
                                        .startsWith("Creative \u00b7 "))
                .click("#auto_install_view .installation")
                .check("the aside shows the arguments, trigger, gap, overlap and state", context -> {
                    var texts = context.all("#auto_install_aside .install-value-text").stream()
                            .map(text -> text.text()).toList();
                    return context.el("#auto_install_view .installation").as(UIElement.class).hasClass("selected")
                            && texts.equals(java.util.List.of("Iron Ingot", "256", "20 ticks", "Idle"))
                            && chosen(context, "#auto_install_trigger_manual")
                            && chosen(context, "#auto_install_overlap_ignore");
                })
                .frames(2).screenshot("ui-automation-install")
                // Editing in place (G1e-ii): a click opens the field, Enter sends it, the new index shows it.
                .click("#auto_install_arg_1 .install-value")
                .check("a click opens the argument's field with its text selected", context -> {
                    var field = context.el("#auto_install_arg_1 text-field").as(TextField.class);
                    return context.el("#auto_install_arg_1 text-field").isVisible() && field.getText().equals("256")
                            && field.getHighlighted().equals("256");
                })
                .step("type a new minimum", context ->
                        context.el("#auto_install_arg_1 text-field").as(TextField.class).setText("128", true))
                .key(GLFW.GLFW_KEY_ENTER)
                .waitUntil("Enter sends the arguments", context ->
                        context.el("#auto_install_view .install-summary").text().equals("Iron Ingot \u00b7 128"))
                .click("#auto_install_overlap_restart")
                .waitUntil("a chip sets what a trigger does while it runs", context ->
                        chosen(context, "#auto_install_overlap_restart"))
                .click("#auto_install_trigger_every")
                .check("Every opens its ticks field before sending anything", context ->
                        chosen(context, "#auto_install_trigger_every")
                                && context.el("#auto_install_every text-field").isVisible())
                .step("type the ticks", context ->
                        context.el("#auto_install_every text-field").as(TextField.class).setText("40", true))
                .key(GLFW.GLFW_KEY_ENTER)
                .waitUntil("Enter sends the trigger", context ->
                        context.all("#auto_install_every text-field").size() == 1
                                && context.el("#auto_install_every .install-value-text").text().equals("40 ticks"))
                .click("#auto_install_gap .install-value")
                .step("type a new gap, as short as one tick", context ->
                        context.el("#auto_install_gap text-field").as(TextField.class).setText("1", true))
                .key(GLFW.GLFW_KEY_ENTER)
                .waitUntil("the gap is sent", context ->
                        context.el("#auto_install_gap .install-value-text").text().equals("1 ticks"))
                .checkServer("the server holds every change", context -> {
                    var installation = installation(context);
                    return installation.args().equals(java.util.List.of("minecraft:iron_ingot", 128L))
                            && installation.trigger().equals(new Installation.Trigger.Schedule(40))
                            && installation.interval() == 1 && installation.overlap() == Installation.Overlap.RESTART;
                })
                .frames(2).screenshot("ui-automation-install-edit")
                .click("#auto_install_trigger_manual")
                .waitUntil("Manual is sent at once", context -> chosen(context, "#auto_install_trigger_manual")
                        && context.all("#auto_install_every").isEmpty())
                // A condition: a public function of the program giving a bool without acting, then its arguments.
                .click("#auto_install_trigger_when")
                .check("Condition lists only the functions a condition may name", context ->
                        context.all("#auto_install_choose .install-choice").size() == 1
                                && context.all("#auto_install_choice_low").size() == 1)
                .click("#auto_install_choice_low")
                .check("choosing it opens its first argument", context ->
                        context.el("#auto_install_when_arg_0 text-field").isVisible())
                .step("type the item", context -> context.el("#auto_install_when_arg_0 text-field")
                        .as(TextField.class).setText("minecraft:iron_ingot", true))
                .key(GLFW.GLFW_KEY_ENTER)
                .waitUntil("the next argument opens", context ->
                        context.all("#auto_install_when_arg_1 text-field").size() == 1
                                && context.el("#auto_install_when_arg_1 text-field").isVisible())
                .step("type the minimum", context -> context.el("#auto_install_when_arg_1 text-field")
                        .as(TextField.class).setText("64", true))
                .key(GLFW.GLFW_KEY_ENTER)
                .waitUntilServer("the condition is sent with its arguments", context ->
                        installation(context).trigger().equals(new Installation.Trigger.Condition("low",
                                java.util.List.of("minecraft:iron_ingot", 64L))))
                .waitUntil("the aside shows it", context -> chosen(context, "#auto_install_trigger_when")
                        && context.el("#auto_install_when_arg_1 .install-value-text").text().equals("64"))
                .frames(2).screenshot("ui-automation-install-condition")
                // An event: typing lists the events containing the text, Enter takes the first.
                .click("#auto_install_trigger_on")
                .check("Event opens its field with every event listed", context ->
                        context.all("#auto_install_choose .install-choice").size() == 12)
                .step("type part of a name", context -> context.el("#auto_install_choose text-field")
                        .as(TextField.class).setText("c", true))
                .frames(2).screenshot("ui-automation-install-event")
                .step("type more", context -> context.el("#auto_install_choose text-field")
                        .as(TextField.class).setText("stock", true))
                .check("the list narrows", context -> context.all("#auto_install_choose .install-choice").size() == 1
                        && context.all("#auto_install_choice_stock_changed").size() == 1)
                .key(GLFW.GLFW_KEY_ENTER)
                .waitUntil("Enter takes it and opens its resource", context ->
                        context.all("#auto_install_on_arg_0 text-field").size() == 1
                                && context.el("#auto_install_on_arg_0 text-field").isVisible())
                .step("type the resource", context -> context.el("#auto_install_on_arg_0 text-field")
                        .as(TextField.class).setText("minecraft:iron_ingot", true))
                .key(GLFW.GLFW_KEY_ENTER)
                .waitUntilServer("the event is sent", context -> installation(context).trigger().equals(
                        new Installation.Trigger.Event("stock_changed", java.util.List.of("minecraft:iron_ingot"))))
                .waitUntil("the aside shows the event", context -> chosen(context, "#auto_install_trigger_on")
                        && context.el("#auto_install_choose text-field").as(TextField.class).getText()
                                .equals("stock_changed"))
                .click("#auto_install_run")
                .waitUntil("Run now triggers it", context -> context.el("#automation_answer").text().equals("triggered"))
                .waitUntil("the log box shows the run, newest first, with its age", context -> {
                    var texts = context.all("#auto_install_aside .install-log-row .install-log-text").stream()
                            .map(text -> text.text()).toList();
                    var ages = context.all("#auto_install_aside .install-log-age").stream()
                            .map(age -> age.text()).toList();
                    return texts.size() >= 2 && texts.get(texts.size() - 1).equals("started")
                            && (texts.get(0).startsWith("done") || texts.get(0).startsWith("failed"))
                            && ages.stream().allMatch(age -> age.equals("now") || age.endsWith(" s"));
                })
                .waitUntil("the state reads idle once the run ended", context ->
                        context.el("#auto_install_state").text().equals("Idle"))
                .frames(2).screenshot("ui-automation-install-log")
                .click("#auto_install_run")
                .waitUntil("a second run is logged", context -> logRows(context) >= 4
                        && context.el("#auto_install_state").text().equals("Idle"))
                .click("#auto_install_run")
                .waitUntil("a third run is logged", context -> logRows(context) >= 6
                        && context.el("#auto_install_state").text().equals("Idle"))
                .frames(2).screenshot("ui-automation-install-log-long")
                .check("a long log scrolls in a box a few lines high, so the actions stay in view", context -> {
                    var log = context.el("#auto_install_log").bounds();
                    var run = context.el("#auto_install_run").bounds();
                    var aside = context.el("#auto_install_aside").bounds();
                    return log.height() <= 61 && run.y() + run.height() <= aside.y() + aside.height();
                })
                .click("#auto_install_open")
                .waitUntil("Open in editor shows the program on the Programs tab", context ->
                        context.el("#auto_edit_view").isVisible() && editor(context).getLines().size() > 2
                                && String.join("\n", editor(context).getLines()).equals(STOCK)
                                && context.el("#tab_programs").as(UIElement.class).hasClass("selected"))
                .check("the installed function is marked in the list", context ->
                        context.all(".function-row .program-installed").size() == 1)
                .click("#tab_automation")
                .click("#auto_install_view .installation")
                .click("#auto_install_uninstall")
                .waitUntil("Uninstall removes the row", context ->
                        context.all("#auto_install_view .installation").isEmpty()
                                && context.el("#automation_answer").text().equals("uninstalled"))
                // Drawing a wire (G1e-iv): a function dropped on a device asks for its arguments, then installs.
                .drag("#auto_install_fn_stock_low", "#auto_install_view .install-device")
                .check("the drop opens a draft with its first argument", context ->
                        context.el("#auto_install_aside .install-aside-title").text().startsWith("low \u2192 ")
                                && context.el("#auto_install_arg_0 text-field").isVisible()
                                && context.all("#auto_install_view .installation").isEmpty())
                .step("type the item", context -> context.el("#auto_install_arg_0 text-field")
                        .as(TextField.class).setText("minecraft:gold_ingot", true))
                .key(GLFW.GLFW_KEY_ENTER)
                .waitUntil("the next argument opens", context ->
                        context.all("#auto_install_arg_1 text-field").size() == 1
                                && context.el("#auto_install_arg_1 text-field").isVisible())
                .frames(2).screenshot("ui-automation-install-drag")
                .step("type the minimum", context -> context.el("#auto_install_arg_1 text-field")
                        .as(TextField.class).setText("32", true))
                .key(GLFW.GLFW_KEY_ENTER)
                .waitUntil("the installation is listed and shown", context ->
                        context.all("#auto_install_view .installation").size() == 1
                                && context.el("#auto_install_view .install-summary").text()
                                        .equals("Gold Ingot \u00b7 32")
                                && context.all("#auto_install_aside .install-aside-title").size() == 1
                                && context.el("#auto_install_aside .install-aside-title").text()
                                        .startsWith("low \u2192 "))
                .checkServer("the server installed it with its arguments", context ->
                        installation(context).function().equals("low") && installation(context).args()
                                .equals(java.util.List.of("minecraft:gold_ingot", 32L)))
                .closeScreen()
                .waitUntilServer("closing the screen unsubscribes", context ->
                        !ProgramSync.get(context.server()).subscribed(context.player().getUUID()));
    }

    /** The local copies of the test world, as the tab finds them. */
    private static Path localFolder() {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var world = minecraft.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize()
                .getFileName().toString();
        return minecraft.gameDirectory.toPath().resolve("ae2federation").resolve("programs")
                .resolve(LocalCopies.key("world", world));
    }

    private static String readLocal(String name) {
        return new LocalCopies(localFolder()).read(name).map(LocalCopies.Copy::text).orElse("");
    }

    /** Writes a local copy as an outside editor does: the file only, its base left as it was. */
    private static void writeLocal(String name, String text) {
        try {
            Files.createDirectories(localFolder());
            Files.writeString(localFolder().resolve(name + ".ae2f"), text);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void clearLocalCopies() {
        var folder = localFolder();
        if (!Files.isDirectory(folder)) return;
        try (var files = Files.list(folder)) {
            for (var file : files.toList()) Files.delete(file);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** The place of the alarm's bound, 64 in {@code now < 64}, on its graph. */
    private static String alarmBound() {
        var alarm = ScriptParser.parse(IRON_TOOLS).program().functions().stream()
                .filter(function -> function.name().equals("low_iron_alarm")).findFirst().orElseThrow();
        var condition = alarm.body().stream().filter(statement -> statement instanceof Stmt.If).map(statement ->
                ((Stmt.If) statement).condition()).findFirst().orElseThrow();
        return "#auto_graph_value_" + condition.id() + "_right";
    }

    /**
     * A pin on the alarm's graph: {@code in}, the right port of {@code 64 - now}; {@code out}, the {@code now =}
     * statement's next; {@code now}, the exec pin of the {@code now =} statement; {@code after if}, the if's
     * next; {@code minus}, the value pin of {@code 64 - now}; {@code bound}, the right port of {@code now < 64};
     * {@code remove minus} and {@code remove now}, the × of {@code 64 - now} and of the {@code now =} statement.
     */
    private static String alarmPin(String kind) {
        var alarm = ScriptParser.parse(IRON_TOOLS).program().functions().stream()
                .filter(function -> function.name().equals("low_iron_alarm")).findFirst().orElseThrow();
        var branch = (Stmt.If) alarm.body().stream().filter(statement -> statement instanceof Stmt.If).findFirst()
                .orElseThrow();
        var minus = ((Stmt.CallStmt) branch.body().get(0)).call().args().get(1).id();
        return switch (kind) {
            case "out" -> "#auto_graph_out_" + alarm.body().get(1).id() + "_next";
            case "in" -> "#auto_graph_in_" + minus + "_right";
            case "now" -> "#auto_graph_exec_" + alarm.body().get(1).id();
            case "after if" -> "#auto_graph_out_" + branch.id() + "_next";
            case "minus" -> "#auto_graph_result_" + minus;
            case "bound" -> "#auto_graph_in_" + branch.condition().id() + "_right";
            case "remove minus" -> "#auto_graph_remove_" + minus;
            case "remove now" -> "#auto_graph_remove_" + alarm.body().get(1).id();
            default -> throw new IllegalArgumentException(kind);
        };
    }

    /** The dry-run value of a card of {@code low_iron_alarm}. */
    private static String alarmDry(String kind) {
        var alarm = ScriptParser.parse(IRON_TOOLS).program().functions().stream()
                .filter(function -> function.name().equals("low_iron_alarm")).findFirst().orElseThrow();
        var branch = (Stmt.If) alarm.body().stream().filter(statement -> statement instanceof Stmt.If).findFirst()
                .orElseThrow();
        return "#auto_graph_dry_" + switch (kind) {
            case "now" -> alarm.body().get(1).id();
            case "minus" -> ((Stmt.CallStmt) branch.body().get(0)).call().args().get(1).id();
            case "lamp on" -> branch.body().get(1).id();
            case "lamp off" -> branch.orElse().body().get(0).id();
            default -> throw new IllegalArgumentException(kind);
        };
    }

    private static boolean graphOffered(com.lowdragmc.lowdraglib2.uitest.TestContext context) {
        return context.el("#auto_view_graph").as(com.lowdragmc.lowdraglib2.gui.ui.UIElement.class).isActive();
    }

    /** The line, from 1, of IRON_TOOLS that starts with {@code start} after its indentation. */
    private static int alarmLine(String start) {
        var lines = IRON_TOOLS.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].strip().startsWith(start)) return i + 1;
        }
        throw new IllegalArgumentException("no line " + start);
    }

    private static ScriptEditor editor(com.lowdragmc.lowdraglib2.uitest.TestContext context) {
        return context.el("#auto_script .script-editor").as(ScriptEditor.class);
    }

    /** The open completion list's candidates, or none. */
    private static java.util.List<String> popupLabels(com.lowdragmc.lowdraglib2.uitest.TestContext context) {
        var editor = editor(context);
        return editor.isCompleting() ? editor.completionPopup().labels() : java.util.List.of();
    }

    /** A press and release at a button's centre that may open a dialog over it, as a mouse does. */
    private static void pressAndRelease(com.lowdragmc.lowdraglib2.uitest.TestContext context, String selector) {
        var bounds = context.el(selector).bounds();
        context.input().mouseDown(bounds.centerX(), bounds.centerY(), 0);
        context.input().mouseUp(bounds.centerX(), bounds.centerY(), 0);
    }

    private static int logRows(com.lowdragmc.lowdraglib2.uitest.TestContext context) {
        return context.all("#auto_install_aside .install-log-row").size();
    }

    /** A line counted from the last non-empty one: 0 is that line, 1 the one before. */
    private static String lastLine(com.lowdragmc.lowdraglib2.uitest.TestContext context, int back) {
        var lines = new java.util.ArrayList<>(editor(context).getLines());
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) lines.remove(lines.size() - 1);
        return lines.get(lines.size() - 1 - back);
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

    /** The sample device's one installation. */
    private static Installation installation(ServerContext context) {
        return ComputingDevices.get(context.level()).installations().stream()
                .filter(found -> found.device().equals(device)).findFirst().orElseThrow();
    }

    private static boolean chosen(com.lowdragmc.lowdraglib2.uitest.TestContext context, String chip) {
        return context.el(chip).as(UIElement.class).hasClass("selected");
    }

    /** The short IDs and names of the Router's domain's networks. */
    private static void noteDomainNetworks(ServerContext context) {
        var level = context.level();
        var node = FederationDomainRegistryAccess.nodeId(level, TaskFifteenWorldFixture.routerPosition(context));
        var shortIds = NetworkShortIds.get(context.server());
        var names = NetworkNames.get(level);
        var ids = new java.util.HashSet<String>();
        var shown = new java.util.HashSet<String>();
        FederationDomainRegistryAccess.get(level).federationDomains().stream()
                .filter(domain -> domain.nodes().contains(node))
                .forEach(domain -> domain.memberships().keySet().forEach(network -> {
                    ids.add(shortIds.id(network));
                    names.name(network).ifPresent(shown::add);
                }));
        if (ids.isEmpty()) throw new IllegalStateException("the Router's domain must have networks");
        context.put(DOMAIN_NETWORKS, java.util.Set.copyOf(ids));
        context.put(DOMAIN_NETWORK_NAMES, java.util.Set.copyOf(shown));
    }

    private static BlockPos devicePosition(ServerContext context) {
        return TaskFifteenWorldFixture.routerPosition(context).east(2);
    }

    /** A third program, and a Creative Computing Device joined to the Router by a federation cable. */
    private static void placeDevice(ServerContext context) {
        try {
            ProgramStore.get(context.server()).library().save("stock", STOCK);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        ProgramSync.get(context.server()).changed();
        var router = TaskFifteenWorldFixture.routerPosition(context);
        context.level().setBlockAndUpdate(router.east(), RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
        context.level().setBlockAndUpdate(devicePosition(context), AutomationRegistration.DEVICE.get().defaultBlockState());
    }

    private static void removeDevice(ServerContext context) {
        var scheduler = ProgramScheduler.get(context.server());
        for (var installation : ComputingDevices.get(context.level()).installations()) {
            if (installation.device().equals(device)) scheduler.uninstall(installation.id());
        }
        var router = TaskFifteenWorldFixture.routerPosition(context);
        context.level().setBlockAndUpdate(devicePosition(context), Blocks.AIR.defaultBlockState());
        context.level().setBlockAndUpdate(router.east(), Blocks.AIR.defaultBlockState());
        device = "";
    }

    private static void removePrograms(ServerContext context) {
        var library = ProgramStore.get(context.server()).library();
        try {
            for (var name : new String[] {"iron_tools", "smelting", "smelting_local", "scratch", "stock", "pinger"}) {
                if (library.names().contains(name)) library.delete(name);
            }
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
