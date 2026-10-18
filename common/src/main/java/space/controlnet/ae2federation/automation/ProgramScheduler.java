package space.controlnet.ae2federation.automation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.function.Checker;
import space.controlnet.ae2federation.function.FunctionDef;
import space.controlnet.ae2federation.function.Ops;
import space.controlnet.ae2federation.function.Runner;
import space.controlnet.ae2federation.function.ScriptParser;
import space.controlnet.ae2federation.function.TypeRef;
import space.controlnet.ae2federation.function.Workspace;

/**
 * Runs the installed functions. Each server tick it fires the schedule and condition triggers, starts what the
 * {@link TriggerGate} lets through, and ticks every run with the device's step budget. Runs live in memory: a restart
 * or an unloaded device stops them, and triggers start new ones later. What happened is kept in a short log per
 * installation.
 */
public final class ProgramScheduler {
    /**
     * Steps one run may take per tick on the Creative Computing Device. Its compute is unlimited (user, 2026-10-18);
     * the cap only keeps a loop without effects from stalling the tick (assistant proposal).
     */
    public static final int CREATIVE_STEPS = 10_000;
    static final int LOG_SIZE = 20;
    private static final Map<MinecraftServer, ProgramScheduler> SERVICES = new HashMap<>();

    public record LogEntry(long tick, String text) {
    }

    private static final class State {
        final Installation installation;
        final TriggerGate gate;
        final List<ProgramRun> runs = new ArrayList<>();
        long nextSchedule = Long.MIN_VALUE;
        ProgramRun condition;
        boolean conditionWas;
        long nextCondition = Long.MIN_VALUE;
        /** A resource event's value at the last reading, and when to read it next. */
        Object polled;
        boolean hasPolled;
        long nextPoll = Long.MIN_VALUE;

        State(Installation installation) {
            this.installation = installation;
            this.gate = new TriggerGate(installation.interval(), installation.overlap());
        }
    }

    private final MinecraftServer server;
    private final Map<String, State> states = new HashMap<>();
    private final Map<String, ArrayDeque<LogEntry>> logs = new HashMap<>();
    /** Pushed versions, prepared once per push; by the pushed file. */
    private final Map<String, ProgramRun.Prepared> prepared = new HashMap<>();

    private ProgramScheduler(MinecraftServer server) {
        this.server = server;
    }

    public static ProgramScheduler get(MinecraftServer server) {
        return SERVICES.computeIfAbsent(server, ProgramScheduler::new);
    }

    public static void tickAll(MinecraftServer server) {
        var scheduler = SERVICES.get(server);
        if (scheduler != null) {
            scheduler.tick();
        }
        ProgramEvents.clear();
    }

    public static void closeServer(MinecraftServer server) {
        SERVICES.remove(server);
        ProgramEvents.listen(false, false);
        ProgramEvents.clear();
    }

    private ComputingDevices devices() {
        return ComputingDevices.get(server.overworld());
    }

    /**
     * Pushes the library's version of {@code file}, with every file it calls, to the devices that run its functions;
     * their next runs use it. Refused, with the first problem, unless every one of those files checks clean.
     */
    public Map<String, String> push(String file) {
        var library = ProgramStore.get(server).library();
        if (!library.names().contains(file)) {
            throw new IllegalArgumentException("there is no program " + file);
        }
        var texts = new LinkedHashMap<String, String>();
        var waiting = new ArrayDeque<String>(List.of(file));
        while (!waiting.isEmpty()) {
            var name = waiting.poll();
            if (texts.putIfAbsent(name, library.text(name)) == null) {
                waiting.addAll(library.calledFiles(name));
            }
        }
        var catalog = ProgramStore.catalog(server, null);
        var programs = new LinkedHashMap<String, space.controlnet.ae2federation.function.Program>();
        texts.forEach((name, text) -> {
            var parsed = ScriptParser.parse(text);
            if (!parsed.ok()) {
                throw new IllegalArgumentException(name + " line " + parsed.errors().get(0).line() + ": "
                        + parsed.errors().get(0).message());
            }
            programs.put(name, parsed.program());
        });
        programs.forEach((name, program) -> {
            var others = new LinkedHashMap<>(programs);
            others.remove(name);
            var problems = Checker.check(program, new Workspace(others, catalog)).problems();
            if (!problems.isEmpty()) {
                var at = ScriptParser.parse(texts.get(name)).positions().get(problems.get(0).node());
                throw new IllegalArgumentException(name + (at == null ? "" : " line " + at.line()) + ": "
                        + problems.get(0).message());
            }
        });
        devices().push(file, texts);
        prepared.remove(file);
        return texts;
    }

