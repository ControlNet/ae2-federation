package space.controlnet.ae2federation.processing.endpoint;

import appeng.helpers.externalstorage.GenericStackItemStorage;
import java.util.Objects;
import net.neoforged.neoforge.items.IItemHandler;
import net.minecraft.server.level.ServerLevel;

public record EndpointItemReturnContext(EndpointReturnOwner owner, IItemHandler capability) {
    public EndpointItemReturnContext(EndpointReturnOwner owner) {
        this(Objects.requireNonNull(owner), new GenericStackItemStorage(owner.inventory()));
    }

    public EndpointItemReturnContext(ServerLevel level, EndpointReturnOwner owner) {
        this(owner, new ObservedItemReturnHandler(level, owner, new GenericStackItemStorage(owner.inventory())));
    }

    public EndpointItemReturnContext {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(capability);
    }
}
