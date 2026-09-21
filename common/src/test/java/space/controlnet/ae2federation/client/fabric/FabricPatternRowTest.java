package space.controlnet.ae2federation.client.fabric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Set;
import org.junit.jupiter.api.Test;

final class FabricPatternRowTest {
    @Test
    void displayedPatternRowsUseVisibleSeparatorsWithoutControlCharacters() {
        var row = FabricPatternRow.format(0, "处理样板", "4000000000", Set.of(0));

        assertEquals("0 | 处理样板 | inputs=4000000000 | lanes=[0]", row);
        assertFalse(row.codePoints().anyMatch(Character::isISOControl));
    }
}
