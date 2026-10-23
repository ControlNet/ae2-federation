package space.controlnet.ae2federation.automation;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import space.controlnet.ae2federation.script.Library;
import space.controlnet.ae2federation.script.ListValue;
import space.controlnet.ae2federation.script.Ops;

/**
 * {@code /ae2federation programs reload} reads the programs folder again; {@code list} shows each program with its
 * problems. Both report what a player would otherwise only see in the editor. Until the Automation tab exists, the
 * other commands install and run functions on computing devices: {@code push}, {@code devices}, {@code interfaces},
 * {@code install}, {@code installations}, {@code uninstall}, {@code trigger} (manual, redstone, every, when, on),
 * {@code interval}, {@code overlap}, {@code run} and {@code log}. The tab calls the same {@link ProgramScheduler}
 * methods through {@link ProgramSync}.
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
                                ProgramSync.get(source.getServer()).changed();
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
                        .then(Commands.literal("list").executes(context -> list(context.getSource())))
                        .then(Commands.literal("push").then(Commands.argument("file", StringArgumentType.word())
                                .executes(context -> act(context, scheduler -> {
                                    var texts = scheduler.push(StringArgumentType.getString(context, "file"));
                                    return "Pushed " + String.join(", ", texts.keySet());
                                }))))
                        .then(Commands.literal("devices").executes(context -> devices(context.getSource())))
                        .then(Commands.literal("interfaces").executes(context -> interfaces(context.getSource())))
                        .then(Commands.literal("install").then(Commands.argument("device", StringArgumentType.word())
                                .then(Commands.argument("file", StringArgumentType.word())
                                        .then(Commands.argument("function", StringArgumentType.word())
                                                .executes(context -> install(context, ""))
                                                .then(Commands.argument("args", StringArgumentType.greedyString())
                                                        .executes(context -> install(context,
                                                                StringArgumentType.getString(context, "args"))))))))
                        .then(Commands.literal("installations")
                                .executes(context -> installations(context.getSource())))
                        .then(Commands.literal("uninstall").then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> act(context, scheduler -> scheduler.uninstall(id(context))
                                        ? "Uninstalled " + id(context) : "There is no installation " + id(context)))))
                        .then(Commands.literal("trigger").then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.literal("manual").executes(context -> trigger(context,
                                        new Installation.Trigger.Manual())))
                                .then(Commands.literal("redstone").executes(context -> trigger(context,
                                        new Installation.Trigger.Redstone())))
                                .then(Commands.literal("every").then(Commands.argument("ticks",
                                        IntegerArgumentType.integer(1)).executes(context -> trigger(context,
                                        new Installation.Trigger.Schedule(
                                                IntegerArgumentType.getInteger(context, "ticks"))))))
                                .then(Commands.literal("on").then(Commands.argument("event",
                                        StringArgumentType.word()).suggests((context, builder) -> {
                                            ProgramEvents.EVENTS.keySet().forEach(builder::suggest);
                                            return builder.buildFuture();
                                        }).executes(context -> trigger(context, new Installation.Trigger.Event(
                                                StringArgumentType.getString(context, "event"), List.of())))
                                        .then(Commands.argument("args", StringArgumentType.greedyString())
                                                .executes(context -> trigger(context, new Installation.Trigger.Event(
                                                        StringArgumentType.getString(context, "event"),
                                                        List.copyOf(TriggerText.words(
                                                                StringArgumentType.getString(context, "args")))))))))
                                .then(Commands.literal("when").then(Commands.argument("function",
                                        StringArgumentType.word()).executes(context -> trigger(context,
                                        new Installation.Trigger.Condition(
                                                StringArgumentType.getString(context, "function"), List.of())))
                                        .then(Commands.argument("args", StringArgumentType.greedyString())
                                                .executes(context -> trigger(context, new Installation.Trigger
                                                        .Condition(StringArgumentType.getString(context, "function"),
                                                        List.copyOf(TriggerText.words(
                                                                StringArgumentType.getString(context, "args")))))))))))
                        .then(Commands.literal("interval").then(Commands.argument("id", StringArgumentType.word())
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(1))
                                        .executes(context -> act(context, scheduler -> describe(scheduler.setInterval(
                                                id(context), IntegerArgumentType.getInteger(context, "ticks"))))))))
                        .then(Commands.literal("overlap").then(Commands.argument("id", StringArgumentType.word())
                                .then(overlap("ignore")).then(overlap("restart")).then(overlap("parallel"))))
                        .then(Commands.literal("run").then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> act(context, scheduler -> {
                                    scheduler.trigger(id(context));
                                    return "Triggered " + id(context);
                                }))))
                        .then(Commands.literal("log").then(Commands.argument("id", StringArgumentType.word())
                                .executes(context -> log(context.getSource(), id(context)))))));
    }

    private static String id(CommandContext<CommandSourceStack> context) {
        return StringArgumentType.getString(context, "id");
    }

    /** Runs a scheduler call and reports its message, or the reason it was refused. */
    private static int act(CommandContext<CommandSourceStack> context, Function<ProgramScheduler, String> call) {
        var source = context.getSource();
        try {
            var message = call.apply(ProgramScheduler.get(source.getServer()));
            ProgramSync.get(source.getServer()).changed();
            source.sendSuccess(() -> Component.literal(message), true);
            return 1;
        } catch (IllegalArgumentException | UncheckedIOException e) {
            source.sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int install(CommandContext<CommandSourceStack> context, String args) {
        return act(context, scheduler -> "Installed " + describe(scheduler.install(
                StringArgumentType.getString(context, "device"), StringArgumentType.getString(context, "file"),
                StringArgumentType.getString(context, "function"), TriggerText.words(args))));
    }

    private static int trigger(CommandContext<CommandSourceStack> context, Installation.Trigger trigger) {
        return act(context, scheduler -> describe(scheduler.setTrigger(id(context), trigger)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> overlap(String mode) {
        return Commands.literal(mode).executes(context -> act(context, scheduler -> describe(scheduler.setOverlap(
                id(context), Installation.Overlap.valueOf(mode.toUpperCase(Locale.ROOT))))));
    }

    static String describe(Installation installation) {
        return installation.id() + ": " + installation.file() + "." + installation.function()
                + Ops.shown(new ListValue(installation.args())) + " on " + installation.device() + ", "
                + TriggerText.format(installation.trigger()) + ", at most every " + installation.interval()
                + " ticks, " + installation.overlap().name().toLowerCase(Locale.ROOT);
    }

    private static int devices(CommandSourceStack source) {
        var devices = ComputingDevices.get(source.getServer().overworld()).entries();
        if (devices.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No computing devices"), false);
        }
        devices.forEach((id, entry) -> source.sendSuccess(() -> Component.literal(id
                + (entry.alias().isEmpty() ? "" : " (" + entry.alias() + ")") + " at "
                + entry.position().toShortString() + " in " + entry.dimension()), false));
        return devices.size();
    }

    private static int interfaces(CommandSourceStack source) {
        var interfaces = AutomationInterfaces.get(source.getServer().overworld()).entries();
        if (interfaces.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No automation interfaces"), false);
        }
        interfaces.forEach((id, entry) -> source.sendSuccess(() -> Component.literal(id
                + (entry.alias().isEmpty() ? "" : " (" + entry.alias() + ")") + " at "
                + entry.position().toShortString() + " in " + entry.dimension()), false));
        return interfaces.size();
    }

    private static int installations(CommandSourceStack source) {
        var scheduler = ProgramScheduler.get(source.getServer());
        var installations = ComputingDevices.get(source.getServer().overworld()).installations();
        if (installations.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No installations"), false);
        }
        installations.forEach(installation -> source.sendSuccess(() -> Component.literal(describe(installation)
                + (scheduler.running(installation.id()) > 0 ? ", running" : "")), false));
        return installations.size();
    }

    private static int log(CommandSourceStack source, String id) {
        var log = ProgramScheduler.get(source.getServer()).log(id);
        if (log.isEmpty()) {
            source.sendSuccess(() -> Component.literal("Nothing logged for " + id), false);
        }
        log.forEach(entry -> source.sendSuccess(() -> Component.literal("[" + entry.tick() + "] " + entry.text()),
                false));
        return log.size();
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
            }).toList() : parsed.errors().stream()
                    .map(error -> "line " + error.line() + ": " + error.message()).toList();
            source.sendSuccess(() -> Component.literal(name + ": " + (problems.isEmpty() ? "ok"
                    : problems.size() + " problem(s)")), false);
            problems.forEach(problem -> source.sendSuccess(() -> Component.literal("  " + problem), false));
        }
        return library.names().size();
    }
}
