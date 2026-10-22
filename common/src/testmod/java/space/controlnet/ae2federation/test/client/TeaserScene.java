package space.controlnet.ae2federation.test.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import guideme.extensions.ExtensionCollection;
import guideme.scene.CameraSettings;
import guideme.scene.GuidebookScene;
import guideme.scene.LytGuidebookScene;
import guideme.scene.level.GuidebookLevel;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * Renders a region of a saved world, powered, the way GuideME draws a guide scene: opens the save, places a creative
 * energy cell outside the region, waits for the network to come up, saves the region as a structure (AE2 then writes
 * each part's visual state; ours comes from each block entity's update tag), lights up every AE2 power flag in it and
 * draws it isometric on a transparent background. Writes {@code teaser.nbt}, {@code teaser.snbt} and one PNG per view
 * to {@code ae2federation.teaser.out} and quits. Started by {@code runTeaserClient}; a development tool, not a test.
 */
public final class TeaserScene {
    public static final String OUTPUT = "ae2federation.teaser.out";
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Ticks the networks get after the cell is placed, to boot and share energy. */
    private static final int SETTLE = 200;

    private static boolean started;
    private static int ticks = -1;

    private TeaserScene() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TeaserScene::tick);
    }

    private static void tick(ClientTickEvent.Post event) {
        var minecraft = Minecraft.getInstance();
        if (!started) {
            if (minecraft.getOverlay() == null && minecraft.screen instanceof TitleScreen) {
                started = true;
                var world = System.getProperty("ae2federation.teaser.world");
                minecraft.createWorldOpenFlows().openWorld(world, () -> {
                    LOGGER.error("AE2F_TEASER cannot open {}", world);
                    minecraft.stop();
                });
            }
            return;
        }
        var server = minecraft.getSingleplayerServer();
        if (minecraft.level == null || minecraft.player == null || server == null) return;
        ticks++;
        if (ticks == 20) {
            int[] power = ints("ae2federation.teaser.power");
            server.execute(() -> {
                var level = server.overworld();
                var at = new BlockPos(power[0], power[1], power[2]);
                level.setBlockAndUpdate(at, level.registryAccess().registryOrThrow(Registries.BLOCK)
                        .get(ResourceLocation.fromNamespaceAndPath("ae2", "creative_energy_cell")).defaultBlockState());
                LOGGER.info("AE2F_TEASER power at {}", at);
            });
        } else if (ticks == 20 + SETTLE) {
            server.execute(() -> {
                try {
                    var structure = capture(server.overworld());
                    minecraft.execute(() -> {
                        try {
                            render(structure);
                        } catch (RuntimeException e) {
                            LOGGER.error("AE2F_TEASER render failed", e);
                        } finally {
                            minecraft.stop();
                        }
                    });
                } catch (RuntimeException e) {
                    LOGGER.error("AE2F_TEASER capture failed", e);
                    minecraft.execute(minecraft::stop);
                }
            });
        }
    }

    /** The region as a structure, with each of our block entities' client look merged into its saved data. */
    private static CompoundTag capture(ServerLevel level) {
        int[] box = ints("ae2federation.teaser.box");
        var min = new BlockPos(Math.min(box[0], box[3]), Math.min(box[1], box[4]), Math.min(box[2], box[5]));
        var max = new BlockPos(Math.max(box[0], box[3]), Math.max(box[1], box[4]), Math.max(box[2], box[5]));
        var template = new StructureTemplate();
        template.fillFromWorld(level, min, new Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1,
                max.getZ() - min.getZ() + 1), false, null);
        var tag = template.save(new CompoundTag());
        for (var entry : tag.getList("blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag) entry;
            if (!block.contains("nbt")) continue;
            var nbt = block.getCompound("nbt");
            if (!nbt.getString("id").startsWith("ae2federation:")) continue;
            var pos = block.getList("pos", Tag.TAG_INT);
            var entity = level.getBlockEntity(min.offset(pos.getInt(0), pos.getInt(1), pos.getInt(2)));
            if (entity != null) nbt.merge(entity.getUpdateTag(level.registryAccess()));
        }
        lightUp(tag);
        var out = out();
        try {
            Files.createDirectories(out);
            NbtIo.writeCompressed(tag, out.resolve("teaser.nbt"));
            Files.writeString(out.resolve("teaser.snbt"), NbtUtils.structureToSnbt(tag.copy()), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        LOGGER.info("AE2F_TEASER captured {} to {}", Arrays.toString(box), out);
        return tag;
    }

    /**
     * Sets every AE2 power flag the structure holds: the parts' and drives' visual state and the {@code powered} block
     * states. The region's other networks draw their power through the federation, which a save without energy rules
     * does not do; the channels and connections stay as AE2 worked them out. Our cables' unpowered flag is cleared the
     * same way.
     */
    private static void lightUp(CompoundTag structure) {
        for (var state : structure.getList("palette", Tag.TAG_COMPOUND)) {
            var compound = (CompoundTag) state;
            if (compound.getString("Name").startsWith("ae2:") && compound.getCompound("Properties").contains("powered")) {
                compound.getCompound("Properties").putString("powered", "true");
            }
        }
        for (var block : structure.getList("blocks", Tag.TAG_COMPOUND)) {
            var nbt = ((CompoundTag) block).getCompound("nbt");
            // Our cables' beams draw red at no load while their domain has no power.
            if (nbt.contains("Unpowered")) nbt.putBoolean("Unpowered", false);
            if (Boolean.getBoolean("ae2federation.teaser.full") && nbt.contains("Capacity")) {
                nbt.putInt("CapacityUnits", nbt.getInt("Capacity"));
            }
            lightUpVisuals(nbt);
        }
    }

    private static void lightUpVisuals(CompoundTag tag) {
        for (var key : tag.getAllKeys()) {
            if (tag.get(key) instanceof CompoundTag child) {
                if (key.equals("visual")) {
                    if (child.contains("powered")) child.putBoolean("powered", true);
                    if (child.contains("online")) child.putBoolean("online", true);
                }
                lightUpVisuals(child);
            }
        }
    }

    /**
     * Places the structure in a guide level, then exports it once per {@code yaw:pitch} view of
     * {@code ae2federation.teaser.views}.
     */
    private static void render(CompoundTag structure) {
        for (var view : System.getProperty("ae2federation.teaser.views", "225:30,135:30").split(",")) {
            var angles = view.trim().split(":");
            float yaw = Float.parseFloat(angles[0]), pitch = Float.parseFloat(angles[1]);
            var level = new GuidebookLevel();
            place(level, structure);
            var camera = new CameraSettings();
            camera.setIsometricYawPitchRoll(yaw, pitch, 0);
            var scene = new GuidebookScene(level, camera);
            camera.setRotationCenter(scene.getWorldCenter());
            scene.centerScene();
            var lyt = new LytGuidebookScene(ExtensionCollection.empty());
            lyt.setScene(scene);
            LOGGER.info("AE2F_TEASER view {} filled={} size={}", view, level.getFilledBlocks().count(),
                    lyt.getPreferredSize());
            var png = lyt.exportAsPng(Float.parseFloat(System.getProperty("ae2federation.teaser.scale", "18")), true);
            if (png == null) {
                LOGGER.error("AE2F_TEASER {} rendered nothing", view);
                continue;
            }
            var file = out().resolve("teaser-" + (int) yaw + "-" + (int) pitch + ".png");
            try {
                Files.write(file, straightAlpha(png));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            LOGGER.info("AE2F_TEASER wrote {}", file);
        }
    }

    /**
     * The export as a straight-alpha PNG. The framebuffer holds premultiplied colour, which GuideME writes out as is:
     * glass came out dark, and the beams' additive glow, whose alpha grows only by its square, nearly vanished. Light
     * brighter than its alpha keeps its colour by taking that brightness as alpha.
     */
    private static byte[] straightAlpha(byte[] png) {
        try (var image = NativeImage.read(new ByteArrayInputStream(png))) {
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    int abgr = image.getPixelRGBA(x, y);
                    int r = abgr & 0xFF, g = abgr >> 8 & 0xFF, b = abgr >> 16 & 0xFF;
                    int a = Math.max(abgr >>> 24, Math.max(r, Math.max(g, b)));
                    if (a == 0) continue;
                    image.setPixelRGBA(x, y, a << 24 | Math.min(255, b * 255 / a) << 16 | Math.min(255, g * 255 / a) << 8
                            | Math.min(255, r * 255 / a));
                }
            }
            return image.asByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Each block of the structure and its block entity data, at the structure's own coordinates. */
    private static void place(GuidebookLevel level, CompoundTag structure) {
        var blocks = level.registryAccess().registryOrThrow(Registries.BLOCK).asLookup();
        var palette = structure.getList("palette", Tag.TAG_COMPOUND).stream()
                .map(state -> NbtUtils.readBlockState(blocks, (CompoundTag) state)).toList();
        for (var entry : structure.getList("blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag) entry;
            var pos = block.getList("pos", Tag.TAG_INT);
            var at = new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
            var state = palette.get(block.getInt("state"));
            if (state.isAir()) continue;
            if (!level.setBlock(at, state, 0)) LOGGER.warn("AE2F_TEASER could not place {} at {}", state, at);
            var entity = level.getBlockEntity(at);
            if (entity != null && block.contains("nbt")) {
                entity.loadWithComponents(block.getCompound("nbt"), level.registryAccess());
            }
        }
    }

    private static int[] ints(String property) {
        return Arrays.stream(System.getProperty(property).split(",")).mapToInt(s -> Integer.parseInt(s.trim()))
                .toArray();
    }

    private static Path out() {
        return Path.of(System.getProperty(OUTPUT)).toAbsolutePath();
    }
}
