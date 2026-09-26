package space.controlnet.ae2federation.test.processing;

import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.helpers.patternprovider.PatternProviderLogic;
import java.util.List;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import space.controlnet.ae2federation.test.mixin.PatternProviderLogicReturnAccess;

public final class ProcessingRegressionFixtures {
    private ProcessingRegressionFixtures() {
    }

    public static GenericStack item(Item item, long amount) {
        return new GenericStack(AEItemKey.of(item), amount);
    }

    public static GenericStack fluid(Fluid fluid, long amount) {
        return new GenericStack(AEFluidKey.of(fluid), amount);
    }

    public static List<GenericStack> sendList(PatternProviderLogic logic) {
        return List.copyOf(((PatternProviderLogicReturnAccess) logic).ae2federation_test$getSendList());
    }

    public static boolean injectReturns(PatternProviderLogic logic, GenericStackInv destination) {
        return logic.getReturnInv().injectIntoNetwork(destination, IActionSource.empty(),
                ((PatternProviderLogicReturnAccess) logic)::ae2federation_test$onStackReturned);
    }

    public static int dropCount(List<ItemStack> drops, Item item) {
        return drops.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
