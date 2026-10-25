package space.controlnet.ae2federation.automation.neoeco;

import cn.dancingsnow.neoecoae.blocks.entity.NEBlockEntity;
import cn.dancingsnow.neoecoae.blocks.entity.computation.ECOComputationSystemBlockEntity;
import cn.dancingsnow.neoecoprototype.api.SimplifyTier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import space.controlnet.ae2federation.FederationCreativeTab;

/**
 * ECO - N1: the Federation computation system's controller on Neo ECO Prototype's L1 computation structure, C1's,
 * with the same communication interfaces as N4 to N9 (user, 2026-10-23). Its block entity is N4's with Prototype's L1
 * tier, so Neo ECO resolves C1's build plan for it. This class refers to Neo ECO Prototype, so only
 * {@link NeoEcoSupport} may reach it, and only once Neo ECO Prototype is loaded.
 */
public final class PrototypeComputing {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("ae2federation");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("ae2federation");
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, "ae2federation");
    public static final DeferredBlock<EcoComputationSystemBlock> CONTROLLER = BLOCKS.registerBlock(
            "eco_computation_system_l1", EcoComputationSystemBlock::new,
            BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK).noOcclusion());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<EcoComputationSystemBlockEntity>>
            CONTROLLER_TYPE;

    static {
        ITEMS.registerSimpleBlockItem(CONTROLLER, new Item.Properties());
        CONTROLLER_TYPE = BLOCK_ENTITY_TYPES.register("eco_computation_system_l1", () -> {
            var holder = new BlockEntityType<?>[1];
            @SuppressWarnings("unchecked")
            var type = (BlockEntityType<EcoComputationSystemBlockEntity>) BlockEntityType.Builder.of(
                    (position, state) -> new EcoComputationSystemBlockEntity(holder[0], position, state,
                            SimplifyTier.L1),
                    CONTROLLER.get()).build(null);
            holder[0] = type;
            // Typed on Neo ECO's controller block entity, as N4's registration does.
            @SuppressWarnings("unchecked")
            var entityClass = (Class<ECOComputationSystemBlockEntity>) (Class<?>) EcoComputationSystemBlockEntity.class;
            @SuppressWarnings("unchecked")
            var entityType = (BlockEntityType<ECOComputationSystemBlockEntity>) (BlockEntityType<?>) type;
            CONTROLLER.get().setBlockEntity(entityClass, entityType, null, (level, position, state, entity) ->
                    EcoComputationSystemBlockEntity.serverTick(level, position, state,
                            (EcoComputationSystemBlockEntity) entity));
            NEBlockEntity.registerCalculatorFactory(type, PrototypeComputationClusterCalculator::new);
            return type;
        });
    }

    private PrototypeComputing() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        BLOCK_ENTITY_TYPES.register(modBus);
        modBus.addListener(PrototypeComputing::fillCreativeTab);
    }

    /** N1 before N4, after Neo ECO's controllers are in the tab. */
    private static void fillCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == FederationCreativeTab.MAIN.getKey()) {
            event.insertBefore(new ItemStack(NeoEcoComputing.CONTROLLERS.values().iterator().next()),
                    new ItemStack(CONTROLLER), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }
}
