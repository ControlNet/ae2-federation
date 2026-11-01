package space.controlnet.ae2federation.test.ui;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import mekanism.api.RelativeSide;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.interfaces.ISideConfiguration;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;

/**
 * Mekanism's Enrichment Chamber, for which {@code learn_pattern} writes an iron dust recipe: a recipe the server cannot
 * read, which the client's JEI scan sent ({@link ViewerPatternScenario}).
 */
@LDLRegisterClient(name = "ui.automation-viewer-pattern", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class AutomationViewerPatternScenario extends ViewerPatternScenario {
    public AutomationViewerPatternScenario() {
        super(-36, 20, ResourceLocation.fromNamespaceAndPath("mekanism", "enrichment_chamber"), "mekanism:dust_iron",
                "automation-viewer");
    }

    /**
     * As a player does with Mekanism's Configurator: a newly placed Mekanism machine has every side disabled.
     */
    @Override
    void openMachine(ServerContext context, BlockPos at, Direction face) {
        var chamber = (ISideConfiguration) context.level().getBlockEntity(at);
        var side = RelativeSide.fromDirections(chamber.getDirection(), face);
        if (!chamber.getConfig().getConfig(TransmissionType.ITEM).setDataType(DataType.INPUT, side)) {
            throw new IllegalStateException("The chamber refused items on its side toward the provider");
        }
        chamber.getConfig().sideChanged(TransmissionType.ITEM, side);
    }
}
