package space.controlnet.ae2federation.router;

import java.util.HashSet;
import java.util.OptionalInt;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import space.controlnet.ae2federation.identity.NetworkId;

/**
 * What a player is told about a Router or Switch block: the body it belongs to and, for a Switch, how many distinct
 * settled ME networks that body attaches, the way capacity counting sees them. A block outside a formed body speaks
 * for itself only.
 */
public record DeviceSummary(DeviceStructure.Result body, OptionalInt networks) {
    public static DeviceSummary of(ServerLevel level, BlockPos position) {
        var body = DeviceStructureUpdater.body(level, position);
        if (!(level.getBlockEntity(position) instanceof RouterBlockEntity device)
                || device.getType() != RouterRegistration.SWITCH_BLOCK_ENTITY.get()) {
            return new DeviceSummary(body, OptionalInt.empty());
        }
        var networks = new HashSet<NetworkId>(device.attachedNetworks());
        if (body instanceof DeviceStructure.Valid valid) {
            for (var member : BlockPos.betweenClosed(valid.min(), valid.max())) {
                if (level.getBlockEntity(member) instanceof RouterBlockEntity other) networks.addAll(other.attachedNetworks());
            }
        }
        return new DeviceSummary(body, OptionalInt.of(networks.size()));
    }
}
