package space.controlnet.ae2federation.client.policy;

/**
 * What an Endpoint node's dot on the topology says. Off while no shown network uses it, Local or unclaimed, whatever
 * its subnet; waiting while its owner cannot use it yet, as its ME node is down or nothing is connected behind it;
 * active otherwise.
 */
public enum EndpointHealth {
    ACTIVE, WAITING, OFF;

    /** {@code ownerShown} whether the network whose Provider claims it is on the graph. */
    public static EndpointHealth of(String runtimeMode, boolean ownerShown, boolean ready, boolean alone) {
        if (runtimeMode.equals("LOCAL") || !ownerShown) return OFF;
        return ready && !alone ? ACTIVE : WAITING;
    }

    /** The undrawn class that lets tests read the health. */
    public String cssClass() {
        return "health-" + name().toLowerCase(java.util.Locale.ROOT);
    }
}
