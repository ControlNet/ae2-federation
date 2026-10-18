package space.controlnet.ae2federation.function;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** The standard library's table stays consistent with the types the language knows. */
final class StandardLibraryTest {
    @Test
    void everyTypeInASignatureIsKnown() {
        var signatures = StandardLibrary.NAMESPACES.values().stream().flatMap(java.util.List::stream).toList();
        var types = Stream.concat(
                signatures.stream().flatMap(s -> s.args().stream().map(Signature.Arg::type)),
                signatures.stream().map(Signature::result)).toList();
        for (var type : types) {
            var inner = type.args().isEmpty() ? type : type.args().get(0);
            // Optional wraps a type, as list does; the checker knows it apart from the plain type names.
            assertTrue(Completion.TYPES.contains(type.name()) || type.name().equals("Optional"), type.toString());
            assertTrue(Completion.TYPES.contains(inner.name()), type.toString());
        }
    }

    @Test
    void namesAreUniqueWithinANamespace() {
        StandardLibrary.NAMESPACES.forEach((namespace, signatures) -> {
            var seen = new HashSet<String>();
            signatures.forEach(s -> assertTrue(seen.add(s.name()), namespace + "." + s.name()));
        });
    }

    @Test
    void theActionsAreTheEffects() {
        var actions = new HashSet<String>();
        StandardLibrary.NAMESPACES.forEach((namespace, signatures) -> signatures.stream().filter(Signature::effect)
                .forEach(s -> actions.add(namespace + "." + s.name())));
        assertEquals(java.util.Set.of("ae2.craft", "ae2.cancel", "ae2.import_from", "ae2.export_to", "ae2f.set_rule",
                "ae2f.set_filter", "mc.move", "mc.set_redstone", "mc.break_block", "mc.place_block", "mc.wait",
                "mc.move_fe"), actions);
    }

    @Test
    void everyEnumerationIsATypedString() {
        assertTrue(StandardLibrary.TYPED_STRINGS.containsAll(StandardLibrary.ENUMERATIONS.keySet()));
    }
}
