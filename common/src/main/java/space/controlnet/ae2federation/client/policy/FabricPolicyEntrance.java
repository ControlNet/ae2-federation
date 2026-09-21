package space.controlnet.ae2federation.client.policy;

import appeng.api.parts.PartHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.bridge.BridgeOperationalReason;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.hub.HubBlockEntity;

sealed interface FabricPolicyEntrance permits HubPolicyEntrance, BridgePolicyEntrance {
    BlockPos position();

    boolean present(ServerLevel level);

    boolean enabled();

    String diagnostic();

    Component label();
}

record HubPolicyEntrance(BlockPos position) implements FabricPolicyEntrance {
    @Override
    public boolean present(ServerLevel level) {
        return level.getBlockEntity(position) instanceof HubBlockEntity;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String diagnostic() {
        return "PENDING_TOPOLOGY";
    }

    @Override
    public Component label() {
        return Component.translatable("ae2federation.ui.fabric.entrance.hub");
    }
}

record BridgePolicyEntrance(BlockPos position, Direction side, BridgeOperationalReason reason)
        implements FabricPolicyEntrance {
    @Override
    public boolean present(ServerLevel level) {
        var host = PartHelper.getPartHost(level, position);
        return host != null && host.getPart(side) instanceof MultipartBridgePart;
    }

    @Override
    public boolean enabled() {
        return reason == BridgeOperationalReason.VALID;
    }

    @Override
    public String diagnostic() {
        return reason.name();
    }

    @Override
    public Component label() {
        return Component.translatable("ae2federation.ui.fabric.entrance.bridge");
    }
}
