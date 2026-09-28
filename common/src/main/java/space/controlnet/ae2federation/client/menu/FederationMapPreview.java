package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import space.controlnet.ae2federation.client.policy.BlockMarks;

/**
 * A top-down map tile around a location, coloured like a vanilla map from the blocks the client has loaded. The
 * network's blocks are tinted with its accent and the device of interest is marked. Unloaded columns stay dark;
 * nothing here is sent by the server except the positions.
 */
public final class FederationMapPreview extends UIElement {
    private static final int MIN_RADIUS = 8;
    private static final int MAX_RADIUS = 32;
    private static final int RESAMPLE_FRAMES = 40;
    /** Blocks below the network's lowest block that still count as the ground it stands on. */
    private static final int SLICE_DEPTH = 6;

    private String dimension = "";
    private BlockMarks.Mark center;
    private int radius = MIN_RADIUS;
    private List<BlockMarks.Mark> mask = List.of();
    private int maskColor;
    private List<BlockMarks.Mark> marks = List.of();
    private int markColor;
    private int[] colors = new int[0];
    private int sampledCells;
    private int frames;
    /** Map or 3D, shared by every preview so the player's choice holds across pages and screens. */
    private static boolean threeDimensional;
    /** Made on the first client frame: the UI tree is also built on the server, where a Scene cannot be loaded. */
    private FederationScenePreview scene;
    private final com.lowdragmc.lowdraglib2.gui.ui.elements.Button mode = new com.lowdragmc.lowdraglib2.gui.ui.elements.Button();

