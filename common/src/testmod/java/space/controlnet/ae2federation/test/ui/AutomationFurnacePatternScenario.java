package space.controlnet.ae2federation.test.ui;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.ModList;
import space.controlnet.ae2federation.automation.AutomationInterfaceBlockEntity;
import space.controlnet.ae2federation.automation.AutomationInterfaces;
import space.controlnet.ae2federation.automation.AutomationRegistration;
import space.controlnet.ae2federation.automation.ProgramHost;
import space.controlnet.ae2federation.automation.ProgramRun;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.script.Runner;

/**
 * A Pattern Provider facing a furnace, before and after the guide's {@code learn_pattern} writes the charcoal recipe
 * into it: the provider's screen empty, then holding the processing pattern with its tooltip. One ME network that a
 * Switch puts in a domain, with an ME Chest holding blank patterns and logs. The scene is sample content built for this
 * scenario.
 */
@LDLRegisterClient(name = "ui.automation-furnace-pattern", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class AutomationFurnacePatternScenario implements UIScenario {
    private static final int X = -36;
    private static final int Z = 20;
    private static final String GROUND = "furnace.ground";
    /** The first pattern slot's centre in GUI pixels at GUI scale 3 in the default 1280x720 window. */
    private static final float SLOT_X = 141;
    private static final float SLOT_Y = 65;

    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 3);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        scenario
                .teardownServer("remove the furnace scene", AutomationFurnacePatternScenario::remove)
                .server("build the furnace scene", AutomationFurnacePatternScenario::build)
                .awaitClientChunk(new BlockPos(X, 64, Z))
                .waitUntilServer("the provider is active in the interface's domain",
                        AutomationFurnacePatternScenario::ready)
                .server("stock the chest", AutomationFurnacePatternScenario::stock)
                .server("stand by the provider", AutomationFurnacePatternScenario::stand)
                .serverTicks(10)
                .server("open the provider", AutomationFurnacePatternScenario::open)
                .awaitScreen(PatternProviderScreen.class)
                .frames(4)
                .screenshot("ui-automation-furnace-before")
                .closeScreen()
                .server("run learn_pattern for charcoal", AutomationFurnacePatternScenario::learn)
                .waitUntilServer("the provider holds a pattern", context -> !provider(context).getLogic()
                        .getPatternInv().getStackInSlot(0).isEmpty())
                .serverTicks(5)
                .server("open the provider again", AutomationFurnacePatternScenario::open)
                .awaitScreen(PatternProviderScreen.class)
                .frames(4)
                // A vanilla container screen reads the pointer from the mouse handler, which hoverAt does not move.
                .step("put the pointer on the first pattern slot", context -> {
                    var scale = context.mc().getWindow().getGuiScale();
                    net.neoforged.fml.util.ObfuscationReflectionHelper.setPrivateValue(
                            net.minecraft.client.MouseHandler.class, context.mc().mouseHandler, SLOT_X * scale,
                            "xpos");
                    net.neoforged.fml.util.ObfuscationReflectionHelper.setPrivateValue(
                            net.minecraft.client.MouseHandler.class, context.mc().mouseHandler, SLOT_Y * scale,
                            "ypos");
                })
                .frames(4)
                .check("the pointer is on the first pattern slot", context -> context.screenAs(PatternProviderScreen.class)
                        .getSlotUnderMouse() == firstPatternSlot(context))
                .screenshot("ui-automation-furnace-after");
    }

    private static net.minecraft.world.inventory.Slot firstPatternSlot(com.lowdragmc.lowdraglib2.uitest.TestContext
            context) {
        return context.screenAs(PatternProviderScreen.class).getMenu().slots.stream()
                .filter(each -> each.container != context.player().getInventory()).findFirst().orElseThrow();
    }

    private static void open(ServerContext context) {
        appeng.menu.MenuOpener.open(appeng.menu.implementations.PatternProviderMenu.TYPE, context.player(),
                appeng.menu.locator.MenuLocators.forBlockEntity(provider(context)));
    }

    /** The provider sits on the ground; the blocks of the scene are around and above it. */
    private static BlockPos at(ServerContext context) {
        return new BlockPos(X, context.<Integer>get(GROUND), Z);
    }

    private static PatternProviderBlockEntity provider(ServerContext context) {
        return (PatternProviderBlockEntity) context.level().getBlockEntity(at(context));
    }

    private static void build(ServerContext context) {
        var level = context.level();
        level.getChunk(X >> 4, Z >> 4);
        context.put(GROUND, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, X, Z) + 1);
        var at = at(context);
        level.setBlockAndUpdate(at, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        level.setBlockAndUpdate(at.east(), Blocks.FURNACE.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        level.setBlockAndUpdate(at.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(at.south(), AEBlocks.ME_CHEST.block().defaultBlockState());
        if (level.getBlockEntity(at.south()) instanceof MEChestBlockEntity chest) {
            chest.setCell(AEItems.ITEM_CELL_1K.stack());
        }
        level.setBlockAndUpdate(at.above(), RouterRegistration.SWITCH.get().defaultBlockState());
        level.setBlockAndUpdate(at.above(2), RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(at.above(2).west(), AutomationRegistration.INTERFACE.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.WEST));
    }

    private static boolean ready(ServerContext context) {
        return context.level().getBlockEntity(at(context)) instanceof PatternProviderBlockEntity provider
                && provider.getMainNode().isActive() && domain(context) != null;
    }

    private static space.controlnet.ae2federation.domain.FederationDomainId domain(ServerContext context) {
        var level = context.level();
        if (!(level.getBlockEntity(at(context).above(2).west())
                instanceof AutomationInterfaceBlockEntity automationInterface) || automationInterface.id() == null) {
            return null;
        }
        return AutomationInterfaces.get(level).domainOf(level, automationInterface.id()).orElse(null);
    }

    private static void stock(ServerContext context) {
        var chest = (MEChestBlockEntity) context.level().getBlockEntity(at(context).south());
        var storage = chest.getMainNode().getGrid().getStorageService().getInventory();
        storage.insert(AEItemKey.of(AEItems.BLANK_PATTERN), 4, Actionable.MODULATE, IActionSource.empty());
        storage.insert(AEItemKey.of(net.minecraft.world.item.Items.BIRCH_LOG), 32, Actionable.MODULATE,
                IActionSource.empty());
    }

    /** South of the chest, facing north at the provider's south face over the chest. */
    private static void stand(ServerContext context) {
        var player = context.player();
        var at = at(context);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.connection.teleport(at.getX() + 0.5, at.getY() - 0.2, at.getZ() - 1.6, 0, 20);
    }

    private static void learn(ServerContext context) {
        String program;
        try (var in = ModList.get().getModFileById("ae2federation").getFile().findResource("assets", "ae2federation",
                "ae2guide", "assets", "programs", "learn_pattern.ae2f").toUri().toURL().openStream()) {
            program = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        var host = new ProgramHost(context.server(), domain(context), "scenario:automation-furnace-pattern");
        var catalog = host.catalog();
        var run = ProgramRun.start(ProgramRun.prepare(Map.of("learn", program), catalog), catalog, host, "learn",
                "learn_pattern", List.of("minecraft:charcoal"));
        for (var tick = 0; tick < 10; tick++) {
            var status = run.tick(1000);
            if (status == Runner.Status.DONE) {
                return;
            }
            if (status == Runner.Status.FAILED) {
                throw new IllegalStateException("The program failed: " + run.failure());
            }
        }
        throw new IllegalStateException("The program did not finish");
    }

    private static void remove(ServerContext context) {
        if (context.state().get(GROUND) == null) return;
        var level = context.level();
        var at = at(context);
        for (var position : List.of(at.above(2).west(), at.above(2), at.above(), at.south(), at.west(), at.east(),
                at)) {
            level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        }
    }
}
