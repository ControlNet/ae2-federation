package space.controlnet.ae2federation.persistence;

import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import space.controlnet.ae2federation.policy.PolicyDelete;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyRecord;
import space.controlnet.ae2federation.policy.PolicyRevision;
import space.controlnet.ae2federation.policy.PolicyStore;
import space.controlnet.ae2federation.policy.PolicyStoreSnapshot;

public final class PolicySavedData extends SavedData {
    private static final String DATA_NAME = "ae2federation_global_policies";
    private static final Factory<PolicySavedData> FACTORY = new Factory<>(PolicySavedData::new, PolicySavedData::load);

    private final PolicyStore store;

    public PolicySavedData() {
        this(new PolicyStore());
    }

    private PolicySavedData(PolicyStore store) {
        this.store = store;
    }

    public static PolicySavedData get(ServerLevel level) {
        return level.getServer().overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    public PolicyMutationResult edit(PolicyEdit edit) {
        var result = store.edit(edit);
        markAccepted(result);
        return result;
    }

    public PolicyMutationResult delete(PolicyDelete deletion) {
        var result = store.delete(deletion);
        markAccepted(result);
        return result;
    }

    public Optional<PolicyRecord.Configured> configured(PolicyKey key) {
        return store.configured(key);
    }

    public PolicyRevision revision(PolicyKey key) {
        return store.revision(key);
    }

    public int configuredCount() {
        return store.configuredCount();
    }

    public int tombstoneCount() {
        return store.tombstoneCount();
    }

    public PolicyStoreSnapshot snapshot() {
        return store.snapshot();
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        return PolicyStateCodec.save(store.snapshot(), tag);
    }

    private static PolicySavedData load(CompoundTag tag, HolderLookup.Provider registries) {
        return new PolicySavedData(PolicyStore.restore(PolicyStateCodec.load(tag)));
    }

    private void markAccepted(PolicyMutationResult result) {
        if (result instanceof PolicyMutationResult.Accepted) {
            setDirty();
        }
    }
}
