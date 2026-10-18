package space.controlnet.ae2federation.automation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import space.controlnet.ae2federation.function.Catalog;
import space.controlnet.ae2federation.function.Checker;
import space.controlnet.ae2federation.function.Compiler;
import space.controlnet.ae2federation.function.Evaluator;
import space.controlnet.ae2federation.function.Graph;
import space.controlnet.ae2federation.function.Library;
import space.controlnet.ae2federation.function.ParseResult;
import space.controlnet.ae2federation.function.Program;
import space.controlnet.ae2federation.function.Runner;
import space.controlnet.ae2federation.function.ScriptParser;
import space.controlnet.ae2federation.function.Workspace;

/**
 * One run of a library function. A set of program texts is {@link #prepare prepared} once (parsed and compiled);
 * each start checks the file against the others and the domain's catalog, since names in the world change, and the
 * caller ticks the {@link Runner}. A file that does not check clean does not start: a run never meets a mistake the
 * checker would have reported.
 */
public final class ProgramRun {
    /** Parsed and compiled program texts, shared by the runs that start from them. */
    public static final class Prepared {
        private final Map<String, ParseResult> parsed;
        private final Map<String, Program> files;
        private final Map<String, Map<String, Graph>> graphs = new HashMap<>();

        private Prepared(Map<String, ParseResult> parsed, Catalog catalog) {
            this.parsed = parsed;
            this.files = new LinkedHashMap<>();
            parsed.forEach((name, result) -> {
                if (result.ok()) {
                    files.put(name, result.program());
                }
            });
            // Compiling reads only the catalog's namespaces, which every catalog shares.
            var workspace = new Workspace(files, catalog);
            files.keySet().forEach(name -> graphs.put(name, Compiler.compile(name, workspace)));
        }
    }

    private final Prepared prepared;
    private final Runner runner;

    private ProgramRun(Prepared prepared, Runner runner) {
        this.prepared = prepared;
        this.runner = runner;
    }

    public static Prepared prepare(Map<String, String> texts, Catalog catalog) {
        var parsed = new LinkedHashMap<String, ParseResult>();
        texts.forEach((name, text) -> parsed.put(name, ScriptParser.parse(text)));
        return new Prepared(parsed, catalog);
    }

    /** Starts {@code file.function(args)} from the library as it is now. */
    public static ProgramRun start(Library library, Catalog catalog, Evaluator.Host host, String file,
            String function, List<Object> args) {
        var texts = new LinkedHashMap<String, String>();
        library.names().forEach(name -> texts.put(name, library.text(name)));
        return start(prepare(texts, catalog), catalog, host, file, function, args);
    }

    /** Starts {@code file.function(args)}; throws {@link IllegalArgumentException} with the reason it cannot. */
    public static ProgramRun start(Prepared prepared, Catalog catalog, Evaluator.Host host, String file,
            String function, List<Object> args) {
        var parsed = prepared.parsed.get(file);
        if (parsed == null) {
            throw new IllegalArgumentException("there is no program " + file);
        }
        if (!parsed.ok()) {
            throw new IllegalArgumentException(file + " has " + parsed.errors().size()
                    + " syntax error(s), first: line " + parsed.errors().get(0).line() + ": "
                    + parsed.errors().get(0).message());
        }
        var others = new LinkedHashMap<>(prepared.files);
        others.remove(file);
        var problems = Checker.check(parsed.program(), new Workspace(others, catalog)).problems();
        if (!problems.isEmpty()) {
            var first = problems.get(0);
            var at = parsed.positions().get(first.node());
            throw new IllegalArgumentException(file + " has " + problems.size() + " problem(s), first: "
                    + (at == null ? "" : "line " + at.line() + ": ") + first.message());
        }
        if (parsed.program().functions().stream().noneMatch(f -> f.name().equals(function))) {
            throw new IllegalArgumentException(file + " has no function " + function);
        }
        return new ProgramRun(prepared, new Runner(new Workspace(prepared.files, catalog), prepared.graphs, host,
                (where, node, value) -> {
                }, file, function, args));
    }

    public Runner.Status tick(int budget) {
        return runner.tick(budget);
    }

    public Runner runner() {
        return runner;
    }

    /** Why the run failed, as Python prints it, with the file and line: {@code ZeroDivisionError: ... (f line 3)}. */
    public String failure() {
        var failure = runner.failure();
        var parsed = prepared.parsed.get(failure.file());
        var at = parsed == null || failure.error().node() == null ? null
                : parsed.positions().get(failure.error().node());
        return failure.error() + " (" + failure.file() + (at == null ? "" : " line " + at.line()) + ")";
    }
}
