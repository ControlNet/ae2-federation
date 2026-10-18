package space.controlnet.ae2federation.automation;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import space.controlnet.ae2federation.identity.NetworkId;

/** The overworld's {@link NetworkShortIdBook}. Server thread only: asking for an ID may draw one. */
public final class NetworkShortIds extends SavedData {
    private static final String DATA_NAME = "ae2federation_network_short_ids";
    private static final Factory<NetworkShortIds> FACTORY = new Factory<>(NetworkShortIds::new, NetworkShortIds::load);

    private final NetworkShortIdBook book = new NetworkShortIdBook();

    public static NetworkShortIds get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public String id(NetworkId network) {
        var before = book.entries().size();
        var id = book.id(network, ThreadLocalRandom.current());
        if (book.entries().size() != before) {
            setDirty();
        }
        return id;
    }

    public Optional<NetworkId> network(String id) {
        return book.network(id);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new CompoundTag();
        book.entries().forEach((network, id) -> entries.putString(id, network.value().toString()));
        tag.put("networks", entries);
        return tag;
    }

    private static NetworkShortIds load(CompoundTag tag, HolderLookup.Provider registries) {
        var loaded = new NetworkShortIds();
        var entries = tag.getCompound("networks");
        for (var id : entries.getAllKeys()) {
            try {
                loaded.book.put(new NetworkId(UUID.fromString(entries.getString(id))), id);
            } catch (IllegalArgumentException ignored) {
                // A malformed record is dropped rather than failing the whole save.
            }
        }
        return loaded;
    }
}
