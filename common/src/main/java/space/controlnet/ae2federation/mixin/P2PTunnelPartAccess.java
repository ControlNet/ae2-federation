package space.controlnet.ae2federation.mixin;

import appeng.parts.p2p.P2PTunnelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Whether a P2P tunnel is an output, which AE2 sets only from a player's memory card; programs make a tunnel an input
 * or unlink it as the card does.
 */
@Mixin(P2PTunnelPart.class)
public interface P2PTunnelPartAccess {
    @Invoker("setOutput")
    void ae2federation$setOutput(boolean output);
}
