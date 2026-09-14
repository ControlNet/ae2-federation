package space.controlnet.ae2federation.identity;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

public final class NetworkIdentityNodeSeed {
    private static final String DATA_KEY = "ae2federation_network_identity";

    private NetworkIdentityNodeSeed() {
    }

    public static CompoundTag managedNode(String tagName, NetworkId networkId) {
        var identity = new CompoundTag();
        identity.putInt("schema", 1);
        identity.putUUID("network", networkId.value());
        identity.putUUID("node", UUID.randomUUID());
        identity.putLong("revision", 1);

        var node = new CompoundTag();
        node.put(DATA_KEY, identity);
        var root = new CompoundTag();
        root.put(tagName, node);
        return root;
    }
}
