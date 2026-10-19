package space.controlnet.ae2federation.test.ui;

import appeng.core.definitions.AEBlocks;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.GraphView;
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
import space.controlnet.ae2federation.router.RouterRegistration;

/** A domain of one ME network: the topology opens on its card without blowing it up to the canvas's largest zoom. */
@LDLRegisterClient(name = "ui.one-network", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class OneNetworkDomainScenario implements UIScenario {
    /** The largest zoom the opening fit may choose; one card fits the canvas far closer than this. */
    private static final float MAX_OPENING_SCALE = 1f;
    private static final String SWITCH = "oneNetwork.switch";

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("actual-client").guiScale(3).defaultTimeoutMs(15_000).scenarioTimeoutMs(90_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        scenario.timeoutMs(45_000).waitUntil("resources finish loading", context -> context.mc().getOverlay() == null)
                .frames(5)
                .server("place a Switch with one ME Chest and a Federation Cable", OneNetworkDomainScenario::place)
                .teardownServer("remove them", OneNetworkDomainScenario::remove)
                .waitUntilServer("the Switch's domain holds one network", OneNetworkDomainScenario::oneNetwork)
                .server("open the Switch", context -> {
                    if (!FederationDomainPolicyMenu.openRouter(context.player(), context.<BlockPos>get(SWITCH))) {
                        throw new IllegalStateException("the Switch's screen must open");
                    }
                })
                .awaitScreen(ModularUIContainerScreen.class)
                .awaitModularUI()
                .waitUntil("the network's card is drawn", context -> context.all(".graph-node-member").size() == 1)
                .frames(10)
                .check("the opening fit stays at or below the largest opening zoom", context ->
                        context.el("#domain_graph").as(GraphView.class).getScale() <= MAX_OPENING_SCALE + 0.0001f)
                .screenshot("ui-one-network-topology")
                .click("#graph_fit").frames(3)
                .check("Fit stays at or below it too", context ->
                        context.el("#domain_graph").as(GraphView.class).getScale() <= MAX_OPENING_SCALE + 0.0001f)
                .closeScreen();
    }

    private static void place(ServerContext context) {
        context.level().getChunk(0, 0);
        var ground = context.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 6, 8);
        context.player().connection.teleport(6.5, ground, 8.5, 0, 0);
        var at = new BlockPos(8, ground + 3, 8);
        context.put(SWITCH, at);
        context.level().setBlockAndUpdate(at, RouterRegistration.SWITCH.get().defaultBlockState());
        context.level().setBlockAndUpdate(at.west(), AEBlocks.ME_CHEST.block().defaultBlockState());
        context.level().setBlockAndUpdate(at.east(), RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
    }

    private static boolean oneNetwork(ServerContext context) {
        var node = FederationDomainRegistryAccess.nodeId(context.level(), context.<BlockPos>get(SWITCH));
        return FederationDomainRegistryAccess.get(context.level()).snapshot().federationDomains().values().stream()
                .anyMatch(domain -> domain.nodes().contains(node) && domain.memberships().size() == 1);
    }

    private static void remove(ServerContext context) {
        BlockPos at = context.get(SWITCH);
        if (at == null) return;
        for (var position : new BlockPos[] {at.west(), at.east(), at}) {
            context.level().setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        }
    }
}
