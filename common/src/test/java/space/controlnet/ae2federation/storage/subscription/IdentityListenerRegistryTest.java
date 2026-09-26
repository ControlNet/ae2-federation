package space.controlnet.ae2federation.storage.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

final class IdentityListenerRegistryTest {
    @Test
    void dispatchesOncePerRegistrationAndReleasesExactListeners() {
        var registry = new IdentityListenerRegistry<Object, java.util.function.Consumer<String>>();
        var source = new Object();
        var events = new ArrayList<String>();
        var first = registry.register(source, event -> events.add("first:" + event));
        var second = registry.register(source, event -> events.add("second:" + event));

        registry.visitAll(source, listener -> listener.accept("one"));
        first.close();
        registry.visitAll(source, listener -> listener.accept("two"));
        second.close();
        registry.visitAll(source, listener -> listener.accept("three"));

        assertEquals(3, events.size());
        assertEquals(java.util.Set.of("first:one", "second:one", "second:two"), java.util.Set.copyOf(events));
        assertEquals(2, registry.registrationCount());
        assertEquals(2, registry.removalCount());
        assertEquals(0, registry.activeCount());
        assertEquals(first.id() + 1, second.id());
    }

    @Test
    void roundRobinVisitsOneExactActiveRegistrationAtATime() {
        var registry = new IdentityListenerRegistry<Object, String>();
        var source = new Object();
        var visited = new ArrayList<String>();
        var first = registry.register(source, "first");
        registry.register(source, "second");

        registry.visitNext(source, visited::add);
        registry.visitNext(source, visited::add);
        first.close();
        registry.visitNext(source, visited::add);

        assertEquals(java.util.List.of("first", "second", "second"), visited);
        assertEquals(false, first.active());
    }

    @Test
    void closesEveryExactRegistrationForOneSourceFromSnapshot() {
        var registry = new IdentityListenerRegistry<Object, String>();
        var source = new Object();
        var other = new Object();
        var first = registry.register(source, "first");
        var second = registry.register(source, "second");
        var unrelated = registry.register(other, "other");

        assertEquals(2, registry.closeAll(source));
        assertEquals(0, registry.closeAll(source));
        assertEquals(false, first.active());
        assertEquals(false, second.active());
        assertEquals(true, unrelated.active());
        assertEquals(1, registry.activeCount());
        assertEquals(2, registry.removalCount());
        unrelated.close();
    }
}
