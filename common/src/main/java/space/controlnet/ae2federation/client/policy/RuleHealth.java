package space.controlnet.ae2federation.client.policy;

import java.util.Set;

/**
 * How a rule reads on the graph, from the server's last runtime observation. An error is a condition that will not
 * clear by waiting: a missing operation or a backend reason the player has to fix in the world.
 */
public enum RuleHealth {
    OFF, ACTIVE, WAITING, ERROR;

    private static final Set<String> BLOCKING_BACKENDS = Set.of("crafting_cycle",
            "crafting_cpu_missing", "crafting_provider_missing", "crafting_storage_required",
            "energy_connection_missing", "domain_reference_missing");

    public static RuleHealth of(boolean enabled, String code, String backend) {
        if (!enabled) return OFF;
        if (code.equals("published")) return ACTIVE;
        if (code.equals("operation_missing") || BLOCKING_BACKENDS.contains(backend)) return ERROR;
        return WAITING;
    }
}
