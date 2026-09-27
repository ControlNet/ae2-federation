package space.controlnet.ae2federation.persistence;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import space.controlnet.ae2federation.identity.NetworkId;

/** The overworld's {@link NetworkNameBook}; names outlive controllers and restarts. */
public final class NetworkNames extends SavedData {
    private static final String DATA_NAME = "ae2federation_network_names";
    private static final Factory<NetworkNames> FACTORY = new Factory<>(NetworkNames::new, NetworkNames::load);

    private final NetworkNameBook book = new NetworkNameBook();

    public static NetworkNames get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public Optional<String> name(NetworkId network) {
        return book.name(network);
    }

    public boolean rename(NetworkId network, String name) {
        var changed = book.rename(network, name);
        if (changed) setDirty();
        return changed;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new CompoundTag();
        book.entries().forEach((network, name) -> entries.putString(network.value().toString(), name));
        tag.put("names", entries);
        return tag;
    }

    private static NetworkNames load(CompoundTag tag, HolderLookup.Provider registries) {
        var loaded = new NetworkNames();
        var entries = tag.getCompound("names");
        for (var key : entries.getAllKeys()) {
            try {
                var network = new NetworkId(UUID.fromString(key));
                NetworkNameBook.sanitize(entries.getString(key)).filter(name -> !name.isEmpty())
                        .ifPresent(name -> loaded.book.rename(network, name));
            } catch (IllegalArgumentException ignored) {
                // A malformed record is dropped rather than failing the whole save.
            }
        }
        return loaded;
    }
}