    /**
     * Installs a public function of a program on a device, with its arguments as typed text, converted by the
     * parameters' types. A program never pushed is pushed first; otherwise the pushed version is the one installed.
     */
    public Installation install(String device, String file, String function, List<String> args) {
        if (devices().entry(device).isEmpty()) {
            throw new IllegalArgumentException("there is no device " + device);
        }
        if (devices().pushed(file).isEmpty()) {
            push(file);
        }
        var definition = definition(file, function);
        return devices().install(device, file, function, arguments(definition, args));
    }

    /** Sets what starts the installation's runs; a condition must be a {@code bool} function that does not act. */
    public Installation setTrigger(String id, Installation.Trigger trigger) {
        var installation = installed(id);
        if (trigger instanceof Installation.Trigger.Condition condition) {
            var definition = definition(installation.file(), condition.function());
            if (!definition.result().name().equals("bool")) {
                throw new IllegalArgumentException(condition.function() + " does not give a bool");
            }
            if (acts(installation.file(), condition.function())) {
                throw new IllegalArgumentException(condition.function()
                        + " acts on the world; a condition may only read");
            }
            trigger = new Installation.Trigger.Condition(condition.function(),
                    arguments(definition, condition.args().stream().map(String::valueOf).toList()));
        }
        if (trigger instanceof Installation.Trigger.Event event) {
            trigger = event(event);
        }
        return update(installation.withTrigger(trigger));
    }

    /** Checks an event's name and arguments: a resource, or an interface and a reach from 1 to 4. */
    private static Installation.Trigger.Event event(Installation.Trigger.Event event) {
        var spec = ProgramEvents.EVENTS.get(event.name());
        if (spec == null) {
            throw new IllegalArgumentException("there is no event " + event.name() + "; one of "
                    + String.join(", ", ProgramEvents.EVENTS.keySet()));
        }
        var args = event.args().stream().map(String::valueOf).toList();
        return switch (spec.kind()) {
            case NETWORK -> {
                if (!args.isEmpty()) {
                    throw new IllegalArgumentException(event.name() + " takes no arguments");
                }
                yield new Installation.Trigger.Event(event.name(), List.of());
            }
            case RESOURCE -> {
                if (args.size() != 1) {
                    throw new IllegalArgumentException(event.name() + " takes a resource");
                }
                yield new Installation.Trigger.Event(event.name(), List.of(args.get(0)));
            }
            case BLOCK -> {
                if (args.isEmpty() || args.size() > 2) {
                    throw new IllegalArgumentException(event.name() + " takes an interface and a reach");
                }
                long reach;
                try {
                    reach = args.size() == 2 ? Long.parseLong(args.get(1)) : 1;
                } catch (NumberFormatException e) {
                    reach = 0;
                }
                if (reach < 1 || reach > 4) {
                    throw new IllegalArgumentException("the reach must be from 1 to 4");
                }
                yield new Installation.Trigger.Event(event.name(), List.of(args.get(0), reach));
            }
        };
    }

    public Installation setInterval(String id, int interval) {
        return update(installed(id).withInterval(interval));
    }

