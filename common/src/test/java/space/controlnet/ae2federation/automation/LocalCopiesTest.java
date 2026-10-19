package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The client's local copies: what opening a program shows, and what it uploads, from the local copy and the server. */
final class LocalCopiesTest {
    @TempDir
    Path folder;

    private static String v(String text) {
        return ProgramSync.version(text);
    }

    @Test
    void withoutALocalCopyTheServersTextIsShownAndKept() throws IOException {
        var copies = new LocalCopies(folder);
        var opening = copies.open("lamps", v("a = 1\n"), "a = 1\n", Set.of("lamps"));
        assertEquals(new LocalCopies.Opening("a = 1\n", false, Optional.empty()), opening);
        assertEquals("a = 1\n", Files.readString(folder.resolve("lamps.ae2f")));
        assertEquals(Optional.of(new LocalCopies.Copy("a = 1\n", v("a = 1\n"))), copies.read("lamps"));
    }

    @Test
    void unsentEditsOnTheServersVersionAreShownAndUploaded() throws IOException {
        var copies = new LocalCopies(folder);
        copies.edited("lamps", "a = 2\n", v("a = 1\n"));
        var opening = copies.open("lamps", v("a = 1\n"), "a = 1\n", Set.of("lamps"));
        assertEquals(new LocalCopies.Opening("a = 2\n", true, Optional.empty()), opening);
    }

    @Test
    void aCopyHoldingTheServersTextIsInSync() throws IOException {
        var copies = new LocalCopies(folder);
        copies.edited("lamps", "a = 2\n", v("a = 1\n"));
        var opening = copies.open("lamps", v("a = 2\n"), "a = 2\n", Set.of("lamps"));
        assertEquals(new LocalCopies.Opening("a = 2\n", false, Optional.empty()), opening);
        assertEquals(v("a = 2\n"), copies.read("lamps").orElseThrow().base());
    }

    @Test
    void editsOnAnOlderVersionMoveBesideTheServersText() throws IOException {
        var copies = new LocalCopies(folder);
        Files.writeString(folder.resolve("lamps_local.ae2f"), "taken\n");
        copies.edited("lamps", "a = 2\n", v("a = 1\n"));
        var opening = copies.open("lamps", v("a = 3\n"), "a = 3\n", Set.of("lamps", "lamps_local2"));
        assertEquals(new LocalCopies.Opening("a = 3\n", false, Optional.of("lamps_local3")), opening);
        assertEquals("a = 2\n", Files.readString(folder.resolve("lamps_local3.ae2f")));
        assertEquals(Optional.of(new LocalCopies.Copy("a = 3\n", v("a = 3\n"))), copies.read("lamps"));
    }

    @Test
    void theBasesSurviveANewInstanceAndAreNotPrograms() throws IOException {
        new LocalCopies(folder).edited("lamps", "a = 2\n", v("a = 1\n"));
        var again = new LocalCopies(folder);
        assertEquals(Optional.of(new LocalCopies.Copy("a = 2\n", v("a = 1\n"))), again.read("lamps"));
        try (var files = Files.list(folder)) {
            assertEquals(Set.of("lamps.ae2f", LocalCopies.BASES),
                    files.map(path -> path.getFileName().toString()).collect(java.util.stream.Collectors.toSet()));
        }
        assertFalse(LocalCopies.BASES.endsWith(".ae2f"));
    }

    @Test
    void anotherWriterIsSeenAtOnce() throws IOException {
        var copies = new LocalCopies(folder);
        copies.sent("lamps", "a = 1\n");
        new LocalCopies(folder).edited("lamps", "a = 2\n", v("a = 0\n"));
        assertEquals(Optional.of(new LocalCopies.Copy("a = 2\n", v("a = 0\n"))), copies.read("lamps"));
        assertEquals(Optional.of("lamps_local"), copies.open("lamps", v("a = 1\n"), "a = 1\n", Set.of("lamps")).movedTo());
    }

    @Test
    void sentRecordsTheServersVersionAsTheBase() throws IOException {
        var copies = new LocalCopies(folder);
        copies.edited("lamps", "a = 2\n", v("a = 1\n"));
        copies.sent("lamps", "a = 2\n");
        assertEquals(Optional.of(new LocalCopies.Copy("a = 2\n", v("a = 2\n"))), copies.read("lamps"));
        assertTrue(copies.read("other").isEmpty());
    }

    @Test
    void aDeletedProgramLeavesNoCopyOrBase() throws IOException {
        var copies = new LocalCopies(folder);
        copies.sent("lamps", "a = 1\n");
        copies.sent("other", "b = 1\n");
        copies.delete("lamps");
        assertTrue(copies.read("lamps").isEmpty());
        assertEquals(java.util.List.of("other"), copies.names());
        assertFalse(Files.readString(folder.resolve(LocalCopies.BASES)).contains("lamps"));
        copies.delete("missing");
    }

    @Test
    void aFolderKeyKeepsOnlySafeCharacters() {
        assertEquals("server-play.example.org_25565", LocalCopies.key("server", "play.example.org:25565"));
        assertEquals("world-New_World", LocalCopies.key("world", "New World"));
    }

    @Test
    void anOutsideEditorsWriteIsFoundWithItsLineEndsMadePlain() throws IOException {
        var copies = new LocalCopies(folder);
        copies.sent("lamps", "a = 1\n");
        assertEquals(Optional.empty(), copies.outside("lamps", "a = 1\n"));
        Files.writeString(folder.resolve("lamps.ae2f"), "a = 2\r\nb = 3\r\n");
        assertEquals(Optional.of("a = 2\nb = 3\n"), copies.outside("lamps", "a = 1\n"));
        assertEquals(Optional.empty(), copies.outside("lamps", "a = 2\nb = 3\n"));
        assertEquals(Optional.empty(), copies.outside("missing", "a = 1\n"));
    }

    @Test
    void theProgramsInTheFolderAreItsAe2fFiles() throws IOException {
        var copies = new LocalCopies(folder);
        assertEquals(java.util.List.of(), new LocalCopies(folder.resolve("none")).names());
        copies.sent("lamps", "a = 1\n");
        Files.writeString(folder.resolve("alarm.ae2f"), "b = 1\n");
        Files.writeString(folder.resolve("notes.txt"), "not a program\n");
        assertEquals(java.util.List.of("alarm", "lamps"), copies.names());
    }
}
