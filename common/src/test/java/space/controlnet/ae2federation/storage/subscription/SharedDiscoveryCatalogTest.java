package space.controlnet.ae2federation.storage.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

final class SharedDiscoveryCatalogTest {
    @Test
    void replaysKeysAcrossSourcesAndRegistrationOrders() {
        var firstOrder = new SharedDiscoveryCatalog<String>(4);
        assertEquals(List.of(), firstOrder.replay());
        assertEquals(List.of("iron"), firstOrder.discover(List.of("iron")));
        assertEquals(List.of("iron"), firstOrder.replay());

        var oppositeOrder = new SharedDiscoveryCatalog<String>(4);
        oppositeOrder.discover(List.of("iron"));
        assertEquals(List.of("iron"), oppositeOrder.replay());
        assertEquals(List.of(), oppositeOrder.discover(List.of("iron")));
    }

    @Test
    void overflowIsObservableAndNeverEvictsRetainedAuthority() {
        var catalog = new SharedDiscoveryCatalog<String>(2);
        catalog.discover(List.of("iron", "gold"));

        assertThrows(KeyRetentionOverflowException.class, () -> catalog.discover(List.of("copper")));
        assertEquals(2, catalog.size());
        assertEquals(List.of("iron", "gold"), catalog.replay());
    }
}
