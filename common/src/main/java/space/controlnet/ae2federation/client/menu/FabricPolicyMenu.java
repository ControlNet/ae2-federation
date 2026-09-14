package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import space.controlnet.ae2federation.bridge.BridgeRightClickContext;
import space.controlnet.ae2federation.client.policy.FabricPolicySession;

public final class FabricPolicyMenu {
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("ae2federation", "fabric_policy");
    private static final Map<UUID, FabricPolicySession> PENDING = new ConcurrentHashMap<>();

    private FabricPolicyMenu() {
    }

    public static void register() {
        PlayerUIMenuType.register(ID, player -> new FabricPolicyMenuHolder(
                player instanceof ServerPlayer serverPlayer ? PENDING.remove(serverPlayer.getUUID()) : null));
    }

    public static boolean openHub(ServerPlayer player, BlockPos position) {
        return open(player, FabricPolicySession.forHub(player, position));
    }

    public static boolean openBridge(ServerPlayer player, BridgeRightClickContext bridge) {
        return open(player, FabricPolicySession.forBridge(player, bridge));
    }

    private static boolean open(ServerPlayer player, FabricPolicySession session) {
        PENDING.put(player.getUUID(), session);
        var opened = PlayerUIMenuType.openUI(player, ID);
        if (!opened) {
            PENDING.remove(player.getUUID());
        }
        return opened;
    }
}
