package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeNotesTest {
    @Test
    void aCraftingRuleWhoseStorageRuleIsNotInEffectSaysSo() {
        assertEquals("crafting_storage_not_in_effect", RuntimeNotes.crafting(true, Optional.of(false)));
    }

    @Test
    void aCraftingRuleWithItsStorageRuleInEffectHasNoNote() {
        assertEquals("", RuntimeNotes.crafting(true, Optional.of(true)));
    }

    @Test
    void anUnobservedStorageServiceAddsNoNote() {
        assertEquals("", RuntimeNotes.crafting(true, Optional.empty()));
    }

    @Test
    void aCraftingRuleWithItsStorageRuleOffHasNoNote() {
        // Crafting does not need the storage rule; switching it off is a choice, not a fault.
        assertEquals("", RuntimeNotes.crafting(false, Optional.of(false)));
        assertEquals("", RuntimeNotes.crafting(false, Optional.empty()));
    }
}
