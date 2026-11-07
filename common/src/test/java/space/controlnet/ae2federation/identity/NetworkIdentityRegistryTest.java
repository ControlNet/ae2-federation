package space.controlnet.ae2federation.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import appeng.api.networking.IGrid;
import java.lang.reflect.Proxy;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

final class NetworkIdentityRegistryTest {
    @Test
    void settlingKeepsNothingToSave() {
        var registry = new NetworkIdentityRegistry();
        for (int index = 0; index < 3; index++) {
            var grid = grid();
            registry.add(grid, new NodeLineage(new NetworkId(UUID.randomUUID()), UUID.randomUUID(), 1));
            assertEquals(IdentityStatus.SETTLED, registry.settle(grid).status());
        }

        assertFalse(registry.isDirty(), "Settled statuses are never read back, so settling must not dirty the save");
        assertEquals(new CompoundTag(), registry.save(new CompoundTag(), null));
    }

    /** A Grid the claim index only keys by identity. */
    private static IGrid grid() {
        var identity = new Object();
        return (IGrid) Proxy.newProxyInstance(IGrid.class.getClassLoader(), new Class<?>[] {IGrid.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(identity);
                    case "equals" -> proxy == arguments[0];
                    case "toString" -> "grid@" + System.identityHashCode(identity);
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }
}
