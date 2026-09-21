package space.controlnet.ae2federation.energy;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import java.util.List;
import space.controlnet.ae2federation.identity.NetworkId;

record NativeEnergyBackend(NetworkId origin, EnergyProviderGeneration generation, IGrid sourceGrid,
        IEnergyService service, List<NativeEnergySource> sources) {
}
