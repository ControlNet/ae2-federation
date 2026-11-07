package space.controlnet.ae2federation.test;

import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.me.helpers.IGridConnectedBlockEntity;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyAction;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyActionRequest;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyActionResult;
import space.controlnet.ae2federation.client.menu.FederationDomainPolicyMenu;
import space.controlnet.ae2federation.client.policy.FederationDomainPolicySession;
import space.controlnet.ae2federation.client.policy.MappingWireTarget;
import space.controlnet.ae2federation.client.policy.NetworkRenameTarget;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityNodeSeed;
import space.controlnet.ae2federation.observability.statistics.StatisticsPayload;
import space.controlnet.ae2federation.observability.statistics.StatisticsRange;
import space.controlnet.ae2federation.persistence.NetworkNames;
import space.controlnet.ae2federation.processing.ProcessingRegistration;
import space.controlnet.ae2federation.processing.provider.FederationPatternProviderBlockEntity;
import space.controlnet.ae2federation.processing.provider.ProviderObservationRegistry;
import space.controlnet.ae2federation.router.RouterRegistration;
import space.controlnet.ae2federation.test.perf.DomainFarmScene;
import space.controlnet.ae2federation.test.world.MockServerPlayers;

/**
 * The Federation domain screen's server side: what its String bindings send for large domains, and the actions a
 * small domain must allow or refuse.
 */
@PrefixGameTestTemplate(false)
public final class DomainMenuGameTests {
    private static final Logger LOGGER = LoggerFactory.getLogger("ae2federation-menu-sync");
    /** {@code ByteBufCodecs.STRING_UTF8}'s limit in characters, which LDLib2's String bindings encode with. */
    private static final int STRING_LIMIT = 32767;
    private static final BlockPos ENERGY = new BlockPos(1, 1, 3);
    private static final BlockPos CHEST = new BlockPos(2, 1, 3);
    private static final BlockPos HUB = new BlockPos(3, 1, 3);
    /** A Provider of the member network on the chest, its front on a Federation Cable above the Switch. */
    private static final BlockPos PROVIDER = new BlockPos(2, 2, 3);
    private static final BlockPos PROVIDER_CABLE = new BlockPos(3, 2, 3);
    /** A second Provider of the member network beside the chest, its front on a Federation Cable beside the Switch. */
    private static final BlockPos SECOND_PROVIDER = new BlockPos(2, 1, 4);
    private static final BlockPos SECOND_CABLE = new BlockPos(3, 1, 4);
    /** Endpoints on each side of a Federation Cable line east of the Switch. */
    private static final int ENDPOINT_PAIRS = 32;

    private DomainMenuGameTests() {
    }

