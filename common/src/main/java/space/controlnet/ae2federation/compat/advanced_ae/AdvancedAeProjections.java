package space.controlnet.ae2federation.compat.advanced_ae;

import space.controlnet.ae2federation.crafting.projection.AddonProjections;

/** AdvancedAE's provider logic, which Neo ECO reads and pushes to only through its provider interface. */
public final class AdvancedAeProjections implements AddonProjections {
    private static final String LOGIC = "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic";

    @Override
    public boolean showsLogic(Class<?> type) {
        return type.getName().equals(LOGIC);
    }
}
