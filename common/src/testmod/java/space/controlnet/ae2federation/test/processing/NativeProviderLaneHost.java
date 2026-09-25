package space.controlnet.ae2federation.test.processing;

import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

final class NativeProviderLaneHost implements PatternProviderLogicHost {
    private final GameTestHelper helper;
    private final BlockPos position;
    private final Direction target;
    private PatternProviderLogic logic;
    private int saveCalls;

    NativeProviderLaneHost(GameTestHelper helper, BlockPos position, Direction target) {
        this.helper = helper;
        this.position = position;
        this.target = target;
    }

    void setLogic(PatternProviderLogic logic) {
        this.logic = logic;
    }

    int saveCalls() {
        return saveCalls;
    }

    @Override
    public PatternProviderLogic getLogic() {
        return logic;
    }

    @Override
    public BlockEntity getBlockEntity() {
        return helper.getBlockEntity(position);
    }

    @Override
    public EnumSet<Direction> getTargets() {
        return EnumSet.of(target);
    }

    @Override
    public void saveChanges() {
        saveCalls++;
        getBlockEntity().setChanged();
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(AEItems.PROCESSING_PATTERN.asItem());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return AEItems.PROCESSING_PATTERN.stack();
    }
}
