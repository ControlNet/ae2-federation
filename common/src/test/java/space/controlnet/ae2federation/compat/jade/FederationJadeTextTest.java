package space.controlnet.ae2federation.compat.jade;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

final class FederationJadeTextTest {
    private static final Path LANG = Path.of("../common/src/main/resources/assets/ae2federation/lang");

    private static TranslatableContents contents(Component component) {
        return (TranslatableContents) component.getContents();
    }

    private static void assertLine(String key, Object[] args, Component line) {
        assertEquals(key, contents(line).getKey());
        assertArrayEquals(args, contents(line).getArgs());
    }

    @Test
    void aSingleBlockDeviceShowsNoStructureLine() {
        assertEquals(Optional.empty(), FederationJadeText.body("valid", 1, 1, 1));
    }

    @Test
    void aFormedBodyShowsItsSizeAndAnUnformedOneItsReason() {
        assertLine("ae2federation.jade.structure.size", new Object[] {2, 3, 1},
                FederationJadeText.body("valid", 2, 3, 1).orElseThrow());
        for (var reason : List.of("not_solid", "touching", "unloaded")) {
            assertLine("ae2federation.jade.structure." + reason, new Object[0],
                    FederationJadeText.body(reason, 0, 0, 0).orElseThrow());
        }
        assertLine("ae2federation.jade.structure.too_large", new Object[] {8},
                FederationJadeText.body("too_large", 0, 0, 0).orElseThrow());
    }

    @Test
    void endpointAndProviderLinesSayWhatTheirScreensSay() {
        assertLine("ae2federation.jade.endpoint.local", new Object[0], FederationJadeText.endpoint(true, false));
        assertLine("ae2federation.jade.endpoint.claimed", new Object[0], FederationJadeText.endpoint(false, true));
        assertLine("ae2federation.jade.endpoint.unclaimed", new Object[0], FederationJadeText.endpoint(false, false));
        assertLine("ae2federation.jade.switch_networks", new Object[] {3}, FederationJadeText.networks(3));
        assertLine("ae2federation.jade.pattern_provider", new Object[] {2, 9, 4}, FederationJadeText.provider(2, 9, 4));
    }

    @Test
    void everyLineAndConfigNameIsTranslatedInBothLanguages() throws IOException {
        var keys = List.of("structure.size", "structure.not_solid", "structure.touching", "structure.too_large",
                "structure.unloaded", "switch_networks", "endpoint.local", "endpoint.claimed", "endpoint.unclaimed",
                "pattern_provider");
        for (var language : List.of("en_us", "zh_cn")) {
            JsonObject lang = JsonParser.parseString(Files.readString(LANG.resolve(language + ".json"))).getAsJsonObject();
            keys.forEach(key -> assertTrue(lang.has("ae2federation.jade." + key), language + " lacks " + key));
            for (var uid : List.of("cable_capacity", "device", "endpoint", "pattern_provider")) {
                assertTrue(lang.has("config.jade.plugin_ae2federation." + uid), language + " lacks Jade config " + uid);
            }
        }
    }
}