    public Installation setOverlap(String id, Installation.Overlap overlap) {
        return update(installed(id).withOverlap(overlap));
    }

    private Installation update(Installation installation) {
        devices().update(installation);
        return installation;
    }

    public boolean uninstall(String id) {
        stop(id, "uninstalled");
        logs.remove(id);
        return devices().uninstall(id);
    }

    /** A player starts the installation, whatever its trigger; the rate limit and overlap mode still apply. */
    public void trigger(String id) {
        var installation = installed(id);
        var state = state(installation);
        state.gate.fire(state.runs.size());
    }

    /** The device's redstone signal turned on. */
    public void redstone(String device) {
        for (var installation : devices().installations()) {
            if (installation.device().equals(device)
                    && installation.trigger() instanceof Installation.Trigger.Redstone) {
                var state = state(installation);
                state.gate.fire(state.runs.size());
            }
        }
    }

    /** The device was broken: its runs stop. */
    public void deviceGone(String device) {
        for (var installation : devices().installations()) {
            if (installation.device().equals(device)) {
                stop(installation.id(), "the device was broken");
                logs.remove(installation.id());
            }
        }
    }

    public List<LogEntry> log(String id) {
        return List.copyOf(logs.getOrDefault(id, new ArrayDeque<>()));
    }

    public int running(String id) {
        var state = states.get(id);
        return state == null ? 0 : state.runs.size();
    }

    private Installation installed(String id) {
        return devices().installation(id)
                .orElseThrow(() -> new IllegalArgumentException("there is no installation " + id));
    }

    private State state(Installation installation) {
        var state = states.get(installation.id());
        if (state == null || !state.installation.equals(installation)) {
            if (state != null) {
                stop(installation.id(), "its settings changed");
            }
            state = new State(installation);
            states.put(installation.id(), state);
        }
        return state;
    }

    private void stop(String id, String reason) {
        var state = states.remove(id);
        if (state != null && !state.runs.isEmpty()) {
            log(id, "stopped " + state.runs.size() + " run(s): " + reason);
        }
    }

    private void log(String id, String text) {
        var log = logs.computeIfAbsent(id, ignored -> new ArrayDeque<>());
        log.addLast(new LogEntry(server.overworld().getGameTime(), text));
        while (log.size() > LOG_SIZE) {
            log.removeFirst();
        }
    }

    private void tick() {
        var now = server.overworld().getGameTime();
        var seen = new HashSet<String>();
        var gridListeners = false;
        var blockListeners = false;
        for (var installation : devices().installations()) {
            if (installation.trigger() instanceof Installation.Trigger.Event event
                    && ProgramEvents.EVENTS.get(event.name()) instanceof ProgramEvents.Spec spec) {
                gridListeners |= spec.kind() == ProgramEvents.Kind.NETWORK;
                blockListeners |= spec.kind() == ProgramEvents.Kind.BLOCK;
            }
            seen.add(installation.id());
            var state = state(installation);
            var domain = domain(installation.device());
            if (domain.isEmpty()) {
                if (!state.runs.isEmpty() || state.condition != null) {
                    stop(installation.id(), "the device is not loaded");
                }
                continue;
            }
            if (installation.trigger() instanceof Installation.Trigger.Schedule schedule && now >= state.nextSchedule) {
                state.gate.fire(state.runs.size());
                state.nextSchedule = now + schedule.ticks();
            }
            if (installation.trigger() instanceof Installation.Trigger.Condition condition) {
                checkCondition(state, condition, domain.get(), now);
            }
            if (installation.trigger() instanceof Installation.Trigger.Event event) {
                checkEvent(state, event, domain.get(), now);
            }
            var action = state.gate.poll(now, state.runs.size());
            if (action == TriggerGate.Action.RESTART) {
                log(installation.id(), "stopped " + state.runs.size() + " run(s) for a restart");
                state.runs.clear();
            }
            if (action != TriggerGate.Action.NONE) {
                start(installation, installation.function(), installation.args(), domain.get())
                        .ifPresent(run -> {
                            log(installation.id(), "started");
                            state.runs.add(run);
                        });
            }
            state.runs.removeIf(run -> finished(installation.id(), run));
        }
        states.keySet().retainAll(seen);
        ProgramEvents.listen(gridListeners, blockListeners);
    }

