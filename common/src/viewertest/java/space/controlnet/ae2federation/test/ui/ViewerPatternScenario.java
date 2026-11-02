package space.controlnet.ae2federation.test.ui;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import com.lowdragmc.lowdraglib2.uitest.TestContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import space.controlnet.ae2federation.automation.ViewerScans;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.script.Runner;

/**
 * A Pattern Provider facing another mod's machine, before and after the guide's {@code learn_pattern} writes a recipe
 * for an item into it from the recipes the client's JEI scan sent and the world saved. The provider's screen empty,
 * then holding the pattern with its tooltip. One ME network that a Switch puts in a domain, with an ME Chest holding
 * blank patterns. The scene is sample content built for these scenarios; they run only on the viewer client
 * ({@code viewerUiTestClient}).
 */
abstract class ViewerPatternScenario implements UIScenario {
    private static final String GROUND = "viewer.ground";
    /** The first pattern slot's centre in GUI pixels at GUI scale 3 in the default 1280x720 window. */
    private static final float SLOT_X = 141;
    private static final float SLOT_Y = 65;

    private final int x;
    private final int z;
    private final ResourceLocation machine;
    private final String item;
    private final String name;

    /** The scene at {@code x}, {@code z}; {@code name} names the screenshots and the program's host. */
    ViewerPatternScenario(int x, int z, ResourceLocation machine, String item, String name) {
        this.x = x;
        this.z = z;
        this.machine = machine;
        this.item = item;
        this.name = name;
    }

