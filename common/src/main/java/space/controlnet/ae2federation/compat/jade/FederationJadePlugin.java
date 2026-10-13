package space.controlnet.ae2federation.compat.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import space.controlnet.ae2federation.router.FederationCableBlock;
import space.controlnet.ae2federation.router.FederationCableBlockEntity;

/** Jade finds this by its annotation; nothing else refers to Jade, so the mod runs without it. */
@WailaPlugin
public final class FederationJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(CableCapacityProvider.INSTANCE, FederationCableBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CableCapacityProvider.INSTANCE, FederationCableBlock.class);
    }
}
