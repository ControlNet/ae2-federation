package space.controlnet.ae2federation.automation;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import space.controlnet.ae2federation.script.Catalog;
import space.controlnet.ae2federation.script.Checker;
import space.controlnet.ae2federation.script.Compiler;
import space.controlnet.ae2federation.script.DryRun;
import space.controlnet.ae2federation.script.Evaluator;
import space.controlnet.ae2federation.script.Graph;
import space.controlnet.ae2federation.script.Library;
import space.controlnet.ae2federation.script.ParseResult;
import space.controlnet.ae2federation.script.Program;
import space.controlnet.ae2federation.script.Runner;
import space.controlnet.ae2federation.script.ScriptParser;
import space.controlnet.ae2federation.script.Workspace;

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
        return start(prepared, catalog, host, file, function, args, false);
    }

    /**
     * As {@link #start}; with {@code condition}, only a function that gives a {@code bool} and does not act starts, as
     * a condition must. A push can change a condition after it was chosen, so it is checked again at every start.
     */
    public static ProgramRun start(Prepared prepared, Catalog catalog, Evaluator.Host host, String file,
            String function, List<Object> args, boolean condition) {
        var checked = check(prepared, catalog, file, function);
        if (condition) {
            var result = checked.results().get(function);
            if (result == null || !result.name().equals("bool")) {
                throw new IllegalArgumentException(function + " does not give a bool");
            }
            if (checked.effectfulFunctions().contains(function)) {
                throw new IllegalArgumentException(function + " acts on the world; a condition may only read");
            }
        }
        fits(prepared.parsed.get(file).program(), function, args);
        return new ProgramRun(prepared, new Runner(new Workspace(prepared.files, catalog), prepared.graphs, host,
                (where, node, value) -> {
                }, file, function, args));
    }

    /**
     * Throws {@link IllegalArgumentException} unless the arguments, kept since they were typed in, still fit the
     * function's parameters: a push can add a parameter or change its type.
     */
    private static void fits(Program program, String function, List<Object> args) {
        var params = program.functions().stream().filter(f -> f.name().equals(function)).findFirst().orElseThrow()
                .params();
        if (args.size() > params.size()) {
            throw new IllegalArgumentException(function + " now takes " + params.size()
                    + " argument(s); change the installation's arguments");
        }
        for (int i = 0; i < params.size(); i++) {
            var param = params.get(i);
            if (i >= args.size()) {
                if (param.defaultValue() == null) {
                    throw new IllegalArgumentException(function + " now needs " + param.name()
                            + "; change the installation's arguments");
                }
                continue;
            }
            var value = args.get(i);
            var type = param.type();
            var optional = type.name().equals("Optional") && type.args().size() == 1;
            if (optional) {
                type = type.args().get(0);
            }
            boolean fits = value == null ? optional : switch (type.name()) {
                case "int" -> value instanceof Long;
                case "float" -> value instanceof Double || value instanceof Long;
                case "bool" -> value instanceof Boolean;
                case "list", "dict", "set" -> false;
                default -> value instanceof String;
            };
            if (!fits) {
                throw new IllegalArgumentException(param.name() + " of " + function + " is now a " + param.type()
                        + "; change the installation's arguments");
            }
        }
    }

    /**
     * Dry-runs {@code file.function(args)} from the library as it is now, at once; throws
     * {@link IllegalArgumentException} with the reason it cannot start, as {@link #start} does.
     */
    public static DryRun.Result dryRun(Library library, Catalog catalog, Evaluator.Host host, String file,
            String function, List<Object> args) {
        var texts = new LinkedHashMap<String, String>();
        library.names().forEach(name -> texts.put(name, library.text(name)));
        return dryRun(prepare(texts, catalog), catalog, host, file, function, args);
    }

    /** As {@link #dryRun(Library, Catalog, Evaluator.Host, String, String, List)}, from a prepared library. */
    public static DryRun.Result dryRun(Prepared prepared, Catalog catalog, Evaluator.Host host, String file,
            String function, List<Object> args) {
        check(prepared, catalog, file, function);
        return DryRun.run(new Workspace(prepared.files, catalog), prepared.graphs, host, file, function, args,
                DryRun.BUDGET);
    }

    /** Throws {@link IllegalArgumentException} unless the file checks clean and has {@code function}. */
    private static Checker.Result check(Prepared prepared, Catalog catalog, String file, String function) {
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
        var checked = Checker.check(parsed.program(), new Workspace(others, catalog));
        var problems = checked.problems();
        if (!problems.isEmpty()) {
            var first = problems.get(0);
            var at = parsed.positions().get(first.node());
            throw new IllegalArgumentException(file + " has " + problems.size() + " problem(s), first: "
                    + (at == null ? "" : "line " + at.line() + ": ") + first.message());
        }
        if (parsed.program().functions().stream().noneMatch(f -> f.name().equals(function))) {
            throw new IllegalArgumentException(file + " has no function " + function);
        }
        return checked;
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
