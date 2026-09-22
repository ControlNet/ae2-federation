package space.controlnet.ae2federation.neoforge.client;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import org.slf4j.Logger;
import space.controlnet.ae2federation.client.ClientStartup;
import space.controlnet.ae2federation.neoforge.network.ObservationPayloads;

@Mod(value = "ae2federation", dist = Dist.CLIENT)
public final class NeoForgeClientEntrypoint {
    private static final Logger LOGGER = LogUtils.getLogger();

    public NeoForgeClientEntrypoint() {
        ClientStartup.start(LOGGER);
        NeoForge.EVENT_BUS.addListener(NeoForgeClientEntrypoint::onLoggingOut);
    }

    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ObservationPayloads.clientStates().reset();
    }
}
