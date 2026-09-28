package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
