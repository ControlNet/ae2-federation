package space.controlnet.ae2federation.automation;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.MinecraftServer;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.script.DryRun;
import space.controlnet.ae2federation.script.RunError;
import space.controlnet.ae2federation.script.ScriptParser;

/**
 * The editor's dry run as the tab asks for it: the server runs a function of a program's saved text at once, with
 * real reads and simulated actions ({@link ProgramHost#dryRun}), and answers with what each node of the file gave.
 * The answer travels as the text of a request's answer.
 *
 * <p>Text form, one entry per line: the version, the status, the failing node and error (tab-separated, empty when
 * none), then {@code +node} for each node that ran and {@code =node<tab>value} for each value, until
 * {@link #MAX_ANSWER} characters.
 */
public final class ProgramDryRun {
    /** Room left in a request's answer, which carries at most 32767 characters. */
    static final int MAX_ANSWER = 30_000;

    public enum Status {
        DONE,
        FAILED,
        /** The budget ran out before the function returned. */
        STOPPED
    }

    /**
     * A dry run of the program's {@code version}: each node's last value as its repr, the nodes that ran, and the node
     * and error that stopped it (both empty when none).
     */
    public record Answer(String version, Status status, String failureNode, String failure, Map<String, String> values,
            Set<String> ran) {
    }

    private ProgramDryRun() {
    }

    /**
     * {@code dry domain file function version args...}: one argument as text per parameter, converted by the
     * parameters' types as an installation's are. A version other than the saved text's is refused, so the editor
     * never shows values of a text it no longer shows.
     */
    static String answer(MinecraftServer server, List<String> args) {
        if (args.size() < 4) {
            throw new IllegalArgumentException("dry takes a domain, a file, a function and a version");
        }
        var domain = args.get(0);
        var file = args.get(1);
        var function = args.get(2);
        if (domain.isBlank()) {
            throw new IllegalArgumentException("no domain");
        }
        var library = ProgramStore.get(server).library();
        if (!library.names().contains(file)) {
            throw new IllegalArgumentException("there is no program " + file);
        }
        var text = library.text(file);
        var version = ProgramSync.version(text);
        if (!version.equals(args.get(3))) {
            throw new IllegalArgumentException("the saved version is " + version);
        }
        var definition = ScriptParser.parse(text).program().functions().stream()
                .filter(f -> f.name().equals(function)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(file + " has no function " + function));
        var values = ProgramScheduler.arguments(definition, args.subList(4, args.size()));
        try {
            var host = ProgramHost.dryRun(server, new FederationDomainId(domain));
            return encode(version, ProgramRun.dryRun(library, host.catalog(), host, file, function, values));
        } catch (RunError e) {
            throw new IllegalArgumentException(e.type() + ": " + e.getMessage());
        }
    }

    static String encode(String version, DryRun.Result result) {
        var out = new StringBuilder();
        var status = result.failure() != null ? Status.FAILED : result.finished() ? Status.DONE : Status.STOPPED;
        out.append(version).append('\n').append(status.name()).append('\n');
        if (result.failure() != null) {
            var error = result.failure().error();
            out.append(line(result.failure().node() == null ? "" : result.failure().node())).append('\t')
                    .append(line(error.type() + ": " + error.getMessage()));
        }
        out.append('\n');
        for (var node : result.ran()) {
            out.append('+').append(node).append('\n');
        }
        for (var entry : result.values().entrySet()) {
            var row = "=" + entry.getKey() + "\t" + line(entry.getValue()) + "\n";
            if (out.length() + row.length() > MAX_ANSWER) {
                break;
            }
            out.append(row);
        }
        return out.toString();
    }

    public static Answer decode(String text) {
        var lines = text.split("\n", -1);
        var failure = lines[2].split("\t", 2);
        var values = new LinkedHashMap<String, String>();
        var ran = new HashSet<String>();
        for (int i = 3; i < lines.length; i++) {
            var line = lines[i];
            if (line.startsWith("+")) {
                ran.add(line.substring(1));
            } else if (line.startsWith("=")) {
                var parts = line.substring(1).split("\t", 2);
                values.put(parts[0], parts.length > 1 ? parts[1] : "");
            }
        }
        return new Answer(lines[0], Status.valueOf(lines[1]), failure[0], failure.length > 1 ? failure[1] : "",
                values, ran);
    }

    /** Text that stays on one line of the answer. */
    private static String line(String text) {
        return text.replace('\n', ' ').replace('\t', ' ').replace('\r', ' ');
    }
}
