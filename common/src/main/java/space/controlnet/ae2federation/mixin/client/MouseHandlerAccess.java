package space.controlnet.ae2federation.mixin.client;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets a switch between Federation screens put the cursor back where it was; see {@code FederationScreenSwitch}. */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccess {
    @Accessor("xpos")
    void ae2federation$setXpos(double xpos);

    @Accessor("ypos")
    void ae2federation$setYpos(double ypos);
}
