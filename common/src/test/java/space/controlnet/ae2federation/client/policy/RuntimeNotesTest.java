package space.controlnet.ae2federation.client.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class RuntimeNotesTest {
    @Test
    void aCraftingRuleWhoseStorageRuleIsNotInEffectSaysSo() {
        assertEquals("crafting_storage_not_in_effect", RuntimeNotes.crafting(Optional.of(false)));
    }

    @Test
    void aCraftingRuleWithItsStorageRuleInEffectHasNoNote() {
        assertEquals("", RuntimeNotes.crafting(Optional.of(true)));
    }

    @Test
    void anUnobservedStorageServiceAddsNoNote() {
        assertEquals("", RuntimeNotes.crafting(Optional.empty()));
    }
}
