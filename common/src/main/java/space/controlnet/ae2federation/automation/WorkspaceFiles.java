package space.controlnet.ae2federation.automation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.stream.Stream;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.script.ListValue;
import space.controlnet.ae2federation.script.Ops;
import space.controlnet.ae2federation.script.RunError;

/**
 * The {@code fs} functions on a world's workspace folder, {@code <world>/ae2federation/workspace}. Paths are relative
 * and {@code /} separated; one that would leave the folder, by {@code ..} or through a link, stops the run with
 * {@code ValueError}. Files hold UTF-8 text of at most {@link #MAX_CHARACTERS} characters, the language's string
 * cap, and the folder at most its cap in bytes; what the disk refuses stops the run with {@code OSError}.
 *
 * <p>Keeping paths inside the folder and the caps keep runs correct and the server stable; they are not permissions,
 * which wait for the mod's permission system (user, 2026-10-22). The folder is made by the first change, so reading
 * an unused workspace leaves nothing behind. Server thread only.
 */
public final class WorkspaceFiles {
    public static final int MAX_CHARACTERS = 100_000;
    public static final long DEFAULT_CAP_BYTES = 16L * 1024 * 1024;
    /** As many entries as a list holds. */
    private static final int MAX_ENTRIES = 100_000;

    private final Path root;
    private final LongSupplier capBytes;

    public WorkspaceFiles(Path root, LongSupplier capBytes) {
        this.root = root.toAbsolutePath().normalize();
        this.capBytes = capBytes;
    }

    /** The file's text, or {@code null} when there is none. */
    public @Nullable String read(String path) {
        var file = file(path);
        if (!Files.exists(file)) {
            return null;
        }
        if (Files.isDirectory(file)) {
            throw osError(Ops.repr(path) + " is a folder");
        }
        try {
            // A character takes at most four bytes, so a larger file is too long without decoding it.
            if (Files.size(file) > 4L * MAX_CHARACTERS) {
                throw osError(Ops.repr(path) + " holds more than " + MAX_CHARACTERS + " characters");
            }
            var text = decode(Files.readAllBytes(file), path);
            if (text.codePointCount(0, text.length()) > MAX_CHARACTERS) {
                throw osError(Ops.repr(path) + " holds more than " + MAX_CHARACTERS + " characters");
            }
            return text;
        } catch (IOException exception) {
            throw osError(exception);
        }
    }

    /** Whether a file or folder is there; {@code ""} is the workspace itself, which always is. */
    public boolean exists(String path) {
        return path.isEmpty() || Files.exists(file(path));
    }

    /** The names in a folder, in order, each folder's with a trailing {@code /}; none for a missing folder. */
    public ListValue list(String path) {
        var folder = path.isEmpty() || path.equals("/") ? inside("", path) : file(strip(path));
        if (!Files.exists(folder)) {
            return new ListValue(new ArrayList<>());
        }
        if (!Files.isDirectory(folder)) {
            throw osError(Ops.repr(strip(path)) + " is not a folder");
        }
        var names = new ArrayList<String>();
        try (Stream<Path> entries = Files.list(folder)) {
            for (var entry : (Iterable<Path>) entries::iterator) {
                if (names.size() == MAX_ENTRIES) {
                    throw osError("the folder holds more than " + MAX_ENTRIES + " entries");
                }
                names.add(entry.getFileName() + (Files.isDirectory(entry) ? "/" : ""));
            }
        } catch (IOException exception) {
            throw osError(exception);
        }
        names.sort(null);
        return new ListValue(new ArrayList<>(names));
    }

    /** Writes the file, making its folders; with {@code simulate} only checks that it could. */
    public boolean write(String path, String text, boolean simulate) {
        var file = file(path);
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        writable(file, path);
        characters(text.codePointCount(0, text.length()));
        room(bytes.length - size(file));
        if (!simulate) {
            store(file, bytes, false);
        }
        return true;
    }

    /** Adds to the end of the file, making it when missing; with {@code simulate} only checks that it could. */
    public boolean append(String path, String text, boolean simulate) {
        var file = file(path);
        var bytes = text.getBytes(StandardCharsets.UTF_8);
        writable(file, path);
        var existing = Files.exists(file) ? read(path) : "";
        characters((long) existing.codePointCount(0, existing.length()) + text.codePointCount(0, text.length()));
        room(bytes.length);
        if (!simulate) {
            store(file, bytes, true);
        }
        return true;
    }

    /** Deletes a file or an empty folder; False when there was nothing. */
    public boolean delete(String path, boolean simulate) {
        var file = file(path);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try {
            if (Files.isDirectory(file, LinkOption.NOFOLLOW_LINKS)) {
                try (Stream<Path> entries = Files.list(file)) {
                    if (entries.findAny().isPresent()) {
                        throw osError("folder " + Ops.repr(path) + " is not empty");
                    }
                }
            }
            if (!simulate) {
                Files.delete(file);
            }
            return true;
        } catch (DirectoryNotEmptyException exception) {
            throw osError("folder " + Ops.repr(path) + " is not empty");
        } catch (IOException exception) {
            throw osError(exception);
        }
    }

