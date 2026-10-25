package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;

/** Test-only inexhaustible input inventory, with exact-key extraction accounting and no item sink. */
final class PlaythroughSource implements MEStorage, AutoCloseable {
    private final Set<AEKey> supplies;
    final Map<AEKey, Long> extracted = new HashMap<>();
    private final IManagedGridNode node;

    PlaythroughSource(ServerLevel level, BlockPos position, IGridNode connection, Set<AEKey> supplies) {
        this.supplies = Set.copyOf(supplies);
        node = GridHelper.createManagedNode(this, new IGridNodeListener<PlaythroughSource>() {
            @Override public void onSaveChanges(PlaythroughSource owner, IGridNode node) { }
        }).setInWorldNode(false).setIdlePowerUsage(0)
                .addService(IStorageProvider.class, mounts -> mounts.mount(this, -100));
        node.create(level, position);
        GridHelper.createConnection(node.getNode(), connection);
    }

    @Override public long extract(AEKey key, long amount, Actionable mode, IActionSource source) {
        if (!supplies.contains(key)) return 0;
        if (mode == Actionable.MODULATE && amount > 0) extracted.merge(key, amount, Math::addExact);
        return amount;
    }

    @Override public void getAvailableStacks(KeyCounter out) {
        supplies.forEach(key -> out.add(key, 1L << 40));
    }

    @Override public Component getDescription() { return Component.literal("Playthrough unlimited materials"); }
    @Override public void close() { node.destroy(); }
}
