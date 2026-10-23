package space.controlnet.ae2federation.test.ui;

import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.IdentityStatus;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * Two ME networks built apart, then cabled into one beside a Switch. Neither has a name or rule, so the merge settles
 * on one id and the joined network appears in the Switch's topology.
 */
@LDLRegisterClient(name = "ui.merged-network", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class MergedNetworkDomainScenario implements UIScenario {
    static final String SWITCH = "mergedNetwork.switch";

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("actual-client").guiScale(3).defaultTimeoutMs(15_000).scenarioTimeoutMs(90_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        scenario.timeoutMs(45_000).waitUntil("resources finish loading", context -> context.mc().getOverlay() == null)
                .frames(5)
                .server("place a Switch beside one ME Chest and another ME Chest apart", MergedNetworkDomainScenario::place)
                .teardownServer("remove them", MergedNetworkDomainScenario::remove)
                .waitUntilServer("both pieces settle on their own ids", MergedNetworkDomainScenario::piecesSettled)
                .server("join the pieces with a cable", context -> PartHelper.setPart(context.level(),
                        context.<BlockPos>get(SWITCH).west(2), null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)))
                .waitUntilServer("the cable joins both pieces", MergedNetworkDomainScenario::joined)
                .frames(40)
                .server("open the Switch", context -> {
                    if (!FederationDomainPolicyMenu.openRouter(context.player(), context.<BlockPos>get(SWITCH))) {
                        throw new IllegalStateException("the Switch's screen must open");
                    }
                })
                .awaitScreen(ModularUIContainerScreen.class)
                .awaitModularUI()
                .frames(20)
                .screenshot("ui-merged-network-topology")
                .checkServer("the joined network settles", MergedNetworkDomainScenario::settled)
                .waitUntil("the joined network's card is drawn", context -> context.all(".graph-node-member").size() == 1)
                .closeScreen();
    }

    static void place(ServerContext context) {
        context.level().getChunk(0, 0);
        var ground = context.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 6, 8);
        context.player().connection.teleport(6.5, ground, 10.5, 0, 0);
        var at = new BlockPos(8, ground + 3, 8);
        context.put(SWITCH, at);
        context.level().setBlockAndUpdate(at, RouterRegistration.SWITCH.get().defaultBlockState());
        context.level().setBlockAndUpdate(at.east(), RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
        context.level().setBlockAndUpdate(at.west(), AEBlocks.ME_CHEST.block().defaultBlockState());
        context.level().setBlockAndUpdate(at.west(3), AEBlocks.ME_CHEST.block().defaultBlockState());
        context.level().setBlockAndUpdate(at.west(3).above(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
    }

    static boolean piecesSettled(ServerContext context) {
        BlockPos at = context.get(SWITCH);
        var near = status(context, at.west());
        var far = status(context, at.west(3));
        return near == IdentityStatus.SETTLED && far == IdentityStatus.SETTLED;
    }

    static boolean joined(ServerContext context) {
        BlockPos at = context.get(SWITCH);
        var near = grid(context, at.west());
        return near != null && near == grid(context, at.west(3));
    }

    private static boolean settled(ServerContext context) {
        return FederationDomainRegistryAccess.confirmedNetworkId(grid(context, context.<BlockPos>get(SWITCH).west()))
                .isPresent();
    }

    private static IdentityStatus status(ServerContext context, BlockPos chest) {
        var grid = grid(context, chest);
        return grid == null ? null : grid.getService(NetworkIdentityService.class).settlement().status();
    }

    static appeng.api.networking.IGrid grid(ServerContext context, BlockPos chest) {
        if (!(context.level().getBlockEntity(chest) instanceof appeng.blockentity.storage.MEChestBlockEntity host)) {
            return null;
        }
        var node = host.getMainNode().getNode();
        return node == null ? null : node.getGrid();
    }

    static void remove(ServerContext context) {
        BlockPos at = context.get(SWITCH);
        if (at == null) return;
        for (var position : new BlockPos[] {at.west(3).above(), at.west(3), at.west(2), at.west(), at.east(), at}) {
            context.level().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        }
    }
}
