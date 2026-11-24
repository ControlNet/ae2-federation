package space.controlnet.ae2federation.test.ui;

import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * In-game checks of the 2026-11-07 review fixes, in Chinese: a domain of one network is renamed from its screen, its
 * statistics page lists a 1.25 MiB crafting CPU and labels the chart's time axis; then a domain with 64 Endpoints opens
 * its screen and mapping page, whose lists are cut to what the screen binding can send and say how many are left out.
 * Test-world scenes only.
 */
@LDLRegisterClient(name = "ui.review-fixes", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class ReviewFixesScenario implements UIScenario {
    private static final String SWITCH = "reviewFixes.switch";
    private static final String BLOCKS = "reviewFixes.blocks";
    private static final String BIG_SWITCH = "reviewFixes.bigSwitch";
    private static final int ENDPOINT_COLUMNS = 8;
    private static final int COLUMN_HEIGHT = 4;

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("actual-client").guiScale(3).defaultTimeoutMs(30_000).scenarioTimeoutMs(240_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        scenario.step("select Simplified Chinese", context -> {
            if (context.firstAttempt()) {
                context.mc().getLanguageManager().setSelected("zh_cn");
                context.mc().options.languageCode = "zh_cn";
                context.put("languageReload", context.mc().reloadResourcePacks());
            }
            java.util.concurrent.CompletableFuture<Void> reload = context.get("languageReload");
            if (!reload.isDone()) context.repeat("Chinese resource reload");
            else reload.join();
        }).timeoutMs(60_000).waitUntil("Chinese resources finish loading", context -> context.mc().getOverlay() == null)
                .frames(5)
                .step("hide the tutorial toast", context -> {
                    context.mc().getToasts().clear();
                    context.mc().getTutorial().stop();
                })
                .server("place a Switch, one ME network with a 1.25 MiB crafting CPU, and 64 Endpoints on a second Switch",
                        ReviewFixesScenario::place)
                .teardownServer("remove them", ReviewFixesScenario::remove)
                .waitUntilServer("the small domain holds one network", ReviewFixesScenario::oneNetwork)
                .server("open the Switch", context -> {
                    if (!FederationDomainPolicyMenu.openRouter(context.player(), context.<BlockPos>get(SWITCH))) {
                        throw new IllegalStateException("the Switch's screen must open");
                    }
                })
                .awaitScreen(ModularUIContainerScreen.class)
                .awaitModularUI()
                .waitUntil("the network's card is drawn", context -> context.all(".graph-node-member").size() == 1)
                .frames(10)
                .click(".graph-node-member").frames(5)
                .click("#network_rename").frames(3)
                // A notified setText submits the name as Enter would.
                .step("type a name and submit it", context -> context.el("#network_rename_field").as(TextField.class)
                        .setText("主基地", true))
                .frames(40)
                .screenshot("review-rename-one-network")
                .click("#tab_statistics").frames(5)
                .click("#stats_range_SECONDS")
                .click("#stats_card_cpus").frames(200)
                .screenshot("review-statistics-cpus")
                .closeScreen()
                .server("open the Switch with 64 Endpoints", context -> {
                    BlockPos big = context.get(BIG_SWITCH);
                    context.player().connection.teleport(big.getX() - 1.5, big.getY() - 3, big.getZ() - 2.5, 0, 0);
                    if (!FederationDomainPolicyMenu.openRouter(context.player(), context.<BlockPos>get(BIG_SWITCH))) {
                        throw new IllegalStateException("the second Switch's screen must open");
                    }
                })
                .awaitScreen(ModularUIContainerScreen.class)
                .awaitModularUI()
                .frames(40)
                .screenshot("review-many-endpoints")
                .click("#tab_mapping").frames(5)
                .waitUntil("the Endpoint column is drawn", context -> !context.all(".processing-note").isEmpty())
                .frames(20)
                .step("scroll the wires to the end of the Endpoint column", context -> context.el("#processing_scroll")
                        .as(ScrollerView.class).verticalScroller.setValue(1f))
                .frames(10)
                .screenshot("review-mapping-many-endpoints")
                .closeScreen();
    }

    private static void place(ServerContext context) {
        context.level().getChunk(0, 0);
        var ground = context.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 6, 8);
        context.player().connection.teleport(6.5, ground, 8.5, 0, 0);
        var placed = new ArrayList<BlockPos>();
        var at = new BlockPos(8, ground + 3, 8);
        context.put(SWITCH, at);
        set(context, placed, at, RouterRegistration.SWITCH.get().defaultBlockState());
        var chest = at.west();
        set(context, placed, chest, AEBlocks.ME_CHEST.block().defaultBlockState());
        ((MEChestBlockEntity) context.level().getBlockEntity(chest)).setCell(AEItems.ITEM_CELL_1K.stack());
        set(context, placed, chest.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        // Five 256k crafting storages in a row: one CPU of 1310720 bytes, which the old format showed as "1.3kk".
        for (int i = 1; i <= 5; i++) {
            set(context, placed, chest.north(i), AEBlocks.CRAFTING_STORAGE_256K.block().defaultBlockState());
        }
        set(context, placed, at.east(), RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());

        // A second domain: one network and 64 Endpoints on cable columns, enough to pass the 32767-character string.
        var big = new BlockPos(8, ground + 3, 20);
        context.put(BIG_SWITCH, big);
        set(context, placed, big, RouterRegistration.SWITCH.get().defaultBlockState());
        var bigChest = big.west();
        set(context, placed, bigChest, AEBlocks.ME_CHEST.block().defaultBlockState());
        ((MEChestBlockEntity) context.level().getBlockEntity(bigChest)).setCell(AEItems.ITEM_CELL_1K.stack());
        set(context, placed, bigChest.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        // A Provider on the ME Chest, its Federation face east onto a cable on the Switch.
        var provider = bigChest.above();
        set(context, placed, provider, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
        set(context, placed, big.above(), RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
        for (int column = 0; column < ENDPOINT_COLUMNS; column++) {
            var base = big.east(1 + column);
            set(context, placed, base, RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
            for (int height = 1; height <= COLUMN_HEIGHT; height++) {
                var cable = base.above(height);
                set(context, placed, cable, RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
                set(context, placed, cable.north(), ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.SOUTH));
                set(context, placed, cable.south(), ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                        .setValue(BlockStateProperties.FACING, Direction.NORTH));
            }
        }
        context.put(BLOCKS, placed);
    }

    private static void set(ServerContext context, List<BlockPos> placed, BlockPos position,
            net.minecraft.world.level.block.state.BlockState state) {
        context.level().setBlockAndUpdate(position, state);
        placed.add(position);
    }

    private static boolean oneNetwork(ServerContext context) {
        var node = FederationDomainRegistryAccess.nodeId(context.level(), context.<BlockPos>get(SWITCH));
        return FederationDomainRegistryAccess.get(context.level()).snapshot().federationDomains().values().stream()
                .anyMatch(domain -> domain.nodes().contains(node) && domain.memberships().size() == 1);
    }

    private static void remove(ServerContext context) {
        List<BlockPos> placed = context.get(BLOCKS);
        if (placed == null) return;
        for (int i = placed.size() - 1; i >= 0; i--) {
            context.level().setBlockAndUpdate(placed.get(i), Blocks.AIR.defaultBlockState());
        }
    }
}