    private void checkEvent(State state, Installation.Trigger.Event event, FederationDomainId domain, long now) {
        var spec = ProgramEvents.EVENTS.get(event.name());
        if (spec == null) {
            // Saved by a version that had this event.
            return;
        }
        switch (spec.kind()) {
            case NETWORK -> {
                if (ProgramEvents.anyGridEvent()
                        && ProgramEvents.posted(new ProgramHost(server, domain, state.installation.id()).grids(),
                        event.name())) {
                    state.gate.fire(state.runs.size());
                }
            }
            case RESOURCE -> {
                if (now < state.nextPoll) {
                    return;
                }
                state.nextPoll = now + state.installation.interval();
                var call = switch (event.name()) {
                    case "stock_changed" -> "amount";
                    case "crafting_changed" -> "crafting";
                    default -> "craftable";
                };
                Object value;
                try {
                    value = new ProgramHost(server, domain, state.installation.id()).call("ae2", call,
                            java.util.Arrays.asList(event.args().get(0), null));
                } catch (RuntimeException e) {
                    log(state.installation.id(), "event not read: " + e.getMessage());
                    return;
                }
                // The first reading only sets the baseline.
                if (state.hasPolled && !java.util.Objects.equals(value, state.polled)) {
                    state.gate.fire(state.runs.size());
                }
                state.polled = value;
                state.hasPolled = true;
            }
            case BLOCK -> {
                if (!ProgramEvents.anyBlockEvent()) {
                    return;
                }
                var host = new ProgramHost(server, domain, state.installation.id());
                try {
                    var place = host.place(new ProgramHost.Block(host.interfaceId((String) event.args().get(0)),
                            (int) (long) (Long) event.args().get(1), null));
                    if (place.isPresent() && ProgramEvents.happened(
                            space.controlnet.ae2federation.domain.FederationDomainRegistryAccess.dimension(
                                    place.get().level()), place.get().position(), event.name())) {
                        state.gate.fire(state.runs.size());
                    }
                } catch (space.controlnet.ae2federation.function.RunError e) {
                    // The interface left the domain: nothing to watch until it is back.
                }
            }
        }
    }

    /** Evaluates a condition every interval and fires when it turns true; it is false before its first answer. */
    private void checkCondition(State state, Installation.Trigger.Condition condition, FederationDomainId domain,
            long now) {
        var id = state.installation.id();
        if (state.condition == null && now >= state.nextCondition) {
            state.nextCondition = now + state.installation.interval();
            state.condition = start(state.installation, condition.function(), condition.args(), domain).orElse(null);
        }
        if (state.condition == null) {
            return;
        }
        var status = state.condition.tick(CREATIVE_STEPS);
        if (status == Runner.Status.DONE) {
            var answer = Boolean.TRUE.equals(state.condition.runner().result());
            if (answer && !state.conditionWas) {
                state.gate.fire(state.runs.size());
            }
            state.conditionWas = answer;
            state.condition = null;
        } else if (status == Runner.Status.FAILED) {
            log(id, "condition failed: " + state.condition.failure());
            state.conditionWas = false;
            state.condition = null;
        }
    }

