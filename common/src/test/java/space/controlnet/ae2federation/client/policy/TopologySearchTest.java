package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TopologySearchTest {
    private static final List<String> TEXTS = List.of("Main Base", "3f9a0000-0000-4000-8000-000000000c21", "Endpoint");
    private static final List<BlockMarks.Mark> PLACES = List.of(new BlockMarks.Mark(120, 12, -30),
            new BlockMarks.Mark(10, -57, 13));

    @Test
    void emptyQueryMatchesEverything() {
        assertTrue(TopologySearch.matches("  ", TEXTS, PLACES));
    }

    @Test
    void textMatchesIgnoringCase() {
        assertTrue(TopologySearch.matches("main", TEXTS, PLACES));
        assertTrue(TopologySearch.matches("3F9A", TEXTS, PLACES));
        assertTrue(TopologySearch.matches("endpoint", TEXTS, PLACES));
        assertFalse(TopologySearch.matches("mine", TEXTS, PLACES));
    }

    @Test
    void coordinatesMatchInAnyCommonSpelling() {
        assertTrue(TopologySearch.matches("10, -57, 13", TEXTS, PLACES));
        assertTrue(TopologySearch.matches("10 -57 13", TEXTS, PLACES));
        assertTrue(TopologySearch.matches("120,12,-30", TEXTS, PLACES));
        assertFalse(TopologySearch.matches("10 -57 14", TEXTS, PLACES));
    }

    @Test
    void aPartialCoordinateMatchesItsLeadingAxes() {
        assertTrue(TopologySearch.matches("120 12", TEXTS, PLACES));
        assertFalse(TopologySearch.matches("12 -30", TEXTS, PLACES));
    }
}
