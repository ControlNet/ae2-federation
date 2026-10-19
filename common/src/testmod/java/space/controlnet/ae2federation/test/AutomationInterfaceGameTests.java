package space.controlnet.ae2federation.test;

import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.automation.AutomationInterfaceBlockEntity;
import space.controlnet.ae2federation.automation.AutomationInterfaceBook.AliasResult;
import space.controlnet.ae2federation.automation.AutomationInterfaces;
import space.controlnet.ae2federation.automation.AutomationRegistration;
import space.controlnet.ae2federation.domain.CableCapacity;
import space.controlnet.ae2federation.domain.CableCapacityCounter.Demand;
import space.controlnet.ae2federation.domain.CableCapacityCounter.Scope;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.router.CableVisualConnections;
import space.controlnet.ae2federation.test.router.RouterEvidence;
import space.controlnet.ae2federation.test.router.RouterFixtures;

/**
 * The ME Federation Automation Interface in a domain: Switch (one chest) - cable - interface along x. It links the cable
 * on any face but its front, counts one capacity unit there, and keeps a server-wide ID with an alias unique in its
 * domain.
 */
@PrefixGameTestTemplate(false)
public final class AutomationInterfaceGameTests {
    private static final BlockPos CENTER = new BlockPos(6, 6, 6);

