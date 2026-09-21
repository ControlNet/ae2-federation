package space.controlnet.ae2federation.observability.id;

import space.controlnet.ae2federation.fabric.FabricId;

public interface ScopedObservationId extends Comparable<ScopedObservationId> {
    FabricId fabricId();

    String value();

    @Override
    default int compareTo(ScopedObservationId other) {
        var kind = getClass().getName().compareTo(other.getClass().getName());
        if (kind != 0) {
            return kind;
        }
        var fabric = fabricId().toString().compareTo(other.fabricId().toString());
        return fabric != 0 ? fabric : value().compareTo(other.value());
    }
}
