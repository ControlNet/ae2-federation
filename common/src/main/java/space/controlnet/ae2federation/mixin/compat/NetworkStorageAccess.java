package space.controlnet.ae2federation.mixin.compat;

import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import java.util.List;
import java.util.NavigableMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read-only native mount order (including order within a priority) and recursion flag, pinned to AE2 19.2.17. */
@Mixin(NetworkStorage.class)
public interface NetworkStorageAccess {
    @Accessor("priorityInventory")
    NavigableMap<Integer, List<MEStorage>> ae2federation$orderedMounts();

    /** True while the storage is inside one of its own operations (AE2's guard against its own recursion). */
    @Accessor("mountsInUse")
    boolean ae2federation$mountsInUse();
}
