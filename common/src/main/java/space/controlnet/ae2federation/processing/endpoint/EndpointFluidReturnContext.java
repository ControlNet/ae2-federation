package space.controlnet.ae2federation.processing.endpoint;

import appeng.helpers.externalstorage.GenericStackFluidStorage;
import java.util.Objects;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.minecraft.server.level.ServerLevel;

public record EndpointFluidReturnContext(EndpointReturnOwner owner, IFluidHandler capability) {
    public EndpointFluidReturnContext(EndpointReturnOwner owner) {
        this(Objects.requireNonNull(owner), new GenericStackFluidStorage(owner.inventory()));
    }

    public EndpointFluidReturnContext(ServerLevel level, EndpointReturnOwner owner) {
        this(owner, new ObservedFluidReturnHandler(level, owner, new GenericStackFluidStorage(owner.inventory())));
    }

    public EndpointFluidReturnContext {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(capability);
    }
}
