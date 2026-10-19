package space.controlnet.ae2federation.automation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import space.controlnet.ae2federation.domain.port.FederationPortCapability;

public final class AutomationRegistration {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("ae2federation");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("ae2federation");
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, "ae2federation");
    public static final DeferredBlock<AutomationInterfaceBlock> INTERFACE = BLOCKS.registerBlock(
            "automation_interface", AutomationInterfaceBlock::new,
            BlockBehaviour.Properties.of().strength(2.2F, 11.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> INTERFACE_ITEM = ITEMS.registerSimpleBlockItem(INTERFACE,
            new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutomationInterfaceBlockEntity>>
            INTERFACE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("automation_interface", () -> {
                var type = BlockEntityType.Builder.of(AutomationInterfaceBlockEntity::new, INTERFACE.get()).build(null);
                INTERFACE.get().setBlockEntity(AutomationInterfaceBlockEntity.class, type, null,
                        AutomationInterfaceBlockEntity::serverTick);
                return type;
            });

    /** The Creative Computing Device, a placeholder for testing automation (user, 2026-10-18). */
    public static final DeferredBlock<ComputingDeviceBlock> DEVICE = BLOCKS.registerBlock(
            "creative_computing_device", ComputingDeviceBlock::new,
            BlockBehaviour.Properties.of().strength(2.2F, 11.0F).sound(SoundType.METAL).requiresCorrectToolForDrops());
    public static final DeferredItem<BlockItem> DEVICE_ITEM = ITEMS.registerSimpleBlockItem(DEVICE,
            new Item.Properties().rarity(Rarity.EPIC));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ComputingDeviceBlockEntity>>
            DEVICE_BLOCK_ENTITY = BLOCK_ENTITY_TYPES.register("creative_computing_device", () -> {
                var type = BlockEntityType.Builder.of(ComputingDeviceBlockEntity::new, DEVICE.get()).build(null);
                DEVICE.get().setBlockEntity(ComputingDeviceBlockEntity.class, type, null,
                        ComputingDeviceBlockEntity::serverTick);
                return type;
            });

    private AutomationRegistration() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        modBus.addListener(AutomationRegistration::registerCapabilities);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(FederationPortCapability.BLOCK, INTERFACE_BLOCK_ENTITY.get(),
                AutomationInterfaceBlockEntity::federationPort);
        event.registerBlockEntity(FederationPortCapability.BLOCK, DEVICE_BLOCK_ENTITY.get(),
                ComputingDeviceBlockEntity::federationPort);
    }
}
