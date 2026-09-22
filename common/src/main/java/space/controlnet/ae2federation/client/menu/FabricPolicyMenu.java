package space.controlnet.ae2federation.client.menu;

import com.lowdragmc.lowdraglib2.gui.factory.PlayerUIMenuType;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerMenu;
import java.util.Optional;
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
        if (PENDING.putIfAbsent(player.getUUID(), session) != null) {
            return false;
        }
        var opened = PlayerUIMenuType.openUI(player, ID);
        if (!opened) {
            PENDING.remove(player.getUUID(), session);
        }
        return opened;
    }

    public static FabricPolicyActionResult dispatch(ServerPlayer player, FabricPolicyActionRequest request) {
        if (!player.getServer().isSameThread()) {
            return FabricPolicyActionResult.WRONG_THREAD;
        }
        if (!(player.containerMenu instanceof ModularUIContainerMenu menu)
                || !(menu.uiHolder instanceof FabricPolicyMenuHolder holder)) {
            return FabricPolicyActionResult.WRONG_MENU;
        }
        return holder.dispatch(player, menu, request);
    }

    public static Optional<FabricPolicyActionRequest> currentRequest(ServerPlayer player, FabricPolicyAction action) {
        if (player.containerMenu instanceof ModularUIContainerMenu menu
                && menu.uiHolder instanceof FabricPolicyMenuHolder holder) {
            return holder.currentRequest(menu, action);
        }
        return Optional.empty();
    }

    public static Receipt currentReceipt(ServerPlayer player) {
        if (player.containerMenu instanceof ModularUIContainerMenu menu
                && menu.uiHolder instanceof FabricPolicyMenuHolder holder) {
            return new Receipt(menu.containerId, holder.currentSequence(), holder.currentMappingStatus());
        }
        throw new IllegalStateException("Fabric policy menu is not current");
    }

    public record Receipt(int containerId, long sequence, String mappingStatus) {
    }
}
