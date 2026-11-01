package space.controlnet.ae2federation.test.ui;

import appeng.api.parts.IPartHost;
import appeng.api.parts.PartHelper;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEParts;
import appeng.parts.p2p.P2PTunnelPart;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.ScenarioBuilder;
import com.lowdragmc.lowdraglib2.uitest.ScenarioOptions;
import com.lowdragmc.lowdraglib2.uitest.ServerContext;
import com.lowdragmc.lowdraglib2.uitest.UIScenario;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import space.controlnet.ae2federation.automation.AutomationInterfaceBlockEntity;
import space.controlnet.ae2federation.automation.AutomationInterfaces;
import space.controlnet.ae2federation.automation.AutomationRegistration;
import space.controlnet.ae2federation.automation.ProgramHost;
import space.controlnet.ae2federation.automation.ProgramRun;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.script.Runner;

/**
 * P2P tunnels in the world before and after a program links them: three tunnels on one ME network that a Switch puts
 * in a domain, two item tunnels and an energy tunnel, their fronts facing the camera. The program links the first to
 * the other two, so all three show the input's frequency colours and the energy tunnel becomes an item tunnel. The
 * scene is sample content built for this scenario.
 */
@LDLRegisterClient(name = "ui.automation-tunnels", group = "ae2federation", registry = UIScenario.REGISTRY,
        environment = RegistrationEnvironment.DEV_ONLY)
public final class AutomationTunnelsScenario implements UIScenario {
    private static final int X = -30;
    private static final int Z = 20;
    private static final String GROUND = "tunnels.ground";
    private static final String PROGRAM = """
            def run() -> int:
                tunnels = ae2.tunnels()
                ae2.link_tunnel(tunnels[0], tunnels[1])
                ae2.link_tunnel(tunnels[0], tunnels[2])
                return len(tunnels)
            """;

    @Override
    public void configure(ScenarioOptions options) {
        TaskThirtyThreeScenarioSupport.configure(options, 3);
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        scenario
                .teardownServer("remove the tunnel scene", AutomationTunnelsScenario::remove)
                .teardown("show the HUD again", context -> context.mc().options.hideGui = false)
                .server("build the tunnel scene", AutomationTunnelsScenario::build)
                .awaitClientChunk(new BlockPos(X, 64, Z))
                .waitUntilServer("the tunnels are active in the interface's domain", AutomationTunnelsScenario::ready)
                .step("hide the HUD", context -> context.mc().options.hideGui = true)
                .server("look at the tunnels", AutomationTunnelsScenario::look)
                .serverTicks(10)
                .frames(4).screenshot("ui-automation-tunnels-before")
                .server("run the program that links the tunnels", AutomationTunnelsScenario::link)
                .waitUntilServer("the tunnels share one frequency", context -> {
                    var tunnels = tunnels(context);
                    return tunnels.size() == 3 && tunnels.get(0).getFrequency() != 0
                            && tunnels.stream().allMatch(tunnel -> tunnel.getFrequency() == tunnels.get(0).getFrequency());
                })
                .serverTicks(10)
                .frames(4).screenshot("ui-automation-tunnels-after");
    }

    private static BlockPos bus(ServerContext context, int index) {
        return new BlockPos(X, context.<Integer>get(GROUND) + 1, Z + index);
    }

    private static void build(ServerContext context) {
        var level = context.level();
        level.getChunk(X >> 4, Z >> 4);
        context.put(GROUND, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, X, Z));
        var first = bus(context, 0);
        level.setBlockAndUpdate(first.west(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        level.setBlockAndUpdate(first.above(), RouterRegistration.SWITCH.get().defaultBlockState());
        level.setBlockAndUpdate(first.above(2), RouterRegistration.FEDERATION_CABLE.get().defaultBlockState());
        level.setBlockAndUpdate(first.above(2).west(), AutomationRegistration.INTERFACE.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.WEST));
        for (var index = 0; index < 3; index++) {
            PartHelper.setPart(level, bus(context, index), null, null, AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        }
        for (var index = 0; index < 3; index++) {
            PartHelper.setPart(level, bus(context, index), Direction.EAST, null,
                    (index == 1 ? AEParts.FE_P2P_TUNNEL : AEParts.ITEM_P2P_TUNNEL).asItem());
        }
    }

    private static List<P2PTunnelPart<?>> tunnels(ServerContext context) {
        var out = new java.util.ArrayList<P2PTunnelPart<?>>();
        for (var index = 0; index < 3; index++) {
            if (context.level().getBlockEntity(bus(context, index)) instanceof IPartHost host
                    && host.getPart(Direction.EAST) instanceof P2PTunnelPart<?> tunnel) {
                out.add(tunnel);
            }
        }
        return out;
    }

    private static boolean ready(ServerContext context) {
        var tunnels = tunnels(context);
        return tunnels.size() == 3 && tunnels.stream().allMatch(P2PTunnelPart::isActive)
                && domain(context) != null;
    }

    private static space.controlnet.ae2federation.domain.FederationDomainId domain(ServerContext context) {
        var level = context.level();
        if (!(level.getBlockEntity(bus(context, 0).above(2).west())
                instanceof AutomationInterfaceBlockEntity automationInterface) || automationInterface.id() == null) {
            return null;
        }
        return AutomationInterfaces.get(level).domainOf(level, automationInterface.id()).orElse(null);
    }

    /** From the east-south-east and a little above, the three fronts with the middle one in the centre. */
    private static void look(ServerContext context) {
        var player = context.player();
        var target = Vec3.atCenterOf(bus(context, 1)).add(0.45, 0, 0);
        var eye = target.add(1.9, 0.8, 1.4);
        var dx = target.x - eye.x;
        var dy = target.y - eye.y;
        var dz = target.z - eye.z;
        var yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        var pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        // Flying, so the camera stays where it is put instead of falling to the ground.
        player.getAbilities().flying = true;
        player.onUpdateAbilities();
        player.connection.teleport(eye.x, eye.y - player.getEyeHeight(), eye.z, yaw, pitch);
    }

    private static void link(ServerContext context) {
        var server = context.server();
        var host = new ProgramHost(server, domain(context), "scenario:automation-tunnels");
        var catalog = host.catalog();
        var run = ProgramRun.start(ProgramRun.prepare(Map.of("tunnels", PROGRAM), catalog), catalog, host, "tunnels",
                "run", List.of());
        // Each link is the tick's one action; the runner continues with the next on the following call.
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
        var first = bus(context, 0);
        for (var position : List.of(first.west(), first.above(), first.above(2), first.above(2).west(), first,
                bus(context, 1), bus(context, 2))) {
            level.setBlockAndUpdate(position, Blocks.AIR.defaultBlockState());
        }
    }
}
