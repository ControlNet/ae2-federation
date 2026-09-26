package space.controlnet.ae2federation.processing.endpoint;

import java.util.Objects;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

public record EndpointItemReturnAttempt(int requestedAmount, int acceptedAmount, ItemStack remainder) {
    public EndpointItemReturnAttempt {
        if (requestedAmount < 0 || acceptedAmount < 0 || acceptedAmount > requestedAmount) {
            throw new IllegalArgumentException("Endpoint return amounts must describe a valid transfer");
        }
        remainder = Objects.requireNonNull(remainder).copy();
    }

    public static EndpointItemReturnAttempt insert(@Nullable IItemHandler handler, int slot, ItemStack stack,
            boolean simulate) {
        var offered = Objects.requireNonNull(stack).copy();
        var requested = offered.getCount();
        if (handler == null) {
            return new EndpointItemReturnAttempt(requested, 0, offered);
        }
        var remainder = handler.insertItem(slot, offered, simulate);
        return new EndpointItemReturnAttempt(requested, requested - remainder.getCount(), remainder);
    }
}
