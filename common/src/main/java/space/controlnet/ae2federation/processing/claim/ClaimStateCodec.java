package space.controlnet.ae2federation.processing.claim;

import net.minecraft.nbt.CompoundTag;
import space.controlnet.ae2federation.processing.provider.ProviderId;
import space.controlnet.ae2federation.processing.provider.ProviderIdentity;
import space.controlnet.ae2federation.processing.provider.ProviderInstanceEpoch;

public final class ClaimStateCodec {
    private static final int SCHEMA = 1;

    private ClaimStateCodec() {
    }

    public static CompoundTag save(ClaimState state) {
        var tag = new CompoundTag();
        tag.putInt("schema", SCHEMA);
        tag.putUUID("endpoint", state.key().endpoint().id().value());
        tag.putLong("endpointEpoch", state.key().endpoint().instanceEpoch().value());
        tag.putLong("claimEpoch", state.epoch().value());
        if (state instanceof ClaimState.Owned owned) {
            tag.putUUID("provider", owned.ownerIdentity().provider().id().value());
            tag.putLong("providerEpoch", owned.ownerIdentity().provider().instanceEpoch().value());
        }
        return tag;
    }

    public static ClaimState load(CompoundTag tag) {
        if (tag.getInt("schema") != SCHEMA || !tag.hasUUID("endpoint")
                || tag.getLong("endpointEpoch") < 1 || tag.getLong("claimEpoch") < 0) {
            throw new IllegalArgumentException("Malformed Claim persistence");
        }
        var endpoint = new EndpointIdentity(new EndpointId(tag.getUUID("endpoint")),
                new EndpointInstanceEpoch(tag.getLong("endpointEpoch")));
        var key = new ClaimKey(endpoint);
        var epoch = new ClaimEpoch(tag.getLong("claimEpoch"));
        if (!tag.hasUUID("provider")) {
            if (tag.contains("providerEpoch") || !epoch.equals(ClaimEpoch.NONE)) {
                throw new IllegalArgumentException("Unclaimed persistence has owner epoch data");
            }
            return new ClaimState.Unclaimed(key, epoch);
        }
        if (tag.getLong("providerEpoch") < 1 || epoch.equals(ClaimEpoch.NONE)) {
            throw new IllegalArgumentException("Owned persistence has malformed epochs");
        }
        var provider = new ProviderIdentity(new ProviderId(tag.getUUID("provider")),
                new ProviderInstanceEpoch(tag.getLong("providerEpoch")));
        return new ClaimState.Owned(key, epoch, new EndpointOwnerIdentity(provider));
    }
}
