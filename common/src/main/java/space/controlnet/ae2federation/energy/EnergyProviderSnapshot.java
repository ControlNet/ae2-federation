package space.controlnet.ae2federation.energy;

import java.util.List;
import space.controlnet.ae2federation.identity.NetworkId;

record EnergyProviderSnapshot<S, P>(NetworkId origin, EnergyProviderGeneration generation, S service,
        List<P> sources) {
}
