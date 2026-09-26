package space.controlnet.ae2federation.test.processing;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.me.service.TickManagerService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import space.controlnet.ae2federation.mixin.PatternProviderLogicAccess;
import space.controlnet.ae2federation.processing.provider.PatternSlotHandle;

public final class ProviderLifecycleFixtureSupport {
    private ProviderLifecycleFixtureSupport() {
    }

    public static void setPattern(NativeProviderLaneFixtures fixture, int slot, Item input, Item output) {
        var pattern = appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(input), 1)),
                List.of(new GenericStack(AEItemKey.of(output), 1)));
        fixture.composition().patternInventory().setItemDirect(slot, pattern);
    }

    public static void clearPattern(NativeProviderLaneFixtures fixture, int slot) {
        fixture.composition().patternInventory().setItemDirect(slot, ItemStack.EMPTY);
    }

    public static IPatternDetails pattern(NativeProviderLaneFixtures fixture, int laneIndex, int patternIndex) {
        return fixture.lane(laneIndex).getAvailablePatterns().get(patternIndex);
    }

    public static boolean pushDetails(NativeProviderLaneFixtures fixture, int laneIndex, IPatternDetails pattern) {
        var input = pattern.getInputs()[0].getPossibleInputs()[0];
        var counter = new KeyCounter();
        counter.add(input.what(), input.amount());
        return fixture.lane(laneIndex).pushPattern(pattern, new KeyCounter[] { counter });
    }

    public static List<Long> nativeProviderRefreshInvocations(NativeProviderLaneFixtures fixture) {
        return fixture.composition().nativeProviderRefreshInvocations();
    }

    public static PatternSlotHandle mappingHandle(NativeProviderLaneFixtures fixture, int slot) {
        return fixture.composition().mappingHandle(slot);
    }

    public static boolean replaceMapping(NativeProviderLaneFixtures fixture, PatternSlotHandle handle,
            Set<Integer> lanes) {
        return fixture.composition().replaceMapping(handle, lanes);
    }

    public static Set<AEKey> blockingInputs(NativeProviderLaneFixtures fixture, int laneIndex) {
        return Set.copyOf(((PatternProviderLogicAccess) (Object) fixture.lane(laneIndex))
                .ae2federation$getPatternInputs());
    }

    public static List<PatternContainer> activePatternContainers(NativeProviderLaneFixtures fixture) {
        var grid = fixture.managedNode().getGrid();
        var containers = new ArrayList<PatternContainer>();
        for (var machineClass : grid.getMachineClasses()) {
            if (PatternContainer.class.isAssignableFrom(machineClass)) {
                @SuppressWarnings("unchecked")
                var containerClass = (Class<? extends PatternContainer>) machineClass;
                containers.addAll(grid.getActiveMachines(containerClass));
            }
        }
        return List.copyOf(containers);
    }

    public static void putTargetItem(NativeProviderLaneFixtures fixture, Item item) {
        ((ChestBlockEntity) fixture.helper().getBlockEntity(NativeProviderLaneFixtures.TARGET_POS))
                .setItem(0, new ItemStack(item));
    }

    public static void clearTarget(NativeProviderLaneFixtures fixture) {
        ((ChestBlockEntity) fixture.helper().getBlockEntity(NativeProviderLaneFixtures.TARGET_POS)).clearContent();
    }

    public static void setRedstoneSignal(NativeProviderLaneFixtures fixture, boolean powered) {
        fixture.helper().setBlock(NativeProviderLaneFixtures.HOST_POS.above(),
                powered ? Blocks.REDSTONE_BLOCK : Blocks.AIR);
        fixture.composition().updateRedstoneState();
    }

    public static TickManagerService.NodeStatus tickerStatus(NativeProviderLaneFixtures fixture) {
        var node = fixture.managedNode();
        return ((TickManagerService) node.getGrid().getTickManager()).getStatus(node.getNode());
    }

    public static boolean sleepNativeTicker(NativeProviderLaneFixtures fixture) {
        var node = fixture.managedNode();
        return node.getGrid().getTickManager().sleepDevice(node.getNode());
    }
}
