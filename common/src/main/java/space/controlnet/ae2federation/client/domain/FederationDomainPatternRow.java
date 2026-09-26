package space.controlnet.ae2federation.client.domain;

import java.util.Set;

public final class FederationDomainPatternRow {
    private FederationDomainPatternRow() {
    }

    public static String format(int slot, String name, String quantities, Set<Integer> lanes) {
        return slot + " | " + name + " | inputs=" + quantities + " | lanes=" + lanes;
    }
}
