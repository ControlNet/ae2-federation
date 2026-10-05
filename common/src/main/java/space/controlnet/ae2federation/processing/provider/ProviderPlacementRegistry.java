package space.controlnet.ae2federation.processing.provider;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Where each Provider identity lives. A Provider's saved data loads into a new block not only when its chunk loads but
 * also when the block is placed from a structure or from an item that carries block data, and nothing in the data
 * tells these apart. The position does: a Provider identity already placed somewhere else that was not removed from
 * the world belongs to that other block, so the new one is a copy. A Provider removed from the world, as a block mover
 * does before placing it again, gives up its position, so the moved block keeps its identity.
 */
public final class ProviderPlacementRegistry extends SavedData {
    private static final String DATA_NAME = "ae2federation_provider_placements";
    private static final Factory<ProviderPlacementRegistry> FACTORY =
            new Factory<>(ProviderPlacementRegistry::new, ProviderPlacementRegistry::load);

    private final Map<UUID, GlobalPos> placements = new HashMap<>();

    public static ProviderPlacementRegistry get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * Records that {@code provider} lives at {@code position}, unless it already lives elsewhere.
     *
     * @return false when the identity belongs to a Provider at another position, so the caller is a copy
     */
    public boolean place(ProviderId provider, GlobalPos position) {
        var current = placements.putIfAbsent(provider.value(), position);
        if (current == null) {
            setDirty();
            return true;
        }
        return current.equals(position);
    }

    /** Forgets {@code provider}'s position when the Provider there is removed from the world. */
    public void remove(ProviderId provider, GlobalPos position) {
        if (placements.remove(provider.value(), position)) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        placements.forEach((provider, position) -> {
            var entry = new CompoundTag();
            entry.putUUID("provider", provider);
            entry.put("position", GlobalPos.CODEC.encodeStart(NbtOps.INSTANCE, position).getOrThrow());
            entries.add(entry);
        });
        tag.put("placements", entries);
        return tag;
    }

    static ProviderPlacementRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        var registry = new ProviderPlacementRegistry();
        for (var raw : tag.getList("placements", Tag.TAG_COMPOUND)) {
            var entry = (CompoundTag) raw;
            GlobalPos.CODEC.parse(NbtOps.INSTANCE, entry.get("position")).result()
                    .ifPresent(position -> registry.placements.put(entry.getUUID("provider"), position));
        }
        return registry;
    }
}
