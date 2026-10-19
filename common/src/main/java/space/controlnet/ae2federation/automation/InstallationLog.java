package space.controlnet.ae2federation.automation;

import java.util.ArrayList;
import java.util.List;

/**
 * An installation's state and log as the {@code log} request answers it: a line {@code running N}, then one line per
 * entry, newest first, its age in ticks and its text separated by a tab. The Install view asks for it while it shows
 * the installation, so a run's progress never resends the whole index.
 */
public final class InstallationLog {
    /** An entry: how many ticks ago it was logged, and what. */
    public record Line(long age, String text) {
    }

    /** How many runs are going, and the entries, newest first. */
    public record Log(int running, List<Line> lines) {
    }

    private InstallationLog() {
    }

    public static String answer(int running, long now, List<ProgramScheduler.LogEntry> entries) {
        var text = new StringBuilder("running ").append(running);
        for (int i = entries.size() - 1; i >= 0; i--) {
            var entry = entries.get(i);
            text.append('\n').append(Math.max(0, now - entry.tick())).append('\t')
                    .append(entry.text().replace('\t', ' ').replace('\n', ' '));
        }
        return text.toString();
    }

    public static Log parse(String answer) {
        var rows = answer.split("\n");
        int running = Integer.parseInt(rows[0].substring("running ".length()));
        var lines = new ArrayList<Line>();
        for (int i = 1; i < rows.length; i++) {
            int tab = rows[i].indexOf('\t');
            lines.add(new Line(Long.parseLong(rows[i].substring(0, tab)), rows[i].substring(tab + 1)));
        }
        return new Log(running, List.copyOf(lines));
    }

    /** An age as the log box shows it: {@code now}, then seconds, minutes and hours ({@code 12 s}, {@code 3 min}). */
    public static String age(long ticks) {
        long seconds = ticks / 20;
        if (seconds < 1) return "now";
        if (seconds < 60) return seconds + " s";
        if (seconds < 3600) return seconds / 60 + " min";
        return seconds / 3600 + " h";
    }
}
