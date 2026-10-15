package space.controlnet.ae2federation.compat.jade;

import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlock;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlock;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;
import space.controlnet.ae2federation.router.FederationCableBlock;
import space.controlnet.ae2federation.router.FederationCableBlockEntity;
import space.controlnet.ae2federation.router.RouterBlock;
import space.controlnet.ae2federation.router.RouterBlockEntity;

/** Jade finds this by its annotation; nothing else refers to Jade, so the mod runs without it. */
@WailaPlugin
public final class FederationJadePlugin implements IWailaPlugin {
    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(CableCapacityProvider.INSTANCE, FederationCableBlockEntity.class);
        registration.registerBlockDataProvider(DeviceProvider.INSTANCE, RouterBlockEntity.class);
        registration.registerBlockDataProvider(EndpointProvider.INSTANCE, EndpointBlockEntity.class);
        registration.registerBlockDataProvider(PatternProviderProvider.INSTANCE, FederationPatternProviderBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(CableCapacityProvider.INSTANCE, FederationCableBlock.class);
        registration.registerBlockComponent(DeviceProvider.INSTANCE, RouterBlock.class);
        registration.registerBlockComponent(EndpointProvider.INSTANCE, EndpointBlock.class);
        registration.registerBlockComponent(PatternProviderProvider.INSTANCE, FederationPatternProviderBlock.class);
    }
}
