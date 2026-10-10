package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RelatedDomainLabelTest {
    @Test
    void namesTheEntranceKindFromTheDomainIdentity() {
        assertEquals("bridge", RelatedDomainLabel.of("direct:bridge:1f2e:east").kind());
        assertEquals("router", RelatedDomainLabel.of("physical:node-7").kind());
        assertEquals("other", RelatedDomainLabel.of("legacy:thing").kind());
    }

    @Test
    void tagIsShortStableAndTellsDomainsApart() {
        var first = RelatedDomainLabel.of("direct:bridge:1f2e:east");
        assertEquals(first, RelatedDomainLabel.of("direct:bridge:1f2e:east"));
        assertTrue(first.tag().matches("[0-9A-F]{4}"));
        assertNotEquals(first.tag(), RelatedDomainLabel.of("direct:bridge:1f2e:west").tag());
    }

    /** Players see every domain as "Domain" and its tag; how it is formed stays internal. */
    @Test
    void everyDomainReadsDomainAndItsTag() throws IOException {
        var lang = Path.of("..").toAbsolutePath().normalize().resolve("common/src/main/resources/assets/ae2federation/lang");
        for (var entry : Map.of("en_us.json", "Domain %s", "zh_cn.json", "域 %s").entrySet()) {
            var text = Files.readString(lang.resolve(entry.getKey()));
            assertTrue(text.contains("\"ae2federation.ui.topology.domain_label\": \"" + entry.getValue() + "\""), entry.getKey());
            assertFalse(text.contains("ae2federation.ui.topology.domain_label."), entry.getKey());
        }
    }
}
