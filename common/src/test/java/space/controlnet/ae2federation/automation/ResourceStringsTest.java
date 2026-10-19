package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Which strings in a script's line name a resource, so the editor shows its icon before them. */
final class ResourceStringsTest {
    private static final Set<String> KNOWN = Set.of("minecraft:iron_ingot", "minecraft:water");

    @Test
    void aStringNamingAResourceIsFoundAtItsQuote() {
        var segments = List.of("    ", "iron", ": ", "Item", " = ", "\"minecraft:iron_ingot\"");
        assertEquals(List.of(new ResourceStrings.At(17, "minecraft:iron_ingot")),
                ResourceStrings.find(segments, KNOWN::contains));
    }

    @Test
    void singleQuotesAndSeveralStringsOnALine() {
        var segments = List.of("f(", "'minecraft:water'", ", ", "\"minecraft:iron_ingot\"", ")");
        assertEquals(List.of(new ResourceStrings.At(2, "minecraft:water"), new ResourceStrings.At(21,
                "minecraft:iron_ingot")), ResourceStrings.find(segments, KNOWN::contains));
    }

    @Test
    void unknownIdsCommentsAndOpenStringsHaveNoIcon() {
        assertEquals(List.of(), ResourceStrings.find(List.of("\"minecraft:iron_ingt\""), KNOWN::contains));
        assertEquals(List.of(), ResourceStrings.find(List.of("# \"minecraft:iron_ingot\""), KNOWN::contains));
        assertEquals(List.of(), ResourceStrings.find(List.of("\"minecraft:iron_ingot"), KNOWN::contains));
        assertEquals(List.of(), ResourceStrings.find(List.of("\"minecraft:iron_ingot'"), KNOWN::contains));
        assertEquals(List.of(), ResourceStrings.find(List.of("\"iron\""), KNOWN::contains));
    }
}
