package space.controlnet.ae2federation.test.p2p;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.parts.p2p.MEP2PTunnelPart;
import appeng.parts.p2p.P2PTunnelPart;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import space.controlnet.ae2federation.domain.FederationDomainId;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.domain.FederationDomainSnapshot;
import space.controlnet.ae2federation.domain.port.FederationPort;
import space.controlnet.ae2federation.p2p.FederationP2PTunnelPart;
import space.controlnet.ae2federation.policy.PolicyEdit;
import space.controlnet.ae2federation.policy.PolicyMutationResult;
import space.controlnet.ae2federation.policy.PolicyRule;
import space.controlnet.ae2federation.policy.PolicyService;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.storage.RouterStorageMountFixture;

/**
 * TEST-ONLY: {@link RouterStorageMountFixture}'s two networks, joined by Federation P2P tunnels instead of a Federation
 * cable run. Each Router's east or west face meets a Federation cable whose other end faces a tunnel on an AE2 cable
 * bus; the buses sit on a third ME network, the carrier, with its own creative energy cell. Tunnels are made the way a
 * player makes them: an ME P2P tunnel attuned with a Federation cable, then paired with a memory card.
 */
public final class FederationP2PScene implements AutoCloseable {
    public static final AEItemKey IRON = AEItemKey.of(Items.IRON_INGOT);
    /** The bus whose west tunnel faces the consumer's cable, and the one whose east tunnel faces the provider's. */
    public static final BlockPos LEFT_BUS = new BlockPos(5, 4, 6);
    public static final BlockPos RIGHT_BUS = new BlockPos(7, 4, 6);
    public static final BlockPos LEFT_CABLE = new BlockPos(4, 4, 6);
    public static final BlockPos RIGHT_CABLE = new BlockPos(8, 4, 6);
    public static final BlockPos CARRIER_CABLE = new BlockPos(6, 4, 6);
    public static final BlockPos CARRIER_POWER = new BlockPos(6, 3, 6);

    private final GameTestHelper helper;
    private final RouterStorageMountFixture networks;
    private final Player player;
    private final ItemStack memoryCard = AEItems.MEMORY_CARD.stack();

    public FederationP2PScene(GameTestHelper helper) {
        this.helper = helper;
        networks = new RouterStorageMountFixture(helper);
        player = helper.makeMockPlayer(GameType.CREATIVE);
        placeCable(CARRIER_CABLE);
        helper.setBlock(CARRIER_POWER, AEBlocks.CREATIVE_ENERGY_CELL.block());
    }

    public RouterStorageMountFixture networks() {
        return networks;
    }

    /** The Routers and the Federation cable from each to its tunnel's bus; the buses come with their tunnels. */
    public void placeRouters() {
        networks.placeRouters();
        helper.setBlock(LEFT_CABLE, RouterRegistration.FEDERATION_CABLE.get());
        helper.setBlock(RIGHT_CABLE, RouterRegistration.FEDERATION_CABLE.get());
    }

    /** An AE2 glass cable at {@code position}: a cable bus with a cable and nothing else yet. */
    public void placeCable(BlockPos position) {
        helper.assertTrue(PartHelper.setPart(helper.getLevel(), helper.absolutePos(position), null, null,
                AEParts.GLASS_CABLE.item(appeng.api.util.AEColor.TRANSPARENT)) != null, "An AE2 cable must be placed");
    }

    /**
     * An ME P2P tunnel on {@code side} of the bus at {@code bus}, attuned with a Federation cable as a player would:
     * using the cable on the tunnel replaces it with a Federation P2P tunnel.
     */
    public FederationP2PTunnelPart attunedTunnel(BlockPos bus, Direction side) {
        var tunnel = PartHelper.setPart(helper.getLevel(), helper.absolutePos(bus), side, null,
                AEParts.ME_P2P_TUNNEL.get());
        helper.assertTrue(tunnel instanceof MEP2PTunnelPart, "An ME P2P tunnel must be placed on the bus");
        tunnel.onUseItemOn(RouterRegistration.FEDERATION_CABLE_ITEM.get().getDefaultInstance(), player,
                InteractionHand.MAIN_HAND, Vec3.atCenterOf(helper.absolutePos(bus)));
        return tunnel(bus, side);
    }

