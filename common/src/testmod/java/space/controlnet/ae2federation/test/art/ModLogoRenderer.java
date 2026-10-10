package space.controlnet.ae2federation.test.art;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * Renders the mod logo: the Router's inventory icon (its own item model, GUI angle and lighting) drawn offscreen over
 * a transparent {@code size}x{@code size} target. Runs once the title screen is up, writes the PNG to
 * {@code ae2federation.logo.out} and quits. Started by {@code ./gradlew :neoforge-1.21.1:runLogoClient}.
 */
public final class ModLogoRenderer {
    public static final String OUTPUT = "ae2federation.logo.out";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation ITEM = ResourceLocation.fromNamespaceAndPath("ae2federation", "router");
    private static boolean written;

    private ModLogoRenderer() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ModLogoRenderer::rendered);
    }

    private static void rendered(ScreenEvent.Render.Post event) {
        var minecraft = Minecraft.getInstance();
        if (written || minecraft.getOverlay() != null || !(event.getScreen() instanceof TitleScreen)) {
            return;
        }
        written = true;
        var out = Path.of(System.getProperty(OUTPUT)).toAbsolutePath();
        int size = Integer.getInteger("ae2federation.logo.size", 512);
        try (var image = render(minecraft, new ItemStack(BuiltInRegistries.ITEM.get(ITEM)), size)) {
            Files.createDirectories(out.getParent());
            image.writeToFile(out);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        LOGGER.info("AE2F_LOGO_WRITTEN item={} size={} path={}", ITEM, size, out);
        minecraft.stop();
    }

    private static NativeImage render(Minecraft minecraft, ItemStack stack, int size) {
        var target = new TextureTarget(size, size, true, Minecraft.ON_OSX);
        var projection = RenderSystem.getProjectionMatrix();
        var sorting = RenderSystem.getVertexSorting();
        try {
            target.setClearColor(0, 0, 0, 0);
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            // The GUI projection of one 16x16 slot, stretched over the whole target; the model view stays the GUI's.
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, 16, 16, 0, 1000, ClientHooks.getGuiFarPlane()),
                    VertexSorting.ORTHOGRAPHIC_Z);
            Lighting.setupFor3DItems();
            var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
            graphics.renderItem(stack, 0, 0);
            graphics.flush();
            var image = new NativeImage(size, size, false);
            RenderSystem.bindTexture(target.getColorTextureId());
            // Screenshot.takeScreenshot passes true here, which makes every pixel opaque.
            image.downloadTexture(0, false);
            image.flipY();
            return image;
        } finally {
            RenderSystem.setProjectionMatrix(projection, sorting);
            target.destroyBuffers();
            minecraft.getMainRenderTarget().bindWrite(true);
        }
    }
}
