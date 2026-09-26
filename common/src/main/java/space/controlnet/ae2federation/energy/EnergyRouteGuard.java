package space.controlnet.ae2federation.energy;

import java.util.HashSet;
import java.util.function.DoubleSupplier;
import space.controlnet.ae2federation.policy.PolicyKey;

final class EnergyRouteGuard {
    private static final ThreadLocal<HashSet<PolicyKey>> ACTIVE = ThreadLocal.withInitial(HashSet::new);

    private EnergyRouteGuard() {
    }

    static double call(PolicyKey key, DoubleSupplier operation) {
        var active = ACTIVE.get();
        if (!active.add(key)) {
            return 0;
        }
        try {
            return operation.getAsDouble();
        } finally {
            active.remove(key);
            if (active.isEmpty()) {
                ACTIVE.remove();
            }
        }
    }
}
