package space.controlnet.ae2federation.test.processing;

import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.core.definitions.AEBlocks;
import appeng.me.service.CraftingService;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.processing.provider.MappedPatternProvider;

final class NativeProviderInspection {
    private static final BlockPos BYPASS_POS = NativeProviderLaneFixtures.HOST_POS.north();
    private final GameTestHelper helper;
    private final IManagedGridNode node;
    private final MappedPatternProvider provider;

    NativeProviderInspection(GameTestHelper helper, IManagedGridNode node, MappedPatternProvider provider) {
        this.helper = helper;
        this.node = node;
        this.provider = provider;
    }

    List<Long> nativeTickerInvocations() {
        return provider.nativeTickerInvocations();
    }

    List<ICraftingProvider> publishedProviders(appeng.api.crafting.IPatternDetails pattern) {
        var service = (CraftingService) node.getGrid().getCraftingService();
        var providers = new ArrayList<ICraftingProvider>();
        service.getProviders(pattern).forEach(providers::add);
        return List.copyOf(providers);
    }

    void installBypassCraftingMachine() {
        helper.setBlock(BYPASS_POS, AEBlocks.MOLECULAR_ASSEMBLER.block());
    }

    boolean hasBypassCraftingMachine() {
        var machine = ICraftingMachine.of(helper.getLevel(), helper.absolutePos(BYPASS_POS), Direction.SOUTH);
        return machine != null && machine.acceptsPlans();
    }

    boolean bypassCraftingMachineIsEmpty() {
        var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(BYPASS_POS));
        return blockEntity instanceof appeng.blockentity.crafting.MolecularAssemblerBlockEntity assembler
                && assembler.getInternalInventory().isEmpty();
    }
}