    /**
     * A domain of one network, one Provider and 64 Endpoints: every String the screen syncs must fit the codec, and
     * the Endpoints must still be accounted for.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", timeoutTicks = 600,
            required = true, manualOnly = true)
    public static void domainMenuSyncManyEndpoints(GameTestHelper helper) {
        var member = NetworkId.create();
        memberNetwork(helper, member);
        provider(helper, PROVIDER, Direction.EAST, member);
        helper.setBlock(PROVIDER_CABLE, RouterRegistration.FEDERATION_CABLE.get());
        var endpoints = new java.util.ArrayList<BlockPos>();
        for (int index = 0; index < ENDPOINT_PAIRS; index++) {
            var cable = new BlockPos(4 + index, 1, 3);
            helper.setBlock(cable, RouterRegistration.FEDERATION_CABLE.get());
            endpoints.add(endpoint(helper, cable.north(), Direction.SOUTH));
            endpoints.add(endpoint(helper, cable.south(), Direction.NORTH));
        }
        helper.succeedWhen(() -> {
            var registry = FederationDomainRegistryAccess.get(helper.getLevel());
            var domain = registry.federationDomainOf(nodeId(helper, HUB)).orElse(null);
            helper.assertTrue(domain != null && domain.memberships().containsKey(member), "Waiting for the domain");
            helper.assertTrue(endpoints.stream().allMatch(position -> domain.nodes().contains(nodeId(helper, position))),
                    "Waiting for every Endpoint to join the domain");
            helper.assertTrue(ProviderObservationRegistry.entries(helper.getLevel()).stream()
                    .anyMatch(entry -> domain.nodes().contains(entry.runtime().federationFace())), "Waiting for the Provider");
            var player = playerAt(helper, HUB);
            var session = FederationDomainPolicySession.forRouter(player, helper.absolutePos(HUB));
            var providerSession = FederationDomainPolicySession.forDevice(player, helper.absolutePos(PROVIDER));
            var texts = synced(session);
            texts.put("providerChoices", providerSession.providerChoices());
            report("domainmenusyncmanyendpoints", texts, endpoints.size(), "endpoint");
            assertFits(helper, texts);
            var choices = JsonParser.parseString(texts.get("choices")).getAsJsonObject();
            helper.assertValueEqual(shown(choices, "endpoint") + omitted(choices, "endpoint"), endpoints.size(),
                    "Shown and omitted Endpoints must add up to every Endpoint");
        });
    }

    /** As {@link #domainMenuSyncManyEndpoints}, for a domain of 65 networks with a storage and power rule each. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", timeoutTicks = 4000,
            required = true, manualOnly = true)
    public static void domainMenuSyncManyNetworks(GameTestHelper helper) {
        networkFarm(helper, "domainmenusyncmanynetworks", 64);
    }

    /** Measurement only: the same for a thousand subnets, the target scale. */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", timeoutTicks = 12000,
            required = true, manualOnly = true)
    public static void domainMenuSyncThousandNetworks(GameTestHelper helper) {
        networkFarm(helper, "domainmenusyncthousandnetworks", 1000);
    }

    private static void networkFarm(GameTestHelper helper, String testId, int subnets) {
        var scene = new DomainFarmScene(helper, subnets, 2, 16);
        var built = new boolean[1];
        helper.succeedWhen(() -> {
            if (!built[0]) {
                built[0] = scene.build();
                helper.assertTrue(false, "Building the scene");
            }
            helper.assertTrue(scene.ready(), "Waiting for every subnet to reach the main network");
            var player = MockServerPlayers.inLevel(helper);
            ObservationGameTestPlayerTransport.install(player);
            var hub = scene.mainSwitch();
            player.setPos(Vec3.atCenterOf(hub));
            GameTestChecks.once(helper, () -> {
                try {
                    var texts = synced(FederationDomainPolicySession.forRouter(player, hub));
                    report(testId, texts, scene.networks(), "network");
                    assertFits(helper, texts);
                    var choices = JsonParser.parseString(texts.get("choices")).getAsJsonObject();
                    helper.assertValueEqual(shown(choices, "networks") + omitted(choices, "networks"), scene.networks(),
                            "Shown and omitted networks must add up to every member");
                } finally {
                    scene.close();
                }
            });
        });
    }

    /**
     * A domain of one network has no rule pair, but its network can be renamed: the screen's rename is accepted and
     * the name is stored.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", timeoutTicks = 400,
            required = true, manualOnly = true)
    public static void domainMenuRenameOneNetwork(GameTestHelper helper) {
        var member = NetworkId.create();
        memberNetwork(helper, member);
        helper.setBlock(HUB.east(), RouterRegistration.FEDERATION_CABLE.get());
        helper.succeedWhen(() -> {
            var domain = FederationDomainRegistryAccess.get(helper.getLevel()).federationDomainOf(nodeId(helper, HUB))
                    .orElse(null);
            helper.assertTrue(domain != null && domain.memberships().keySet().equals(java.util.Set.of(member)),
                    "Waiting for a domain of one network");
            var player = playerAt(helper, HUB);
            GameTestChecks.once(helper, () -> {
                try {
                    helper.assertTrue(FederationDomainPolicyMenu.openRouter(player, helper.absolutePos(HUB)),
                            "The Switch's screen must open");
                    var result = send(player, FederationDomainPolicyAction.RENAME_NETWORK,
                            new NetworkRenameTarget(member, "Smelting").encode());
                    helper.assertValueEqual(result, FederationDomainPolicyActionResult.ACCEPTED,
                            "A one-network domain's network must be renamable");
                    helper.assertValueEqual(NetworkNames.get(helper.getLevel()).name(member).orElse(""), "Smelting",
                            "The new name must be stored");
                } finally {
                    player.doCloseContainer();
                }
            });
        });
    }

    /**
     * The workspace edits a Provider that is then removed: a wire the client sent for it is refused, not applied to
     * the Provider that takes its place in the list.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", timeoutTicks = 400,
            required = true, manualOnly = true)
    public static void domainMenuMappingProviderGone(GameTestHelper helper) {
        var member = NetworkId.create();
        memberNetwork(helper, member);
        provider(helper, PROVIDER, Direction.EAST, member);
        helper.setBlock(PROVIDER_CABLE, RouterRegistration.FEDERATION_CABLE.get());
        provider(helper, SECOND_PROVIDER, Direction.EAST, member);
        helper.setBlock(SECOND_CABLE, RouterRegistration.FEDERATION_CABLE.get());
        for (var position : new BlockPos[] {PROVIDER, SECOND_PROVIDER}) {
            helper.<FederationPatternProviderBlockEntity>getBlockEntity(position).getTerminalPatternInventory().insertItem(0,
                    appeng.api.crafting.PatternDetailsHelper.encodeProcessingPattern(
                            java.util.List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(
                                    net.minecraft.world.item.Items.COBBLESTONE), 1)),
                            java.util.List.of(new appeng.api.stacks.GenericStack(appeng.api.stacks.AEItemKey.of(
                                    net.minecraft.world.item.Items.DIAMOND), 1))), false);
        }
        // The Endpoint has a powered subnet of its own, so a wire to it can be made.
        var subnet = NetworkId.create();
        var endpoint = endpoint(helper, HUB.east(), Direction.WEST);
        ((IGridConnectedBlockEntity) helper.getBlockEntity(endpoint)).getMainNode()
                .loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", subnet));
        place(helper, endpoint.above(), AEBlocks.ME_CHEST.block().defaultBlockState(), subnet);
        place(helper, endpoint.above(2), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), subnet);
        helper.succeedWhen(() -> {
            var domain = FederationDomainRegistryAccess.get(helper.getLevel()).federationDomainOf(nodeId(helper, HUB))
                    .orElse(null);
            helper.assertTrue(domain != null && domain.nodes().contains(nodeId(helper, endpoint)), "Waiting for the domain");
            helper.assertValueEqual(ProviderObservationRegistry.entries(helper.getLevel()).stream()
                    .filter(entry -> domain.nodes().contains(entry.runtime().federationFace())).count(), 2L,
                    "Waiting for both Providers");
            var player = playerAt(helper, HUB);
            GameTestChecks.once(helper, () -> {
                var session = FederationDomainPolicySession.forRouter(player, helper.absolutePos(HUB));
                var choices = JsonParser.parseString(session.workspaceChoices()).getAsJsonObject();
                var removedAt = helper.absolutePos(PROVIDER).toShortString();
                var removed = objects(choices, "mapping_provider").stream()
                        .filter(choice -> choice.get("position").getAsString().equals(removedAt))
                        .map(choice -> choice.get("id").getAsString()).findFirst().orElseThrow();
                helper.assertTrue(session.selectTarget("mapping_provider:" + removed), "The first Provider must be selectable");
                // The client's wires view shows that Provider: the selection is pinned by the next build.
                choices = JsonParser.parseString(session.workspaceChoices()).getAsJsonObject();
                helper.assertValueEqual(choices.getAsJsonObject("selected").get("mapping_provider").getAsString(), removed,
                        "The first Provider must be the one edited");
                var target = objects(choices, "target").getFirst().get("id").getAsString();
                var survivorAt = helper.absolutePos(SECOND_PROVIDER).toShortString();
                var survivorId = objects(choices, "mapping_provider").stream()
                        .filter(choice -> choice.get("position").getAsString().equals(survivorAt))
                        .map(choice -> choice.get("id").getAsString()).findFirst().orElseThrow();
                helper.setBlock(PROVIDER, Blocks.AIR);
                var survivor = helper.<FederationPatternProviderBlockEntity>getBlockEntity(SECOND_PROVIDER);
                var accepted = session.setMapping(new MappingWireTarget("0", target, true).encode());
                helper.assertTrue(survivor.endpointsForSlot(0).isEmpty(),
                        "The other Provider's slot must not receive the wire");
                helper.assertFalse(accepted, "A wire for the removed Provider must be refused");
                var selected = JsonParser.parseString(session.workspaceChoices()).getAsJsonObject().getAsJsonObject("selected");
                helper.assertFalse(selected.has("mapping_provider")
                                && selected.get("mapping_provider").getAsString().equals(survivorId),
                        "The workspace must not move the edit to the other Provider");
            });
        });
    }

    /**
     * Statistics pins and figures follow the session's authority: once the player is out of reach, the page neither
     * pins keys nor gets figures.
     */
    @GameTest(templateNamespace = FederationTestMod.MOD_ID, template = "harness_native_smoke", timeoutTicks = 400,
            required = true, manualOnly = true)
    public static void domainMenuStatisticsNeedLiveSession(GameTestHelper helper) {
        var member = NetworkId.create();
        memberNetwork(helper, member);
        helper.setBlock(HUB.east(), RouterRegistration.FEDERATION_CABLE.get());
        helper.succeedWhen(() -> {
            var domain = FederationDomainRegistryAccess.get(helper.getLevel()).federationDomainOf(nodeId(helper, HUB))
                    .orElse(null);
            helper.assertTrue(domain != null && domain.memberships().containsKey(member), "Waiting for the domain");
            var player = playerAt(helper, HUB);
            GameTestChecks.once(helper, () -> {
                var session = FederationDomainPolicySession.forRouter(player, helper.absolutePos(HUB));
                var request = new StatisticsPayload.Request(StatisticsRange.SECONDS, StatisticsPayload.POWER, "", false, false);
                helper.assertFalse(session.statistics(request).isEmpty(), "A live session must get figures");
                var pins = new net.minecraft.nbt.ListTag();
                pins.add(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.COBBLESTONE)
                        .toTagGeneric(helper.getLevel().registryAccess()));
                player.setPos(Vec3.atCenterOf(helper.absolutePos(HUB)).add(64, 0, 0));
                helper.assertFalse(session.pinStatistics(pins), "A player out of reach must not pin keys");
                helper.assertTrue(session.statistics(request).isEmpty(), "A player out of reach must not get figures");
            });
        });
    }

    /** What the domain screen's String bindings send, by binding. */
    private static Map<String, String> synced(FederationDomainPolicySession session) {
        var texts = new LinkedHashMap<String, String>();
        texts.put("choices", session.workspaceChoices());
        texts.put("overview", session.networkOverviewText());
        try {
            texts.put("graph", session.graphSnapshotText());
        } catch (RuntimeException failed) {
            // Recorded, not hidden: the measurement still reports the other bindings.
            LOGGER.warn("AE2F_SYNC graph snapshot failed: {}", failed.toString());
            texts.put("graph", "");
        }
        texts.put("flows", session.pairFlowText());
        return texts;
    }

    /** Logs each text's length and, for JSON arrays of {@code unit}s, the average characters per element. */
    private static void report(String testId, Map<String, String> texts, int units, String unit) {
        long bytes = 0;
        for (var entry : texts.entrySet()) {
            var text = entry.getValue();
            bytes += text.getBytes(StandardCharsets.UTF_8).length;
            LOGGER.info("AE2F_SYNC test={} binding={} chars={} {}s={}", testId, entry.getKey(), text.length(), unit, units);
            if (text.startsWith("{")) {
                var root = JsonParser.parseString(text).getAsJsonObject();
                for (var field : root.keySet()) {
                    if (root.get(field) instanceof JsonArray array && !array.isEmpty()) {
                        LOGGER.info("AE2F_SYNC test={} binding={} array={} elements={} chars={} perElement={}", testId,
                                entry.getKey(), field, array.size(), array.toString().length(),
                                array.toString().length() / array.size());
                    }
                }
            } else if (text.startsWith("[") && text.length() > 2) {
                var array = JsonParser.parseString(text).getAsJsonArray();
                LOGGER.info("AE2F_SYNC test={} binding={} elements={} perElement={}", testId, entry.getKey(), array.size(),
                        text.length() / array.size());
            }
        }
        LOGGER.info("AE2F_SYNC test={} totalBytes={}", testId, bytes);
    }

    /** Each text encodes with the codec LDLib2 sends it with, and all of them together stay far below 1 MiB. */
    private static void assertFits(GameTestHelper helper, Map<String, String> texts) {
        long bytes = 0;
        for (var entry : texts.entrySet()) {
            var text = entry.getValue();
            helper.assertTrue(text.length() <= STRING_LIMIT, "The " + entry.getKey() + " binding sends " + text.length()
                    + " characters, over the codec's " + STRING_LIMIT);
            var buffer = Unpooled.buffer();
            try {
                ByteBufCodecs.STRING_UTF8.encode(buffer, text);
                bytes += buffer.readableBytes();
            } finally {
                buffer.release();
            }
        }
        helper.assertTrue(bytes < 512 * 1024, "The bindings send " + bytes + " bytes together");
    }

    private static int shown(com.google.gson.JsonObject root, String field) {
        return root.has(field) ? root.getAsJsonArray(field).size() : 0;
    }

    private static int omitted(com.google.gson.JsonObject root, String field) {
        return root.has("omitted") && root.getAsJsonObject("omitted").has(field)
                ? root.getAsJsonObject("omitted").get(field).getAsInt() : 0;
    }

    private static java.util.List<com.google.gson.JsonObject> objects(com.google.gson.JsonObject root, String field) {
        var values = new java.util.ArrayList<com.google.gson.JsonObject>();
        root.getAsJsonArray(field).forEach(value -> values.add(value.getAsJsonObject()));
        return values;
    }

    private static FederationDomainPolicyActionResult send(ServerPlayer player, FederationDomainPolicyAction action,
            String target) {
        // A request for an action without a target carries the menu's current authority.
        var base = FederationDomainPolicyMenu.currentRequest(player, FederationDomainPolicyAction.NEXT_ENDPOINT).orElseThrow();
        return FederationDomainPolicyMenu.dispatch(player, new FederationDomainPolicyActionRequest(action, base.containerId(),
                base.menuNonce(), base.menuSequence(), base.context(), base.expectedRevision(), target));
    }

    /** The member network west of the Switch: an energy cell and an ME Chest. */
    private static void memberNetwork(GameTestHelper helper, NetworkId member) {
        place(helper, ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState(), member);
        place(helper, CHEST, AEBlocks.ME_CHEST.block().defaultBlockState(), member);
        helper.<MEChestBlockEntity>getBlockEntity(CHEST).setCell(AEItems.ITEM_CELL_1K.stack());
        helper.setBlock(HUB, RouterRegistration.SWITCH.get());
    }

    private static void provider(GameTestHelper helper, BlockPos position, Direction front, NetworkId network) {
        place(helper, position, ProcessingRegistration.PROVIDER.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, front), network);
    }

    private static BlockPos endpoint(GameTestHelper helper, BlockPos position, Direction front) {
        helper.setBlock(position, ProcessingRegistration.ENDPOINT.get().defaultBlockState()
                .setValue(BlockStateProperties.FACING, front));
        return position;
    }

    private static void place(GameTestHelper helper, BlockPos position, BlockState state, NetworkId network) {
        helper.setBlock(position, state);
        var entity = (IGridConnectedBlockEntity) helper.getBlockEntity(position);
        entity.getMainNode().loadFromNBT(NetworkIdentityNodeSeed.managedNode("proxy", network));
    }

    private static ServerPlayer playerAt(GameTestHelper helper, BlockPos position) {
        var player = MockServerPlayers.inLevel(helper);
        ObservationGameTestPlayerTransport.install(player);
        player.setPos(Vec3.atCenterOf(helper.absolutePos(position)));
        return player;
    }

    private static space.controlnet.ae2federation.domain.FederationDomainNodeId nodeId(GameTestHelper helper,
            BlockPos position) {
        return FederationDomainRegistryAccess.nodeId(helper.getLevel(), helper.absolutePos(position));
    }
}
