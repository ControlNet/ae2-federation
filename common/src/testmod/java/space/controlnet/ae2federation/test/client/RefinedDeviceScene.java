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

/** Real-client acceptance captures of all device sizes, orientations, resource reload and rejoining a saved world. */
public final class RefinedDeviceScene {
    public static final String OUTPUT = "ae2federation.refinedDevice.out";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String WORLD = "refined-device-scene-v4";
    /** The flat world's first free layer. */
    private static final int Y = -60;

    private static final Deque<Step> STEPS = new ArrayDeque<>();
    private static boolean started;
    private static int wait;
    private static volatile boolean verified;

    private record Step(String name, int ticks, Runnable action) {
    }

    private RefinedDeviceScene() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RefinedDeviceScene::tick);
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
        LOGGER.info("AE2F_REFINED_DEVICE step={}", step.name());
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
        STEPS.add(new Step("settle", 80, () -> {
            minecraft.options.hideGui = true;
            server(level -> {
                level.setDayTime(6000);
                level.getServer().getPlayerList().getPlayers().forEach(player -> player.setGameMode(GameType.SPECTATOR));
            });
        }));
        if (!reopen) STEPS.add(new Step("build", 180, () -> server(RefinedDeviceScene::build)));
        for (int n=1;n<=3;n++) {
            int x=n==1 ? 0 : n==2 ? 8 : 14;
            view("laser-"+n,out,new Vec3(x+n+4,Y+n+3,-6),new Vec3(x+n/2.0,Y+n/2.0,n));
            int k=n+2;
            view("quantum-"+n,out,new Vec3(x+k+5,Y+k+3,13),new Vec3(x+k/2.0,Y+k/2.0,20+k/2.0));
        }
        view("vertical",out,new Vec3(39,Y+8,-7),new Vec3(34,Y+2,1));
        view("items-unformed",out,new Vec3(35,Y+2.8,14),new Vec3(34.5,Y+.5,10.5));
        view("converter-palettes",out,new Vec3(45,Y+4.5,10),new Vec3(41,Y+1,15));
        STEPS.add(new Step("inventory-icons",10,() -> captureItems(minecraft,out)));
        held("beam_converter",out);
        held("creative_beam_converter",out);
        held("quantum_cube",out);

        if (!reopen) {
            STEPS.add(new Step("remove-member",60,() -> server(level -> level.setBlock(new BlockPos(8,Y,20),
                    Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL))));
            view("quantum-unformed",out,new Vec3(17,Y+7,13),new Vec3(10,Y+2,22));
            STEPS.add(new Step("restore-member",80,() -> server(level -> level.setBlock(new BlockPos(8,Y,20),
                    space.controlnet.ae2federation.quantum.QuantumRegistration.QUANTUM_CUBE.get().defaultBlockState(),Block.UPDATE_ALL))));
            STEPS.add(new Step("reload",200,minecraft::reloadResourcePacks));
            view("quantum-after-reload",out,new Vec3(17,Y+7,13),new Vec3(10,Y+2,22));
            view("laser-after-reload",out,new Vec3(14,Y+5,-6),new Vec3(9,Y+1,2));
        }
        STEPS.add(new Step("report",20,() -> server(level -> {
            for (int n=1;n<=3;n++) {
                int x=n==1 ? 0 : n==2 ? 8 : 14;
                for (int z:new int[]{0,20}) {
                    if (!RouterBlock.formed(level.getBlockState(new BlockPos(x,Y,z))))
                        throw new IllegalStateException("Device failed to form: "+x+","+z);
                }
            }
            verified = true;
            LOGGER.info("REFINED_DEVICE_SCENE_OK all six devices formed; reload/rejoin={}",reopen);
        })));
        STEPS.add(new Step("quit",20,() -> {
            if (!verified) throw new IllegalStateException("Device scene server verification did not pass");
            minecraft.stop();
        }));
    }
    private static void captureItems(Minecraft minecraft,Path out) {
        for (var name:new String[]{"beam_converter","creative_beam_converter","quantum_cube"}) {
            var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ae2federation",name);
            var item=net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id);
            try (var image=space.controlnet.ae2federation.test.art.ModLogoRenderer.render(minecraft,
                    new net.minecraft.world.item.ItemStack(item),512)) {
                int visible=0;
                for (int y=0;y<image.getHeight();y++) for (int x=0;x<image.getWidth();x++) {
                    if ((image.getPixelRGBA(x,y)>>>24)!=0) visible++;
                }
                if (visible<512) throw new IllegalStateException("Empty inventory capture: "+name);
                Files.createDirectories(out);
                image.writeToFile(out.resolve("inventory-"+name+".png"));
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
        }
    }

    private static void held(String name,Path out) {
        STEPS.add(new Step("hold "+name,30,() -> server(level -> {
            var id=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ae2federation",name);
            for (var player:level.getServer().getPlayerList().getPlayers()) {
                player.setGameMode(GameType.CREATIVE);
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(id)));
            }
        })));
        STEPS.add(new Step("show hand "+name,20,() -> Minecraft.getInstance().options.hideGui=false));
        STEPS.add(new Step("held capture "+name,5,() -> {
            var minecraft=Minecraft.getInstance();
            try (var image=Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
                image.writeToFile(out.resolve("held-"+name+".png"));
            } catch (IOException exception) { throw new UncheckedIOException(exception); }
            minecraft.options.hideGui=true;
            server(level -> level.getServer().getPlayerList().getPlayers().forEach(player -> player.setGameMode(GameType.SPECTATOR)));
        }));
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
                LOGGER.info("AE2F_REFINED_DEVICE_SHOT path={}", file);
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
        for (int n=1;n<=3;n++) {
            int x=n==1 ? 0 : n==2 ? 8 : 14;
            var converter=space.controlnet.ae2federation.power.PowerRegistration.CREATIVE_BEAM_CONVERTER.get();
            for (var p:BlockPos.betweenClosed(x,Y,0,x+n-1,Y+n-1,2*n-1)) level.setBlock(p,
                    converter.defaultBlockState().setValue(space.controlnet.ae2federation.power.BeamConverterBlock.FACING,
                    net.minecraft.core.Direction.NORTH),Block.UPDATE_ALL);
            box(level,space.controlnet.ae2federation.quantum.QuantumRegistration.QUANTUM_CUBE.get(),x,Y,20,n+2,n+2,n+2);
            cable(level,new BlockPos(x,Y,-3),new BlockPos(x+n-1,Y+n-1,-1),n);
            cable(level,new BlockPos(x+1,Y+1,17),new BlockPos(x+n,Y+n,19),n);
            for (var p:BlockPos.betweenClosed(x+1,Y+1,17-2*n,x+n,Y+n,16)) level.setBlock(p,
                    converter.defaultBlockState().setValue(space.controlnet.ae2federation.power.BeamConverterBlock.FACING,
                    net.minecraft.core.Direction.SOUTH),Block.UPDATE_ALL);
        }
        for (int i=0;i<2;i++) {
            var facing=i==0 ? net.minecraft.core.Direction.UP : net.minecraft.core.Direction.DOWN;
            for (var p:BlockPos.betweenClosed(32+4*i,Y,0,33+4*i,Y+3,1)) level.setBlock(p,
                    space.controlnet.ae2federation.power.PowerRegistration.BEAM_CONVERTER.get().defaultBlockState()
                    .setValue(space.controlnet.ae2federation.power.BeamConverterBlock.FACING,facing),Block.UPDATE_ALL);
        }
        level.setBlock(new BlockPos(32,Y,10),space.controlnet.ae2federation.power.PowerRegistration.BEAM_CONVERTER.get()
                .defaultBlockState(),Block.UPDATE_ALL);
        level.setBlock(new BlockPos(34,Y,10),space.controlnet.ae2federation.power.PowerRegistration.CREATIVE_BEAM_CONVERTER.get()
                .defaultBlockState(),Block.UPDATE_ALL);
        level.setBlock(new BlockPos(36,Y,10),space.controlnet.ae2federation.quantum.QuantumRegistration.QUANTUM_CUBE.get()
                .defaultBlockState(),Block.UPDATE_ALL);
        for (int i=0;i<2;i++) {
            var device=i==0 ? space.controlnet.ae2federation.power.PowerRegistration.BEAM_CONVERTER.get()
                    : space.controlnet.ae2federation.power.PowerRegistration.CREATIVE_BEAM_CONVERTER.get();
            for (var p:BlockPos.betweenClosed(39+4*i,Y,14,40+4*i,Y+1,17)) level.setBlock(p,
                    device.defaultBlockState().setValue(space.controlnet.ae2federation.power.BeamConverterBlock.FACING,
                    net.minecraft.core.Direction.NORTH),Block.UPDATE_ALL);
        }
    }
    private static void cable(ServerLevel level,BlockPos min,BlockPos max,int n) {
        for (var p:BlockPos.betweenClosed(min,max)) level.setBlock(p,RouterRegistration.FEDERATION_CABLE.get()
                .defaultBlockState(),Block.UPDATE_ALL);
        if (n>1) space.controlnet.ae2federation.bundle.BundledCables.get(level).form(level,
                new space.controlnet.ae2federation.bundle.BundleSegment(min,max,net.minecraft.core.Direction.Axis.Z));
    }
}
