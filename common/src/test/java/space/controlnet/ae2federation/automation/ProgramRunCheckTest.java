package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.script.Candidate;
import space.controlnet.ae2federation.script.Catalog;
import space.controlnet.ae2federation.script.TypeRef;

/** A start reuses the file's last check while the catalog answers the check's questions as before. */
final class ProgramRunCheckTest {
    /** Test-only catalog: the interfaces in {@link #interfaces}, no resources. */
    private static final class Interfaces implements Catalog {
        final Set<String> interfaces = new HashSet<>(Set.of("north_gate"));

        @Override
        public List<Candidate> values(TypeRef type, String prefix) {
            return List.of();
        }

        @Override
        public Set<String> kinds(String id) {
            return Set.of();
        }

        @Override
        public boolean exists(TypeRef type, String value) {
            return type.name().equals("Interface") && interfaces.contains(value);
        }
    }

    private static final String PROGRAM = """
            def f() -> int:
                gate: Interface = "north_gate"
                return 1
            """;

    @Test
    void aCheckIsReusedWhileTheCatalogAnswersAlike() {
        var catalog = new Interfaces();
        var prepared = ProgramRun.prepare(Map.of("main", PROGRAM), catalog);
        var first = ProgramRun.check(prepared, catalog, "main", "f");
        assertSame(first, ProgramRun.check(prepared, catalog, "main", "f"));
        // Another catalog object with the same answers is as good.
        assertSame(first, ProgramRun.check(prepared, new Interfaces(), "main", "f"));
    }

    @Test
    void aChangedAnswerChecksAgain() {
        var catalog = new Interfaces();
        var prepared = ProgramRun.prepare(Map.of("main", PROGRAM), catalog);
        ProgramRun.check(prepared, catalog, "main", "f");
        catalog.interfaces.clear();
        var problem = assertThrows(IllegalArgumentException.class,
                () -> ProgramRun.check(prepared, catalog, "main", "f"));
        assertTrue(problem.getMessage().contains("north_gate"), problem.getMessage());
        // A failed check is not kept as a pass: it fails again until the name is back.
        assertThrows(IllegalArgumentException.class, () -> ProgramRun.check(prepared, catalog, "main", "f"));
        catalog.interfaces.add("north_gate");
        assertEquals(List.of(), ProgramRun.check(prepared, catalog, "main", "f").problems());
    }
}
