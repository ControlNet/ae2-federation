package space.controlnet.ae2federation.client.guide;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import space.controlnet.ae2federation.client.automation.PythonLanguage;
import space.controlnet.ae2federation.script.Candidate;
import space.controlnet.ae2federation.script.Catalog;
import space.controlnet.ae2federation.script.TypeRef;

/** The guide's programs: coloured as the editor colours them, and every example file checks clean. */
final class GuideProgramTest {
    private static final Path PROGRAMS = Path.of("..").toAbsolutePath().normalize()
            .resolve("common/src/main/resources/assets/ae2federation/ae2guide/assets/programs");

    /**
     * As the guide checks them: any interface or network exists, and resources are what the registries say. The
     * registries are not loaded here, so these are the vanilla IDs the examples name; a typo fails.
     */
    private static final Catalog CATALOG = new Catalog() {
        @Override
        public List<Candidate> values(TypeRef type, String prefix) {
            return List.of();
        }

        @Override
        public Set<String> kinds(String id) {
            return switch (id) {
                case "minecraft:iron_ingot", "minecraft:raw_iron", "minecraft:wheat_seeds" -> Set.of("Item");
                case "minecraft:water" -> Set.of("Fluid");
                default -> Set.of();
            };
        }

        @Override
        public boolean exists(TypeRef type, String value) {
            return true;
        }
    };

    @Test
    void everyExampleProgramChecksClean() throws IOException {
        try (Stream<Path> files = Files.list(PROGRAMS)) {
            var programs = files.filter(file -> file.toString().endsWith(".ae2f")).sorted().toList();
            assertTrue(!programs.isEmpty(), PROGRAMS.toString());
            for (var file : programs) {
                var program = GuideProgram.of(Files.readString(file), "", CATALOG);
                assertTrue(!program.graph().cards().isEmpty(), file.toString());
            }
        }
    }

    @Test
    void eachLineIsCutIntoRunsInTheEditorsColours() {
        var lines = GuideProgram.lines("def f() -> None:\n\tpass\n\n");
        assertEquals(2, lines.size(), "trailing blank lines are left out");
        assertEquals("def f() -> None:", String.join("", lines.get(0).stream().map(GuideProgram.Run::text).toList()));
        assertEquals(new GuideProgram.Run("def", PythonLanguage.color("keyword")), lines.get(0).get(0));
        assertEquals("    pass", String.join("", lines.get(1).stream().map(GuideProgram.Run::text).toList()),
                "a tab is four spaces");
    }

    @Test
    void theGraphIsTheNamedFunctionsOrTheFirst() {
        var text = "def a() -> int:\n    return 1\n\ndef b() -> int:\n    return 2\n";
        assertEquals("a", GuideProgram.of(text, "", CATALOG).graph().canvas().nodes().get(0).name());
        assertEquals("b", GuideProgram.of(text, "b", CATALOG).graph().canvas().nodes().get(0).name());
        var missing = assertThrows(IllegalArgumentException.class, () -> GuideProgram.of(text, "c", CATALOG));
        assertEquals("the program has no function c", missing.getMessage());
    }

    @Test
    void aProgramWithAProblemIsRefusedWithItsLine() {
        var problem = assertThrows(IllegalArgumentException.class,
                () -> GuideProgram.of("def f() -> int:\n    return ae2.amount(\"minecraft:iron_ingo\")\n", "",
                        CATALOG));
        assertTrue(problem.getMessage().startsWith("line 2: "), problem.getMessage());
    }
}
