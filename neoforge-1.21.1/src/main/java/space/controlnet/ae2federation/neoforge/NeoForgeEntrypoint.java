package space.controlnet.ae2federation.neoforge;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import space.controlnet.ae2federation.CommonStartup;
import space.controlnet.ae2federation.bridge.BridgeRegistration;
import space.controlnet.ae2federation.hub.HubRegistration;
import space.controlnet.ae2federation.processing.ProcessingRegistration;

@Mod(NeoForgeEntrypoint.MOD_ID)
public final class NeoForgeEntrypoint {
    public static final String MOD_ID = "ae2federation";
    private static final Logger LOGGER = LogUtils.getLogger();

    public NeoForgeEntrypoint(IEventBus modBus) {
        CommonStartup.start(LOGGER);
        BridgeRegistration.register(modBus);
        HubRegistration.register(modBus);
        ProcessingRegistration.register(modBus);
    }
}
