package space.controlnet.ae2federation.test.client;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * Screenshots of formed Router and Switch bodies in the real client, for visual review: thin, flat, full and floating
 * bodies, singles, an unformed L, near, low and distant views. It also removes and puts back a member, reloads the
 * resource packs, and on a second run reopens the saved world without building anything, so the images show the
 * client following blockstate changes, a reload, and a re-entered save. Writes PNGs to
 * {@code ae2federation.structureScene.out} and quits. Started by {@code runStructureSceneClient}; a development tool,
 * not a test with assertions.
 */
public final class DeviceStructureScene {
    public static final String OUTPUT = "ae2federation.structureScene.out";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = "device-structure-scene";
    /** The flat world's first free layer. */
    private static final int Y = -60;

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean started;
    private static int wait;

    private record Step(String name, int ticks, Runnable action) {
    }

    private DeviceStructureScene() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(DeviceStructureScene::tick);
    }

    private static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (!started) {
            if (minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                started = true;
                open(minecraft);
            }
            return;
        }
        if (minecraft.level == null || minecraft.player == null || minecraft.getSingleplayerServer() == null) return;
        if (wait > 0) {
            wait--;
            return;
        }
        var step = STEPS.pollFirst();
        if (step == null) return;
        LOGGER.info("AE2F_STRUCTURE_SCENE step={}", step.name());
        step.action().run();
        wait = step.ticks();
    }

    private static void open(Minecraft minecraft) {
        var reopen = minecraft.getLevelSource().levelExists(WORLD);
        plan(minecraft, reopen);
        if (reopen) {
            minecraft.createWorldOpenFlows().openWorld(WORLD, () -> LOGGER.error("Cannot reopen {}", WORLD));
            return;
        }
        var rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        var settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261011L, false, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT)
                        .value().createWorldDimensions(), minecraft.screen);
    }

    private static void plan(Minecraft minecraft, boolean reopen) {
        var out = Path.of(System.getProperty(OUTPUT)).toAbsolutePath().resolve(reopen ? "reopened" : "first-run");
        STEPS.add(new Step("settle", 100, () -> {
            minecraft.options.hideGui = true;
            server(level -> {
                level.setDayTime(6000);
                level.getServer().getPlayerList().getPlayers().forEach(player -> player.setGameMode(GameType.SPECTATOR));
            });
        }));
        if (!reopen) STEPS.add(new Step("build", 160, () -> server(DeviceStructureScene::build)));
        view("overview", out, new Vec3(-7, Y + 6, -9), new Vec3(10, Y + 1, 4));
        view("router-front-east", out, new Vec3(5.2, Y + 3.6, -3.2), new Vec3(1.5, Y + 1, 1));
        view("router-back-west", out, new Vec3(-2.2, Y + 3.6, 5.2), new Vec3(1.5, Y + 1, 1));
        view("switch-front-east", out, new Vec3(11.2, Y + 3.6, -3.2), new Vec3(7.5, Y + 1, 1));
        view("switch-back-west", out, new Vec3(3.8, Y + 3.6, 5.2), new Vec3(7.5, Y + 1, 1));
        view("switch-grazing", out, new Vec3(10.8, Y + 0.7, -1.4), new Vec3(7.5, Y + 0.6, 0));
        view("router-grazing", out, new Vec3(4.8, Y + 0.7, -1.4), new Vec3(1.5, Y + 0.6, 0));
        view("floating-from-below", out, new Vec3(4, Y + 3, 4.5), new Vec3(4, Y + 8.5, 9));
        view("thin-and-flat", out, new Vec3(18, Y + 4, -6), new Vec3(18, Y + 1.5, 1.5));
        view("unformed-and-singles", out, new Vec3(22, Y + 3, -4), new Vec3(24, Y + 0.8, 0.5));
        view("walls-distance-32", out, new Vec3(9, Y + 6, -18), new Vec3(9, Y + 2, 14));
        if (reopen) {
            STEPS.add(new Step("quit", 20, minecraft::stop));
            return;
        }
        // A member removed from the formed Switch unforms it on the client; putting it back forms it again.
        var corner = new BlockPos(8, Y + 1, 0);
        STEPS.add(new Step("remove-member", 60, () -> server(level -> level.setBlock(corner, Blocks.AIR.defaultBlockState(),
                Block.UPDATE_ALL))));
        view("switch-member-removed", out, new Vec3(11.2, Y + 3.6, -3.2), new Vec3(7.5, Y + 1, 1));
        STEPS.add(new Step("replace-member", 60, () -> server(level -> level.setBlock(corner,
                RouterRegistration.SWITCH.get().defaultBlockState(), Block.UPDATE_ALL))));
        view("switch-member-replaced", out, new Vec3(11.2, Y + 3.6, -3.2), new Vec3(7.5, Y + 1, 1));
        STEPS.add(new Step("reload-resources", 200, minecraft::reloadResourcePacks));
        view("router-after-reload", out, new Vec3(5.2, Y + 3.6, -3.2), new Vec3(1.5, Y + 1, 1));
        view("switch-after-reload", out, new Vec3(11.2, Y + 3.6, -3.2), new Vec3(7.5, Y + 1, 1));
        STEPS.add(new Step("report", 10, () -> server(DeviceStructureScene::report)));
        STEPS.add(new Step("quit", 20, minecraft::stop));
    }

    private static void view(String name, Path out, Vec3 eye, Vec3 target) {
        STEPS.add(new Step("camera " + name, 60, () -> server(level -> {
            var d = target.subtract(eye);
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            for (var player : level.getServer().getPlayerList().getPlayers()) {
                player.teleportTo(level, eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
            }
        })));
        STEPS.add(new Step("shot " + name, 5, () -> {
            var minecraft = Minecraft.getInstance();
            try (var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                Files.createDirectories(out);
                var file = out.resolve(name + ".png");
                image.writeToFile(file);
                LOGGER.info("AE2F_STRUCTURE_SCENE_SHOT path={}", file);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        }));
    }

    private static void server(java.util.function.Consumer<ServerLevel> action) {
        var server = Minecraft.getInstance().getSingleplayerServer();
        server.execute(() -> action.accept(server.overworld()));
    }

    private static void box(ServerLevel level, Block block, int x, int y, int z, int sx, int sy, int sz) {
        for (var position : BlockPos.betweenClosed(x, y, z, x + sx - 1, y + sy - 1, z + sz - 1)) {
            level.setBlock(position, block.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    private static void build(ServerLevel level) {
        var router = RouterRegistration.ROUTER.get();
        var block = RouterRegistration.SWITCH.get();
        box(level, router, 0, Y, 0, 3, 2, 2);
        box(level, block, 6, Y, 0, 3, 2, 2);
        box(level, router, -3, Y, 0, 1, 1, 1);
        box(level, block, 11, Y, 0, 1, 1, 1);
        box(level, router, 14, Y, 0, 1, 4, 1);
        box(level, block, 16, Y, 0, 1, 1, 4);
        box(level, block, 19, Y, 0, 1, 3, 3);
        // An L stays unformed beside a single and a formed pair.
        box(level, block, 23, Y, 0, 2, 1, 1);
        box(level, block, 23, Y + 1, 0, 1, 1, 1);
        box(level, block, 27, Y, 0, 1, 1, 1);
        box(level, router, 29, Y, 0, 1, 1, 2);
        // Floating cubes, seen from below.
        box(level, router, 1, Y + 8, 8, 2, 2, 2);
        box(level, block, 5, Y + 8, 8, 2, 2, 2);
        // The largest edge, for the distant view.
        box(level, block, 0, Y, 14, 8, 4, 1);
        box(level, router, 10, Y, 14, 8, 4, 1);
    }

    private static void report(ServerLevel level) {
        var formed = new ArrayList<String>();
        for (var position : List.of(new BlockPos(0, Y, 0), new BlockPos(6, Y, 0), new BlockPos(23, Y, 0),
                new BlockPos(14, Y, 0), new BlockPos(0, Y, 14))) {
            var state = level.getBlockState(position);
            formed.add(position.toShortString() + " formed=" + RouterBlock.formed(state)
                    + " connections=" + RouterBlock.connections(state));
        }
        LOGGER.info("AE2F_STRUCTURE_SCENE_REPORT {}", formed);
    }
}
