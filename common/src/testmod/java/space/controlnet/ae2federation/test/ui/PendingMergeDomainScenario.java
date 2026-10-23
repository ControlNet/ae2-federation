package space.controlnet.ae2federation.test.ui;

import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEParts;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import java.util.List;
import net.minecraft.core.BlockPos;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu;
import space.controlnet.ae2federation.identity.IdentityStatus;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.persistence.NetworkNames;

/**
 * The pieces of {@link MergedNetworkDomainScenario}, each named first, so the merge stays pending. The joined network is
 * no member of the Switch's domain, yet its topology shows both ids as cards that say the merge is pending. Keep on
 * one card, once confirmed, settles the merge on it and deletes the other's name; the open screen follows to the
 * rebuilt domain.
 */
@LDLRegisterClient(name = "ui.pending-merge", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class PendingMergeDomainScenario implements UIScenario {
    private static final String IDS = "pendingMerge.ids";

    @Override
    public void configure(ScenarioOptions options) {
        options.tags("actual-client").guiScale(3).defaultTimeoutMs(15_000).scenarioTimeoutMs(90_000);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        scenario.timeoutMs(45_000).waitUntil("resources finish loading", context -> context.mc().getOverlay() == null)
                .frames(5)
                .server("place a Switch beside one ME Chest and another ME Chest apart", MergedNetworkDomainScenario::place)
                .teardownServer("remove them and their names", PendingMergeDomainScenario::remove)
                .waitUntilServer("both pieces settle on their own ids", MergedNetworkDomainScenario::piecesSettled)
                .server("name both pieces", PendingMergeDomainScenario::name)
                .server("join the pieces with a cable", context -> PartHelper.setPart(context.level(),
                        context.<BlockPos>get(MergedNetworkDomainScenario.SWITCH).west(2), null, null,
                        AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT)))
                .waitUntilServer("the joined Grid reports a merge", PendingMergeDomainScenario::pending)
                .frames(40)
                .checkServer("the merge is still pending", PendingMergeDomainScenario::pending)
                .server("open the Switch", context -> {
                    if (!FederationDomainPolicyMenu.openRouter(context.player(),
                            context.<BlockPos>get(MergedNetworkDomainScenario.SWITCH))) {
                        throw new IllegalStateException("the Switch's screen must open");
                    }
                })
                .awaitScreen(ModularUIContainerScreen.class)
                .awaitModularUI()
                .waitUntil("both ids of the merge are drawn", context -> context.all(".graph-node-member").size() == 2)
                .step("select the first id", context -> TaskThirtyThreeScenarioSupport.selectNetworkCard(context,
                        context.<List<String>>get(IDS).getFirst()))
                .waitForTextContains("#graph_selection", "Merge pending")
                .waitUntil("Keep is offered instead of Devices", context -> context.el("#network_keep").isVisible()
                        && !context.el("#graph_open").isVisible())
                .frames(5)
                .screenshot("ui-pending-merge-topology")
                .step("press Keep", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#network_keep"))
                .waitUntil("the confirmation names what is deleted", context -> !context.all("#keep_dialog").isEmpty()
                        && context.el("#keep_consequence").text().contains("Annex"))
                .frames(5)
                .screenshot("ui-pending-merge-keep-confirm")
                .step("confirm", context -> TaskThirtyThreeScenarioSupport.activateNavigation(context, "#keep_confirm"))
                .waitUntilServer("the merge settles on the kept network", PendingMergeDomainScenario::kept)
                .awaitScreen(ModularUIContainerScreen.class)
                .awaitModularUI()
                .waitUntil("the open screen follows the kept network", context -> context.all(".graph-node-member").size() == 1)
                .frames(10)
                .screenshot("ui-pending-merge-kept")
                .closeScreen();
    }

    private static void name(ServerContext context) {
        BlockPos at = context.get(MergedNetworkDomainScenario.SWITCH);
        var names = NetworkNames.get(context.level());
        var ids = new java.util.ArrayList<String>();
        for (var chest : new BlockPos[] {at.west(), at.west(3)}) {
            var grid = MergedNetworkDomainScenario.grid(context, chest);
            var network = grid.getService(NetworkIdentityService.class).settlement().networkId().orElseThrow();
            names.rename(network, chest.equals(at.west()) ? "Base" : "Annex");
            ids.add(network.value().toString());
        }
        context.put(IDS, ids);
    }

    /** Base, the selected card, survives with its name; Annex's name is gone. */
    private static boolean kept(ServerContext context) {
        BlockPos at = context.get(MergedNetworkDomainScenario.SWITCH);
        List<String> ids = context.get(IDS);
        var base = new NetworkId(java.util.UUID.fromString(ids.get(0)));
        var annex = new NetworkId(java.util.UUID.fromString(ids.get(1)));
        var names = NetworkNames.get(context.level());
        return MergedNetworkDomainScenario.grid(context, at.west()).getService(NetworkIdentityService.class).settlement()
                .networkId().filter(base::equals).isPresent()
                && names.name(base).orElse("").equals("Base") && names.name(annex).isEmpty();
    }

    private static boolean pending(ServerContext context) {
        BlockPos at = context.get(MergedNetworkDomainScenario.SWITCH);
        if (!MergedNetworkDomainScenario.joined(context)) return false;
        return MergedNetworkDomainScenario.grid(context, at.west()).getService(NetworkIdentityService.class).settlement()
                .status() == IdentityStatus.AMBIGUOUS_MERGE;
    }

    private static void remove(ServerContext context) {
        List<String> ids = context.get(IDS);
        if (ids != null) {
            var names = NetworkNames.get(context.level());
            ids.forEach(id -> names.rename(new NetworkId(java.util.UUID.fromString(id)), ""));
        }
        MergedNetworkDomainScenario.remove(context);
    }
}
