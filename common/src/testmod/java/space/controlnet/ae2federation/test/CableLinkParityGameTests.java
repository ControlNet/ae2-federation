package space.controlnet.ae2federation.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.beam.BeamRegistration;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.power.PowerRegistration;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.quantum.QuantumRegistration;
import space.controlnet.ae2federation.router.CableVisualConnections;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/**
 * Every block a Federation Cable can link to draws the cable's arm toward it through exactly the faces that link: a
 * cable on a face joins the device's domain if and only if it reaches the device with an arm.
 */
@PrefixGameTestTemplate(false)
public final class CableLinkParityGameTests {
    private CableLinkParityGameTests() {
    }

    /** One device cell under test, the faces that carry a cable, and how many of them link. */
    private record Probe(String name, BlockPos cell, List<Direction> faces, int links) {
    }

    /**
     * A Router and a Switch link on all six faces; a Provider and an Endpoint on their front; a 1x1 Beam Former on its
     * back; a formed Beam Converter, creative too, on its front; a 3x3x3 Quantum Cube inside each face's centre and
     * not on its border. A cable on each probed face reaches the device with an arm exactly when it joins its domain.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "scale_36_empty",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void everyLinkedFaceDrawsAnArm(GameTestHelper helper) {
        var fixtures = new RouterFixtures(helper);
        var all = List.of(Direction.values());
        var probes = new ArrayList<Probe>();

        helper.setBlock(new BlockPos(4, 4, 4), RouterRegistration.ROUTER.get());
        probes.add(new Probe("router", new BlockPos(4, 4, 4), all, 6));
        helper.setBlock(new BlockPos(10, 4, 4), RouterRegistration.SWITCH.get());
        probes.add(new Probe("switch", new BlockPos(10, 4, 4), all, 6));
        helper.setBlock(new BlockPos(16, 4, 4), facing(ProcessingRegistration.PROVIDER.get().defaultBlockState(),
                Direction.EAST));
        probes.add(new Probe("provider", new BlockPos(16, 4, 4), all, 1));
        helper.setBlock(new BlockPos(22, 4, 4), facing(ProcessingRegistration.ENDPOINT.get().defaultBlockState(),
                Direction.UP));
        probes.add(new Probe("endpoint", new BlockPos(22, 4, 4), all, 1));
        helper.setBlock(new BlockPos(28, 4, 4), facing(BeamRegistration.BEAM_FORMER.get().defaultBlockState(),
                Direction.EAST));
        probes.add(new Probe("beam former", new BlockPos(28, 4, 4), all, 1));

        // A 1x1x2 converter facing west: its west block's front links; its east face is the rest of the body.
        var converterFaces = List.of(Direction.WEST, Direction.NORTH, Direction.SOUTH, Direction.UP, Direction.DOWN);
        for (var converter : List.of(PowerRegistration.BEAM_CONVERTER.get(), PowerRegistration.CREATIVE_BEAM_CONVERTER.get())) {
            var front = new BlockPos(converter == PowerRegistration.BEAM_CONVERTER.get() ? 4 : 10, 4, 12);
            for (var cell : List.of(front, front.east())) {
                helper.setBlock(cell, facing(converter.defaultBlockState(), Direction.WEST));
            }
            probes.add(new Probe(converter.getDescriptionId(), front, converterFaces, 1));
        }

        // A 3x3x3 cube: the middle of its west face links, a corner links on none of its outer faces.
        BlockPos.betweenClosed(new BlockPos(16, 4, 12), new BlockPos(18, 6, 14)).forEach(cell ->
                helper.setBlock(cell, QuantumRegistration.QUANTUM_CUBE.get().defaultBlockState()));
        probes.add(new Probe("quantum cube middle", new BlockPos(16, 5, 13), List.of(Direction.WEST), 1));
        probes.add(new Probe("quantum cube corner", new BlockPos(16, 4, 12),
                List.of(Direction.WEST, Direction.NORTH, Direction.DOWN), 0));

        for (var probe : probes) {
            for (var face : probe.faces()) fixtures.placeFederationCable(probe.cell().relative(face));
        }
        var level = helper.getLevel();
        helper.succeedWhen(() -> {
            for (var probe : probes) {
                var device = FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(probe.cell()));
                int links = 0;
                for (var face : probe.faces()) {
                    var cable = helper.absolutePos(probe.cell().relative(face));
                    var domain = FederationDomainRegistryAccess.get(level).federationDomainOf(
                            FederationDomainRegistryAccess.nodeId(level, cable));
                    boolean linked = domain.isPresent() && domain.get().nodes().contains(device);
                    boolean drawn = CableVisualConnections.kind(CableVisualConnections.connections(level, cable),
                            face.getOpposite()) != CableVisualConnections.NONE;
                    helper.assertValueEqual(drawn, linked, probe.name() + " " + face + ": an arm exactly where it links");
                    if (linked) links++;
                }
                helper.assertValueEqual(links, probe.links(), probe.name() + ": linked faces");
            }
            RouterEvidence.write("everylinkedfacedrawsanarm", 2, Map.of("devices", Integer.toString(probes.size()),
                    "parity", "arm drawn iff linked", "linkedFaces", "as each device's rule says"));
            fixtures.close();
        });
    }

    private static BlockState facing(BlockState state, Direction facing) {
        return state.setValue(BlockStateProperties.FACING, facing);
    }
}
