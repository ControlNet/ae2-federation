package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.client.scene.ISceneBlockRenderHook;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.client.utils.RenderUtils;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Scene;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import space.controlnet.ae2federation.client.policy.BlockMarks;
import space.controlnet.ae2federation.client.policy.SceneWindow;

/**
 * An isometric 3D view of the blocks around a location, drawn from the chunks the client has loaded. As on the map,
 * the surroundings are dimmed; the network's blocks keep their own look with an edge in its accent, and the devices
 * of interest an edge in their colour. The player can turn and zoom it. Nothing is invented: air and unloaded blocks
 * are simply not drawn.
 */
public final class FederationScenePreview extends Scene {
    private static final int RADIUS = 6;
    private static final int MAX_HALF_HEIGHT = 8;
    /** How often the loaded blocks are read again, so a change in the world shows up while the preview is open. */
    private static final int RESAMPLE_FRAMES = 200;
    /** How bright the surroundings stay, as the map dims them. */
    private static final float SURROUNDINGS = 0.42f;
    /** Edge thickness in blocks: about a pixel of a block's texture, a little more for the devices. */
    private static final float EDGE = 1 / 16f;
    private static final float MARK_EDGE = 1.5f / 16f;

    private BlockMarks.Mark focus;
    private List<BlockMarks.Mark> mask = List.of();
    private int maskColor;
    private List<BlockMarks.Mark> marks = List.of();
    private int markColor;
    private SceneWindow window;
    private List<BlockPos> rendered = List.of();
    private int frames;
    private net.minecraft.world.level.Level sceneLevel;
    /** The network's blocks and the devices, drawn undimmed. */
    private Set<BlockPos> own = Set.of();
    private java.util.function.DoubleSupplier highlight = () -> 0;
    private boolean highlightOnMarks;

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
        var newOwn = new HashSet<BlockPos>();
        for (var block : all) newOwn.add(new BlockPos(block.x(), block.y(), block.z()));
        // The dimming is part of the drawn blocks, so new blocks of the network need a new sample.
        if (!java.util.Objects.equals(newWindow, window) || !newOwn.equals(own)) frames = 0;
        own = Set.copyOf(newOwn);
        focus = newFocus;
        window = newWindow;
        this.mask = List.copyOf(mask);
        this.maskColor = maskColor;
        this.marks = List.copyOf(marks);
        this.markColor = markColor;
    }

    /** Blinks the edges of the outlined blocks, the devices when {@code onMarks}, with the world outline's brightness. */
    void setHighlight(java.util.function.DoubleSupplier brightness, boolean onMarks) {
        highlight = brightness;
        highlightOnMarks = onMarks;
    }

    /** Non-air blocks drawn at the last sample; zero when nothing around the location is loaded. */
    public int renderedBlocks() {
        return rendered.size();
    }

    /** The non-air blocks drawn at the last sample. */
    public List<BlockPos> rendered() {
        return rendered;
    }

    /** The level the preview draws from, or null before the first sample. */
    public Level sceneWorld() {
        return dummyWorld;
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
            createScene(new SceneLevel(level));
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
        rendered = List.copyOf(blocks);
        var undimmed = own;
        setRenderedCore(blocks, new ISceneBlockRenderHook() {
            @Override
            public void applyVertexConsumerWrapper(Level world, BlockPos pos, BlockState state,
                    WorldSceneRenderer.VertexConsumerWrapper wrapper, RenderType layer, float partialTicks) {
                if (!undimmed.contains(pos)) wrapper.setColorMultiplier(SURROUNDINGS, SURROUNDINGS, SURROUNDINGS, 1);
            }
        }, true);
    }

    /** Edges over the blocks, blended normally so the textures stay visible; outlined ones blink with the world. */
    private void overlay() {
        if (window == null || own.isEmpty()) return;
        float glow = (float) highlight.getAsDouble();
        var pose = new PoseStack();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        // Hidden edges stay hidden, so a block reads as a box and not a wireframe through its neighbours.
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (var block : mask) edges(pose, buffer, block, EDGE, maskColor, highlightOnMarks ? 0 : glow);
        for (var mark : marks) edges(pose, buffer, mark, MARK_EDGE, markColor, highlightOnMarks ? glow : 0);
        var mesh = buffer.build();
        if (mesh != null) BufferUploader.drawWithShader(mesh);
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    /**
     * The twelve edges of a block's shape as thin bars, so a cable gets a slim box and a machine a full one; while
     * outlined, they turn faint and back as the world outline blinks.
     */
    private void edges(PoseStack pose, BufferBuilder buffer, BlockMarks.Mark block, float width, int color, float glow) {
        if (sceneLevel == null || !window.contains(block.x(), block.y(), block.z())) return;
        var position = new BlockPos(block.x(), block.y(), block.z());
        var shape = sceneLevel.getBlockState(position).getShape(sceneLevel, position);
        if (shape.isEmpty()) return;
        var bounds = shape.bounds();
        float alpha = glow > 0 && glow < 1 ? 0.25f : 1f;
        float red = (color >> 16 & 0xff) / 255f;
        float green = (color >> 8 & 0xff) / 255f;
        float blue = (color & 0xff) / 255f;
        float grow = 0.003f;
        float x0 = block.x() + (float) bounds.minX - grow;
        float y0 = block.y() + (float) bounds.minY - grow;
        float z0 = block.z() + (float) bounds.minZ - grow;
        float x1 = block.x() + (float) bounds.maxX + grow;
        float y1 = block.y() + (float) bounds.maxY + grow;
        float z1 = block.z() + (float) bounds.maxZ + grow;
        float w = Math.min(width, (float) Math.min(bounds.getXsize(), Math.min(bounds.getYsize(), bounds.getZsize())) / 3);
        for (float y : new float[] {y0, y1 - w}) for (float z : new float[] {z0, z1 - w}) {
            RenderUtils.renderCubeFace(pose, buffer, x0, y, z, x1, y + w, z + w, red, green, blue, alpha);
        }
        for (float x : new float[] {x0, x1 - w}) for (float z : new float[] {z0, z1 - w}) {
            RenderUtils.renderCubeFace(pose, buffer, x, y0, z, x + w, y1, z + w, red, green, blue, alpha);
        }
        for (float x : new float[] {x0, x1 - w}) for (float y : new float[] {y0, y1 - w}) {
            RenderUtils.renderCubeFace(pose, buffer, x, y, z0, x + w, y + w, z1, red, green, blue, alpha);
        }
    }
}
