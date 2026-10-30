package space.controlnet.ae2federation.automation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
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
 * cap, and the folder at most its cap in bytes and {@link #MAX_WORKSPACE_ENTRIES} files and folders; what the disk
 * refuses stops the run with {@code OSError}.
 *
 * <p>Keeping paths inside the folder and the caps keep runs correct and the server stable; they are not permissions,
 * which wait for the mod's permission system (user, 2026-10-22). The folder is made by the first change, so reading
 * an unused workspace leaves nothing behind. Server thread only.
 *
 * <p>The checks go by path, and another program on the server could swap a folder for a link right after them. So
 * every read, write, delete and listing walks from the workspace's real folder through the names the checks saw,
 * links inside the workspace already followed, opening each folder without following a link: a link swapped in
 * meanwhile fails the walk instead of leading out. Only regular files are read or written, since a pipe would block
 * the server thread, and a read stops at the size cap however much the file grew. What is left: a folder made on the
 * way to a new file is made by path, so a swap at that moment could make an empty folder outside; on a platform without
 * {@link SecureDirectoryStream} (Windows) the access itself goes by path again, with the race the walk closes
 * elsewhere.
 */
public final class WorkspaceFiles {
    public static final int MAX_CHARACTERS = 100_000;
    public static final long DEFAULT_CAP_BYTES = 16L * 1024 * 1024;
    /** As many entries as a list holds. */
    private static final int MAX_ENTRIES = 100_000;
    /**
     * Files and folders in the whole workspace, of any origin. Empty ones cost no bytes, and each change walks them
     * all to count the bytes, so their number is capped too.
     */
    public static final int MAX_WORKSPACE_ENTRIES = 10_000;
    /** A character takes at most four bytes, so a larger file is too long without decoding it. */
    private static final long MAX_BYTES = 4L * MAX_CHARACTERS;

    private final Path root;
    private final LongSupplier capBytes;
    /** Tests only: runs between the checks and the access, where another program could change the folder. */
    volatile Runnable beforeAccess = () -> { };

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
            if (Files.size(file) > MAX_BYTES) {
                throw tooLong(path);
            }
            var text = decode(at(file, path, true, (folder, name) -> {
                regular(folder, name, file, path);
                try (var channel = folder == null ? Files.newByteChannel(file)
                        : folder.newByteChannel(name, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
                    return limited(channel, path);
                }
            }), path);
            if (text.codePointCount(0, text.length()) > MAX_CHARACTERS) {
                throw tooLong(path);
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
        try {
            var walk = realNames(folder, path);
            beforeAccess.run();
            try (var secure = folder(walk);
                    DirectoryStream<Path> entries = secure != null ? secure : Files.newDirectoryStream(folder)) {
                for (var entry : entries) {
                    if (names.size() == MAX_ENTRIES) {
                        throw osError("the folder holds more than " + MAX_ENTRIES + " entries");
                    }
                    var name = entry.getFileName();
                    names.add(name + (isFolder(secure, name, folder.resolve(name)) ? "/" : ""));
                }
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
        room(bytes.length - size(file), added(file));
        if (!simulate) {
            store(file, path, bytes, false);
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
        room(bytes.length, added(file));
        if (!simulate) {
            store(file, path, bytes, true);
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
                at(file, path, false, (folder, name) -> {
                    if (folder == null) {
                        Files.delete(file);
                    } else if (attributes(folder, name).isDirectory()) {
                        folder.deleteDirectory(name);
                    } else {
                        folder.deleteFile(name);
                    }
                    return null;
                });
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

    /**
     * The names from the workspace's real folder to the place, links on the way followed now; the nearest part of the
     * place that exists is resolved, the rest kept as written. Names that would climb out stop the run.
     */
    private List<String> realNames(Path place, String shown) throws IOException {
        var realRoot = root.toRealPath();
        var rest = new ArrayDeque<String>();
        var existing = place;
        while (!existing.equals(root) && !Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
            rest.push(existing.getFileName().toString());
            existing = existing.getParent();
        }
        var names = new ArrayList<String>();
        for (var part : realRoot.relativize(existing.equals(root) ? realRoot : existing.toRealPath())) {
            if (!part.toString().isEmpty()) {
                names.add(part.toString());
            }
        }
        names.addAll(rest);
        if (names.stream().anyMatch(name -> name.equals("..") || name.equals("."))) {
            throw outside(shown);
        }
        return names;
    }

    /** One access: the folder holding the place and the place's name in it, or both null where only paths work. */
    private interface Access<T> {
        T apply(@Nullable SecureDirectoryStream<Path> folder, @Nullable Path name) throws IOException;
    }

    /**
     * Runs {@code use} on the folder holding the place, walked to as the class comment says. With {@code follow}, a
     * link that is the place itself is followed too, as reading and writing do; deleting removes the link.
     */
    private <T> T at(Path place, String shown, boolean follow, Access<T> use) throws IOException {
        List<String> names;
        if (follow) {
            names = realNames(place, shown);
        } else {
            names = new ArrayList<>(realNames(place.getParent(), shown));
            names.add(place.getFileName().toString());
        }
        beforeAccess.run();
        try (var folder = folder(names.subList(0, names.size() - 1))) {
            return use.apply(folder, folder == null ? null : Path.of(names.getLast()));
        }
    }

    /** The folder at {@code names}, each step opened without following a link; null without such folders. */
    private @Nullable SecureDirectoryStream<Path> folder(List<String> names) throws IOException {
        var stream = Files.newDirectoryStream(root.toRealPath());
        if (!(stream instanceof SecureDirectoryStream<Path> secure)) {
            stream.close();
            return null;
        }
        var folder = secure;
        try {
            for (var name : names) {
                var next = folder.newDirectoryStream(Path.of(name), LinkOption.NOFOLLOW_LINKS);
                folder.close();
                folder = next;
            }
            return folder;
        } catch (IOException | RuntimeException failure) {
            folder.close();
            throw failure;
        }
    }

    private static BasicFileAttributes attributes(SecureDirectoryStream<Path> folder, Path name) throws IOException {
        return folder.getFileAttributeView(name, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS)
                .readAttributes();
    }

    /** Whether a listed entry is a folder, a link to one included, as the listing marks it. */
    private static boolean isFolder(@Nullable SecureDirectoryStream<Path> folder, Path name, Path path) {
        if (folder == null) {
            return Files.isDirectory(path);
        }
        try {
            return folder.getFileAttributeView(name, BasicFileAttributeView.class).readAttributes().isDirectory();
        } catch (IOException dangling) {
            return false;
        }
    }

    /** Fails unless the place is a regular file; a missing one passes, for writing makes it. */
    private static void regular(@Nullable SecureDirectoryStream<Path> folder, @Nullable Path name, Path file,
            String path) throws IOException {
        BasicFileAttributes found;
        try {
            found = folder == null ? Files.readAttributes(file, BasicFileAttributes.class) : attributes(folder, name);
        } catch (NoSuchFileException missing) {
            return;
        }
        if (found.isDirectory()) {
            throw osError(Ops.repr(path) + " is a folder");
        }
        if (!found.isRegularFile()) {
            throw osError(Ops.repr(path) + " is not a regular file");
        }
    }

    /** The channel's bytes, refused as soon as they pass {@link #MAX_BYTES}, however the file grew. */
    private static byte[] limited(SeekableByteChannel channel, String path) throws IOException {
        var out = new ByteArrayOutputStream();
        var buffer = ByteBuffer.allocate(8192);
        while (channel.read(buffer) >= 0) {
            if (out.size() + buffer.position() > MAX_BYTES) {
                throw tooLong(path);
            }
            out.write(buffer.array(), 0, buffer.position());
            buffer.clear();
        }
        return out.toByteArray();
    }

    private static RunError tooLong(String path) {
        return osError(Ops.repr(path) + " holds more than " + MAX_CHARACTERS + " characters");
    }

    // ---- writing ----

    /** Fails when the place is a folder or not a regular file, or a part of the way to it is a file. */
    private void writable(Path file, String path) {
        if (Files.isDirectory(file)) {
            throw osError(Ops.repr(path) + " is a folder");
        }
        if (Files.exists(file) && !Files.isRegularFile(file)) {
            throw osError(Ops.repr(path) + " is not a regular file");
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

    /**
     * Fails when {@code growth} more bytes or {@code entries} more files and folders would take the workspace past its
     * caps; files of any origin count.
     */
    private void room(long growth, int entries) {
        if (growth <= 0 && entries <= 0) {
            return;
        }
        var used = used();
        long cap = capBytes.getAsLong();
        if (growth > 0 && used.bytes() + growth > cap) {
            throw osError(capMessage(cap));
        }
        if (entries > 0 && used.entries() + entries > MAX_WORKSPACE_ENTRIES) {
            throw osError("the workspace holds at most " + MAX_WORKSPACE_ENTRIES + " files and folders");
        }
    }

    private record Usage(long bytes, long entries) {
    }

    /** The workspace's bytes and entries; the walk stops past the entry cap, so it costs at most that many. */
    private Usage used() {
        if (!Files.isDirectory(root)) {
            return new Usage(0, 0);
        }
        long bytes = 0;
        long entries = 0;
        try (Stream<Path> all = Files.walk(root)) {
            var walk = all.iterator();
            walk.next(); // the workspace itself
            while (walk.hasNext() && entries <= MAX_WORKSPACE_ENTRIES) {
                bytes += size(walk.next());
                entries++;
            }
        } catch (IOException exception) {
            throw osError(exception);
        } catch (UncheckedIOException exception) {
            throw osError(exception.getCause());
        }
        return new Usage(bytes, entries);
    }

    /** The files and folders a write to {@code file} makes: the file and its missing folders. */
    private int added(Path file) {
        int count = 0;
        for (var place = file; place != null && !place.equals(root) && !Files.exists(place, LinkOption.NOFOLLOW_LINKS);
                place = place.getParent()) {
            count++;
        }
        return count;
    }

    private static long size(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.size(file) : 0;
        } catch (IOException exception) {
            throw osError(exception);
        }
    }

    private void store(Path file, String path, byte[] bytes, boolean append) {
        try {
            // Made by path: a link swapped in at this moment could make an empty folder outside, never a file.
            Files.createDirectories(file.getParent());
            at(file, path, true, (folder, name) -> {
                regular(folder, name, file, path);
                var how = append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING;
                try (var channel = folder == null
                        ? Files.newByteChannel(file, StandardOpenOption.WRITE, StandardOpenOption.CREATE, how)
                        : folder.newByteChannel(name, Set.<OpenOption>of(StandardOpenOption.WRITE,
                                StandardOpenOption.CREATE, how, LinkOption.NOFOLLOW_LINKS))) {
                    var buffer = ByteBuffer.wrap(bytes);
                    while (buffer.hasRemaining()) {
                        channel.write(buffer);
                    }
                }
                return null;
            });
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
