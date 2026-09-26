package space.controlnet.ae2federation.crafting.binding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;

final class CraftingProviderGenerationLedgerTest {
    private static final NetworkId ORIGIN = new NetworkId(new UUID(0, 26));

    @Test
    void retainsGenerationAndNativeIdentityForEquivalentSnapshot() {
        var ledger = new CraftingProviderGenerationLedger<Object, Object>();
        var service = new Object();
        var provider = new Object();
        var first = ledger.update(ORIGIN, service, List.of(provider));

        var second = ledger.update(ORIGIN, service, List.of(provider));

        assertSame(first, second);
        assertEquals(1, second.generation().value());
        assertSame(service, second.service());
        assertSame(provider, second.providers().getFirst());
        assertTrue(ledger.isCurrent(second));
    }

    @Test
    void replacementAdvancesGenerationAndInvalidatesOldSnapshot() {
        var ledger = new CraftingProviderGenerationLedger<Object, Object>();
        var first = ledger.update(ORIGIN, new Object(), List.of(new Object()));

        var replacement = ledger.update(ORIGIN, new Object(), List.of(new Object()));

        assertEquals(2, replacement.generation().value());
        assertFalse(ledger.isCurrent(first));
        assertTrue(ledger.isCurrent(replacement));
    }

    @Test
    void duplicateProviderIdentityIsRejectedRatherThanMultiplied() {
        var ledger = new CraftingProviderGenerationLedger<Object, Object>();
        var provider = new Object();

        var rejected = false;
        try {
            ledger.update(ORIGIN, new Object(), List.of(provider, provider));
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }

        assertTrue(rejected);
    }
}
