package space.controlnet.ae2federation.automation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.function.ListValue;
import space.controlnet.ae2federation.function.Ops;
import space.controlnet.ae2federation.function.ScriptParser;
import space.controlnet.ae2federation.persistence.NetworkNames;

/**
 * The server side of the Automation tab's traffic: the library index the tab shows, file texts on request, uploads
 * put back together from chunks and saved (the last write wins), and the install, push, run and log requests. Players who
 * have the tab open are subscribed and get a new index after every change. The loader's network code registers how
 * the messages travel. Programs are global with no permissions for now (user, 2026-10-18), so any player may do this.
 */
public final class ProgramSync {
    /** A function as the tab lists it: its parameters as written ({@code item: Item}), and whether it acts. */
    public record FunctionInfo(String name, List<String> params, String result, boolean acts) {
    }

    /**
     * A program: its version (a hash of the text), whether its devices run that version, whether it was ever pushed,
     * how many problems it has, and its public functions.
     */
    public record FileInfo(String name, String version, boolean current, boolean pushed, int problems,
            List<FunctionInfo> functions) {
    }

    /** A computing device or an Automation Interface: its alias is empty when it has none. */
    public record DeviceInfo(String id, String alias, String dimension, BlockPos position, String domain) {
    }

    /** A network a program can name: its short ID, its name (empty when it has none) and its domain. */
    public record NetworkInfo(String id, String name, String domain) {
    }

    /**
     * An installation, with each argument as the text a request takes back ({@link Installation#text}) and its trigger
     * as {@link TriggerText} writes it.
     */
    public record InstallationInfo(String id, String device, String file, String function, List<String> args,
            String trigger, int interval, String overlap, int running) {
    }

    /**
     * Every program, computing device, Automation Interface, domain network and installation on the server; the tab
     * shows the devices and interfaces of its own domain, and checks programs against every interface and network.
     */
    public record Index(List<FileInfo> files, List<DeviceInfo> devices, List<DeviceInfo> interfaces,
            List<NetworkInfo> networks, List<InstallationInfo> installations) {
    }

    /** A file's text, in chunks of {@link ChunkAssembler#CHUNK} characters. */
    public record Text(String name, String version, int index, int count, String part) {
    }

    /** The answer to a request or an upload. */
    public record Answer(int request, boolean ok, String message) {
    }

    public interface ToClient {
        void index(ServerPlayer player, Index index);

        void text(ServerPlayer player, Text text);

        void answer(ServerPlayer player, Answer answer);
    }

    private static @Nullable ToClient toClient;
    private static final Map<MinecraftServer, ProgramSync> SERVICES = new java.util.HashMap<>();

    private final MinecraftServer server;
    private final Set<UUID> subscribers = new LinkedHashSet<>();
    private final ChunkAssembler<String> uploads = new ChunkAssembler<>();

    private ProgramSync(MinecraftServer server) {
        this.server = server;
    }

    public static void register(ToClient client) {
        toClient = Objects.requireNonNull(client);
    }

    public static ProgramSync get(MinecraftServer server) {
        return SERVICES.computeIfAbsent(server, ProgramSync::new);
    }

    public static void tickAll(MinecraftServer server) {
        var sync = SERVICES.get(server);
        if (sync != null) {
            sync.uploads.expire(server.overworld().getGameTime());
        }
    }

    public static void closeServer(MinecraftServer server) {
        SERVICES.remove(server);
    }

