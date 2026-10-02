package space.controlnet.ae2federation.client.policy;

import java.util.Set;

/**
 * How a rule reads on the graph, from the server's last runtime observation. An error is a condition that will not
 * clear by waiting: a missing operation or a backend reason the player has to fix in the world.
 */
public enum RuleHealth {
    OFF, ACTIVE, WAITING, ERROR;

    private static final Set<String> BLOCKING_BACKENDS = Set.of("crafting_storage_required",
            "energy_connection_missing", "domain_reference_missing", "storage_access_none", "storage_compile_budget");

    /** {@code storage} is the storage source diagnostic; only an unsettled source network clears by waiting. */
    public static RuleHealth of(boolean enabled, String code, String backend, String storage) {
        if (!enabled) return OFF;
        if (code.equals("published")) return ACTIVE;
        if (code.equals("operation_missing") || BLOCKING_BACKENDS.contains(backend)) return ERROR;
        if (!storage.isEmpty() && !storage.equals("unsettled_origin")) return ERROR;
        return WAITING;
    }
}
