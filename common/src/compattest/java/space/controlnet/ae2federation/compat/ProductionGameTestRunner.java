package space.controlnet.ae2federation.compat;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestBatch;
import net.minecraft.gametest.framework.GameTestBatchFactory;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestRegistry;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.gametest.framework.GameTestTicker;
import net.minecraft.gametest.framework.MultipleTestTracker;
import net.minecraft.gametest.framework.StructureGridSpawner;
import net.minecraft.gametest.framework.TestFunction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHooks;
import org.slf4j.Logger;

/**
 * Runs the selected compatibility GameTests in an ordinary (production) dedicated server, then stops it.
 *
 * <p>NeoForge registers and ticks GameTests only outside production, so this registers the test methods with vanilla's
 * {@link GameTestRegistry} itself and ticks {@link GameTestTicker} from the server tick. Each test is its own batch, so
 * tests run one after another and never share the level with a running test. The report is a JSON file; the server log
 * also gets vanilla's summary lines.
 */
final class ProductionGameTestRunner {
    private static final Logger LOGGER = LogUtils.getLogger();
    // Near the origin on a superflat level's surface: some mods keep positions as floats, which lose sub-block
    // precision millions of blocks out.
    private static final BlockPos ORIGIN = new BlockPos(0, -59, 0);

    private final List<String> requested;
    private final Path report;
    private MultipleTestTracker tracker;
    private List<GameTestInfo> infos = List.of();
    private long started;

    private ProductionGameTestRunner(List<String> requested, Path report) {
        this.requested = requested;
        this.report = report;
    }

    /** Starts the run when {@code ae2federation.compat.tests} names tests or groups; {@code all} selects every test. */
    static void registerIfRequested() {
        var selection = System.getProperty("ae2federation.compat.tests", "").trim();
        if (selection.isEmpty()) return;
        var report = Path.of(System.getProperty("ae2federation.compat.report", "compat-report.json"));
        var requested = Arrays.stream(selection.split(",")).map(String::trim).filter(id -> !id.isEmpty())
                .map(id -> id.toLowerCase(Locale.ROOT)).distinct().toList();
        var runner = new ProductionGameTestRunner(requested, report);
        NeoForge.EVENT_BUS.addListener(runner::onServerStarted);
        NeoForge.EVENT_BUS.addListener(runner::onServerTick);
        NeoForge.EVENT_BUS.addListener(runner::onServerStopping);
        NeoForge.EVENT_BUS.addListener(runner::onServerStopped);
    }

    private void onServerStarted(ServerStartedEvent event) {
        var server = event.getServer();
        try {
            var functions = select(CompatTestClasses.groups());
            var level = server.overworld();
            var rules = level.getGameRules();
            rules.getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.setWeatherParameters(20_000_000, 20_000_000, false, false);
            var batches = new ArrayList<GameTestBatch>();
            for (int index = 0; index < functions.size(); index++) {
                var info = GameTestBatchFactory.toGameTestInfo(functions.get(index), 0, level);
                batches.add(GameTestBatchFactory.toGameTestBatch(List.of(info), functions.get(index).testName(), index));
            }
            var runner = GameTestRunner.Builder.fromBatches(batches, level)
                    .newStructureSpawner(new StructureGridSpawner(ORIGIN, 8, false)).build();
            infos = List.copyOf(runner.getTestInfos());
            tracker = new MultipleTestTracker(infos);
            started = System.nanoTime();
            LOGGER.info("AE2F_COMPAT_START tests={}", functions.stream().map(TestFunction::testName).toList());
            runner.start();
        } catch (RuntimeException exception) {
            LOGGER.error("AE2F_COMPAT_SETUP_FAILED", exception);
            writeReport(server, exception.toString());
            server.halt(false);
        }
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (tracker == null) return;
        // Outside production NeoForge already ticks the GameTest ticker.
        if (!GameTestHooks.isGametestEnabled()) {
            try {
                GameTestTicker.SINGLETON.tick();
            } catch (RuntimeException exception) {
                // A test that throws something other than an assertion would stop the server; it fails instead, and
                // the run goes on with the next test.
                LOGGER.error("AE2F_COMPAT_TEST_THREW", exception);
                infos.stream().filter(info -> info.hasStarted() && !info.isDone()).forEach(info -> info.fail(exception));
            }
        }
        if (!tracker.isDone()) return;
        var server = event.getServer();
        LOGGER.info("========= {} GAME TESTS COMPLETE IN {} ms ======================", tracker.getTotalCount(),
                (System.nanoTime() - started) / 1_000_000);
        if (tracker.hasFailedRequired()) {
            LOGGER.info("{} required tests failed :(", tracker.getFailedRequiredCount());
            tracker.getFailedRequired().forEach(info -> LOGGER.info("   - {}", info.getTestName()));
        } else {
            LOGGER.info("All {} required tests passed :)", tracker.getTotalCount());
        }
        writeReport(server, null);
        tracker = null;
        server.halt(false);
    }

