package space.controlnet.ae2federation.compat;

import net.neoforged.fml.common.Mod;

/** Test-only mod that runs compatibility GameTests in a production server; see {@link ProductionGameTestRunner}. */
@Mod("ae2federation_compat")
public final class FederationCompatMod {
    public FederationCompatMod(net.neoforged.bus.api.IEventBus modBus) {
        modBus.addListener(space.controlnet.ae2federation.test.world.OtherDimensionSite::register);
        modBus.addListener(space.controlnet.ae2federation.test.world.RestartChunkTickets::register);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                space.controlnet.ae2federation.test.world.OtherDimensionSite::onServerStopped);
        ProductionGameTestRunner.registerIfRequested();
    }
}