    /** Opens the machine's face toward the provider to items, where a newly placed machine keeps it closed. */
    void openMachine(ServerContext context, BlockPos at, Direction face) {
    }

    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 3);
        // A pack with Create sends its recipes in about twenty seconds under xvfb.
        options.scenarioTimeoutMs(360_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        scenario
                .teardownServer("remove the scene", this::remove)
                .timeoutMs(240_000)
                .waitUntilServer("JEI's recipes reached the server", context ->
                        ViewerScans.progress(context.server()).done())
                .server("the scan kept only real recipes that make something else", ViewerPatternScenario::onlyReal)
                .server("build the scene", this::build)
                .awaitClientChunk(new BlockPos(x, 64, z))
                .waitUntilServer("the provider is active in the interface's domain", this::ready)
                .serverTicks(2)
                .server("open the machine's side toward the provider", context ->
                        openMachine(context, at(context).east(), Direction.WEST))
                .waitUntilServer("the machine takes items from the provider's side", this::machineTakesItems)
                .server("stock the chest", this::stock)
                .server("stand by the provider", this::stand)
                .serverTicks(10)
                .server("open the provider", this::open)
                .awaitScreen(PatternProviderScreen.class)
                .frames(4)
                .screenshot("ui-" + name + "-before")
                .closeScreen()
                .server("run learn_pattern for " + item, this::learn)
                .waitUntilServer("the provider holds a pattern", context -> !provider(context).getLogic()
                        .getPatternInv().getStackInSlot(0).isEmpty())
                .serverTicks(5)
                .server("open the provider again", this::open)
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
                .check("the pointer is on the first pattern slot", context -> context.screenAs(
                        PatternProviderScreen.class).getSlotUnderMouse() == firstPatternSlot(context))
                .screenshot("ui-" + name + "-after");
    }

    /**
     * JEI's own display categories (anvil, brewing, fuel, information pages) are not recipes; each saved recipe makes
     * something other than what it takes; a saved ID is one the server has (client-built recipes carry made-up IDs).
     */
    private static void onlyReal(ServerContext context) {
        var recipes = ViewerRecipes.get(context.server()).recipes();
        var display = java.util.Set.of("minecraft:anvil", "minecraft:brewing", "minecraft:fuel", "jei:information");
        var named = 0;
        for (var recipe : recipes) {
            if (display.contains(recipe.category())) {
                throw new IllegalStateException("A display object was saved: " + recipe);
            }
            var taken = recipe.inputs().stream().map(slot -> slot.stacks().get(0)).collect(
                    java.util.stream.Collectors.groupingBy(stack -> stack, java.util.stream.Collectors.counting()));
            var made = java.util.stream.Stream.concat(recipe.outputs().stream(), recipe.byproducts().stream()).collect(
                    java.util.stream.Collectors.groupingBy(stack -> stack, java.util.stream.Collectors.counting()));
            if (taken.equals(made)) {
                throw new IllegalStateException("A recipe making what it takes was saved: " + recipe);
            }
            if (recipe.id().isPresent()) {
                named++;
                if (context.server().getRecipeManager().byKey(ResourceLocation.parse(recipe.id().get())).isEmpty()) {
                    throw new IllegalStateException("A recipe kept an ID the server does not have: " + recipe);
                }
            }
        }
        if (named == 0) {
            throw new IllegalStateException("No saved recipe has the server's ID");
        }
    }

    private static net.minecraft.world.inventory.Slot firstPatternSlot(TestContext context) {
        return context.screenAs(PatternProviderScreen.class).getMenu().slots.stream()
                .filter(each -> each.container != context.player().getInventory()).findFirst().orElseThrow();
    }

    private void open(ServerContext context) {
        appeng.menu.MenuOpener.open(appeng.menu.implementations.PatternProviderMenu.TYPE, context.player(),
                appeng.menu.locator.MenuLocators.forBlockEntity(provider(context)));
    }

    /** The provider sits on the ground; the blocks of the scene are around and above it. */
    private BlockPos at(ServerContext context) {
        return new BlockPos(x, context.<Integer>get(GROUND), z);
    }

    private PatternProviderBlockEntity provider(ServerContext context) {
        return (PatternProviderBlockEntity) context.level().getBlockEntity(at(context));
    }

    private void build(ServerContext context) {
        var level = context.level();
        level.getChunk(x >> 4, z >> 4);
        context.put(GROUND, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) + 1);
        var at = at(context);
        level.setBlockAndUpdate(at, AEBlocks.PATTERN_PROVIDER.block().defaultBlockState());
        level.setBlockAndUpdate(at.east(), BuiltInRegistries.BLOCK.getOptional(machine).orElseThrow()
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

    /** AE2 counts a machine on a provider's face only where it takes items or fluids there. */
    private boolean machineTakesItems(ServerContext context) {
        var items = context.level().getCapability(Capabilities.ItemHandler.BLOCK, at(context).east(), Direction.WEST);
        return items != null && items.getSlots() > 0;
    }

    private boolean ready(ServerContext context) {
        return context.level().getBlockEntity(at(context)) instanceof PatternProviderBlockEntity provider
                && provider.getMainNode().isActive() && domain(context) != null;
    }

    private space.controlnet.ae2federation.domain.FederationDomainId domain(ServerContext context) {
        var level = context.level();
        if (!(level.getBlockEntity(at(context).above(2).west())
                instanceof AutomationInterfaceBlockEntity automationInterface) || automationInterface.id() == null) {
            return null;
        }
        return AutomationInterfaces.get(level).domainOf(level, automationInterface.id()).orElse(null);
    }

    private void stock(ServerContext context) {
        var chest = (MEChestBlockEntity) context.level().getBlockEntity(at(context).south());
        var storage = chest.getMainNode().getGrid().getStorageService().getInventory();
        storage.insert(AEItemKey.of(AEItems.BLANK_PATTERN), 4, Actionable.MODULATE, IActionSource.empty());
    }

    /** South of the chest, facing north at the provider's south face over the chest. */
    private void stand(ServerContext context) {
        var player = context.player();
        var at = at(context);
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.connection.teleport(at.getX() + 0.5, at.getY() - 0.2, at.getZ() - 1.6, 0, 20);
    }

    private void learn(ServerContext context) {
        String program;
        try (var in = ModList.get().getModFileById("ae2federation").getFile().findResource("assets", "ae2federation",
                "ae2guide", "assets", "programs", "learn_pattern.ae2f").toUri().toURL().openStream()) {
            program = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
        var host = new ProgramHost(context.server(), domain(context), "scenario:" + name);
        var catalog = host.catalog();
        var run = ProgramRun.start(ProgramRun.prepare(Map.of("learn", program), catalog), catalog, host, "learn",
                "learn_pattern", List.of(item));
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

    private void remove(ServerContext context) {
        if (context.state().get(GROUND) == null) return;
        var level = context.level();
        var at = at(context);
        for (var position : List.of(at.above(2).west(), at.above(2), at.above(), at.south(), at.west(), at.east(),
                at)) {
            level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        }
    }
}