    /** A server that stops before the run is done still leaves a report, with the unfinished tests as missing. */
    private void onServerStopping(ServerStoppingEvent event) {
        if (tracker == null) return;
        LOGGER.error("AE2F_COMPAT_STOPPED_EARLY done={}/{}", tracker.getDoneCount(), tracker.getTotalCount());
        writeReport(event.getServer(), "The server stopped before every test finished");
        tracker = null;
    }

    /** A crashed server skips the stopping event and only reports that it stopped. */
    private void onServerStopped(ServerStoppedEvent event) {
        if (tracker == null) return;
        LOGGER.error("AE2F_COMPAT_CRASHED done={}/{}", tracker.getDoneCount(), tracker.getTotalCount());
        writeReport(event.getServer(), "The server crashed before every test finished; see crash-reports/");
        tracker = null;
    }

    private List<TestFunction> select(Map<String, Class<?>> groups) {
        var byName = new LinkedHashMap<String, Method>();
        var byGroup = new LinkedHashMap<String, List<String>>();
        for (var group : groups.entrySet()) {
            var names = new ArrayList<String>();
            for (var method : group.getValue().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(GameTest.class)) continue;
                var name = method.getName().toLowerCase(Locale.ROOT);
                byName.put(name, method);
                names.add(name);
            }
            names.sort(null);
            byGroup.put(group.getKey(), names);
        }
        var wanted = new ArrayList<String>();
        for (var id : requested) {
            if (id.equals("all")) wanted.addAll(byName.keySet());
            else if (byGroup.containsKey(id)) wanted.addAll(byGroup.get(id));
            else wanted.add(id);
        }
        var missing = wanted.stream().filter(id -> !byName.containsKey(id)).toList();
        if (!missing.isEmpty()) throw new IllegalArgumentException("Unknown or unloaded compatibility tests: " + missing);
        var before = new ArrayList<>(GameTestRegistry.getAllTestFunctions());
        wanted.stream().distinct().forEach(id -> GameTestRegistry.register(byName.get(id)));
        var functions = new ArrayList<>(GameTestRegistry.getAllTestFunctions());
        functions.removeAll(before);
        return functions;
    }

    private void writeReport(MinecraftServer server, String setupError) {
        var root = new JsonObject();
        root.addProperty("setupError", setupError);
        var tests = new JsonArray();
        for (var info : infos) {
            var test = new JsonObject();
            test.addProperty("id", info.getTestName());
            test.addProperty("status", info.hasSucceeded() ? "pass" : info.hasFailed() ? "fail" : "missing");
            test.addProperty("required", info.isRequired());
            test.addProperty("ms", info.hasStarted() ? info.getRunTime() : 0);
            if (info.getError() != null) test.addProperty("error", String.valueOf(info.getError().getMessage()));
            tests.add(test);
        }
        root.add("tests", tests);
        var mods = new JsonObject();
        for (var mod : net.neoforged.fml.ModList.get().getMods()) mods.addProperty(mod.getModId(), mod.getVersion().toString());
        root.add("mods", mods);
        try {
            if (report.getParent() != null) Files.createDirectories(report.getParent());
            Files.writeString(report, new GsonBuilder().setPrettyPrinting().create().toJson(root), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            LOGGER.error("AE2F_COMPAT_REPORT_FAILED {}", report, exception);
        }
    }
}
