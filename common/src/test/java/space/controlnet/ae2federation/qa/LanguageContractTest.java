package space.controlnet.ae2federation.qa;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The Simplified Chinese translation covers every English key and names the mod 「AE2 联邦组网」, with the space. */
final class LanguageContractTest {
    private static final Path ASSETS = Path.of("..").toAbsolutePath().normalize()
            .resolve("common/src/main/resources/assets/ae2federation");
    /** The old name 「AE2 联邦」 and the unspaced 「AE2联邦…」; item names such as ME联邦线缆 keep 联邦. */
    private static final Pattern OLD_OR_UNSPACED_NAME = Pattern.compile("AE2联邦|AE2 联邦(?!组网)");
    private static final Path LANG = ASSETS.resolve("lang");
    private static final String MOD_DESCRIPTION = "fml.menu.mods.info.description.ae2federation";

    @Test
    void chineseHasEveryEnglishKey() throws IOException {
        assertEquals(language("en_us").keySet(), language("zh_cn").keySet());
    }

    @Test
    void chineseNamesTheModAe2LianbangZuwang() throws IOException {
        var chinese = language("zh_cn");
        assertEquals("AE2 联邦组网", chinese.get("itemGroup.ae2federation.main").getAsString());
        assertEquals("AE2 联邦组网", chinese.get("mod.ae2federation.name").getAsString());
        chinese.entrySet().forEach(entry -> {
            assertFalse(entry.getValue().getAsString().contains("AE2 Federation"), entry.getKey() + ": " + entry.getValue());
            assertFalse(OLD_OR_UNSPACED_NAME.matcher(entry.getValue().getAsString()).find(), entry.getKey() + ": " + entry.getValue());
        });
        try (Stream<Path> pages = Files.walk(ASSETS.resolve("ae2guide/_zh_cn"))) {
            for (var page : pages.filter(path -> path.toString().endsWith(".md")).toList()) {
                assertFalse(OLD_OR_UNSPACED_NAME.matcher(Files.readString(page)).find(), page.toString());
            }
        }
    }

    /** NeoForge's mod list translates a mod's description, though not its display name. */
    @Test
    void modListDescriptionIsTranslated() throws IOException {
        assertTrue(language("en_us").has(MOD_DESCRIPTION));
        assertTrue(language("zh_cn").has(MOD_DESCRIPTION));
    }

    private static JsonObject language(String name) throws IOException {
        return JsonParser.parseString(Files.readString(LANG.resolve(name + ".json"))).getAsJsonObject();
    }
}
