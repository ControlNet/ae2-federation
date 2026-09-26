package space.controlnet.ae2federation;

import appeng.api.networking.GridServices;
import org.slf4j.Logger;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu;
import space.controlnet.ae2federation.identity.NetworkIdentityGridService;
import space.controlnet.ae2federation.identity.NetworkIdentityService;

public final class CommonStartup {
    private CommonStartup() {
    }

    public static void start(Logger logger) {
        GridServices.register(NetworkIdentityService.class, NetworkIdentityGridService.class);
        FederationDomainPolicyMenu.register();
        logger.info("AE2 Federation common startup complete");
    }
}