    /** {@code the workspace holds at most 16 MB}: the message of a write past the cap. */
    static String capMessage(long cap) {
        long megabyte = 1024L * 1024;
        return "the workspace holds at most " + (cap > 0 && cap % megabyte == 0 ? cap / megabyte + " MB" : cap + " bytes");
    }

    // ---- paths ----

    /** A file's place: a non-empty path, kept inside the workspace. */
    private Path file(String path) {
        if (path.isEmpty()) {
            throw new RunError("ValueError", "a file needs a path, as in 'notes.txt'");
        }
        return inside(path, path);
    }

    private static String strip(String path) {
        return path.endsWith("/") && path.length() > 1 ? path.substring(0, path.length() - 1) : path;
    }

    /**
     * {@code path} under the workspace, checked: names joined by {@code /}, none of them {@code .} or {@code ..},
     * and no link on the way that leads out. {@code shown} is the path as the program wrote it.
     */
    private Path inside(String path, String shown) {
        var place = root;
        if (!path.isEmpty()) {
            for (var name : path.split("/", -1)) {
                if (name.isEmpty() || name.indexOf('\\') >= 0 || name.indexOf(':') >= 0
                        || name.chars().anyMatch(c -> c < 0x20)) {
                    throw new RunError("ValueError", "path must be relative, with '/' between names: "
                            + Ops.repr(shown));
                }
                if (name.equals(".") || name.equals("..")) {
                    throw outside(shown);
                }
                place = place.resolve(name);
            }
        }
        confined(place, shown);
        return place;
    }

    /** Fails when the place, or the nearest part of it that exists, really lies outside the workspace. */
    private void confined(Path place, String shown) {
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try {
            var realRoot = root.toRealPath();
            var existing = place;
            while (!existing.equals(root) && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
                existing = existing.getParent();
            }
            if (Files.isSymbolicLink(existing) && !Files.exists(existing)) {
                // A link to nothing: writing through it would make a file wherever it points.
                throw outside(shown);
            }
            if (!existing.toRealPath().startsWith(realRoot)) {
                throw outside(shown);
            }
        } catch (IOException exception) {
            throw osError(exception);
        }
    }

    private static RunError outside(String shown) {
        return new RunError("ValueError", "path must stay inside the workspace: " + Ops.repr(shown));
    }

    // ---- writing ----

    /** Fails when the place is a folder, or a part of the way to it is a file. */
    private void writable(Path file, String path) {
        if (Files.isDirectory(file)) {
            throw osError(Ops.repr(path) + " is a folder");
        }
        var names = path.split("/");
        var way = root;
        for (int i = 0; i < names.length - 1; i++) {
            way = way.resolve(names[i]);
            if (Files.exists(way) && !Files.isDirectory(way)) {
                throw osError(Ops.repr(String.join("/", List.of(names).subList(0, i + 1))) + " is not a folder");
            }
        }
    }

    private static void characters(long count) {
        if (count > MAX_CHARACTERS) {
            throw osError("a file holds at most " + MAX_CHARACTERS + " characters");
        }
    }

    /** Fails when {@code growth} more bytes would take the workspace past its cap; files of any origin count. */
    private void room(long growth) {
        if (growth <= 0) {
            return;
        }
        long cap = capBytes.getAsLong();
        if (used() + growth > cap) {
            throw osError(capMessage(cap));
        }
    }

    private long used() {
        if (!Files.isDirectory(root)) {
            return 0;
        }
        try (Stream<Path> all = Files.walk(root)) {
            return all.filter(Files::isRegularFile).mapToLong(WorkspaceFiles::size).sum();
        } catch (IOException exception) {
            throw osError(exception);
        }
    }

    private static long size(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.size(file) : 0;
        } catch (IOException exception) {
            throw osError(exception);
        }
    }

    private static void store(Path file, byte[] bytes, boolean append) {
        try {
            Files.createDirectories(file.getParent());
            if (append) {
                Files.write(file, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } else {
                Files.write(file, bytes);
            }
        } catch (IOException exception) {
            throw osError(exception);
        }
    }

    private static String decode(byte[] bytes, String path) {
        try {
            return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw osError(Ops.repr(path) + " is not UTF-8 text");
        }
    }

    private static RunError osError(String message) {
        return new RunError("OSError", message);
    }

    /** What the disk refused, by the kind of refusal; its message would show the server's own paths. */
    private static RunError osError(IOException exception) {
        return osError("the disk refused it (" + exception.getClass().getSimpleName() + ")");
    }
}
