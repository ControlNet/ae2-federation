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
import space.controlnet.ae2federation.function.Program;
import space.controlnet.ae2federation.function.Runner;
import space.controlnet.ae2federation.function.Workspace;

/**
 * One run of a library function: the file is checked against the other files and the domain's catalog, every file
 * that parses is compiled, and the {@link Runner} is ticked by the caller. A file that does not check clean does not
 * start: a run never meets a mistake the checker would have reported.
 */
public final class ProgramRun {
    private final Runner runner;

    private ProgramRun(Runner runner) {
        this.runner = runner;
    }

    /** Starts {@code file.function(args)}; throws {@link IllegalArgumentException} with the reason it cannot. */
    public static ProgramRun start(Library library, Catalog catalog, Evaluator.Host host, String file,
            String function, List<Object> args) {
        if (!library.names().contains(file)) {
            throw new IllegalArgumentException("there is no program " + file);
        }
        var parsed = library.parse(file);
        if (!parsed.ok()) {
            throw new IllegalArgumentException(file + " has " + parsed.errors().size() + " syntax error(s), first: line "
                    + parsed.errors().get(0).line() + ": " + parsed.errors().get(0).message());
        }
        var files = new LinkedHashMap<String, Program>();
        for (var name : library.names()) {
            var other = name.equals(file) ? parsed : library.parse(name);
            if (other.ok()) {
                files.put(name, other.program());
            }
        }
        var others = new LinkedHashMap<>(files);
        others.remove(file);
        var problems = Checker.check(parsed.program(), new Workspace(others, catalog)).problems();
        if (!problems.isEmpty()) {
            var first = problems.get(0);
            var at = parsed.positions().get(first.node());
            throw new IllegalArgumentException(file + " has " + problems.size() + " problem(s), first: "
                    + (at == null ? "" : "line " + at.line() + ": ") + first.message());
        }
        var workspace = new Workspace(files, catalog);
        if (parsed.program().functions().stream().noneMatch(f -> f.name().equals(function))) {
            throw new IllegalArgumentException(file + " has no function " + function);
        }
        Map<String, Map<String, Graph>> graphs = new HashMap<>();
        files.keySet().forEach(name -> graphs.put(name, Compiler.compile(name, workspace)));
        return new ProgramRun(new Runner(workspace, graphs, host, (where, node, value) -> {
        }, file, function, args));
    }

    public Runner.Status tick(int budget) {
        return runner.tick(budget);
    }

    public Runner runner() {
        return runner;
    }
}
