package space.controlnet.ae2federation.test.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(GameTestServer.class)
public abstract class CompatibilityGameTestPlacementMixin {
    @ModifyVariable(method = "startTests", at = @At("STORE"), ordinal = 0)
    private BlockPos ae2federation$placePipesNearOrigin(BlockPos position) {
        String testId = System.getProperty("ae2federation.testId", "");
        if (testId.equalsIgnoreCase("compatprettyitems") || testId.equalsIgnoreCase("compatprettyfluids")) {
            return new BlockPos(0, position.getY(), 0);
        }
        return position;
    }
}
