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
import space.controlnet.ae2federation.automation.Installation;
import space.controlnet.ae2federation.automation.ProgramScheduler;
import space.controlnet.ae2federation.automation.ProgramStore;
import space.controlnet.ae2federation.automation.ProgramSync;
import space.controlnet.ae2federation.domain.CableCapacityLimit;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * An installation's log while its domain is stopped over cable capacity (review of 2026-11-24): a device beside the
 * Router runs a function every 20 ticks; the server's cable capacity is then set to one, which stops the Router's
 * domain, and the Install view shows what the installation did in the next three seconds. The program, the device and
 * its installation are sample content written for this scenario.
 */
@LDLRegisterClient(name = "ui.automation-domain-stopped", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class AutomationDomainStoppedScenario implements UIScenario {
    private static final String PROGRAM = "beat";
    private static final String TEXT = """
            def beat() -> int:
                return mc.time()
            """;
    private static String device = "";

    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 3);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        TaskFifteenScenarioSupport.open(scenario, TaskFifteenScenarioSupport.Entrance.ROUTER)
                .teardownServer("restore the cable capacity", context ->
                        CableCapacityLimit.set(true, CableCapacityLimit.DEFAULT_CAPACITY))
                .teardownServer("remove the device and the program", AutomationDomainStoppedScenario::remove)
                .server("place a device beside the Router", AutomationDomainStoppedScenario::place)
                .click("#tab_automation")
                .waitUntilServer("the device joins the Router's domain", context -> {
                    var devices = ComputingDevices.get(context.level());
                    device = devices.entries().entrySet().stream()
                            .filter(entry -> entry.getValue().position().equals(devicePosition(context)))
                            .map(java.util.Map.Entry::getKey).findFirst().orElse("");
                    return !device.isEmpty() && devices.domainOf(context.level(), device).isPresent();
                })
                .server("install beat every 20 ticks", context -> {
                    var scheduler = ProgramScheduler.get(context.server());
                    var installation = scheduler.install(device, PROGRAM, "beat", List.of());
                    scheduler.setTrigger(installation.id(), new Installation.Trigger.Schedule(20));
                    scheduler.setInterval(installation.id(), 20);
                    ProgramSync.get(context.server()).changed();
                })
                .waitUntil("the installation is listed", context ->
                        context.all("#auto_install_view .installation").size() == 1)
                .click("#auto_install_view .installation")
                .waitUntil("it has run", context -> context.all("#auto_install_aside .install-log-row").size() >= 2)
                .server("set the cable capacity to one", context -> CableCapacityLimit.set(true, 1))
                .waitUntilServer("the Router's domain stops", context -> {
                    var level = context.level();
                    return ComputingDevices.get(level).domainOf(level, device)
                            .map(FederationDomainRegistryAccess.get(level)::stopped).orElse(false);
                })
                .serverTicks(60)
                .frames(4).screenshot("ui-automation-domain-stopped");
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
