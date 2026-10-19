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
        assertEquals(java.util.Set.of("ae2.craft", "ae2.cancel", "ae2.import_from", "ae2.export_to", "ae2.move",
                "ae2f.set_rule", "ae2f.set_filter", "mc.move", "mc.set_redstone", "mc.break_block", "mc.place_block", "mc.wait",
                "mc.move_fe"), actions);
    }

    /** Every example is a whole function that checks against the real library and calls the function it shows. */
    @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.MethodSource("functions")
    void everyFunctionHasAnExampleThatChecks(String qualified, Signature signature) {
        var parsed = ScriptParser.parse(signature.example());
        assertEquals(java.util.List.of(), parsed.errors(), signature.example());
        assertEquals(1, parsed.program().definitions().size(), "one function per example");
        var result = Checker.check(parsed.program(), new Workspace(java.util.Map.of(), CompletionTest.CATALOG));
        assertEquals(java.util.List.of(), result.problems(), signature.example());
        var namespace = qualified.substring(0, qualified.indexOf('.'));
        var called = new boolean[1];
        Effects.calls(parsed.program().functions().get(0).body(), c -> called[0] |= c.name().equals(signature.name())
                && c.receiver() instanceof Expr.Name n && n.name().equals(namespace));
        assertTrue(called[0], qualified + " is called in its example");
        assertEquals(signature.example(), ScriptPrinter.print(parsed.program()), "examples are written as printed");
    }

    static Stream<org.junit.jupiter.params.provider.Arguments> functions() {
        return StandardLibrary.NAMESPACES.entrySet().stream().sorted(java.util.Map.Entry.comparingByKey())
                .flatMap(e -> e.getValue().stream().map(s -> org.junit.jupiter.params.provider.Arguments.of(
                        e.getKey() + "." + s.name(), s)));
    }

    @Test
    void everyEnumerationIsATypedString() {
        assertTrue(StandardLibrary.TYPED_STRINGS.containsAll(StandardLibrary.ENUMERATIONS.keySet()));
    }
}