    private Optional<ProgramRun> start(Installation installation, String function, List<Object> args,
            FederationDomainId domain) {
        var texts = devices().pushed(installation.file());
        if (texts.isEmpty()) {
            log(installation.id(), "not started: " + installation.file() + " was never pushed");
            return Optional.empty();
        }
        var host = new ProgramHost(server, domain, installation.id());
        try {
            var program = prepared.computeIfAbsent(installation.file(),
                    file -> ProgramRun.prepare(texts.get(), host.catalog()));
            return Optional.of(ProgramRun.start(program, host.catalog(), host, installation.file(), function, args));
        } catch (IllegalArgumentException e) {
            log(installation.id(), "not started: " + e.getMessage());
            return Optional.empty();
        }
    }

    /** Ticks a run; true when it has ended, which is logged. */
    private boolean finished(String id, ProgramRun run) {
        return switch (run.tick(CREATIVE_STEPS)) {
            case DONE -> {
                log(id, "done: " + Ops.repr(run.runner().result()));
                yield true;
            }
            case FAILED -> {
                log(id, "failed: " + run.failure());
                yield true;
            }
            case RUNNING, WAITING -> false;
        };
    }

    /** The device's domain while it is loaded; empty while it is unloaded, gone, or not yet published. */
    private Optional<FederationDomainId> domain(String device) {
        var entry = devices().entry(device);
        if (entry.isEmpty()) {
            return Optional.empty();
        }
        var location = ResourceLocation.tryParse(entry.get().dimension());
        ServerLevel level = location == null ? null
                : server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
        var position = entry.get().position();
        if (level == null || !level.isLoaded(position)
                || !(level.getBlockEntity(position) instanceof ComputingDeviceBlockEntity)) {
            return Optional.empty();
        }
        return devices().domainOf(level, device);
    }

    /** A public function of the pushed version of a file. */
    private FunctionDef definition(String file, String function) {
        var texts = devices().pushed(file).orElseThrow(() -> new IllegalArgumentException(file + " was never pushed"));
        if (function.startsWith("_")) {
            throw new IllegalArgumentException(function + " is private to " + file);
        }
        return ScriptParser.parse(texts.get(file)).program().functions().stream()
                .filter(f -> f.name().equals(function)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(file + " has no function " + function));
    }

    private boolean acts(String file, String function) {
        var texts = devices().pushed(file).orElseThrow();
        var others = new LinkedHashMap<String, space.controlnet.ae2federation.function.Program>();
        texts.forEach((name, text) -> {
            if (!name.equals(file)) {
                others.put(name, ScriptParser.parse(text).program());
            }
        });
        return Checker.check(ScriptParser.parse(texts.get(file)).program(),
                new Workspace(others, ProgramStore.catalog(server, null))).effectfulFunctions().contains(function);
    }

    /**
     * Typed text to values by the parameters' types: {@code int}, {@code float}, {@code bool}, and text for {@code str}
     * and the typed strings (an interface, a resource). Parameters left out keep their defaults.
     */
    static List<Object> arguments(FunctionDef definition, List<String> args) {
        var params = definition.params();
        if (args.size() > params.size()) {
            throw new IllegalArgumentException(definition.name() + " takes " + params.size() + " argument(s)");
        }
        for (int i = args.size(); i < params.size(); i++) {
            if (params.get(i).defaultValue() == null) {
                throw new IllegalArgumentException(definition.name() + " needs " + params.get(i).name());
            }
        }
        var values = new ArrayList<Object>();
        for (int i = 0; i < args.size(); i++) {
            values.add(value(params.get(i).name(), params.get(i).type(), args.get(i)));
        }
        return values;
    }

    private static Object value(String name, TypeRef type, String text) {
        try {
            return switch (type.name()) {
                case "int" -> Long.parseLong(text);
                case "float" -> Double.parseDouble(text);
                case "bool" -> switch (text) {
                    case "True", "true" -> true;
                    case "False", "false" -> false;
                    default -> throw new NumberFormatException();
                };
                case "list", "dict", "Optional" ->
                        throw new IllegalArgumentException(name + ": a " + type + " cannot be typed in yet");
                default -> text;
            };
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be a " + type + ", not " + text);
        }
    }
}
