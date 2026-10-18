package space.controlnet.ae2federation.automation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainNodeId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;

/**
 * The overworld's record of the automation that runs: every computing device's ID and place, the functions installed
 * on them, and the version of each program last pushed to them. A pushed version holds the file and every file it
 * calls, as text, so editing the library changes nothing that runs until the next push.
 */
public final class ComputingDevices extends SavedData {
    public static final String PREFIX = "device";
    private static final String DATA_NAME = "ae2federation_computing_devices";
    private static final Factory<ComputingDevices> FACTORY =
            new Factory<>(ComputingDevices::new, ComputingDevices::load);

    private final AutomationInterfaceBook book = new AutomationInterfaceBook(PREFIX);
    private final Map<String, Installation> installations = new LinkedHashMap<>();
    private final Map<String, Map<String, String>> pushed = new LinkedHashMap<>();

    public static ComputingDevices get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public String claim(@Nullable String stored, ServerLevel level, BlockPos position) {
        var before = book.entries().size();
        var id = book.claim(stored, FederationDomainRegistryAccess.dimension(level), position,
                ThreadLocalRandom.current());
        if (book.entries().size() != before || !id.equals(stored)) {
            setDirty();
        }
        return id;
    }

    /** The device was broken: its ID is free again and its installations go with it. */
    public void release(String id) {
        if (book.entry(id).isPresent()) {
            book.release(id);
            installations.values().removeIf(installation -> installation.device().equals(id));
            setDirty();
        }
    }

    public Optional<AutomationInterfaceBook.Entry> entry(String id) {
        return book.entry(id);
    }

    /** Every device on the server, loaded or not. */
    public Map<String, AutomationInterfaceBook.Entry> entries() {
        return book.entries();
    }

    /** The domain the device is in now; empty until it has published. */
    public Optional<FederationDomainId> domainOf(ServerLevel level, String id) {
        return book.entry(id).flatMap(entry -> FederationDomainRegistryAccess.get(level)
                .federationDomainOf(new FederationDomainNodeId(entry.dimension(), entry.position().asLong()))
                .map(snapshot -> snapshot.federationDomainId()));
    }

    /** Installs a function; gives the new installation. */
    public Installation install(String device, String file, String function, List<Object> args) {
        var id = ShortId.draw(Installation.PREFIX, ThreadLocalRandom.current(), installations::containsKey);
        var installation = new Installation(id, device, file, function, args, new Installation.Trigger.Manual(),
                Installation.DEFAULT_INTERVAL, Installation.Overlap.IGNORE);
        installations.put(id, installation);
        setDirty();
        return installation;
    }

    /** Replaces an installation's settings. */
    public void update(Installation installation) {
        if (installations.containsKey(installation.id())) {
            installations.put(installation.id(), installation);
            setDirty();
        }
    }

    public boolean uninstall(String id) {
        if (installations.remove(id) == null) {
            return false;
        }
        setDirty();
        return true;
    }

    public Optional<Installation> installation(String id) {
        return Optional.ofNullable(installations.get(id));
    }

    public List<Installation> installations() {
        return List.copyOf(installations.values());
    }

    /** The version of {@code file} last pushed, with the files it calls; empty when it never was. */
    public Optional<Map<String, String>> pushed(String file) {
        return Optional.ofNullable(pushed.get(file));
    }

    public void push(String file, Map<String, String> texts) {
        pushed.put(file, Collections.unmodifiableMap(new LinkedHashMap<>(texts)));
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var devices = new CompoundTag();
        book.entries().forEach((id, entry) -> {
            var saved = new CompoundTag();
            saved.putString("dimension", entry.dimension());
            saved.putLong("position", entry.position().asLong());
            devices.put(id, saved);
        });
        tag.put("devices", devices);
        var installed = new CompoundTag();
        installations.forEach((id, installation) -> installed.put(id, InstallationCodec.save(installation)));
        tag.put("installations", installed);
        var versions = new CompoundTag();
        pushed.forEach((file, texts) -> {
            var saved = new CompoundTag();
            texts.forEach(saved::putString);
            versions.put(file, saved);
        });
        tag.put("pushed", versions);
        return tag;
    }

    private static ComputingDevices load(CompoundTag tag, HolderLookup.Provider registries) {
        var loaded = new ComputingDevices();
        var devices = tag.getCompound("devices");
        for (var id : devices.getAllKeys()) {
            var saved = devices.getCompound(id);
            if (ShortId.valid(PREFIX, id) && !saved.getString("dimension").isBlank()) {
                loaded.book.put(id, new AutomationInterfaceBook.Entry(saved.getString("dimension"),
                        BlockPos.of(saved.getLong("position")), ""));
            }
        }
        var installed = tag.getCompound("installations");
        for (var id : installed.getAllKeys()) {
            InstallationCodec.load(id, installed.getCompound(id))
                    .filter(installation -> loaded.book.entry(installation.device()).isPresent())
                    .ifPresent(installation -> loaded.installations.put(id, installation));
        }
        var versions = tag.getCompound("pushed");
        for (var file : versions.getAllKeys()) {
            var saved = versions.getCompound(file);
            var texts = new LinkedHashMap<String, String>();
            new ArrayList<>(saved.getAllKeys()).stream().sorted()
                    .forEach(name -> texts.put(name, saved.getString(name)));
            if (texts.containsKey(file)) {
                loaded.pushed.put(file, Collections.unmodifiableMap(texts));
            }
        }
        return loaded;
    }
}