    private AutomationInterfaceGameTests() {
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void automationInterfaceJoinsItsDomain(GameTestHelper helper) {
        var fixtures = build(helper, Direction.EAST);
        var level = helper.getLevel();
        var interfaces = AutomationInterfaces.get(level);
        helper.succeedWhen(() -> {
            var id = id(helper);
            helper.assertValueEqual(demand(helper), new Demand(Scope.SUBDOMAIN, 2),
                    "The cable counts the Switch's network and the interface");
            var domain = cableDomain(helper);
            helper.assertTrue(domain.isPresent(), "Waiting for the cable to join a domain");
            helper.assertValueEqual(interfaces.domainOf(level, id), domain, "The interface is in the cable's domain");
            helper.assertTrue(interfaces.idsIn(level, domain.get()).contains(id), "The domain lists the interface");
            helper.assertValueEqual(interfaces.setAlias(level, id, " furnace "), AliasResult.SET, "An alias is set");
            helper.assertValueEqual(interfaces.resolve(level, domain.get(), "furnace"), Optional.of(id),
                    "Programs find the interface by its alias");
            helper.assertValueEqual(interfaces.setAlias(level, id, "interface-abcdef"), AliasResult.INVALID,
                    "An alias that looks like an ID is refused");
            RouterEvidence.write("automationinterfacejoinsitsdomain", 7, Map.of("id", id, "domain",
                    domain.get().toString()));
            interfaces.setAlias(level, id, "");
            fixtures.close();
        });
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void automationInterfaceFrontLinksNoCable(GameTestHelper helper) {
        var fixtures = build(helper, Direction.WEST);
        var level = helper.getLevel();
        // Long enough for the interface to publish, had its front linked.
        helper.runAfterDelay(40, () -> {
            var id = id(helper);
            helper.assertValueEqual(demand(helper), new Demand(Scope.SUBDOMAIN, 1),
                    "The cable counts only the Switch's network");
            var domain = cableDomain(helper);
            helper.assertTrue(domain.isPresent() && !AutomationInterfaces.get(level).domainOf(level, id).equals(domain),
                    "An interface whose front faces the cable is not in the cable's domain");
            RouterEvidence.write("automationinterfacefrontlinksnocable", 3, Map.of("id", id));
            fixtures.close();
            helper.succeed();
        });
    }

    /** A wrench changes only the facing: the block entity stays, and the face that acted now links the cable. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void automationInterfaceTurnedLinksTheCable(GameTestHelper helper) {
        var fixtures = build(helper, Direction.WEST);
        var claimed = new String[1];
        helper.startSequence()
                .thenWaitUntil(() -> claimed[0] = id(helper))
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertValueEqual(demand(helper), new Demand(Scope.SUBDOMAIN, 1),
                            "Facing the cable, the interface does not count");
                    helper.setBlock(CENTER.east(), helper.getBlockState(CENTER.east())
                            .setValue(BlockStateProperties.FACING, Direction.EAST));
                })
                .thenWaitUntil(() -> helper.assertValueEqual(demand(helper), new Demand(Scope.SUBDOMAIN, 2),
                        "Turned away, the interface links the cable and counts"))
                .thenExecute(() -> {
                    helper.assertValueEqual(id(helper), claimed[0], "Turning keeps the ID");
                    RouterEvidence.write("automationinterfaceturnedlinksthecable", 3, Map.of("id", claimed[0]));
                    fixtures.close();
                })
                .thenSucceed();
    }

    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 300, required = true, manualOnly = true)
    public static void automationInterfaceReleasesItsIdWhenBroken(GameTestHelper helper) {
        var fixtures = build(helper, Direction.EAST);
        var level = helper.getLevel();
        var interfaces = AutomationInterfaces.get(level);
        var claimed = new String[1];
        helper.startSequence()
                .thenWaitUntil(() -> claimed[0] = id(helper))
                .thenExecute(() -> {
                    helper.assertTrue(interfaces.entry(claimed[0]).isPresent(), "The server keeps the claimed ID");
                    helper.setBlock(CENTER.east(), Blocks.AIR);
                })
                .thenWaitUntil(() -> helper.assertValueEqual(demand(helper), new Demand(Scope.SUBDOMAIN, 1),
                        "The cable no longer counts the interface"))
                .thenExecute(() -> {
                    helper.assertValueEqual(interfaces.entry(claimed[0]), Optional.empty(),
                            "Breaking the interface frees its ID");
                    RouterEvidence.write("automationinterfacereleasesitsidwhenbroken", 3, Map.of("id", claimed[0]));
                    fixtures.close();
                })
                .thenSucceed();
    }

    /**
     * The cable draws its arm toward the faces that link it: the Computing Device's every face and the interface's
     * faces but its front, as toward an Endpoint's front.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 100, required = true, manualOnly = true)
    public static void automationBlocksJoinTheCableAsDrawn(GameTestHelper helper) {
        BlockPos.betweenClosed(CENTER.offset(-3, -1, -1), CENTER.offset(3, 2, 1))
                .forEach(position -> helper.setBlock(position, Blocks.AIR));
        var fixtures = new RouterFixtures(helper);
        fixtures.placeFederationCable(CENTER);
        helper.setBlock(CENTER.west(), AutomationRegistration.DEVICE.get().defaultBlockState());
        helper.setBlock(CENTER.east(), AutomationRegistration.INTERFACE.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.EAST));
        helper.setBlock(CENTER.above(), AutomationRegistration.INTERFACE.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.DOWN));
        helper.runAfterDelay(2, () -> {
            var connections = CableVisualConnections.connections(helper.getLevel(), helper.absolutePos(CENTER));
            helper.assertValueEqual(CableVisualConnections.kind(connections, Direction.WEST),
                    CableVisualConnections.DENSE, "The cable joins the Computing Device");
            helper.assertValueEqual(CableVisualConnections.kind(connections, Direction.EAST),
                    CableVisualConnections.DENSE, "The cable joins the interface's back");
            helper.assertValueEqual(CableVisualConnections.kind(connections, Direction.UP),
                    CableVisualConnections.NONE, "The cable stays closed toward the interface's front");
            RouterEvidence.write("automationblocksjointhecableasdrawn", 3, Map.of());
            fixtures.close();
            helper.succeed();
        });
    }

    /** The Switch and its chest west of the cable, the interface east of it with its front facing {@code front}. */
    private static RouterFixtures build(GameTestHelper helper, Direction front) {
        // Batched tests share the level, and an ME device left beside a Switch would count as one more network.
        BlockPos.betweenClosed(CENTER.offset(-3, -1, -1), CENTER.offset(3, 2, 1))
                .forEach(position -> helper.setBlock(position, Blocks.AIR));
        var fixtures = new RouterFixtures(helper);
        fixtures.placeSwitch(CENTER.west());
        fixtures.placeNativeDevice(CENTER.west(), Direction.UP);
        fixtures.placeFederationCable(CENTER);
        helper.setBlock(CENTER.east(), AutomationRegistration.INTERFACE.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, front));
        return fixtures;
    }

    private static String id(GameTestHelper helper) {
        AutomationInterfaceBlockEntity automationInterface = helper.getBlockEntity(CENTER.east());
        var id = automationInterface.id();
        helper.assertTrue(id != null, "Waiting for the interface to claim its ID");
        return id;
    }

    private static Demand demand(GameTestHelper helper) {
        var demand = CableCapacity.demand(helper.getLevel(), helper.absolutePos(CENTER));
        helper.assertTrue(demand.isPresent(), "Waiting for the cable to join a domain");
        return demand.get();
    }

    private static Optional<FederationDomainId> cableDomain(GameTestHelper helper) {
        var level = helper.getLevel();
        return FederationDomainRegistryAccess.get(level)
                .federationDomainOf(FederationDomainRegistryAccess.nodeId(level, helper.absolutePos(CENTER)))
                .map(snapshot -> snapshot.federationDomainId());
    }
}
