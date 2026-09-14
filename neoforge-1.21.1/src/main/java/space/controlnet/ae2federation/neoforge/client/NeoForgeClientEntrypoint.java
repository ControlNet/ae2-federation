package space.controlnet.ae2federation.neoforge.client;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import space.controlnet.ae2federation.client.ClientStartup;

@Mod(value = "ae2federation", dist = Dist.CLIENT)
public final class NeoForgeClientEntrypoint {
    private static final Logger LOGGER = LogUtils.getLogger();

    public NeoForgeClientEntrypoint() {
        ClientStartup.start(LOGGER);
    }
}
