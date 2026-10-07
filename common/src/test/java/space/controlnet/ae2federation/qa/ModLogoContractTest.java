package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Pins the mod list logo: the Router as rendered by {@code ModLogoRenderer}, at the JAR root, on a transparent
 * background, drawn with sharp texels.
 */
final class ModLogoContractTest {
    private static final Path ROOT = Path.of("..").toAbsolutePath().normalize();
    private static final Path RESOURCES = ROOT.resolve("neoforge-1.21.1/src/main/resources");

    @Test
    void theModDeclaresTheRenderedRouterAsItsLogo() throws IOException {
        var metadata = Files.readString(RESOURCES.resolve("META-INF/neoforge.mods.toml"));
        var mod = metadata.substring(metadata.indexOf("[[mods]]"), metadata.indexOf("[[mixins]]"));
        var logo = Pattern.compile("(?m)^logoFile=\"([^\"]+)\"$").matcher(mod);
        assertTrue(logo.find(), "logoFile belongs to the [[mods]] table");
        assertTrue(mod.contains("\nlogoBlur=false\n"), "the pixel art stays sharp in the mod list");

        var image = ImageIO.read(RESOURCES.resolve(logo.group(1)).toFile());
        assertEquals(512, image.getWidth());
        assertEquals(512, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        assertEquals(0, image.getRGB(0, 0) >>> 24, "transparent background");
        assertEquals(0xFF, image.getRGB(256, 256) >>> 24, "the Router fills the centre");
    }
}
