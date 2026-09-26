package space.controlnet.ae2federation.crafting.binding;

import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

final class NativeCraftingRequestRegistry implements AutoCloseable {
    private final Map<NativeCraftingRequestKey, NativeCraftingRequestBinding> nativeRequests = new HashMap<>();
    private final Map<UUID, NativeCraftingRequestKey> nativeLinkOwners = new HashMap<>();

    Optional<NativeCraftingRequestBinding> synchronize(NativeCraftingRequestKey key, ICraftingRequester requester,
            ICraftingLink link, boolean registrationAllowed) {
        var existing = nativeRequests.get(key);
        if (existing == null) {
            return registrationAllowed ? register(key, requester, link) : Optional.empty();
        }
        if (!existing.link().getCraftingID().equals(link.getCraftingID())) {
            return Optional.empty();
        }
        var exactOwner = existing.requester() == requester && existing.link() == link;
        var loadedOwner = requester.getRequestedJobs().contains(link);
        if (existing.link().isDone() || existing.link().isCanceled() || link.isDone() || link.isCanceled()) {
            if (exactOwner || loadedOwner) {
                retire(key, link.getCraftingID());
            }
            return Optional.empty();
        }
        if (exactOwner) {
            return Optional.of(existing);
        }
        if (!loadedOwner) {
            return Optional.empty();
        }
        var rebound = new NativeCraftingRequestBinding(key, requester, link);
        nativeRequests.put(key, rebound);
        return Optional.of(rebound);
    }

    Optional<NativeCraftingRequestBinding> get(NativeCraftingRequestKey key) {
        return Optional.ofNullable(nativeRequests.get(key));
    }

    int retireRequester(ICraftingRequester requester) {
        var owned = nativeRequests.entrySet().stream()
                .filter(entry -> entry.getValue().requester() == requester)
                .map(Map.Entry::getKey)
                .toList();
        owned.forEach(key -> retire(key, nativeRequests.get(key).link().getCraftingID()));
        return owned.size();
    }

    int retireTerminal() {
        var terminal = nativeRequests.entrySet().stream()
                .filter(entry -> entry.getValue().link().isDone() || entry.getValue().link().isCanceled())
                .map(Map.Entry::getKey)
                .toList();
        terminal.forEach(key -> retire(key, nativeRequests.get(key).link().getCraftingID()));
        return terminal.size();
    }

    int requestCount() {
        return nativeRequests.size();
    }

    int linkOwnerCount() {
        return nativeLinkOwners.size();
    }

    @Override
    public void close() {
        nativeRequests.clear();
        nativeLinkOwners.clear();
    }

    private Optional<NativeCraftingRequestBinding> register(NativeCraftingRequestKey key,
            ICraftingRequester requester, ICraftingLink link) {
        if (link.isDone() || link.isCanceled() || !requester.getRequestedJobs().contains(link)) {
            return Optional.empty();
        }
        var craftingId = link.getCraftingID();
        if (nativeLinkOwners.containsKey(craftingId)) {
            return Optional.empty();
        }
        var binding = new NativeCraftingRequestBinding(key, requester, link);
        nativeRequests.put(key, binding);
        nativeLinkOwners.put(craftingId, key);
        return Optional.of(binding);
    }

    private void retire(NativeCraftingRequestKey key, UUID craftingId) {
        nativeRequests.remove(key);
        nativeLinkOwners.remove(craftingId, key);
    }
}
