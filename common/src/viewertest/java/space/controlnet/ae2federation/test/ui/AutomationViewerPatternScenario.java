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
import mekanism.api.RelativeSide;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.component.config.DataType;
import mekanism.common.tile.interfaces.ISideConfiguration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import space.controlnet.ae2federation.automation.AutomationInterfaceBlockEntity;
import space.controlnet.ae2federation.automation.AutomationInterfaces;
import space.controlnet.ae2federation.automation.AutomationRegistration;
import space.controlnet.ae2federation.automation.ProgramHost;
import space.controlnet.ae2federation.automation.ProgramRun;
import space.controlnet.ae2federation.automation.ViewerRecipes;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.script.Runner;

/**
 * A Pattern Provider facing Mekanism's Enrichment Chamber, before and after the guide's {@code learn_pattern} writes
 * an iron dust recipe into it: a recipe the server cannot read, which the client's JEI scan sent and the world saved.
 * The provider's screen empty, then holding the processing pattern with its tooltip. One ME network that a Switch puts
 * in a domain, with an ME Chest holding blank patterns. The scene is sample content built for this scenario; it runs
 * only on the viewer client ({@code viewerUiTestClient}, with JEI, Mekanism, Applied Mekanistics and AE2 JEI
 * Integration).
 */
@LDLRegisterClient(name = "ui.automation-viewer-pattern", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class AutomationViewerPatternScenario implements UIScenario {
    private static final int X = -36;
    private static final int Z = 20;
    private static final String GROUND = "viewer.ground";
    private static final ResourceLocation CHAMBER = ResourceLocation.fromNamespaceAndPath("mekanism",
            "enrichment_chamber");
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
                .teardownServer("remove the chamber scene", AutomationViewerPatternScenario::remove)
                .waitUntilServer("JEI's recipes reached the server", context ->
                        ViewerRecipes.get(context.server()).size() > 0)
                .server("build the chamber scene", AutomationViewerPatternScenario::build)
                .awaitClientChunk(new BlockPos(X, 64, Z))
                .waitUntilServer("the provider is active in the interface's domain",
                        AutomationViewerPatternScenario::ready)
                .serverTicks(2)
                .server("open the chamber's side toward the provider", AutomationViewerPatternScenario::configureChamber)
                .waitUntilServer("the chamber takes items from the provider's side",
                        AutomationViewerPatternScenario::chamberTakesItems)
                .server("stock the chest", AutomationViewerPatternScenario::stock)
                .server("stand by the provider", AutomationViewerPatternScenario::stand)
                .serverTicks(10)
                .server("open the provider", AutomationViewerPatternScenario::open)
                .awaitScreen(PatternProviderScreen.class)
                .frames(4)
                .screenshot("ui-automation-viewer-before")
                .closeScreen()
                .server("run learn_pattern for iron dust", AutomationViewerPatternScenario::learn)
                .waitUntilServer("the provider holds a pattern", context -> !provider(context).getLogic()
                        .getPatternInv().getStackInSlot(0).isEmpty())
                .serverTicks(5)
                .server("open the provider again", AutomationViewerPatternScenario::open)
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
                .screenshot("ui-automation-viewer-after");
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
        level.setBlockAndUpdate(at.east(), BuiltInRegistries.BLOCK.getOptional(CHAMBER).orElseThrow()
                .defaultBlockState());
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

    /**
     * Opens the chamber's face toward the provider to items, as a player does with Mekanism's Configurator: a newly
     * placed Mekanism machine has every side disabled, and AE2 counts a machine on a provider's face only where it takes
     * items or fluids there.
     */
    private static void configureChamber(ServerContext context) {
        var chamber = (ISideConfiguration) context.level().getBlockEntity(at(context).east());
        var side = RelativeSide.fromDirections(chamber.getDirection(), Direction.WEST);
        if (!chamber.getConfig().getConfig(TransmissionType.ITEM).setDataType(DataType.INPUT, side)) {
            throw new IllegalStateException("The chamber refused items on its side toward the provider");
        }
        chamber.getConfig().sideChanged(TransmissionType.ITEM, side);
    }

    private static boolean chamberTakesItems(ServerContext context) {
        var items = context.level().getCapability(Capabilities.ItemHandler.BLOCK, at(context).east(), Direction.WEST);
        return items != null && items.getSlots() > 0;
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
        var host = new ProgramHost(context.server(), domain(context), "scenario:automation-viewer-pattern");
        var catalog = host.catalog();
        var run = ProgramRun.start(ProgramRun.prepare(Map.of("learn", program), catalog), catalog, host, "learn",
                "learn_pattern", List.of("mekanism:dust_iron"));
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
