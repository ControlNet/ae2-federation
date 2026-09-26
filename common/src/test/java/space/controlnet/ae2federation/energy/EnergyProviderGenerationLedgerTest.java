package space.controlnet.ae2federation.energy;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.identity.NetworkId;

final class EnergyProviderGenerationLedgerTest {
    @Test
    void advancesGenerationWhenNativeSourceIdentityChanges() {
        var ledger = new EnergyProviderGenerationLedger<Object, Object>();
        var network = new NetworkId(UUID.randomUUID());
        var service = new Object();
        var firstSource = new Object();
        var first = ledger.update(network, service, List.of(firstSource));

        var unchanged = ledger.update(network, service, List.of(firstSource));
        var replacement = ledger.update(network, service, List.of(new Object()));

        assertSame(first, unchanged);
        assertTrue(replacement.generation().value() > first.generation().value());
        assertFalse(ledger.isCurrent(first));
        assertTrue(ledger.isCurrent(replacement));
    }

    @Test
    void invalidationMakesCapturedProviderStale() {
        var ledger = new EnergyProviderGenerationLedger<Object, Object>();
        var network = new NetworkId(UUID.randomUUID());
        var snapshot = ledger.update(network, new Object(), List.of(new Object()));

        ledger.invalidate(network);

        assertFalse(ledger.isCurrent(snapshot));
    }

    @Test
    void preservesGenerationWhenEquivalentSourceDescriptorsAreRediscovered() {
        var ledger = new EnergyProviderGenerationLedger<Object, SourceDescriptor>();
        var network = new NetworkId(UUID.randomUUID());
        var service = new Object();
        var first = ledger.update(network, service, List.of(new SourceDescriptor("cell")));

        var rediscovered = ledger.update(network, service, List.of(new SourceDescriptor("cell")));

        assertSame(first, rediscovered);
    }

    private record SourceDescriptor(String id) {
    }
}