    public FederationP2PTunnelPart tunnel(BlockPos bus, Direction side) {
        var part = PartHelper.getPart(helper.getLevel(), helper.absolutePos(bus), side);
        helper.assertTrue(part instanceof FederationP2PTunnelPart,
                "A Federation cable must attune the tunnel at " + bus + " " + side + " into a Federation P2P tunnel");
        return (FederationP2PTunnelPart) part;
    }

    /** Makes {@code input} an input with the memory card (sneaking), and each of {@code outputs} its output. */
    public void pair(P2PTunnelPart<?> input, List<? extends P2PTunnelPart<?>> outputs) {
        player.setShiftKeyDown(true);
        input.onUseItemOn(memoryCard, player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        player.setShiftKeyDown(false);
        for (var output : outputs) {
            output.onUseItemOn(memoryCard, player, InteractionHand.MAIN_HAND, Vec3.ZERO);
        }
        helper.assertTrue(input.getFrequency() != 0 && !input.isOutput(), "The memory card must make an input");
        for (var output : outputs) {
            helper.assertTrue(output.isOutput() && output.getFrequency() == input.getFrequency(),
                    "The memory card must make an output of the input's frequency");
        }
    }

    /** Whether every tunnel is on a booted, powered carrier. */
    public static boolean online(FederationP2PTunnelPart... tunnels) {
        for (var tunnel : tunnels) {
            var node = tunnel.getGridNode();
            if (node == null || !node.hasGridBooted() || !tunnel.isActive()) return false;
        }
        return true;
    }

    /** Sets the storage rule in which the consumer reads the provider, and puts nine iron in the provider. */
    public void enableStorage() {
        var policies = PolicyService.get(helper.getLevel());
        var key = networks.key();
        var result = policies.editAll(List.of(new PolicyEdit(key, policies.revision(key), PolicyRule.storageDefaults())));
        helper.assertTrue(result instanceof PolicyMutationResult.Accepted, "The storage rule must be accepted: " + result);
        helper.assertValueEqual(networks.providerGrid().getStorageService().getInventory()
                .insert(IRON, 9, Actionable.MODULATE, IActionSource.empty()), 9L, "The provider takes the iron");
    }

    /** How much iron the consumer network sees. */
    public long consumerIron() {
        return networks.consumerGrid().getStorageService().getInventory().getAvailableStacks().get(IRON);
    }

    public void remove(BlockPos position) {
        helper.setBlock(position, Blocks.AIR);
    }

    /** The domain holding the tunnel's node, if any. */
    public Optional<FederationDomainSnapshot> domainOf(FederationP2PTunnelPart tunnel) {
        var level = helper.getLevel();
        var port = new FederationPort(tunnel.getBlockEntity().getBlockPos(), tunnel.getSide(),
                tunnel.getSide().getSerializedName());
        return FederationDomainRegistryAccess.get(level).federationDomainOf(FederationDomainRegistryAccess.nodeId(level, port));
    }

    /** The domains the consumer network is a member of. */
    public java.util.Set<FederationDomainId> consumerDomains() {
        return FederationDomainRegistryAccess.get(helper.getLevel()).federationdomainsFor(networks.key().consumerNetworkId());
    }

    /** Whether the carrier network belongs to no Federation Domain. */
    public boolean carrierIsNoMember(IGrid carrier) {
        return FederationDomainRegistryAccess.confirmedNetworkId(carrier)
                .map(id -> FederationDomainRegistryAccess.get(helper.getLevel()).federationdomainsFor(id).isEmpty())
                .orElse(true);
    }

    @Override
    public void close() {
        networks.close();
    }
}
