package space.controlnet.ae2federation.processing.provider;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

public interface MappedPatternProviderHost extends PatternProviderLogicHost {
    MappedPatternProvider mappedPatternProvider();

    @Override
    default PatternProviderLogic getLogic() {
        return mappedPatternProvider().nativeLane(0);
    }

    @Override
    default int getPriority() {
        return mappedPatternProvider().priority();
    }

    @Override
    default void setPriority(int priority) {
        mappedPatternProvider().setPriority(priority);
    }

    @Override
    default InternalInventory getTerminalPatternInventory() {
        return mappedPatternProvider().getTerminalPatternInventory();
    }

    @Override
    default boolean isVisibleInTerminal() {
        return mappedPatternProvider().isVisibleInTerminal();
    }
}
