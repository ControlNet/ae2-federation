package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import space.controlnet.ae2federation.automation.AutomationRegistration;
import space.controlnet.ae2federation.automation.ComputingDevices;
import space.controlnet.ae2federation.automation.ProgramScheduler;
import space.controlnet.ae2federation.automation.ProgramStore;
import space.controlnet.ae2federation.automation.ProgramSync;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * CPython's whitespace and repr in a program's log (review of 2026-11-24): a function splits a text whose words are
 * parted by a full-width space (U+3000) and a no-break space (U+00A0), and lists the no-break space; the
 * Install view shows its result after Run now. The program, the device and its installation are sample content
 * written for this scenario.
 */
@LDLRegisterClient(name = "ui.automation-strings", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class AutomationStringsScenario implements UIScenario {
    private static final String PROGRAM = "words";
    private static final String TEXT = """
            def words() -> str:
                text = "iron\u3000gold\u00a0copper"
                return str(text.split()) + " " + str([text[9]])
            """;
    private static String device = "";

    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 3);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskFifteenScenarioSupport.open(scenario, TaskFifteenScenarioSupport.Entrance.ROUTER)
                .teardownServer("remove the device and the program", AutomationStringsScenario::remove)
                .server("place a device beside the Router", AutomationStringsScenario::place)
                .click("#tab_automation")
                .waitUntilServer("the device joins the Router's domain", context -> {
                    var devices = ComputingDevices.get(context.level());
                    device = devices.entries().entrySet().stream()
                            .filter(entry -> entry.getValue().position().equals(devicePosition(context)))
                            .map(java.util.Map.Entry::getKey).findFirst().orElse("");
                    return !device.isEmpty() && devices.domainOf(context.level(), device).isPresent();
                })
                .server("install words", context -> {
                    ProgramScheduler.get(context.server()).install(device, PROGRAM, "words", List.of());
                    ProgramSync.get(context.server()).changed();
                })
                .waitUntil("the installation is listed", context ->
                        context.all("#auto_install_view .installation").size() == 1)
                .click("#auto_install_view .installation")
                .waitUntil("the aside shows Run now", context -> context.all("#auto_install_run").size() == 1
                        && context.el("#auto_install_run").isVisible())
                .frames(2)
                .click("#auto_install_run")
                .waitUntil("it has run", context -> context.all("#auto_install_aside .install-log-row").size() >= 2)
                .frames(4).screenshot("ui-automation-strings");
    }

    private static BlockPos devicePosition(ServerContext context) {
        return TaskFifteenWorldFixture.routerPosition(context).east(2);
    }

    private static void place(ServerContext context) {
        try {
            ProgramStore.get(context.server()).library().save(PROGRAM, TEXT);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        ProgramSync.get(context.server()).changed();
        var router = TaskFifteenWorldFixture.routerPosition(context);
        context.level().setBlockAndUpdate(router.east(), RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
        context.level().setBlockAndUpdate(devicePosition(context), AutomationRegistration.DEVICE.get().defaultBlockState());
    }

    private static void remove(ServerContext context) {
        var scheduler = ProgramScheduler.get(context.server());
        for (var installation : ComputingDevices.get(context.level()).installations()) {
            if (installation.device().equals(device)) scheduler.uninstall(installation.id());
        }
        var router = TaskFifteenWorldFixture.routerPosition(context);
        context.level().setBlockAndUpdate(devicePosition(context), Blocks.AIR.defaultBlockState());
        context.level().setBlockAndUpdate(router.east(), Blocks.AIR.defaultBlockState());
        device = "";
        var library = ProgramStore.get(context.server()).library();
        try {
            if (library.names().contains(PROGRAM)) library.delete(PROGRAM);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
