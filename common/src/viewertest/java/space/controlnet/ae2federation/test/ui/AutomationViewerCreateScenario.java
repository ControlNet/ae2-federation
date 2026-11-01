package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.minecraft.resources.ResourceLocation;

/**
 * Create's Basin, for which {@code learn_pattern} writes the iron block: JEI shows the crafting table's recipe again
 * as Create's packing in a basin under the same ID, and the provider facing the basin takes that one as a processing
 * pattern ({@link ViewerPatternScenario}).
 */
@LDLRegisterClient(name = "ui.automation-viewer-create", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class AutomationViewerCreateScenario extends ViewerPatternScenario {
    public AutomationViewerCreateScenario() {
        super(-44, 20, ResourceLocation.fromNamespaceAndPath("create", "basin"), "minecraft:iron_block",
                "automation-viewer-create");
    }
}
