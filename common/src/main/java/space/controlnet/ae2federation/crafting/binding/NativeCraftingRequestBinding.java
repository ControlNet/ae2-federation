package space.controlnet.ae2federation.crafting.binding;

import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import java.util.Objects;

public record NativeCraftingRequestBinding(NativeCraftingRequestKey key, ICraftingRequester requester,
        ICraftingLink link) {
    public NativeCraftingRequestBinding {
        Objects.requireNonNull(key);
        Objects.requireNonNull(requester);
        Objects.requireNonNull(link);
    }

    public boolean canceled() {
        return link().isCanceled();
    }

    public boolean completed() {
        return link().isDone() && !link().isCanceled();
    }
}
