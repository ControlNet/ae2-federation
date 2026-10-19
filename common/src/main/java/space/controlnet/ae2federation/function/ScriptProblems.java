package space.controlnet.ae2federation.function;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A script's problems as the editor marks them: its parse errors, or, when it parses, what the checker finds. A
 * checker problem covers its node from where it starts to where it ends, on its first line, except that a call covers
 * the name of the function it calls; a parse error covers the word it is at, or one column.
 */
public final class ScriptProblems {
    /** A problem: its line, the columns it covers (from 1, the end excluded) and its message. */
    public record Mark(int line, int start, int end, String message) {
    }

    private ScriptProblems() {
    }

    /** The problems of {@code text}, checked against {@code workspace}, in text order. */
    public static List<Mark> check(String text, Workspace workspace) {
        var lines = text.split("\n", -1);
        var parsed = ScriptParser.parse(text);
        var marks = new ArrayList<Mark>();
        if (!parsed.ok()) {
            parsed.errors().forEach(error -> marks.add(new Mark(error.line(), error.column(),
                    wordEnd(lines, error.line(), error.column()), error.message())));
            return marks;
        }
        var calls = new HashMap<String, Expr.Call>();
        parsed.program().functions().forEach(function -> Effects.calls(function.body(),
                call -> calls.put(call.id(), call)));
        for (var problem : Checker.check(parsed.program(), workspace).problems()) {
            var start = parsed.positions().get(problem.node());
            if (start == null) {
                marks.add(new Mark(1, 1, 1, problem.message()));
                continue;
            }
            var call = calls.get(problem.node());
            if (call != null) {
                var name = Pattern.compile("\\b" + Pattern.quote(call.name()) + "\\s*\\(")
                        .matcher(line(lines, start.line()));
                if (name.find(start.column() - 1)) {
                    marks.add(new Mark(start.line(), name.start() + 1, name.start() + 1 + call.name().length(),
                            problem.message()));
                    continue;
                }
            }
            var end = parsed.ends().get(problem.node());
            int to;
            if (end != null && end.line() > start.line()) {
                to = line(lines, start.line()).length() + 1;
            } else if (end != null && end.line() == start.line() && end.column() > start.column()) {
                to = end.column();
            } else {
                to = wordEnd(lines, start.line(), start.column());
            }
            marks.add(new Mark(start.line(), start.column(), to, problem.message()));
        }
        marks.sort(Comparator.comparingInt(Mark::line).thenComparingInt(Mark::start));
        return marks;
    }

    private static String line(String[] lines, int line) {
        return line >= 1 && line <= lines.length ? lines[line - 1] : "";
    }

    /** Just after the word (letters, digits, _) at {@code column}, or one column on when none is there. */
    private static int wordEnd(String[] lines, int line, int column) {
        var text = line(lines, line);
        int end = column - 1;
        while (end < text.length() && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_')) {
            end++;
        }
        return Math.max(end + 1, column + 1);
    }
}
