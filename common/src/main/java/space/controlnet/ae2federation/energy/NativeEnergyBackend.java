package space.controlnet.ae2federation.energy;

import appeng.api.networking.IGrid;
import appeng.api.networking.energy.IEnergyService;
import java.util.List;
import space.controlnet.ae2federation.identity.NetworkId;

/**
 * One discovery of a provider Grid's native energy sources. Each discovery is a new object, held by the binding it was
 * discovered for, which also keeps the tick of its last currency check and that check's answer.
 */
final class NativeEnergyBackend {
    private final NetworkId origin;
    private final EnergyProviderGeneration generation;
    private final IGrid sourceGrid;
    private final IEnergyService service;
    private final List<NativeEnergySource> sources;
    private long checkedTick = Long.MIN_VALUE;
    private boolean checkedCurrent;

    NativeEnergyBackend(NetworkId origin, EnergyProviderGeneration generation, IGrid sourceGrid,
            IEnergyService service, List<NativeEnergySource> sources) {
        this.origin = origin;
        this.generation = generation;
        this.sourceGrid = sourceGrid;
        this.service = service;
        this.sources = sources;
    }

    NetworkId origin() {
        return origin;
    }

    EnergyProviderGeneration generation() {
        return generation;
    }

    IGrid sourceGrid() {
        return sourceGrid;
    }

    IEnergyService service() {
        return service;
    }

    List<NativeEnergySource> sources() {
        return sources;
    }

    boolean checkedAt(long tick) {
        return checkedTick == tick;
    }

    boolean checkedCurrent() {
        return checkedCurrent;
    }

    void checked(long tick, boolean current) {
        checkedTick = tick;
        checkedCurrent = current;
    }
}
