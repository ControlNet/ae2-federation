package space.controlnet.ae2federation.test.mixed;

import java.util.Map;

public record MixedFactoryIteration(int index, boolean warmup, long elapsedNanos,
        MixedFactoryObservation.Snapshot nativeObservation, Map<String, Long> initialSource,
        Map<String, Long> finalSource, Map<String, Long> callbackResults, long finalStocked, long finalExported) {
    public String runtimeIdentity() {
        return nativeObservation.planningService() + ":" + nativeObservation.cpuOwner() + ":"
                + String.join(",", nativeObservation.craftingIds().stream().sorted().toList());
    }
}
