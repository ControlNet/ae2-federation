package space.controlnet.ae2federation.automation;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import space.controlnet.ae2federation.function.Library;

/**
 * The client's copy of the programs it edits, one {@code .ae2f} file each in a folder per server or world, so edits
 * survive a disconnect or a crash. Each copy remembers the server version its edits started from (its base), in a
 * file beside them that is not a program. Opening a program compares the copy with the server's version: unsent
 * edits on that version are shown and uploaded; edits on an older one move beside it as a new program. Nothing is
 * cached: another writer of the folder (another screen, an outside editor) is seen at once.
 */
public final class LocalCopies {
    /** The bases, by program name. */
    public static final String BASES = ".bases.properties";

    /** A local copy: its text and the server version its edits started from. */
    public record Copy(String text, String base) {
    }

    /**
     * What opening a program shows, whether that text must be uploaded, and the name the older edits moved to, if
     * they did.
     */
    public record Opening(String text, boolean upload, Optional<String> movedTo) {
    }

    private final Path folder;

    public LocalCopies(Path folder) {
        this.folder = folder;
    }

    private Properties bases() {
        var bases = new Properties();
        var file = folder.resolve(BASES);
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                bases.load(reader);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + file, e);
            }
        }
        return bases;
    }

    /** A folder name for a server or world: its kind and its name, with only letters, digits, dots, dashes and _. */
    public static String key(String kind, String name) {
        return kind + "-" + name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    public Optional<Copy> read(String name) {
        var file = file(name);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        try {
            return Optional.of(new Copy(Files.readString(file, StandardCharsets.UTF_8), bases().getProperty(name, "")));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + file, e);
        }
    }

    /** The programs in the folder: its {@code .ae2f} files, by name, in order. */
    public List<String> names() {
        if (!Files.isDirectory(folder)) return List.of();
        try (var files = Files.list(folder)) {
            return files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(Library.EXTENSION))
                    .map(name -> name.substring(0, name.length() - Library.EXTENSION.length())).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("cannot list " + folder, e);
        }
    }

    /**
     * The copy of {@code name} when another writer (an outside editor) has changed it from {@code shown}, the text the
     * game shows, with CRLF and CR line ends made LF; empty while they agree or there is no copy.
     */
    public Optional<String> outside(String name, String shown) {
        return read(name).map(copy -> plain(copy.text())).filter(text -> !text.equals(shown));
    }

    /** {@code text} with CRLF and CR line ends, as outside editors may write them, made LF. */
    public static String plain(String text) {
        return text.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** Stores an edit that has not reached the server; {@code base} is the server version it started from. */
    public void edited(String name, String text, String base) {
        write(name, text, base);
    }

    /** The server now holds {@code text}: its version becomes the copy's base. */
    public void sent(String name, String text) {
        write(name, text, ProgramSync.version(text));
    }

    /**
     * Opens a program the server holds as {@code serverText}, at {@code serverVersion}. {@code taken} are the names
     * the server's programs have, which a moved copy must not take.
     */
    public Opening open(String name, String serverVersion, String serverText, Set<String> taken) {
        var local = read(name).orElse(null);
        if (local == null || ProgramSync.version(local.text()).equals(serverVersion)) {
            sent(name, serverText);
            return new Opening(serverText, false, Optional.empty());
        }
        if (local.base().equals(serverVersion)) {
            return new Opening(local.text(), true, Optional.empty());
        }
        var moved = freeName(name + "_local", taken);
        edited(moved, local.text(), "");
        sent(name, serverText);
        return new Opening(serverText, false, Optional.of(moved));
    }

    private String freeName(String stem, Set<String> taken) {
        var candidate = stem;
        for (int n = 2; taken.contains(candidate) || Files.exists(file(candidate)); n++) {
            candidate = stem + n;
        }
        return candidate;
    }

    private void write(String name, String text, String base) {
        try {
            Files.createDirectories(folder);
            Files.writeString(file(name), text, StandardCharsets.UTF_8);
            var bases = bases();
            bases.setProperty(name, base);
            try (Writer writer = Files.newBufferedWriter(folder.resolve(BASES), StandardCharsets.UTF_8)) {
                bases.store(writer, "The server version each local copy's edits started from");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write the local copy of " + name, e);
        }
    }

    private Path file(String name) {
        return folder.resolve(name + Library.EXTENSION);
    }
}
