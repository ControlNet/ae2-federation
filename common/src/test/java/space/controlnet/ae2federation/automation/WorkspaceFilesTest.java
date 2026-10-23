package space.controlnet.ae2federation.automation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import space.controlnet.ae2federation.script.Ops;
import space.controlnet.ae2federation.script.RunError;

/** The {@code fs} functions on a world's workspace folder: paths kept inside it, text only, and its size caps. */
final class WorkspaceFilesTest {
    @TempDir
    Path world;

    private Path root() {
        return world.resolve("workspace");
    }

    private WorkspaceFiles files() {
        return new WorkspaceFiles(root(), () -> WorkspaceFiles.DEFAULT_CAP_BYTES);
    }

    /** {@code Type: message} of the error the call stops with. */
    private static String error(Executable call) {
        return assertThrows(RunError.class, call).toString();
    }

    @Test
    void writtenTextReadsBackAndFoldersAreMadeOnTheWay() throws IOException {
        var files = files();
        assertTrue(files.write("logs/today.txt", "iron 64\n", false));
        assertTrue(files.append("logs/today.txt", "gold 32\n", false));
        assertEquals("iron 64\ngold 32\n", files.read("logs/today.txt"));
        assertEquals("iron 64\ngold 32\n", Files.readString(root().resolve("logs/today.txt")));
        assertTrue(files.exists("logs"));
        assertTrue(files.exists("logs/today.txt"));
        assertTrue(files.exists(""));
        assertFalse(files.exists("logs/tomorrow.txt"));
        assertNull(files.read("logs/tomorrow.txt"), "a missing file reads as None");
        assertTrue(files.append("new.txt", "a", false), "append makes a missing file");
        assertEquals("a", files.read("new.txt"));
        assertTrue(files.write("new.txt", "b", false), "write replaces");
        assertEquals("b", files.read("new.txt"));
        assertTrue(files.write("emoji.txt", "\u2764\uFE0F \u94c1", false));
        assertEquals("\u2764\uFE0F \u94c1", files.read("emoji.txt"));
    }

    @Test
    void aFolderListsItsNamesInOrderWithFoldersMarked() {
        var files = files();
        assertEquals("[]", Ops.repr(files.list("")), "an empty or missing workspace lists nothing");
        files.write("b.txt", "", false);
        files.write("a/x.txt", "", false);
        files.write("C.txt", "", false);
        assertEquals("['C.txt', 'a/', 'b.txt']", Ops.repr(files.list("")));
        assertEquals("['x.txt']", Ops.repr(files.list("a")));
        assertEquals("['x.txt']", Ops.repr(files.list("a/")), "a trailing slash names the same folder");
        assertEquals("[]", Ops.repr(files.list("missing")));
        assertEquals("OSError: 'b.txt' is not a folder", error(() -> files.list("b.txt")));
    }