    /** A program's version: the first 16 hex digits of the SHA-256 of its text, the same after a restart. */
    public static String version(String text) {
        try {
            var digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The tab opened or closed; an opened tab gets the index at once and after every change. */
    public void subscribe(ServerPlayer player, boolean open) {
        if (open) {
            subscribers.add(player.getUUID());
            if (toClient != null) {
                toClient.index(player, index());
            }
        } else {
            subscribers.remove(player.getUUID());
        }
    }

    /** Whether the player has the tab open. */
    public boolean subscribed(UUID player) {
        return subscribers.contains(player);
    }

    /** Sends a program's text, in chunks. */
    public void fetch(ServerPlayer player, String name) {
        var library = ProgramStore.get(server).library();
        if (toClient == null || !library.names().contains(name)) {
            return;
        }
        var text = library.text(name);
        var chunks = ChunkAssembler.split(text);
        for (int i = 0; i < chunks.size(); i++) {
            toClient.text(player, new Text(name, version(text), i, chunks.size(), chunks.get(i)));
        }
    }

    /**
     * One chunk of an upload. Once the text is whole it is saved; the answer says whether the save replaced a version
     * other than {@code base}, the one the edit started from (the save happens anyway: the last write wins).
     */
    public void upload(ServerPlayer player, int request, String name, String base, int index, int count,
            String part) {
        receive(player.getUUID(), request, name, base, index, count, part).ifPresent(answer -> answer(player, answer));
    }

    /** As {@link #upload}, giving the answer once the upload is whole or refused. */
    public java.util.Optional<Answer> receive(UUID sender, int request, String name, String base, int index,
            int count, String part) {
        try {
            var text = uploads.accept(sender + "/" + name, request, index, count, part,
                    server.overworld().getGameTime());
            return text.map(whole -> new Answer(request, true, save(name, base, whole)));
        } catch (IllegalArgumentException | UncheckedIOException e) {
            return java.util.Optional.of(new Answer(request, false, e.getMessage()));
        }
    }

    /** Saves a whole text; gives what the answer says. */
    String save(String name, String base, String text) {
        var library = ProgramStore.get(server).library();
        var replaced = library.names().contains(name) && !base.equals(version(library.text(name)));
        try {
            library.save(name, text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        changed();
        return "saved " + name + (replaced ? "; it replaced a version saved meanwhile" : "");
    }

    /**
     * A request: {@code push file}, {@code install device file function args...}, {@code uninstall id},
     * {@code args id args...}, {@code trigger id text}, {@code interval id ticks}, {@code overlap id mode}, {@code run id}, {@code delete file}.
     */
    public void request(ServerPlayer player, int request, String action, List<String> args) {
        answer(player, handle(request, action, args));
    }

    /** As {@link #request}, giving the answer. */
    public Answer handle(int request, String action, List<String> args) {
        try {
            return new Answer(request, true, perform(action, args));
        } catch (IllegalArgumentException | UncheckedIOException e) {
            return new Answer(request, false, e.getMessage());
        }
    }

    /** Performs a request; gives the message of its answer. */
    String perform(String action, List<String> args) {
        var scheduler = ProgramScheduler.get(server);
        if (action.equals("log")) {
            // Asked again and again while the tab shows the installation: it changes nothing, so no index is sent.
            var id = arg(args, 0, 1);
            if (ComputingDevices.get(server.overworld()).installation(id).isEmpty()) {
                throw new IllegalArgumentException("no such installation");
            }
            return InstallationLog.answer(scheduler.running(id), server.overworld().getGameTime(), scheduler.log(id));
        }
        var message = switch (action) {
            case "push" -> "pushed " + String.join(", ", scheduler.push(arg(args, 0, 1)).keySet());
            case "install" -> {
                if (args.size() < 3) {
                    throw new IllegalArgumentException("install takes a device, a file and a function");
                }
                yield "installed " + scheduler.install(args.get(0), args.get(1), args.get(2),
                        args.subList(3, args.size())).id();
            }
            case "uninstall" -> scheduler.uninstall(arg(args, 0, 1)) ? "uninstalled" : "no such installation";
            case "args" -> {
                if (args.isEmpty()) {
                    throw new IllegalArgumentException("args takes an installation and its arguments");
                }
                yield Ops.repr(new ListValue(scheduler.setArgs(args.get(0), args.subList(1, args.size())).args()));
            }
            case "trigger" -> TriggerText.format(scheduler.setTrigger(arg(args, 0, 2),
                    TriggerText.parse(args.get(1))).trigger());
            case "interval" -> {
                try {
                    yield "every " + scheduler.setInterval(arg(args, 0, 2), Integer.parseInt(args.get(1))).interval()
                            + " ticks at most";
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("the interval is a number of ticks");
                }
            }
            case "overlap" -> {
                try {
                    yield scheduler.setOverlap(arg(args, 0, 2), Installation.Overlap.valueOf(
                            args.get(1).toUpperCase(Locale.ROOT))).overlap().name().toLowerCase(Locale.ROOT);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("overlap is ignore, restart or parallel");
                }
            }
            case "run" -> {
                scheduler.trigger(arg(args, 0, 1));
                yield "triggered";
            }
            case "delete" -> {
                var name = arg(args, 0, 1);
                var users = ComputingDevices.get(server.overworld()).installations().stream()
                        .filter(installation -> installation.file().equals(name)).map(Installation::id).toList();
                if (!users.isEmpty()) {
                    throw new IllegalArgumentException(name + " is installed as " + String.join(", ", users));
                }
                try {
                    ProgramStore.get(server).library().delete(name);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                yield "deleted " + name;
            }
            default -> throw new IllegalArgumentException("there is no request " + action);
        };
        changed();
        return message;
    }

    private static String arg(List<String> args, int index, int count) {
        if (args.size() != count) {
            throw new IllegalArgumentException("expected " + count + " argument(s)");
        }
        return args.get(index);
    }

    private void answer(ServerPlayer player, Answer answer) {
        if (toClient != null) {
            toClient.answer(player, answer);
        }
    }

    /** Sends the new index to every subscribed player still online. */
    public void changed() {
        if (subscribers.isEmpty() || toClient == null) {
            return;
        }
        var index = index();
        subscribers.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        subscribers.forEach(id -> toClient.index(server.getPlayerList().getPlayer(id), index));
    }

    /** What the tab lists: every program, computing device, Automation Interface, domain network and installation. */
    public Index index() {
        var library = ProgramStore.get(server).library();
        var devices = ComputingDevices.get(server.overworld());
        var scheduler = ProgramScheduler.get(server);
        var files = new ArrayList<FileInfo>();
        for (var name : library.names()) {
            var text = library.text(name);
            var parsed = ScriptParser.parse(text);
            var checked = parsed.ok() ? library.check(name) : null;
            var problems = checked != null ? checked.problems().size() : parsed.errors().size();
            var acting = checked != null ? checked.effectfulFunctions() : Set.<String>of();
            var functions = parsed.program().functions().stream().filter(f -> !f.name().startsWith("_"))
                    .map(f -> new FunctionInfo(f.name(), f.params().stream()
                            .map(param -> param.name() + ": " + param.type()).toList(), f.result().toString(),
                            acting.contains(f.name())))
                    .toList();
            var pushed = devices.pushed(name);
            files.add(new FileInfo(name, version(text), pushed.map(texts -> texts.get(name).equals(text)).orElse(false),
                    pushed.isPresent(), problems, functions));
        }
        var deviceInfos = new ArrayList<DeviceInfo>();
        devices.entries().forEach((id, entry) -> {
            var level = level(entry.dimension());
            var domain = level == null ? "" : devices.domainOf(level, id).map(found -> found.value()).orElse("");
            deviceInfos.add(new DeviceInfo(id, entry.alias(), entry.dimension(), entry.position(), domain));
        });
        var interfaces = AutomationInterfaces.get(server.overworld());
        var interfaceInfos = new ArrayList<DeviceInfo>();
        interfaces.entries().forEach((id, entry) -> {
            var level = level(entry.dimension());
            var domain = level == null ? "" : interfaces.domainOf(level, id).map(found -> found.value()).orElse("");
            interfaceInfos.add(new DeviceInfo(id, entry.alias(), entry.dimension(), entry.position(), domain));
        });
        var shortIds = NetworkShortIds.get(server);
        var names = NetworkNames.get(server.overworld());
        var networks = new ArrayList<NetworkInfo>();
        FederationDomainRegistryAccess.get(server.overworld()).federationDomains().forEach(snapshot ->
                snapshot.memberships().keySet().forEach(network -> networks.add(new NetworkInfo(shortIds.id(network),
                        names.name(network).orElse(""), snapshot.federationDomainId().value()))));
        var installations = devices.installations().stream().map(installation -> new InstallationInfo(
                installation.id(), installation.device(), installation.file(), installation.function(),
                installation.args().stream().map(Installation::text).toList(), TriggerText.format(installation.trigger()),
                installation.interval(), installation.overlap().name().toLowerCase(Locale.ROOT),
                scheduler.running(installation.id()))).toList();
        return new Index(List.copyOf(files), List.copyOf(deviceInfos), List.copyOf(interfaceInfos),
                List.copyOf(networks), installations);
    }

    private @Nullable ServerLevel level(String dimension) {
        var location = ResourceLocation.tryParse(dimension);
        return location == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, location));
    }
}
