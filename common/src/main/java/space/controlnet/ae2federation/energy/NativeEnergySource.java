package space.controlnet.ae2federation.energy;

import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IAEPowerStorage;
import java.util.UUID;

public record NativeEnergySource(UUID registrationNodeId, IGridNode node, IAEPowerStorage storage) {
}
