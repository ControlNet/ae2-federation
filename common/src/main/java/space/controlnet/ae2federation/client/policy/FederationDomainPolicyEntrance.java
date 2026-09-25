package space.controlnet.ae2federation.client.policy;

import appeng.api.parts.PartHelper;
import appeng.api.util.AECableType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.bridge.BridgeOperationalReason;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.router.RouterBlockEntity;

sealed interface FederationDomainPolicyEntrance permits RouterPolicyEntrance, BridgePolicyEntrance {
    BlockPos position();

    boolean present(ServerLevel level);

    boolean enabled();

    String diagnostic();

    Component label(ServerLevel level);
}

record RouterPolicyEntrance(BlockPos position) implements FederationDomainPolicyEntrance {
    @Override
    public boolean present(ServerLevel level) {
        return level.getBlockEntity(position) instanceof RouterBlockEntity;
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
    public Component label(ServerLevel level) {
        return Component.translatable("ae2federation.ui.domain.entrance.router");
    }
}

record BridgePolicyEntrance(BlockPos position, Direction side, BridgeOperationalReason reason)
        implements FederationDomainPolicyEntrance {
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
    public Component label(ServerLevel level) {
        var host = PartHelper.getPartHost(level, position);
        if (host != null && host.getPart(side) instanceof MultipartBridgePart bridge) {
            return Component.translatable("ae2federation.ui.domain.entrance.bridge_detail",
                    side.getSerializedName(), bridge.getCableConnectionLength(AECableType.GLASS));
        }
        return Component.translatable("ae2federation.ui.domain.entrance.bridge");
    }
}
