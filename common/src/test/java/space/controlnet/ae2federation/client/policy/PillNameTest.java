package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class PillNameTest {
    private static final String ID = "3f9a81d0-0000-4000-8000-000000000000";

    @Test
    void anUnnamedNetworkUsesItsIdentityTag() {
        assertEquals("3F9A", PillName.of("", ID));
    }

    @Test
    void aNamedNetworkUsesItsFirstWordCappedToEightCharacters() {
        assertEquals("Main", PillName.of("Main", ID));
        assertEquals("North", PillName.of("North Storage", ID));
        assertEquals("Automati", PillName.of("Automation hub", ID));
        assertEquals("矿场", PillName.of("矿场 地下", ID));
    }

    @Test
    void aNameOfOnlySpacesCountsAsNoName() {
        assertEquals("3F9A", PillName.of("   ", ID));
    }

    @Test
    void twoNetworksWhoseShortNamesCollideAreToldApartByTheirTags() {
        assertEquals(new PillName.Pair("North", "Main"), PillName.pair("North Storage", ID, "Main", "81d00000-0000"));
        assertEquals(new PillName.Pair("3F9A", "81D0"), PillName.pair("North Storage", ID, "North Mine", "81d00000-0000"));
    }
}
