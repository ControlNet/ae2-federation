package space.controlnet.ae2federation.processing.provider;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Provider identities whose block was removed while some claimed Endpoint was not loaded. When such an Endpoint loads
 * it releases the Claim itself, so a removed Provider never keeps an Endpoint unavailable to other Providers.
 */
public final class RetiredProviderRegistry extends SavedData {
    private static final String DATA_NAME = "ae2federation_retired_providers";
    private static final Factory<RetiredProviderRegistry> FACTORY =
            new Factory<>(RetiredProviderRegistry::new, RetiredProviderRegistry::load);

    private final Set<UUID> retired = new HashSet<>();

    public static RetiredProviderRegistry get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public void retire(ProviderId provider) {
        if (retired.add(provider.value())) {
            setDirty();
        }
    }

    public boolean isRetired(ProviderId provider) {
        return retired.contains(provider.value());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        var entries = new ListTag();
        retired.forEach(id -> entries.add(NbtUtils.createUUID(id)));
        tag.put("retired", entries);
        return tag;
    }

    private static RetiredProviderRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        var registry = new RetiredProviderRegistry();
        for (var entry : tag.getList("retired", Tag.TAG_INT_ARRAY)) {
            registry.retired.add(NbtUtils.loadUUID(entry));
        }
        return registry;
    }
}
