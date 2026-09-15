package space.controlnet.ae2federation.processing.provider;

import net.minecraft.nbt.CompoundTag;

public final class ProviderIdentityCodec {
    private static final int SCHEMA = 1;

    private ProviderIdentityCodec() {
    }

    public static void save(CompoundTag tag, ProviderIdentity identity) {
        tag.putInt("schema", SCHEMA);
        tag.putUUID("id", identity.id().value());
        tag.putLong("instanceEpoch", identity.instanceEpoch().value());
    }

    public static ProviderIdentity load(CompoundTag tag) {
        if (tag.getInt("schema") != SCHEMA || !tag.hasUUID("id") || tag.getLong("instanceEpoch") < 1) {
            throw new IllegalArgumentException("Malformed Provider identity persistence");
        }
        return new ProviderIdentity(new ProviderId(tag.getUUID("id")),
                new ProviderInstanceEpoch(tag.getLong("instanceEpoch")));
    }
}
