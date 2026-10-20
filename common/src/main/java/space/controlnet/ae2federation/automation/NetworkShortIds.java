package space.controlnet.ae2federation.automation;

import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import space.controlnet.ae2federation.identity.NetworkId;

/** The overworld's {@link NetworkShortIdBook}. Server thread only: asking for an ID may give one. */
public final class NetworkShortIds extends SavedData {
    private static final String DATA_NAME = "ae2federation_network_short_ids";
    private static final Factory<NetworkShortIds> FACTORY = new Factory<>(NetworkShortIds::new, NetworkShortIds::load);

    private final NetworkShortIdBook book = new NetworkShortIdBook();

    public static NetworkShortIds get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public String id(NetworkId network) {
        var before = book.entries().size();
        var id = book.id(network);
        if (book.entries().size() != before) {
            setDirty();
        }
        return id;
    }

    public Optional<NetworkId> network(String id) {
        return book.network(id);
    }

    /** See {@link NetworkShortIdBook#fold}. */
    public void fold(NetworkId folded, NetworkId survivor) {
        book.fold(folded, survivor);
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new CompoundTag();
        book.entries().forEach((network, id) -> entries.putString(id, network.value().toString()));
        tag.put("networks", entries);
        var aliases = new CompoundTag();
        book.aliases().forEach((id, network) -> aliases.putString(id, network.value().toString()));
        tag.put("aliases", aliases);
        return tag;
    }

    private static NetworkShortIds load(CompoundTag tag, HolderLookup.Provider registries) {
        var loaded = new NetworkShortIds();
        var entries = tag.getCompound("networks");
        for (var id : entries.getAllKeys()) {
            try {
                loaded.book.put(new NetworkId(UUID.fromString(entries.getString(id))), id);
            } catch (IllegalArgumentException ignored) {
                // A malformed record is dropped rather than failing the whole save, as is one that does not start its
                // network's own digits; the network is given its ID again when it is next asked for.
            }
        }
        var aliases = tag.getCompound("aliases");
        for (var id : aliases.getAllKeys()) {
            try {
                loaded.book.alias(id, new NetworkId(UUID.fromString(aliases.getString(id))));
            } catch (IllegalArgumentException ignored) {
                // As above.
            }
        }
        return loaded;
    }
}
