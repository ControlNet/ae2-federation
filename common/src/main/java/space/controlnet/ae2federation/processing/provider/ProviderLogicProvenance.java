package space.controlnet.ae2federation.processing.provider;

import appeng.helpers.patternprovider.PatternProviderLogic;
import java.util.Objects;

public final class ProviderLogicProvenance {
    private final PatternProviderLogic logic;

    ProviderLogicProvenance(PatternProviderLogic logic) {
        this.logic = Objects.requireNonNull(logic);
    }

    public PatternProviderLogic logic() {
        return logic;
    }
}
