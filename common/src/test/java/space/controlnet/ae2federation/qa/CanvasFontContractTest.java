package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The topology canvas draws Chinese from a vector font and everything else from the player's default font. That holds
 * only while the vector font comes first and carries no Latin glyph: any glyph it has wins over the pixel font.
 */
final class CanvasFontContractTest {
    private static final Path ASSETS = Path.of("..").toAbsolutePath().normalize()
            .resolve("common/src/main/resources/assets/ae2federation");
    /** Below this sit Latin, general punctuation such as the · in Endpoint labels, and symbols. */
    private static final int CJK_START = 0x2E80;
    /** File names Minecraft accepts in a resource path; any other file logs an error on every resource reload. */
    private static final Pattern RESOURCE_NAME = Pattern.compile("[a-z0-9_.-]+");

    @Test
    void theVectorFontComesFirstAndTheDefaultFontCatchesTheRest() throws IOException {
        var providers = JsonParser.parseString(Files.readString(ASSETS.resolve("font/canvas.json")))
                .getAsJsonObject().getAsJsonArray("providers");
        assertEquals(2, providers.size());
        JsonObject vector = providers.get(0).getAsJsonObject();
        assertEquals("ttf", vector.get("type").getAsString());
        assertEquals("ae2federation:canvas_cjk.ttf", vector.get("file").getAsString());
        // Force Unicode Font keeps its meaning: with it on, Chinese is Unifont as everywhere else.
        assertEquals(false, vector.getAsJsonObject("filter").get("uniform").getAsBoolean());
        JsonObject rest = providers.get(1).getAsJsonObject();
        assertEquals("reference", rest.get("type").getAsString());
        assertEquals("minecraft:default", rest.get("id").getAsString());
    }

    @Test
    void theVectorFontDrawsTheModsChineseAndNoLatin() throws IOException, FontFormatException {
        Font font;
        try (InputStream in = Files.newInputStream(ASSETS.resolve("font/canvas_cjk.ttf"))) {
            font = Font.createFont(Font.TRUETYPE_FONT, in);
        }
        var latin = new ArrayList<String>();
        for (int code = 0x20; code < CJK_START; code++) {
            // Java reports invisible format characters (joiners, direction marks) as displayable in any font.
            int type = Character.getType(code);
            boolean invisible = type == Character.FORMAT || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
            if (!invisible && font.canDisplay(code)) latin.add(Integer.toHexString(code));
        }
        assertEquals(List.of(), latin, "glyphs that would replace the default font's");

        var missing = new StringBuilder();
        Files.readString(ASSETS.resolve("lang/zh_cn.json")).codePoints()
                .filter(code -> code >= CJK_START && !font.canDisplay(code))
                .distinct().forEach(missing::appendCodePoint);
        assertEquals("", missing.toString(), "Chinese characters the canvas would draw in Unifont; run tools/visual/build_canvas_font.py");
    }

    @Test
    void theVectorFontShipsWithItsLicenceUnderValidResourceNames() throws IOException {
        var licence = Files.readString(ASSETS.resolve("font/canvas_cjk_license.txt"));
        assertTrue(licence.contains("Droid Sans Fallback"));
        assertTrue(licence.contains("Apache License"));
        assertTrue(licence.contains("TERMS AND CONDITIONS"));
        try (var files = Files.list(ASSETS.resolve("font"))) {
            files.forEach(file -> assertTrue(RESOURCE_NAME.matcher(file.getFileName().toString()).matches(), file.toString()));
        }
    }
}
