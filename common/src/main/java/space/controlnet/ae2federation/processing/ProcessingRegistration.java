package space.controlnet.ae2federation.processing;

import appeng.core.definitions.AEBlocks;
import appeng.api.AECapabilities;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlock;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetBinding;
import space.controlnet.ae2federation.processing.endpoint.EndpointTargetCapability;
import space.controlnet.ae2federation.domain.port.FederationPortCapability;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlock;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;
import space.controlnet.ae2federation.processing.provider.ProviderEnergyRelay;
import space.controlnet.ae2federation.processing.provider.ProviderUpgradeCards;

public final class ProcessingRegistration {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("ae2federation");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("ae2federation");
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, "ae2federation");
    public static final DeferredBlock<EndpointBlock> ENDPOINT = BLOCKS.registerBlock("processing_endpoint",
            EndpointBlock::new, BlockBehaviour.Properties.of().strength(4.0F).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> ENDPOINT_ITEM = ITEMS.registerSimpleBlockItem(ENDPOINT,
            new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EndpointBlockEntity>> ENDPOINT_BLOCK_ENTITY =
            BLOCK_ENTITY_TYPES.register("processing_endpoint", () -> {
                var type = BlockEntityType.Builder.of(EndpointBlockEntity::new, ENDPOINT.get()).build(null);
                ENDPOINT.get().setBlockEntity(EndpointBlockEntity.class, type, null, EndpointBlockEntity::serverTick);
                return type;
            });

    public static final DeferredBlock<FederationPatternProviderBlock> PROVIDER = BLOCKS.registerBlock(
            "pattern_provider", FederationPatternProviderBlock::new,
            BlockBehaviour.Properties.of().strength(2.2F, 11.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> PROVIDER_ITEM = ITEMS.registerSimpleBlockItem(PROVIDER,
            new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FederationPatternProviderBlockEntity>>
            PROVIDER_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("pattern_provider", () -> {
                var type = BlockEntityType.Builder.of(FederationPatternProviderBlockEntity::new, PROVIDER.get())
                        .build(null);
                PROVIDER.get().setBlockEntity(FederationPatternProviderBlockEntity.class, type, null,
                        FederationPatternProviderBlockEntity::serverTick);
                return type;
            });

    private ProcessingRegistration() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        modBus.addListener(ProcessingRegistration::registerCapabilities);
        // Addons register their cards in their own setup, whose order among mods is not fixed; load complete is after all.
        modBus.addListener(FMLLoadCompleteEvent.class,
                event -> event.enqueueWork(() -> ProviderUpgradeCards.inherit(PROVIDER_ITEM.get())));
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlock(EndpointTargetCapability.BLOCK,
                (level, position, state, blockEntity, side) -> level instanceof ServerLevel serverLevel && side != null
                        ? EndpointTargetBinding.find(serverLevel, position, side)
                        : null,
                AEBlocks.INTERFACE.block(), ENDPOINT.get());
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, ENDPOINT_BLOCK_ENTITY.get(),
                (endpoint, context) -> endpoint);
        event.registerBlockEntity(AECapabilities.IN_WORLD_GRID_NODE_HOST, PROVIDER_BLOCK_ENTITY.get(),
                (provider, context) -> provider);
        event.registerBlockEntity(FederationPortCapability.BLOCK, PROVIDER_BLOCK_ENTITY.get(),
                FederationPatternProviderBlockEntity::federationPort);
        event.registerBlockEntity(FederationPortCapability.BLOCK, ENDPOINT_BLOCK_ENTITY.get(),
                EndpointBlockEntity::federationPort);
        // Local input arrives on the Federation face from a native Provider; the runtime gates every face.
        event.registerBlockEntity(AECapabilities.ME_STORAGE, ENDPOINT_BLOCK_ENTITY.get(),
                (endpoint, side) -> side == null || endpointRuntime(endpoint) == null ? null
                        : endpointRuntime(endpoint).inputStorage(side).orElse(null));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, ENDPOINT_BLOCK_ENTITY.get(),
                (endpoint, side) -> endpointRuntime(endpoint, side) == null ? null
                        : endpointRuntime(endpoint, side).itemReturn(side).orElse(null));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ENDPOINT_BLOCK_ENTITY.get(),
                (endpoint, side) -> endpointRuntime(endpoint, side) == null ? null
                        : endpointRuntime(endpoint, side).fluidReturn(side).orElse(null));
        // FE a Provider sends out of its Federation face goes on to the machines at its Endpoints, whatever sends it.
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, position, state, blockEntity, side) -> ProviderEnergyRelay.facing(level, position, side),
                ENDPOINT.get(), space.controlnet.ae2federation.router.RouterRegistration.ROUTER.get(),
                space.controlnet.ae2federation.router.RouterRegistration.FEDERATION_CABLE.get());
        // As on AE2's own Pattern Provider; Applied Mekanistics and other addons wrap it for their resource types.
        event.registerBlockEntity(AECapabilities.GENERIC_INTERNAL_INV, ENDPOINT_BLOCK_ENTITY.get(),
                (endpoint, side) -> endpointRuntime(endpoint, side) == null ? null
                        : endpointRuntime(endpoint, side).genericReturn(side).orElse(null));
    }

    private static space.controlnet.ae2federation.processing.endpoint.EndpointRuntime endpointRuntime(
            EndpointBlockEntity endpoint) {
        if (!(endpoint.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        var binding = EndpointTargetBinding.findEndpoint(level, endpoint.getBlockPos());
        return binding == null ? null : binding.runtime();
    }

    private static space.controlnet.ae2federation.processing.endpoint.EndpointRuntime endpointRuntime(
            EndpointBlockEntity endpoint, net.minecraft.core.Direction side) {
        if (!(endpoint.getLevel() instanceof ServerLevel level) || side == null
                || side == endpoint.federationFace()) {
            return null;
        }
        var binding = EndpointTargetBinding.findEndpoint(level, endpoint.getBlockPos());
        return binding == null ? null : binding.runtime();
    }
}
