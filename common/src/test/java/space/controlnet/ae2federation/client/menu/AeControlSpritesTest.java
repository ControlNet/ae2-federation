package space.controlnet.ae2federation.client.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The workspace's controls are AE2's own sprites, as NeoECO's are: buttons, text fields, switches and the scrollbar.
 * The sprites are read from the AE2 jar, so a region or nine-slice border that no longer fits AE2's art fails here.
 */
final class AeControlSpritesTest {
    private static final Path REPOSITORY_ROOT = Path.of("..").toAbsolutePath().normalize();

    @Test
    void theThemeDrawsControlsWithAe2Sprites() throws IOException {
        var theme = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/java/space/controlnet/ae2federation/client/menu/FederationTheme.java"));
        for (var path : new String[] {"textures/gui/sprites/button.png", "textures/gui/sprites/button_highlighted.png",
                "textures/gui/sprites/button_disabled.png", "textures/guis/text_field.png", "textures/guis/checkbox.png",
                "textures/gui/sprites/small_scroller.png"}) {
            assertTrue(theme.contains("\"" + path + "\""), "The theme must draw with AE2's " + path);
        }
        for (var painter : new String[] {"IGuiTexture button(", "IGuiTexture inset(", "IGuiTexture slider("}) {
            assertTrue(!theme.contains(painter), "A hand-painted copy of an AE2 control remains: " + painter);
        }
    }

    @Test
    void buttonSlicesStretchOnlyTheFace() throws IOException {
        // Left, top, right, bottom borders as FederationTheme slices them; the rest must be one colour.
        assertUniformCentre("textures/gui/sprites/button.png", 0, 0, 200, 20, 2, 2, 2, 5);
        assertUniformCentre("textures/gui/sprites/button_highlighted.png", 0, 0, 200, 20, 2, 3, 2, 4);
        assertUniformCentre("textures/gui/sprites/button_disabled.png", 0, 0, 200, 20, 2, 4, 2, 3);
    }

    @Test
    void textFieldAndScrollbarSlicesStretchOnlyTheirFill() throws IOException {
        // The text field is AE2's inset: a light rim and two shadow rows over the fill; the scrollbar track reuses it.
        assertUniformCentre("textures/guis/text_field.png", 0, 0, 128, 12, 1, 3, 1, 1);
        assertUniformCentre("textures/gui/sprites/small_scroller.png", 0, 0, 7, 15, 2, 2, 2, 4);
    }

    @Test
    void switchesAreAe2TwentyTwoByTwelveSprites() throws IOException {
        var checkbox = ae2Image("textures/guis/checkbox.png");
        int outline = 0xff413f54;
        // Off: the raised knob on the left. On: the blue track on the left, the knob on the right.
        assertEquals(outline, checkbox.getRGB(0, 29), "off switch's left edge");
        assertEquals(0xff9a9fb4, checkbox.getRGB(2, 30), "off switch's knob");
        assertEquals(0xff9cd3ff, checkbox.getRGB(1, 42), "on switch's track");
        assertEquals(outline, checkbox.getRGB(21, 51), "on switch's bottom-right corner");

        var lss = Files.readString(REPOSITORY_ROOT.resolve(
                "common/src/main/resources/assets/ae2federation/lss/domain.lss"));
        assertTrue(lss.contains(".domain-panel .policy-switch { width: 22; height: 12;"),
                "A policy switch must be drawn at the AE2 sprite's own 22x12 size, not stretched");
        assertTrue(lss.contains(".domain-panel .__scroller_view_vertical-scroller__ { width: 9;"),
                "The scrollbar track must be the scroller's 7 pixels inside a one-pixel rim");
    }

    @Test
    void spritesAreDrawnOnWholeScreenPixels() {
        // A button laid out at y 254.25 with height 17.5, at GUI scale 2: its edges move to screen rows 509 and 544.
        var span = PixelSnap.span(254.25f, 17.5f, 1, 0, 2);
        assertEquals(254.5f, span[0], 1e-4f);
        assertEquals(17.5f, span[1], 1e-4f);
        // Inside a list scrolled by 3.3 units, the edges still land on whole screen pixels.
        var scrolled = PixelSnap.span(40.1f, 18, 1, -3.3f, 3);
        assertEquals(Math.round((40.1f - 3.3f) * 3), (scrolled[0] - 3.3f) * 3, 1e-3f);
        assertEquals(Math.round((40.1f + 18 - 3.3f) * 3), (scrolled[0] + scrolled[1] - 3.3f) * 3, 1e-3f);
        // A zoomed pose scales the span; the snapped edges are whole pixels after that scale.
        var zoomed = PixelSnap.span(10.2f, 5, 0.5f, 7, 2);
        assertEquals(Math.round((0.5f * 10.2f + 7) * 2), (0.5f * zoomed[0] + 7) * 2, 1e-3f);
    }

    private static void assertUniformCentre(String path, int x, int y, int width, int height, int left, int top,
            int right, int bottom) throws IOException {
        var image = ae2Image(path);
        int fill = image.getRGB(x + left, y + top);
        for (int row = y + top; row < y + height - bottom; row++) {
            for (int column = x + left; column < x + width - right; column++) {
                assertEquals(fill, image.getRGB(column, row),
                        path + " is not one colour inside its slice borders at " + column + "," + row);
            }
        }
        // Each border must hold the edge: the pixel just outside the centre differs from the fill.
        assertTrue(image.getRGB(x + left, y + top - 1) != fill, path + ": the top border cuts into the fill");
        assertTrue(image.getRGB(x + left, y + height - bottom) != fill, path + ": the bottom border cuts into the fill");
        assertTrue(image.getRGB(x + left - 1, y + top) != fill, path + ": the left border cuts into the fill");
        assertTrue(image.getRGB(x + width - right, y + top) != fill, path + ": the right border cuts into the fill");
    }

    private static BufferedImage ae2Image(String path) throws IOException {
        try (var stream = AeControlSpritesTest.class.getClassLoader().getResourceAsStream("assets/ae2/" + path)) {
            assertNotNull(stream, "AE2 jar has no " + path);
            return ImageIO.read(stream);
        }
    }
}
