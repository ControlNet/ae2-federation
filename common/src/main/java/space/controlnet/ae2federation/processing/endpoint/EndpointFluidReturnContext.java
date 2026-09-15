package space.controlnet.ae2federation.processing.endpoint;

import appeng.helpers.externalstorage.GenericStackFluidStorage;
import java.util.Objects;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public record EndpointFluidReturnContext(EndpointReturnOwner owner, IFluidHandler capability) {
    public EndpointFluidReturnContext(EndpointReturnOwner owner) {
        this(Objects.requireNonNull(owner), new GenericStackFluidStorage(owner.inventory()));
    }

    public EndpointFluidReturnContext {
        Objects.requireNonNull(owner);
        Objects.requireNonNull(capability);
    }
}