    @Test
    void deleteRemovesAFileOrAnEmptyFolder() {
        var files = files();
        files.write("a/x.txt", "1", false);
        assertEquals("OSError: folder 'a' is not empty", error(() -> files.delete("a", false)));
        assertTrue(files.delete("a/x.txt", false));
        assertFalse(files.delete("a/x.txt", false), "nothing to delete");
        assertTrue(files.delete("a", false));
        assertFalse(files.exists("a"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/etc/passwd", "a\\b", "a//b", "C:/x", "a\u0000b"})
    void aPathIsRelativeWithSlashesBetweenNames(String path) {
        assertEquals("ValueError: path must be relative, with '/' between names: " + Ops.repr(path),
                error(() -> files().read(path)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"..", "../x", "a/../../x", "./x", "a/./b"})
    void aPathStaysInsideTheWorkspace(String path) {
        assertEquals("ValueError: path must stay inside the workspace: " + Ops.repr(path),
                error(() -> files().write(path, "x", false)));
    }

    @Test
    void aFileNeedsAName() {
        assertEquals("ValueError: a file needs a path, as in 'notes.txt'", error(() -> files().read("")));
        assertEquals("ValueError: a file needs a path, as in 'notes.txt'", error(() -> files().write("", "x", false)));
    }

    @Test
    void aLinkCannotLeadOutOfTheWorkspace() throws IOException {
        var outside = Files.createDirectories(world.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "no");
        Files.createDirectories(root());
        Files.createSymbolicLink(root().resolve("out"), outside);
        Files.createSymbolicLink(root().resolve("dangling"), world.resolve("nowhere.txt"));
        Files.createDirectories(root().resolve("inside"));
        Files.createSymbolicLink(root().resolve("short"), root().resolve("inside"));
        var files = files();
        assertEquals("ValueError: path must stay inside the workspace: 'out/secret.txt'",
                error(() -> files.read("out/secret.txt")));
        assertEquals("ValueError: path must stay inside the workspace: 'out/new.txt'",
                error(() -> files.write("out/new.txt", "x", false)));
        assertEquals("ValueError: path must stay inside the workspace: 'dangling'",
                error(() -> files.write("dangling", "x", false)));
        assertFalse(Files.exists(world.resolve("nowhere.txt")));
        assertTrue(files.write("short/ok.txt", "fine", false), "a link inside the workspace is followed");
        assertEquals("fine", files.read("inside/ok.txt"));
    }

    @Test
    void foldersAndFilesAreNotTakenForEachOther() {
        var files = files();
        files.write("a/x.txt", "1", false);
        assertEquals("OSError: 'a' is a folder", error(() -> files.read("a")));
        assertEquals("OSError: 'a' is a folder", error(() -> files.write("a", "x", false)));
        assertEquals("OSError: 'a/x.txt' is not a folder", error(() -> files.write("a/x.txt/y", "x", false)));
    }

    @Test
    void aFileHoldsAtMostAHundredThousandCharacters() throws IOException {
        var files = files();
        var most = "a".repeat(100_000);
        assertTrue(files.write("big.txt", most, false));
        assertEquals("OSError: a file holds at most 100000 characters", error(() -> files.append("big.txt", "b", false)));
        assertEquals(most, files.read("big.txt"), "a refused append changes nothing");
        Files.writeString(root().resolve("bigger.txt"), most + "b");
        assertEquals("OSError: 'bigger.txt' holds more than 100000 characters", error(() -> files.read("bigger.txt")));
        Files.write(root().resolve("binary.bin"), new byte[] {(byte) 0xff, (byte) 0xfe, 0});
        assertEquals("OSError: 'binary.bin' is not UTF-8 text", error(() -> files.read("binary.bin")));
    }

    @Test
    void theWorkspaceStopsGrowingAtItsCap() throws IOException {
        var files = new WorkspaceFiles(root(), () -> 10);
        assertTrue(files.write("a.txt", "12345", false));
        assertTrue(files.write("b.txt", "1234", false));
        assertEquals("OSError: the workspace holds at most 10 bytes", error(() -> files.append("b.txt", "56", false)));
        assertTrue(files.write("a.txt", "123456", false), "replacing a file counts only the difference");
        assertEquals("OSError: the workspace holds at most 10 bytes", error(() -> files.write("c.txt", "x", false)));
        assertEquals("OSError: the workspace holds at most 10 bytes",
                error(() -> files.write("c.txt", "\u94c1", false)), "bytes, not characters");
        assertTrue(files.delete("b.txt", false));
        assertTrue(files.write("c.txt", "\u94c1", false));
        Files.writeString(root().resolve("outside-edit.txt"), "1234567890");
        assertEquals("OSError: the workspace holds at most 10 bytes", error(() -> files.write("d.txt", "x", false)),
                "files written outside the game count too");
        assertEquals("the workspace holds at most 16 MB", WorkspaceFiles.capMessage(WorkspaceFiles.DEFAULT_CAP_BYTES));
    }

    @Test
    void aSimulatedChangeIsCheckedButTouchesNothing() {
        var files = files();
        files.write("kept.txt", "old", false);
        assertTrue(files.write("kept.txt", "new", true));
        assertTrue(files.append("kept.txt", "more", true));
        assertTrue(files.write("folder/new.txt", "x", true));
        assertTrue(files.delete("kept.txt", true));
        assertFalse(files.delete("missing.txt", true));
        assertEquals("old", files.read("kept.txt"));
        assertFalse(files.exists("folder"));
        assertEquals("ValueError: path must stay inside the workspace: '../x'", error(() -> files.write("../x", "", true)));
        assertEquals("OSError: a file holds at most 100000 characters",
                error(() -> files.append("kept.txt", "a".repeat(100_000), true)));
    }

    @Test
    void readingLeavesNoFolderBehind() {
        var files = files();
        assertNull(files.read("x.txt"));
        assertEquals("[]", Ops.repr(files.list("")));
        assertFalse(Files.exists(root()), "only a change makes the workspace folder");
        files.write("x.txt", "", false);
        assertTrue(Files.isDirectory(root()));
        assertEquals("", files.read("x.txt"));
    }

    /** {@code dir} becomes a link to {@code target}, as another program on the server could do at any moment. */
    private static void swapForLink(Path dir, Path target) {
        try {
            try (var entries = Files.list(dir)) {
                for (var entry : (Iterable<Path>) entries::iterator) {
                    Files.delete(entry);
                }
            }
            Files.delete(dir);
            Files.createSymbolicLink(dir, target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void aLinkSwappedInAfterTheChecksLeadsNowhere() throws IOException {
        var outside = Files.createDirectories(world.resolve("outside"));
        Files.writeString(outside.resolve("secret.txt"), "no");
        Files.writeString(outside.resolve("keep.txt"), "kept");
        var files = files();
        files.write("logs/result.txt", "old", false);
        var logs = root().resolve("logs");

        files.beforeAccess = () -> swapForLink(logs, outside);
        error(() -> files.write("logs/secret.txt", "overwritten", false));
        assertEquals("no", Files.readString(outside.resolve("secret.txt")), "a write cannot reach outside");

        Files.delete(logs);
        files.beforeAccess = () -> { };
        files.write("logs/secret.txt", "inside", false);
        files.beforeAccess = () -> swapForLink(logs, outside);
        error(() -> files.read("logs/secret.txt"));

        Files.delete(logs);
        files.beforeAccess = () -> { };
        files.write("logs/keep.txt", "inside", false);
        files.beforeAccess = () -> swapForLink(logs, outside);
        error(() -> files.delete("logs/keep.txt", false));
        assertEquals("kept", Files.readString(outside.resolve("keep.txt")), "a delete cannot reach outside");

        Files.delete(logs);
        files.beforeAccess = () -> { };
        files.write("logs/inside.txt", "x", false);
        files.beforeAccess = () -> swapForLink(logs, outside);
        error(() -> files.list("logs"));
    }

    @Test
    void onlyRegularFilesAreReadOrWritten() throws IOException, InterruptedException {
        Files.createDirectories(root());
        var pipe = root().resolve("pipe");
        int made;
        try {
            made = new ProcessBuilder("mkfifo", pipe.toString()).start().waitFor();
        } catch (IOException noMkfifo) {
            made = -1;
        }
        Assumptions.assumeTrue(made == 0, "needs mkfifo");
        var files = files();
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            assertEquals("OSError: 'pipe' is not a regular file", error(() -> files.read("pipe")));
            assertEquals("OSError: 'pipe' is not a regular file", error(() -> files.write("pipe", "x", false)));
            assertEquals("OSError: 'pipe' is not a regular file", error(() -> files.append("pipe", "x", false)));
        }, "a pipe would block the server thread");
    }

    @Test
    void aFileThatGrowsAfterTheCheckIsReadOnlyUpToTheCap() throws IOException {
        var files = files();
        files.write("growing.txt", "small", false);
        files.beforeAccess = () -> {
            try (var file = new RandomAccessFile(root().resolve("growing.txt").toFile(), "rw")) {
                file.setLength(10L * 1024 * 1024 * 1024);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        assertEquals("OSError: 'growing.txt' holds more than 100000 characters", error(() -> files.read("growing.txt")));
    }
}
