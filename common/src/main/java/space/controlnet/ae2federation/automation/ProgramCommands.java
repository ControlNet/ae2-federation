package space.controlnet.ae2federation.automation;

import java.io.UncheckedIOException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import space.controlnet.ae2federation.function.Library;

/**
 * {@code /ae2federation programs reload} reads the programs folder again; {@code list} shows each program with its
 * problems. Both report what a player would otherwise only see in the editor.
 */
public final class ProgramCommands {
    private ProgramCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        // Brigadier merges this root with the other ae2federation commands, but not its requirement: set it here too.
        event.getDispatcher().register(Commands.literal("ae2federation")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("programs")
                        .then(Commands.literal("reload").executes(context -> {
                            var source = context.getSource();
                            try {
                                var library = ProgramStore.get(source.getServer()).reload();
                                source.sendSuccess(() -> Component.literal("Loaded " + library.names().size()
                                        + " program(s)" + (library.skipped().isEmpty() ? ""
                                                : ", skipped " + library.skipped().size())), true);
                                library.skipped().forEach((file, reason) -> source.sendSuccess(
                                        () -> Component.literal("  skipped " + file + ": " + reason), false));
                                return library.names().size();
                            } catch (UncheckedIOException e) {
                                source.sendFailure(Component.literal(e.getMessage()));
                                return 0;
                            }
                        }))
                        .then(Commands.literal("list").executes(context -> list(context.getSource())))));
    }

    private static int list(CommandSourceStack source) {
        Library library;
        try {
            library = ProgramStore.get(source.getServer()).library();
        } catch (UncheckedIOException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        if (library.names().isEmpty()) {
            source.sendSuccess(() -> Component.literal("No programs"), false);
        }
        for (var name : library.names()) {
            var parsed = library.parse(name);
            var problems = parsed.ok() ? library.check(name).problems().stream().map(problem -> {
                var at = parsed.positions().get(problem.node());
                return (at == null ? "" : "line " + at.line() + ": ") + problem.message();
            }).toList() : parsed.errors().stream().map(error -> "line " + error.line() + ": " + error.message()).toList();
            source.sendSuccess(() -> Component.literal(name + ": " + (problems.isEmpty() ? "ok"
                    : problems.size() + " problem(s)")), false);
            problems.forEach(problem -> source.sendSuccess(() -> Component.literal("  " + problem), false));
        }
        return library.names().size();
    }
}
