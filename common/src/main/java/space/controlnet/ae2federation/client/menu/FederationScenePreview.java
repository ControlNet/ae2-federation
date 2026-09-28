package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.client.utils.RenderUtils;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import space.controlnet.ae2federation.client.policy.BlockMarks;
import space.controlnet.ae2federation.client.policy.SceneWindow;

/**
 * An isometric 3D view of the blocks around a location, drawn from the chunks the client has loaded. The devices of
 * interest are overlaid in their colour and the network's blocks faintly in its accent; the player can turn and zoom
 * it. Nothing is invented: air and unloaded blocks are simply not drawn.
 */
public final class FederationScenePreview extends Scene {
    private static final int RADIUS = 6;
    private static final int MAX_HALF_HEIGHT = 8;
    /** How often the loaded blocks are read again, so a change in the world shows up while the preview is open. */
    private static final int RESAMPLE_FRAMES = 200;

    private BlockMarks.Mark focus;
    private List<BlockMarks.Mark> mask = List.of();
    private int maskColor;
    private List<BlockMarks.Mark> marks = List.of();
    private int markColor;
    private SceneWindow window;
    private int renderedBlocks;
    private int frames;
    private net.minecraft.world.level.Level sceneLevel;

    FederationScenePreview() {
        addClass("map-preview-scene");
        layout(style -> style.widthPercent(100).heightPercent(100));
        useOrtho(true);
        // Frame the window a little inside its edges; the square's corners matter less than the devices at its centre.
        setOrthoRange(0.7f);
        setRenderFacing(false);
        setRenderSelect(false);
        setTickWorld(false);
        setAfterWorldRender(scene -> overlay());
    }

    /** The location to show: devices ({@code marks}) over the network's blocks ({@code mask}) in the player's world. */
    void show(List<BlockMarks.Mark> mask, int maskColor, List<BlockMarks.Mark> marks, int markColor) {
        var all = new ArrayList<BlockMarks.Mark>(mask);
        all.addAll(marks);
        var newFocus = BlockMarks.center(marks.isEmpty() ? all : marks).orElse(null);
        var newWindow = newFocus == null ? null : SceneWindow.around(newFocus, all, RADIUS, MAX_HALF_HEIGHT);
        if (!java.util.Objects.equals(newWindow, window)) frames = 0;
        focus = newFocus;
        window = newWindow;
        this.mask = List.copyOf(mask);
        this.maskColor = maskColor;
        this.marks = List.copyOf(marks);
        this.markColor = markColor;
    }

    /** Non-air blocks drawn at the last sample; zero when nothing around the location is loaded. */
    public int renderedBlocks() {
        return renderedBlocks;
    }

    @Override
    public void screenTick() {
        super.screenTick();
        if (!isDisplayed() || window == null || frames-- > 0) return;
        frames = RESAMPLE_FRAMES;
        sample();
    }

    private void sample() {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        if (level != sceneLevel) {
            createScene(level);
            setCameraYawAndPitch(-135, 30);
            sceneLevel = level;
        }
        var blocks = new ArrayList<BlockPos>();
        for (int x = window.minX(); x <= window.maxX(); x++) {
            for (int z = window.minZ(); z <= window.maxZ(); z++) {
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                for (int y = window.minY(); y <= window.maxY(); y++) {
                    var position = new BlockPos(x, y, z);
                    if (!level.getBlockState(position).isAir()) blocks.add(position);
                }
            }
        }
        renderedBlocks = blocks.size();
        setRenderedCore(blocks, null, true);
    }

    private void overlay() {
        if (window == null) return;
        var pose = new PoseStack();
        for (var block : mask) tint(pose, block, maskColor, 0.35f, 1.002f);
        for (var mark : marks) tint(pose, mark, markColor, 0.9f, 1.02f);
    }

    private void tint(PoseStack pose, BlockMarks.Mark block, int color, float strength, float scale) {
        if (!window.contains(block.x(), block.y(), block.z())) return;
        RenderUtils.renderBlockOverLay(pose, new BlockPos(block.x(), block.y(), block.z()),
                (color >> 16 & 0xff) / 255f * strength, (color >> 8 & 0xff) / 255f * strength, (color & 0xff) / 255f * strength, scale);
    }
}
