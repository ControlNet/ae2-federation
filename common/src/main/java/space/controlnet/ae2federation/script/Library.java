package space.controlnet.ae2federation.script;

import java.io.IOException;
import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The program files of a world: one {@code name.ae2f} plain-text file per program in one folder, read and written as
 * UTF-8. The text is the source of truth: loading never writes, a file is written only when it is saved, and an
 * unedited definition keeps its text byte for byte. Files changed outside the game are seen after the next
 * {@link #load}; the folder is not watched. A file with errors is stored as written.
 *
 * <p>A file is checked against every other file; it cannot name itself, as a Python module cannot without an import.
 * Calls between files are written by name, so renaming a function or a file rewrites its callers' text.
 */
public final class Library {
    public static final String EXTENSION = ".ae2f";
    /** A program's characters at most: what the editor fetches and sends in chunks under the payload limits. */
    public static final int MAX_TEXT = 64_000;
    /**
     * Programs, and characters in all, past which a save that adds is refused; files put in the folder by hand still
     * load. Every program travels in the tab's index and is checked on each change, so the library cannot grow
     * without end.
     */
    public static final int MAX_PROGRAMS = 256;
    public static final long MAX_CHARACTERS = 4_000_000;

    private final Path folder;
    private final Catalog catalog;
    private final Map<String, String> texts = new TreeMap<>();
    private final Map<String, String> skipped = new TreeMap<>();

    private Library(Path folder, Catalog catalog) {
        this.folder = folder;
        this.catalog = catalog;
    }

    /** Reads every {@code .ae2f} file of the folder; a missing folder is an empty library. */
    public static Library load(Path folder, Catalog catalog) throws IOException {
        var library = new Library(folder, catalog);
        if (!Files.isDirectory(folder)) {
            return library;
        }
        List<Path> paths;
        try (var listing = Files.list(folder)) {
            paths = listing.sorted().toList();
        }
        for (var path : paths) {
            var file = path.getFileName().toString();
            if (!file.endsWith(EXTENSION) || !Files.isRegularFile(path)) {
                continue;
            }
            var name = file.substring(0, file.length() - EXTENSION.length());
            var problem = library.nameProblem(name);
            if (problem != null) {
                library.skipped.put(file, problem);
                continue;
            }
            try {
                var text = Files.readString(path, StandardCharsets.UTF_8);
                if (text.length() > MAX_TEXT) {
                    library.skipped.put(file, "longer than " + MAX_TEXT + " characters");
                } else {
                    library.texts.put(name, text);
                }
            } catch (MalformedInputException e) {
                library.skipped.put(file, "not UTF-8 text");
            }
        }
        return library;
    }

    /** The programs, by name. */
    public Set<String> names() {
        return Collections.unmodifiableSet(texts.keySet());
    }

    /** Files of the folder that were not loaded, with the reason. */
    public Map<String, String> skipped() {
        return Collections.unmodifiableMap(skipped);
    }

    public String text(String name) {
        var text = texts.get(name);
        if (text == null) {
            throw new IllegalArgumentException("there is no program " + name);
        }
        return text;
    }

    public ParseResult parse(String name) {
        return ScriptParser.parse(text(name));
    }

    /** What a file is checked against: every other file. */
    public Workspace workspace(String name) {
        var files = new LinkedHashMap<String, Program>();
        texts.forEach((other, text) -> {
            if (!other.equals(name)) {
                files.put(other, ScriptParser.parse(text).program());
            }
        });
        return new Workspace(files, catalog);
    }

    public Checker.Result check(String name) {
        return Checker.check(parse(name).program(), workspace(name));
    }

    /** The other programs a program calls directly, as {@code file.function(...)}. */
    public Set<String> calledFiles(String name) {
        var called = new TreeSet<String>();
        for (var f : parse(name).program().functions()) {
            var locals = Canvas.locals(f);
            Effects.calls(f.body(), call -> {
                if (call.receiver() instanceof Expr.Name n && !n.name().equals(name) && texts.containsKey(n.name())
                        && !locals.contains(n.name())) {
                    called.add(n.name());
                }
            });
        }
        return called;
    }

    /** Stores a program's text as given, creating or replacing its file. */
    public void save(String name, String text) throws IOException {
        var problem = nameProblem(name);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        room(Map.of(name, text), Set.of());
        write(name, text);
    }

    /** Throws unless the library still fits its caps once {@code written} is saved and {@code removed} is gone. */
    private void room(Map<String, String> written, Set<String> removed) {
        for (var text : written.values()) {
            if (text.length() > MAX_TEXT) {
                throw new IllegalArgumentException("a program may hold at most " + MAX_TEXT + " characters");
            }
        }
        var after = new HashMap<>(texts);
        removed.forEach(after::remove);
        after.putAll(written);
        if (after.size() > texts.size() && after.size() > MAX_PROGRAMS) {
            throw new IllegalArgumentException("the library holds at most " + MAX_PROGRAMS + " programs");
        }
        long before = characters(texts);
        long now = characters(after);
        if (now > before && now > MAX_CHARACTERS) {
            throw new IllegalArgumentException("the library holds at most " + MAX_CHARACTERS + " characters");
        }
    }

    private static long characters(Map<String, String> texts) {
        return texts.values().stream().mapToLong(String::length).sum();
    }

    /** Stores an edited definition: its text is printed again in place, and the rest of the file is kept. */
    public void replace(String name, Definition edited) throws IOException {
        save(name, splice(text(name), edited));
    }

    /** Deletes a program; refused while other files call it. */
    public void delete(String name) throws IOException {
        text(name);
        var callers = new TreeSet<String>();
        for (var other : texts.keySet()) {
            if (!other.equals(name) && !qualifiedCalls(other, name, null).isEmpty()) {
                callers.add(other);
            }
        }
        if (!callers.isEmpty()) {
            throw new IllegalArgumentException(name + " is called from " + String.join(", ", callers));
        }
        Files.deleteIfExists(file(name));
        texts.remove(name);
    }

    /**
     * Renames a function and rewrites every call of it: bare calls in its own file and {@code file.name(...)} in the
     * others. Refused where the new name would change what the program means. Gives the files written.
     */
    public Set<String> renameFunction(String name, String from, String to) throws IOException {
        var program = parse(name).program();
        var function = program.functions().stream().filter(f -> f.name().equals(from)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(name + " has no function " + from));
        if (!Edits.isName(to)) {
            throw new IllegalArgumentException("'" + to + "' cannot name a function");
        }
        if (program.definitions().stream().anyMatch(d -> d.name().equals(to))) {
            throw new IllegalArgumentException(name + " already has " + to);
        }
        var changes = new LinkedHashMap<String, List<Change>>();
        var own = new ArrayList<Change>();
        var positions = parse(name).positions();
        var tokens = Lexer.lex(text(name), new ArrayList<>());
        own.add(new Change(nameAfter(tokens, positions.get(function.id()), "def"), from, to));
        for (var caller : program.functions()) {
            var locals = Canvas.locals(caller);
            var calls = new ArrayList<Expr.Call>();
            Effects.calls(caller.body(), call -> {
                if (call.receiver() == null && call.name().equals(from) && !locals.contains(from)) {
                    calls.add(call);
                }
            });
            if (!calls.isEmpty() && locals.contains(to)) {
                throw new IllegalArgumentException(to + " is a variable of " + caller.name() + ", which calls " + from);
            }
            calls.forEach(call -> own.add(new Change(token(tokens, positions.get(call.id())), from, to)));
        }
        changes.put(name, own);
        for (var other : texts.keySet()) {
            if (!other.equals(name)) {
                var calls = qualifiedCalls(other, name, from);
                if (!calls.isEmpty()) {
                    if (to.startsWith("_")) {
                        throw new IllegalArgumentException(from + " is called from " + other + "; a name starting"
                                + " with _ is private to " + name);
                    }
                    changes.put(other, calls.stream().map(c -> new Change(c.after(), from, to)).toList());
                }
            }
        }
        return apply(changes, Map.of());
    }

    /** Renames a file and rewrites every {@code file.name(...)} call of it. Gives the files written, by new name. */
    public Set<String> renameFile(String from, String to) throws IOException {
        text(from);
        var problem = nameProblem(to);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        if (texts.containsKey(to)) {
            throw new IllegalArgumentException("there is already a program " + to);
        }
        var changes = new LinkedHashMap<String, List<Change>>();
        for (var other : texts.keySet()) {
            if (other.equals(from)) {
                continue;
            }
            var calls = qualifiedCalls(other, from, null);
            if (calls.isEmpty()) {
                continue;
            }
            for (var function : parse(other).program().functions()) {
                if (Canvas.locals(function).contains(to)
                        && calls.stream().anyMatch(c -> c.function().name().equals(function.name()))) {
                    throw new IllegalArgumentException(to + " is a variable of " + other + "." + function.name()
                            + ", which calls " + from);
                }
            }
            changes.put(other, calls.stream().map(c -> new Change(c.receiver(), from, to)).toList());
        }
        changes.put(from, List.of());
        return apply(changes, Map.of(from, to));
    }

    // ---- names and files ----

    /** Why {@code name} cannot name a program, or {@code null}. */
    private String nameProblem(String name) {
        if (!Edits.isName(name)) {
            return "'" + name + "' is not a name a program can have";
        }
        if (catalog.namespaces().containsKey(name)) {
            return "'" + name + "' is a standard namespace";
        }
        if (Checker.BUILT_INS.contains(name)) {
            return "'" + name + "' is a built-in function";
        }
        return null;
    }

    private Path file(String name) {
        return folder.resolve(name + EXTENSION);
    }

    /** Writes through a temporary file and a rename, so a crash leaves the old text or the new, never half. */
    private void write(String name, String text) throws IOException {
        Files.createDirectories(folder);
        var temporary = folder.resolve("." + name + EXTENSION + ".tmp");
        Files.writeString(temporary, text, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, file(name), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary, file(name), StandardCopyOption.REPLACE_EXISTING);
        }
        texts.put(name, text);
    }

    // ---- rewriting text ----

    /** One name replaced at a token, given by line and column. */
    private record Change(Lexer.Token at, String from, String to) {
    }

    /** A {@code file.name(...)} call: its function, and the tokens of the file name and the function name. */
    private record QualifiedCall(FunctionDef function, Lexer.Token receiver, Lexer.Token after) {
    }

    /** The calls in {@code caller} of a function of {@code file} ({@code null}: any function). */
    private List<QualifiedCall> qualifiedCalls(String caller, String file, String function) {
        var parsed = parse(caller);
        var tokens = Lexer.lex(text(caller), new ArrayList<>());
        var found = new ArrayList<QualifiedCall>();
        for (var f : parsed.program().functions()) {
            var locals = Canvas.locals(f);
            Effects.calls(f.body(), call -> {
                if (call.receiver() instanceof Expr.Name n && n.name().equals(file) && !locals.contains(file)
                        && (function == null || call.name().equals(function))) {
                    var receiver = token(tokens, parsed.positions().get(n.id()));
                    found.add(new QualifiedCall(f, receiver, tokens.get(tokens.indexOf(receiver) + 2)));
                }
            });
        }
        return found;
    }

    private static Lexer.Token token(List<Lexer.Token> tokens, Position at) {
        return tokens.stream().filter(t -> t.line() == at.line() && t.column() == at.column()).findFirst()
                .orElseThrow(() -> new IllegalStateException("no token at " + at));
    }

    /** The name after the keyword at {@code at}, as in {@code def name}. */
    private static Lexer.Token nameAfter(List<Lexer.Token> tokens, Position at, String keyword) {
        var start = token(tokens, at);
        if (!start.text().equals(keyword)) {
            throw new IllegalStateException("expected " + keyword + " at " + at);
        }
        return tokens.get(tokens.indexOf(start) + 1);
    }

    /** Applies the changes to each file's text, then writes them all; {@code moves} renames files. */
    private Set<String> apply(Map<String, List<Change>> changes, Map<String, String> moves) throws IOException {
        var written = new LinkedHashMap<String, String>();
        changes.forEach((name, list) -> written.put(moves.getOrDefault(name, name), rewrite(text(name), list)));
        // Checked for all the files first, so a refusal writes none of them.
        room(written, moves.keySet());
        for (var entry : written.entrySet()) {
            save(entry.getKey(), entry.getValue());
        }
        for (var old : moves.keySet()) {
            Files.deleteIfExists(file(old));
            texts.remove(old);
        }
        return new LinkedHashSet<>(written.keySet());
    }

    /** Replaces each change's token, last first so earlier offsets stay right. */
    private static String rewrite(String text, List<Change> changes) {
        var starts = lineStarts(text);
        var ordered = new ArrayList<>(changes);
        ordered.sort(Comparator.comparingInt((Change c) -> starts.get(c.at().line() - 1) + c.at().column()).reversed());
        var out = new StringBuilder(text);
        for (var change : ordered) {
            if (!change.at().text().equals(change.from())) {
                throw new IllegalStateException("expected " + change.from() + " at line " + change.at().line());
            }
            int offset = starts.get(change.at().line() - 1) + change.at().column() - 1;
            out.replace(offset, offset + change.from().length(), change.to());
        }
        return out.toString();
    }

    /** Where each line starts, breaking lines as the lexer does: at \r\n, \n or \r. */
    private static List<Integer> lineStarts(String text) {
        var starts = new ArrayList<Integer>(List.of(0));
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                i++;
            }
            if (c == '\r' || c == '\n') {
                starts.add(i + 1);
            }
        }
        return starts;
    }

    // ---- splicing an edited definition ----

    /**
     * {@code text} with {@code edited} printed in place of the definition with its ID (IDs as a parse of
     * {@code text} gives them). The definition's own comments above it are part of it; every other line is kept.
     */
    public static String splice(String text, Definition edited) {
        var normal = text.replace("\r\n", "\n");
        var parsed = ScriptParser.parse(normal);
        var definitions = parsed.program().definitions();
        int index = -1;
        for (int i = 0; i < definitions.size(); i++) {
            if (definitions.get(i).id().equals(edited.id())) {
                index = i;
            }
        }
        if (index < 0) {
            throw new IllegalArgumentException("the text has no definition " + edited.id());
        }
        var lines = new ArrayList<>(List.of(normal.split("\n", -1)));
        int start = start(lines, parsed, definitions.get(index));
        int end = index + 1 < definitions.size() ? start(lines, parsed, definitions.get(index + 1))
                : endComments(lines, parsed.program().endComments().size());
        while (end > start && lines.get(end - 1).isBlank()) {
            end--;
        }
        var printed = ScriptPrinter.print(new Program(List.of(edited), List.of()));
        var replacement = List.of(printed.substring(0, printed.length() - 1).split("\n", -1));
        var result = new ArrayList<String>(lines.subList(0, start));
        result.addAll(replacement);
        result.addAll(lines.subList(end, lines.size()));
        return String.join("\n", result);
    }

    /** The first line of a definition (from 0): its comments above it, then its own line. */
    private static int start(List<String> lines, ParseResult parsed, Definition definition) {
        int first = parsed.positions().get(definition.id()).line() - 1;
        int needed = definition.notes().above().size();
        for (int i = first - 1; i >= 0 && needed > 0; i--) {
            var line = lines.get(i).strip();
            if (line.startsWith("#")) {
                first = i;
                needed--;
            } else if (!line.isEmpty()) {
                break;
            }
        }
        return first;
    }

    /** Where the file's closing comments start (from 0); the end of the text when it has none. */
    private static int endComments(List<String> lines, int count) {
        int end = lines.size();
        for (int i = lines.size() - 1; i >= 0 && count > 0; i--) {
            var line = lines.get(i).strip();
            if (line.startsWith("#")) {
                end = i;
                count--;
            } else if (!line.isEmpty()) {
                break;
            }
        }
        return end;
    }
}
