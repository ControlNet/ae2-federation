package space.controlnet.ae2federation.compat;

import net.neoforged.fml.common.Mod;

/** Test-only mod that runs compatibility GameTests in a production server; see {@link ProductionGameTestRunner}. */
@Mod("ae2federation_compat")
public final class FederationCompatMod {
    public FederationCompatMod() {
        ProductionGameTestRunner.registerIfRequested();
    }
}
