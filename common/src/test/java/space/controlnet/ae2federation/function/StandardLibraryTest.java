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
            assertTrue(Completion.TYPES.contains(type.name()), type.toString());
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
    void everyEnumerationIsATypedString() {
        assertTrue(StandardLibrary.TYPED_STRINGS.containsAll(StandardLibrary.ENUMERATIONS.keySet()));
    }
}
