package space.controlnet.ae2federation.client.policy;

import java.util.Optional;

/** Lines that qualify a rule in effect, as translation-key suffixes under {@code runtime.note.}; empty for none. */
final class RuntimeNotes {
    private RuntimeNotes() {
    }

    /**
     * A crafting rule's requester crafts from the items it can see, so while the same direction's storage rule is known
     * not to be in effect it cannot use the other network's materials.
     */
    static String crafting(Optional<Boolean> pairStorageInEffect) {
        return pairStorageInEffect.filter(inEffect -> !inEffect).isPresent() ? "crafting_storage_not_in_effect" : "";
    }
}
