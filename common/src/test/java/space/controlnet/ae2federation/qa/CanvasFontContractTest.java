package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Pattern;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * The topology canvas draws Chinese from a pixel sheet and everything else from the player's default font. That holds
 * only while the sheet comes first and carries no Latin glyph: any glyph it has wins over the default font.
 */
final class CanvasFontContractTest {
    private static final Path ASSETS = Path.of("..").toAbsolutePath().normalize()
            .resolve("common/src/main/resources/assets/ae2federation");
    /** Below this sit Latin, general punctuation such as the · in Endpoint labels, and symbols. */
    private static final int CJK_START = 0x2E80;
    /** File names Minecraft accepts in a resource path; any other file logs an error on every resource reload. */
    private static final Pattern RESOURCE_NAME = Pattern.compile("[a-z0-9_.-]+");

    @Test
    void theSheetComesFirstAndTheDefaultFontCatchesTheRest() throws IOException {
        var providers = canvas().getAsJsonArray("providers");
        assertEquals(2, providers.size());
        JsonObject sheet = providers.get(0).getAsJsonObject();
        assertEquals("bitmap", sheet.get("type").getAsString());
        assertEquals("ae2federation:font/canvas_cjk.png", sheet.get("file").getAsString());
        // Force Unicode Font keeps its meaning: with it on, Chinese is Unifont as everywhere else.
        assertEquals(false, sheet.getAsJsonObject("filter").get("uniform").getAsBoolean());
        JsonObject rest = providers.get(1).getAsJsonObject();
        assertEquals("reference", rest.get("type").getAsString());
        assertEquals("minecraft:default", rest.get("id").getAsString());
    }

    @Test
    void theSheetDrawsOneTexelPerUnit() throws IOException {
        // The Latin pixel font's ratio: zoomed out, Chinese then blurs no earlier than Latin does.
        var sheet = canvas().getAsJsonArray("providers").get(0).getAsJsonObject();
        var image = ImageIO.read(ASSETS.resolve("textures/font/canvas_cjk.png").toFile());
        var rows = sheet.getAsJsonArray("chars");
        long columns = rows.get(0).getAsString().codePoints().count();
        for (var row : rows) assertEquals(columns, row.getAsString().codePoints().count());
        assertEquals(sheet.get("height").getAsInt(), image.getHeight() / rows.size());
        assertEquals(image.getWidth() / columns, image.getHeight() / rows.size());
    }

    @Test
    void theSheetDrawsTheModsChineseAndNoLatin() throws IOException {
        var drawn = new HashSet<Integer>();
        for (var row : canvas().getAsJsonArray("providers").get(0).getAsJsonObject().getAsJsonArray("chars")) {
            row.getAsString().codePoints().filter(code -> code != 0).forEach(drawn::add);
        }
        assertEquals(List.of(), drawn.stream().filter(code -> code < CJK_START).map(Integer::toHexString).toList(),
                "glyphs that would replace the default font's");

        var missing = new StringBuilder();
        Files.readString(ASSETS.resolve("lang/zh_cn.json")).codePoints()
                .filter(code -> code >= CJK_START && !drawn.contains(code))
                .distinct().forEach(missing::appendCodePoint);
        assertEquals("", missing.toString(), "Chinese characters the canvas would draw in Unifont; run tools/visual/build_canvas_font.py");
    }

    @Test
    void theSheetShipsWithItsLicencesUnderValidResourceNames() throws IOException {
        var text = Files.readString(ASSETS.resolve("font/canvas_cjk_license.txt"));
        for (var part : new String[] {"Fusion Pixel Font", "ark-pixel", "boutique-bitmap-9x9", "galmuri", "SIL OPEN FONT LICENSE"}) {
            assertTrue(text.contains(part), part);
        }
        for (var dir : new String[] {"font", "textures/font"}) {
            try (var files = Files.list(ASSETS.resolve(dir))) {
                files.forEach(file -> assertTrue(RESOURCE_NAME.matcher(file.getFileName().toString()).matches(), file.toString()));
            }
        }
    }

    private static JsonObject canvas() throws IOException {
        return JsonParser.parseString(Files.readString(ASSETS.resolve("font/canvas.json"))).getAsJsonObject();
    }
}
