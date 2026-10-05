package space.controlnet.ae2federation.compat;

import appeng.api.parts.IPartHost;
import appeng.parts.networking.CablePart;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.fml.ModList;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import space.controlnet.ae2federation.client.guide.GuideExamplePacks;

/**
 * The guide's scenes as AE2 itself would draw their cables. GuideME draws a cable's arms and channel lights only from
 * what the scene file says, so a wrong count shows unnoticed. Each scene the loaded mods can show is placed in the
 * level in turn; once AE2 has worked out the channels, every cable must have the connections and per-side channel
 * counts the file records. A mismatch is logged with AE2's own values ({@code AE2F_GUIDE_CABLE}).
 */
final class GuideSceneCables {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BlockPos ORIGIN = new BlockPos(2, 1, 2);
    /** Ticks for a scene's networks to boot; an ad-hoc network takes 20. */
    private static final int SETTLE = 40;

    private record Scene(String name, CompoundTag structure) {
    }

    private final GameTestHelper helper;
    private final List<Scene> scenes;
    private final List<String> mismatches = new ArrayList<>();
    private int index;
    private boolean placed;
    private long placedAt;

    GuideSceneCables(GameTestHelper helper) {
        this.helper = helper;
        var scenes = new ArrayList<Scene>();
        var federation = ModList.get().getModFileById("ae2federation").getFile();
        read(federation.findResource("assets", "ae2federation", "ae2guide", "assets"), "core", scenes);
        for (var pack : GuideExamplePacks.active(ModList.get()::isLoaded)) {
            read(federation.findResource("resourcepacks", pack.id(), "assets", "ae2federation", "ae2guide", "assets"),
                    pack.id(), scenes);
        }
        this.scenes = List.copyOf(scenes);
    }

    void tick() {
        if (index == scenes.size()) {
            helper.assertTrue(mismatches.isEmpty(), mismatches.size() + " guide cables differ from AE2's: "
                    + String.join("; ", mismatches));
            return;
        }
        var scene = scenes.get(index);
        if (!placed) {
            place(scene);
            placed = true;
            placedAt = helper.getTick();
            helper.fail("Placed " + scene.name());
        }
        helper.assertTrue(helper.getTick() - placedAt >= SETTLE, "Waiting for AE2's channels in " + scene.name());
        compare(scene);
        clear(scene);
        placed = false;
        index++;
        helper.fail("Checked " + scene.name());
    }

    private static void read(Path root, String source, List<Scene> scenes) {
        try (Stream<Path> files = Files.walk(root)) {
            for (var file : files.filter(path -> path.toString().endsWith(".snbt")).sorted().toList()) {
                var name = source + ":" + root.relativize(file).toString().replace('\\', '/');
                scenes.add(new Scene(name, NbtUtils.snbtToStructure(Files.readString(file))));
            }
        } catch (IOException | CommandSyntaxException exception) {
            throw new IllegalStateException("The guide scenes under " + root + " are unreadable", exception);
        }
    }

    private void place(Scene scene) {
        var level = helper.getLevel();
        var template = new StructureTemplate();
        template.load(level.holderLookup(Registries.BLOCK), scene.structure());
        var origin = helper.absolutePos(ORIGIN);
        template.placeInWorld(level, origin, origin, new StructurePlaceSettings(), level.getRandom(), Block.UPDATE_ALL);
    }

    private void compare(Scene scene) {
        var level = helper.getLevel();
        for (var element : scene.structure().getList("blocks", Tag.TAG_COMPOUND)) {
            var block = (CompoundTag) element;
            if (!block.getCompound("nbt").contains("cable")) continue;
            var expected = block.getCompound("nbt").getCompound("cable").getCompound("visual");
            var pos = position(block);
            var host = level.getBlockEntity(helper.absolutePos(ORIGIN.offset(pos))) instanceof IPartHost found ? found
                    : null;
            if (host == null || !(host.getPart(null) instanceof CablePart cable)) {
                mismatches.add(scene.name() + " " + pos.toShortString() + ": no cable in the level");
                continue;
            }
            var actual = new CompoundTag();
            cable.writeVisualStateToNBT(actual);
            if (!same(expected, actual)) {
                LOGGER.info("AE2F_GUIDE_CABLE {} {} {}", scene.name(), pos.toShortString(), actual);
                mismatches.add(scene.name() + " " + pos.toShortString() + ": the file has " + shown(expected)
                        + ", AE2 has " + shown(actual));
            }
        }
    }

    private static boolean same(CompoundTag expected, CompoundTag actual) {
        return connections(expected).equals(connections(actual)) && shown(expected).equals(shown(actual));
    }

    private static HashSet<String> connections(CompoundTag visual) {
        var sides = new HashSet<String>();
        visual.getList("connections", Tag.TAG_STRING).forEach(side -> sides.add(side.getAsString()));
        return sides;
    }

    /** The connections and the channel count on each side, in a fixed order. */
    private static String shown(CompoundTag visual) {
        var text = new StringBuilder();
        for (var side : Direction.values()) {
            var key = "channels" + StringUtils.capitalize(side.getSerializedName());
            if (connections(visual).contains(side.getSerializedName()) || visual.contains(key)) {
                text.append(side.getSerializedName()).append('=')
                        .append(visual.contains(key) ? String.valueOf(visual.getInt(key)) : "-").append(' ');
            }
        }
        return text.toString().trim();
    }

    private void clear(Scene scene) {
        var size = scene.structure().getList("size", Tag.TAG_INT);
        for (var pos : BlockPos.betweenClosed(BlockPos.ZERO, new BlockPos(size.getInt(0) - 1, size.getInt(1) - 1,
                size.getInt(2) - 1))) {
            helper.setBlock(ORIGIN.offset(pos), Blocks.AIR);
        }
    }

    private static BlockPos position(CompoundTag block) {
        var pos = block.getList("pos", Tag.TAG_INT);
        return new BlockPos(pos.getInt(0), pos.getInt(1), pos.getInt(2));
    }
}