    FederationMapPreview() {
        addClass("map-preview-tile");
        layout(style -> style.widthPercent(100).heightPercent(100));
        setOverflowVisible(false);
        mode.addClass("map-mode-toggle");
        mode.layout(style -> style.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE).right(2).top(2).width(26).height(11));
        mode.style(style -> style.tooltips(FederationWorkspace.trLocation("mode_help")));
        mode.setOnClick(event -> {
            threeDimensional = !threeDimensional;
            applyMode();
        });
        addChild(mode);
        applyMode();
    }

    private void applyMode() {
        if (scene != null) scene.setDisplay(threeDimensional);
        mode.setText(FederationWorkspace.trLocation(threeDimensional ? "mode_map" : "mode_3d"));
        mode.removeClass("three-d");
        if (threeDimensional) mode.addClass("three-d");
    }

    private FederationScenePreview scene() {
        if (scene == null) {
            scene = new FederationScenePreview();
            scene.layout(style -> style.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE).left(0).top(0)
                    .widthPercent(100).heightPercent(100));
            addChildAt(scene, 0);
            showInScene();
            applyMode();
        }
        return scene;
    }

    private void showInScene() {
        if (scene == null) return;
        if (inPlayerDimension()) {
            scene.show(mask, maskColor, marks, markColor);
        } else {
            scene.show(List.of(), maskColor, List.of(), markColor);
        }
    }

    /** The 3D view of this preview once it has been drawn, for tests and diagnostics; null before that. */
    public FederationScenePreview sceneView() {
        return scene;
    }

    /** Whether previews show the 3D view rather than the map. */
    public static boolean threeDimensional() {
        return threeDimensional;
    }

    /** Shows {@code marks} (the devices) over {@code mask} (the network's blocks) in the given dimension. */
    void show(String dimension, List<BlockMarks.Mark> mask, int maskColor, List<BlockMarks.Mark> marks, int markColor) {
        var all = new java.util.ArrayList<BlockMarks.Mark>(mask);
        all.addAll(marks);
        var newCenter = BlockMarks.center(marks.isEmpty() ? all : marks).orElse(null);
        int newRadius = newCenter == null ? MIN_RADIUS : BlockMarks.radius(newCenter, all, MIN_RADIUS, MAX_RADIUS);
        boolean moved = !dimension.equals(this.dimension) || !java.util.Objects.equals(newCenter, center) || newRadius != radius;
        this.dimension = dimension;
        this.center = newCenter;
        this.radius = newRadius;
        this.mask = List.copyOf(mask);
        this.maskColor = maskColor;
        this.marks = List.copyOf(marks);
        this.markColor = markColor;
        if (moved) frames = 0;
        showInScene();
    }

    void clear() {
        center = null;
        mask = List.of();
        marks = List.of();
        colors = new int[0];
        sampledCells = 0;
        if (scene != null) scene.show(List.of(), 0, List.of(), 0);
    }

    /** Columns that had a loaded, coloured surface at the last sample; zero when nothing could be drawn. */
    public int sampledCells() {
        return sampledCells;
    }

    /** Whether the location is in the dimension the player is in, the only one the client can draw. */
    boolean inPlayerDimension() {
        var level = Minecraft.getInstance().level;
        return level != null && level.dimension().location().toString().equals(dimension);
    }

    private void sample() {
        int size = radius * 2 + 1;
        colors = new int[size * size];
        sampledCells = 0;
        var level = Minecraft.getInstance().level;
        if (level == null || center == null || !inPlayerDimension()) return;
        // Look through the network's own heights, so a base under the ground is not hidden by the grass above it.
        var all = new java.util.ArrayList<BlockMarks.Mark>(mask);
        all.addAll(marks);
        var slice = BlockMarks.slice(all, SLICE_DEPTH).orElse(null);
        var cursor = new BlockPos.MutableBlockPos();
        // One extra row to the north, for the height shading of the first row.
        int[] heights = new int[size * (size + 1)];
        java.util.Arrays.fill(heights, Integer.MIN_VALUE);
        for (int dz = -1; dz < size; dz++) {
            for (int dx = 0; dx < size; dx++) {
                int x = center.x() - radius + dx;
                int z = center.z() - radius + dz;
                if (!level.hasChunk(x >> 4, z >> 4)) continue;
                int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                int top = slice == null ? surface : Math.min(surface, slice.top());
                int bottom = slice == null ? surface : slice.bottom();
                for (int y = top; y >= bottom; y--) {
                    cursor.set(x, y, z);
                    var color = level.getBlockState(cursor).getMapColor(level, cursor);
                    if (color == MapColor.NONE) continue;
                    heights[(dz + 1) * size + dx] = y;
                    if (dz >= 0) {
                        int north = heights[dz * size + dx];
                        // Shade by height relative to the northern neighbour, as vanilla maps do.
                        var brightness = north == Integer.MIN_VALUE || y == north ? MapColor.Brightness.NORMAL
                                : y > north ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
                        colors[dz * size + dx] = 0xff000000 | abgrToRgb(color.calculateRGBColor(brightness));
                        sampledCells++;
                    }
                    break;
                }
            }
        }
    }

    private static int abgrToRgb(int abgr) {
        return (abgr & 0xff) << 16 | (abgr & 0xff00) | (abgr >> 16 & 0xff);
    }

    @Override
    public void drawBackgroundAdditional(GUIContext context) {
        super.drawBackgroundAdditional(context);
        if (threeDimensional) {
            if (!scene().isDisplayed()) applyMode();
            context.graphics.fill((int) getPositionX(), (int) getPositionY(), (int) (getPositionX() + getSizeWidth()),
                    (int) (getPositionY() + getSizeHeight()), FederationTheme.WELL);
            return;
        }
        if (scene != null && scene.isDisplayed()) applyMode();
        if (center == null) return;
        if (frames-- <= 0) {
            sample();
            frames = RESAMPLE_FRAMES;
        }
        int size = radius * 2 + 1;
        float cell = Math.min(getSizeWidth(), getSizeHeight()) / size;
        float left = getPositionX() + (getSizeWidth() - cell * size) / 2;
        float top = getPositionY() + (getSizeHeight() - cell * size) / 2;
        var graphics = context.graphics;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(left, top, 0);
        pose.scale(cell, cell, 1);
        graphics.fill(0, 0, size, size, FederationTheme.WELL);
        for (int index = 0; index < colors.length; index++) {
            if (colors[index] != 0) graphics.fill(index % size, index / size, index % size + 1, index / size + 1, colors[index]);
        }
        int tint = 0x99000000 | (maskColor & 0xffffff);
        for (var block : mask) cell(graphics, block, tint, 0);
        for (var mark : marks) {
            cell(graphics, mark, 0xff000000, -1);
            cell(graphics, mark, markColor, 0);
        }
        pose.popPose();
    }

    private void cell(net.minecraft.client.gui.GuiGraphics graphics, BlockMarks.Mark block, int color, int grow) {
        int x = block.x() - center.x() + radius;
        int z = block.z() - center.z() + radius;
        int size = radius * 2 + 1;
        if (x < 0 || z < 0 || x >= size || z >= size) return;
        graphics.fill(x + grow, z + grow, x + 1 - grow, z + 1 - grow, color);
    }
}
