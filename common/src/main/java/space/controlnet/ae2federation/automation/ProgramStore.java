package space.controlnet.ae2federation.automation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.script.Library;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.persistence.NetworkNames;

/**
 * The server's programs: the {@link Library} of {@code <world>/ae2federation/programs/*.ae2f}. Programs are global, so
 * the library is checked against every interface and network on the server; an installation checks again against its
 * own domain. Loaded on first use and again on reload; files changed outside the game are seen only then.
 */
public final class ProgramStore {
    private static final Map<MinecraftServer, ProgramStore> STORES = new HashMap<>();

    private final MinecraftServer server;
    private final Path folder;
    private Library library;

    private ProgramStore(MinecraftServer server) {
        this.server = server;
        this.folder = server.getWorldPath(LevelResource.ROOT).resolve("ae2federation").resolve("programs");
        this.library = load();
    }

    public static ProgramStore get(MinecraftServer server) {
        return STORES.computeIfAbsent(server, ProgramStore::new);
    }

    public static void closeServer(MinecraftServer server) {
        STORES.remove(server);
    }

    public Path folder() {
        return folder;
    }

    public Library library() {
        return library;
    }

    /** Reads the folder again, dropping whatever was loaded. */
    public Library reload() {
        library = load();
        return library;
    }

    private Library load() {
        try {
            Files.createDirectories(folder);
            return Library.load(folder, catalog(server, null));
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read the programs folder " + folder, e);
        }
    }

    /**
     * What programs can name: the interfaces and networks of one domain, or of the whole server when {@code domain}
     * is null. Read on every query. Server thread only, since a network's short ID is drawn on first use.
     */
    public static GameCatalog catalog(MinecraftServer server, @Nullable FederationDomainId domain) {
        var overworld = server.overworld();
        return new GameCatalog(() -> {
            var interfaces = AutomationInterfaces.get(overworld);
            var ids = domain == null ? interfaces.entries().keySet() : interfaces.idsIn(overworld, domain);
            var named = new ArrayList<GameCatalog.Named>();
            ids.forEach(id -> named.add(new GameCatalog.Named(id, interfaces.alias(id))));
            return named;
        }, () -> {
            var networks = new ArrayList<NetworkId>();
            FederationDomainRegistryAccess.get(overworld).federationDomains().stream()
                    .filter(snapshot -> domain == null || snapshot.federationDomainId().equals(domain))
                    .forEach(snapshot -> snapshot.memberships().keySet().stream()
                            .filter(network -> !networks.contains(network)).forEach(networks::add));
            var shortIds = NetworkShortIds.get(server);
            var names = NetworkNames.get(overworld);
            List<GameCatalog.Named> named = new ArrayList<>();
            networks.forEach(network -> named.add(
                    new GameCatalog.Named(shortIds.id(network), names.name(network).orElse(""),
                            names.name(network).orElse(""), network.value().toString())));
            return named;
        }, new RegistryResources(server.registryAccess()));
    }
}
