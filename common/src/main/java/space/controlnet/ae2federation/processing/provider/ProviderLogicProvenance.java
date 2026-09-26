package space.controlnet.ae2federation.processing.provider;

import appeng.helpers.patternprovider.PatternProviderLogic;
import java.util.Objects;

public final class ProviderLogicProvenance {
    private final PatternProviderLogic logic;
    private final ProviderLaneIdentity lane;

    ProviderLogicProvenance(PatternProviderLogic logic, ProviderLaneIdentity lane) {
        this.logic = Objects.requireNonNull(logic);
        this.lane = Objects.requireNonNull(lane);
    }

    public PatternProviderLogic logic() {
        return logic;
    }

    public ProviderLaneIdentity lane() {
        return lane;
    }
}
