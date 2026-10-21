package space.controlnet.ae2federation.client.guide;

import com.lowdragmc.lowdraglib2.gui.ui.elements.codeeditor.language.SyntaxParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import space.controlnet.ae2federation.client.automation.PythonLanguage;
import space.controlnet.ae2federation.client.automation.ScriptChecker;
import space.controlnet.ae2federation.script.Catalog;
import space.controlnet.ae2federation.script.ScriptProblems;
import space.controlnet.ae2federation.script.Workspace;

/**
 * A program shown on a guide page: its lines coloured as the script editor colours them, and the graph of one of its
 * functions. A program with a problem is refused, so a page never shows code the game would not run.
 */
public record GuideProgram(List<List<Run>> lines, ScriptChecker.Graph graph) {
    /** A stretch of a line in one colour (RGB). */
    public record Run(String text, int color) {
    }

    /**
     * Checks {@code text} alone against {@code catalog} and takes the graph of {@code function}, or of the file's
     * first function when it is empty.
     *
     * @throws IllegalArgumentException with the first problem, or when there is no such function
     */
    public static GuideProgram of(String text, String function, Catalog catalog) {
        var workspace = new Workspace(Map.of(), catalog);
        var problems = ScriptProblems.check(text, workspace);
        if (!problems.isEmpty()) {
            var first = problems.get(0);
            throw new IllegalArgumentException("line " + first.line() + ": " + first.message());
        }
        var graphs = ScriptChecker.graphs(text, workspace).orElseThrow();
        var graph = function.isEmpty() ? graphs.values().stream().findFirst().orElse(null) : graphs.get(function);
        if (graph == null) {
            throw new IllegalArgumentException(function.isEmpty() ? "the program has no function"
                    : "the program has no function " + function);
        }
        return new GuideProgram(lines(text), graph);
    }

    /** Each line of {@code text} cut into coloured runs; tabs are four spaces, and trailing blank lines are left out. */
    static List<List<Run>> lines(String text) {
        var parser = new SyntaxParser();
        parser.setLanguageDefinition(PythonLanguage.INSTANCE);
        var lines = new ArrayList<List<Run>>();
        for (var line : text.replace("\r", "").replace("\t", "    ").stripTrailing().split("\n", -1)) {
            lines.add(parser.parseLine(line).stream()
                    .map(token -> new Run(token.text(), PythonLanguage.color(token.type().name))).toList());
        }
        return lines;
    }
}
