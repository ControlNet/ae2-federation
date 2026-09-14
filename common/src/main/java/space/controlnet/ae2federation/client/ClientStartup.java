package space.controlnet.ae2federation.client;

import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

public final class ClientStartup {
    private ClientStartup() {
    }

    public static void start(Logger logger) {
        logger.info("AE2 Federation client startup complete for {}", Minecraft.getInstance().getUser().getName());
    }
}
