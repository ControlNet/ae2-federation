package space.controlnet.ae2federation.test.mixed;

import static space.controlnet.ae2federation.test.processing.ProcessingRegressionFixtures.item;

import appeng.api.stacks.AEItemKey;
import appeng.helpers.externalstorage.GenericStackItemStorage;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.test.processing.NativeProviderLaneFixtures;

final class MixedProcessingMachine {
    private final NativeProviderLaneFixtures processing;
    private final MixedFactoryTopology topology;
    private final MixedMachineBlockEntity machine;

    MixedProcessingMachine(GameTestHelper helper, NativeProviderLaneFixtures processing, MixedFactoryTopology topology) {
        this.processing = processing;
        this.topology = topology;
        helper.setBlock(NativeProviderLaneFixtures.TARGET_POS, MixedMachineRegistration.BLOCK.get());
        machine = helper.getBlockEntity(NativeProviderLaneFixtures.TARGET_POS);
        installPatterns();
        configureMachine();
    }

    void releaseBlocked() {
        machine.releaseBlockedRecipes();
    }

    MixedMachineBlockEntity machine() {
        return machine;
    }

    private void installPatterns() {
        var recipes = topology.processingRecipes();
        for (var index = 0; index < recipes.size(); index++) {
            var recipe = recipes.get(index);
            processing.installPattern(index, List.of(item(recipe.input(), 1)), List.of(item(recipe.output(), 1)));
        }
    }

    void authorizeHandlers() {
        for (var mapping : mappings()) {
            MixedFactoryObservation.authorizeMachine(machine, machine.inputHandler(), machine.outputInventory(),
                    mapping.returnOwner(), AEItemKey.of(mapping.input()), AEItemKey.of(mapping.output()));
        }
    }

    private void configureMachine() {
        machine.configure(mappings().stream().map(mapping -> new MixedMachineBlockEntity.MachineRecipe(mapping.input(),
                mapping.output(), mapping.blocked(), mapping.returnOwner(),
                new GenericStackItemStorage(mapping.returnOwner()))).toList());
    }

    private List<Mapping> mappings() {
        var recipes = topology.processingRecipes();
        var chainPatterns = topology.alternatives().size() + topology.chainOutputs().size() - 1;
        var result = new ArrayList<Mapping>(recipes.size());
        for (var index = 0; index < recipes.size(); index++) {
            var lane = index < chainPatterns ? 0 : index - chainPatterns + 1;
            var recipe = recipes.get(index);
            result.add(new Mapping(recipe.input(), recipe.output(), lane > 0, processing.lane(lane).getReturnInv()));
        }
        return List.copyOf(result);
    }

    private record Mapping(net.minecraft.world.item.Item input, net.minecraft.world.item.Item output, boolean blocked,
            appeng.helpers.patternprovider.PatternProviderReturnInventory returnOwner) {
    }
}
