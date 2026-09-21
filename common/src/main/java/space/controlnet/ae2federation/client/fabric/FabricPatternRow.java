package space.controlnet.ae2federation.client.fabric;

import java.util.Set;

public final class FabricPatternRow {
    private FabricPatternRow() {
    }

    public static String format(int slot, String name, String quantities, Set<Integer> lanes) {
        return slot + " | " + name + " | inputs=" + quantities + " | lanes=" + lanes;
    }
}
