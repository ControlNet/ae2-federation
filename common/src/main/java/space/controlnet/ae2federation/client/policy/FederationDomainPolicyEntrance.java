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

sealed interface FederationDomainPolicyEntrance permits RouterPolicyEntrance, BridgePolicyEntrance, DevicePolicyEntrance {
    BlockPos position();

    boolean present(ServerLevel level);

    boolean enabled();

    Component diagnostic();

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
    public Component diagnostic() {
        return Component.translatable("ae2federation.ui.domain.status.pending");
    }

    @Override
    public Component label(ServerLevel level) {
        return Component.translatable("ae2federation.ui.domain.entrance.router");
    }
}

record BridgePolicyEntrance(BlockPos position, Direction side, BridgeOperationalReason reason, boolean identityConfirmed)
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
    public Component diagnostic() {
        if (reason == BridgeOperationalReason.VALID && !identityConfirmed) {
            return Component.translatable("ae2federation.ui.workspace.bridge_reason.identity_unsettled");
        }
        return Component.translatable("ae2federation.ui.workspace.bridge_reason." + reason.name().toLowerCase(java.util.Locale.ROOT));
    }

    @Override
    public Component label(ServerLevel level) {
        var host = PartHelper.getPartHost(level, position);
        if (host != null && host.getPart(side) instanceof MultipartBridgePart bridge) {
            return Component.translatable("ae2federation.ui.domain.entrance.bridge_detail",
                    Component.translatable("ae2federation.ui.workspace.face." + side.getSerializedName()),
                    bridge.getCableConnectionLength(AECableType.GLASS));
        }
        return Component.translatable("ae2federation.ui.domain.entrance.bridge");
    }
}

/**
 * A Provider or Endpoint entrance holds the block entity it was opened on, as AE2's own menus do: a device broken and
 * replaced at the same position is another device, and the screen's slots still point at the removed one.
 */
record DevicePolicyEntrance(BlockPos position, boolean provider,
        @org.jetbrains.annotations.Nullable net.minecraft.world.level.block.entity.BlockEntity entity)
        implements FederationDomainPolicyEntrance {
    @Override
    public boolean present(ServerLevel level) {
        return entity != null && !entity.isRemoved() && level.getBlockEntity(position) == entity;
    }

    @Override public boolean enabled() { return true; }
    @Override public Component diagnostic() { return Component.translatable("ae2federation.ui.domain.status.pending"); }
    @Override public Component label(ServerLevel level) {
        return Component.translatable("ae2federation.ui.workspace.entrance." + (provider ? "provider" : "endpoint"),
                position.getX(), position.getY(), position.getZ());
    }
}
