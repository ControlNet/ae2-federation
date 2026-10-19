package space.controlnet.ae2federation.mixin.compat;

import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import java.util.List;
import java.util.NavigableMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only native mount order, including order within a priority, pinned to AE2 19.2.17. */
@Mixin(NetworkStorage.class)
public interface NetworkStorageAccess {
    @Accessor("priorityInventory")
    NavigableMap<Integer, List<MEStorage>> ae2federation$orderedMounts();
}
