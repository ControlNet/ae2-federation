package space.controlnet.ae2federation.test;

import static space.controlnet.ae2federation.test.EndpointFederationFaceGameTests.FAR;
import static space.controlnet.ae2federation.test.EndpointFederationFaceGameTests.HUB;
import static space.controlnet.ae2federation.test.EndpointFederationFaceGameTests.MEMBER_CHEST;
import static space.controlnet.ae2federation.test.EndpointFederationFaceGameTests.NEAR;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.ae2.processing.FederationPatternProviderTargetCache;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.claim.ClaimState;
import space.controlnet.ae2federation.processing.endpoint.EndpointBlockEntity;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;
import space.controlnet.ae2federation.processing.provider.ProviderTargetState;
import space.controlnet.ae2federation.router.RouterRegistration;

/**
 * The Federation Pattern Provider reaches a domain through its own Federation face (its FRONT). Its network being a
 * member of the Endpoint's domain by another route, here the Router's west face, does not let it map or use that
 * domain's Endpoints.
 */
@PrefixGameTestTemplate(false)
public final class ProviderFederationFaceGameTests {
    /** South of the member chest, so its other faces join the member network. */
    private static final BlockPos PROVIDER = new BlockPos(2, 1, 4);
    /** A Federation Cable on the Router's south face, east of the Provider. */
    private static final BlockPos ROUTER_CABLE = new BlockPos(3, 1, 4);

    private ProviderFederationFaceGameTests() {
    }

    /**
     * With its Federation face turned away from the cable, the Provider may not map the Router domain's Endpoint.
     * Turned onto the cable, it maps it and its Lane is authorized. Turned away again, the Lane pauses and keeps its
     * Claim, although the Provider's network is still a member of the domain.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke",
            timeoutTicks = 600, required = true, manualOnly = true)
    public static void providerFederationFaceRequired(GameTestHelper helper) {
        var scene = new EndpointFederationFaceGameTests.Scene(helper);
        scene.member();
        helper.setBlock(HUB, RouterRegistration.ROUTER.get());
        helper.setBlock(NEAR, RouterRegistration.FEDERATION_CABLE.get());
        scene.endpoint(FAR, Direction.WEST);
        helper.setBlock(ROUTER_CABLE, RouterRegistration.FEDERATION_CABLE.get());
        scene.place(PROVIDER, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, Direction.SOUTH), scene.member);
        var phase = new int[1];
        helper.succeedWhen(() -> {
            var provider = helper.<FederationPatternProviderBlockEntity>getBlockEntity(PROVIDER);
            var endpoint = helper.<EndpointBlockEntity>getBlockEntity(FAR);
            helper.assertTrue(provider.runtime().isPresent() && endpoint.binding() != null, "waiting for the devices");
            scene.assertEndpointJoins(HUB, FAR);
            helper.assertTrue(scene.node(PROVIDER).getGrid() == scene.node(MEMBER_CHEST).getGrid(),
                    "the Provider must join the member network");
            var domain = scene.domainOf(HUB).orElseThrow();
            var faceJoined = domain.nodes().contains(scene.nodeId(PROVIDER));
            switch (phase[0]) {
                case 0 -> {
                    helper.assertFalse(faceJoined, "a Provider facing away must not be a node of the domain");
                    installPattern(provider);
                    helper.assertValueEqual(map(provider, endpoint), "rejected-domain-disconnected",
                            "a Provider whose Federation face is outside the domain must not map its Endpoint");
                    helper.assertTrue(endpoint.claimState() instanceof ClaimState.Unclaimed,
                            "a refused mapping must not claim the Endpoint");
                    rotate(helper, Direction.EAST);
                    phase[0] = 1;
                    helper.fail("turned the Provider onto the cable");
                }
                case 1 -> {
                    helper.assertTrue(faceJoined, "waiting for the Provider's Federation face to join the domain");
                    if (provider.endpointsForSlot(0).isEmpty()) {
                        var status = map(provider, endpoint);
                        helper.assertTrue(status.startsWith("accepted-"), "a connected Provider must map: " + status);
                    }
                    helper.assertValueEqual(laneState(provider), ProviderTargetState.ACTIVE,
                            "the Lane of a connected Provider must be authorized");
                    rotate(helper, Direction.SOUTH);
                    phase[0] = 2;
                    helper.fail("turned the Provider away");
                }
                default -> {
                    helper.assertFalse(faceJoined, "waiting for the Provider to leave the domain");
                    helper.assertTrue(domain.memberships().containsKey(scene.member),
                            "the Provider's network must stay a member through the Router");
                    helper.assertValueEqual(laneState(provider), ProviderTargetState.FEDERATION_DOMAIN_DISCONNECTED,
                            "the Lane must pause once the Provider's Federation face leaves the domain");
                    helper.assertTrue(endpoint.claimState() instanceof ClaimState.Owned,
                            "a disconnected Provider must keep its Claim");
                }
            }
        });
    }

    private static void installPattern(FederationPatternProviderBlockEntity provider) {
        var inventory = provider.getTerminalPatternInventory();
        if (!inventory.getStackInSlot(0).isEmpty()) return;
        inventory.insertItem(0, PatternDetailsHelper.encodeProcessingPattern(
                List.of(new GenericStack(AEItemKey.of(Items.COBBLESTONE), 1)),
                List.of(new GenericStack(AEItemKey.of(Items.DIAMOND), 1))), false);
    }

    private static String map(FederationPatternProviderBlockEntity provider, EndpointBlockEntity endpoint) {
        return provider.toggleEndpoint(provider.mappedProvider().mappingHandle(0), endpoint.binding());
    }

    private static void rotate(GameTestHelper helper, Direction face) {
        var level = helper.getLevel();
        var position = helper.absolutePos(PROVIDER);
        level.setBlockAndUpdate(position, level.getBlockState(position).setValue(BlockStateProperties.FACING, face));
    }

    private static ProviderTargetState laneState(FederationPatternProviderBlockEntity provider) {
        FederationPatternProviderTargetCache.find(provider.lane(0));
        return provider.runtime().map(runtime -> runtime.lastResolution().state())
                .orElse(ProviderTargetState.NATIVE_TARGET_UNAVAILABLE);
    }
}
