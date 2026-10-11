package space.controlnet.ae2federation.compat;

import appeng.api.config.Actionable;
import appeng.api.parts.PartHelper;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.api.util.AEColor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import space.controlnet.ae2federation.bridge.BridgeRegistration;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.crafting.projection.CraftingProjectionService;
import space.controlnet.ae2federation.crafting.projection.CraftingReturnLedger;
import space.controlnet.ae2federation.domain.FederationDomainRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.test.world.RestartChunkTickets;

/** Real blocks and three JVM phases; transit creation is explicit conservative fault injection. */
@PrefixGameTestTemplate(false)
public final class TransitRecoveryRestartGameTests {
    private static final Path STATE = Path.of("transit-recovery-restart.properties");
    private static final String[] CASES = {"access", "split", "copied", "permanent"};
    private static final AEItemKey STOCK = AEItemKey.of(Items.STONE);
    private static final AEItemKey FILLER = AEItemKey.of(Items.COAL);

    @GameTest(templateNamespace = "ae2federation_test", template = "harness_native_smoke", timeoutTicks = 1400)
    public static void transitRecoveryRestartMatrix(GameTestHelper helper) {
        switch (System.getProperty("ae2federation.compat.transitRestartPhase", "")) {
            case "prepare" -> prepare(helper);
            case "recover" -> recover(helper);
            case "verify" -> verify(helper);
            default -> helper.fail("Use tools/compat_transit_restart.py for three real server phases");
        }
    }

    private static void prepare(GameTestHelper helper) {
        var state = new Properties();
        state.setProperty("preparePid", Long.toString(ProcessHandle.current().pid()));
        var origin = helper.absolutePos(BlockPos.ZERO);
        // Keep initial native node creation in one ticking chunk; chunk-by-chunk readiness must not construct
        // temporarily independent durable networks before the intended fixture cables have initialized.
        var anchor = new BlockPos((origin.getX() >> 4) * 16 + 2, origin.getY() + 4,
                (origin.getZ() >> 4) * 16 + 2);
        var low = anchor.offset(-2, -4, -2);
        var high = anchor.offset(13, 4, 13);
        state.setProperty("ticketLow", Long.toString(low.asLong()));
        state.setProperty("ticketHigh", Long.toString(high.asLong()));
        RestartChunkTickets.force(helper.getLevel(), anchor, chunks(low, high), true);
        var bridges = new MultipartBridgePart[4];
        for (int index = 0; index < CASES.length; index++) {
            var base = anchor.south(3 * index);
            state.setProperty(CASES[index] + ".base", Long.toString(base.asLong()));
            placeChest(helper, base);
            placeChest(helper, base.east(6));
            for (int x = 1; x <= 5; x++) cable(helper, base.east(x));
            bridges[index] = bridge(helper, base.east());
            placeChest(helper, base.east().north());
        }
        int[] phase = {0};
        helper.succeedWhen(() -> {
            var ledger = CraftingReturnLedger.get(helper.getLevel());
            if (phase[0] == 0) {
                for (int index = 0; index < CASES.length; index++) {
                    var base = pos(state, CASES[index]);
                    helper.assertTrue(bridges[index].membershipCandidate().isPresent(), "Waiting for actual Federation access: " + CASES[index] + " " + bridges[index].operationalReason());
                    helper.assertTrue(grid(helper, base) == grid(helper, base.east(6)), "Waiting for complete source native Grid");
                    helper.assertTrue(grid(helper, base) != grid(helper, base.east().north()), "Destination stays a separate native Grid");
                    helper.assertTrue(confirmed(helper, base).isPresent(), "Waiting for settled native identity");
                    helper.assertTrue(cell(helper, base) != null && cell(helper, base.east(6)) != null, "Waiting for native cells");
                }
                for (int index = 0; index < CASES.length; index++) {
                    var name = CASES[index];
                    var base = pos(state, name);
                    var id = confirmed(helper, base).orElseThrow();
                    state.setProperty(name + ".id", id.value().toString());
                    helper.assertValueEqual(cell(helper, base).insert(STOCK, 3, Actionable.MODULATE, IActionSource.empty()), 3L,
                            "Three actual test items enter native storage before transit injection");
                    helper.assertValueEqual(cell(helper, base).extract(STOCK, 3, Actionable.MODULATE, IActionSource.empty()), 3L,
                            "Only the three extracted items become saved transit");
                    addTransit(ledger, id, 3);
                    // Full native cells prevent automatic recovery before the first real shutdown.
                    cell(helper, base).insert(FILLER, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
                    cell(helper, base.east(6)).insert(FILLER, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
                    // Removing a Bridge also exposes the native cable face. Remove its temporary outer chest
                    // first, otherwise the two durable native identities correctly become an ambiguous merge.
                    if (!name.equals("permanent")) {
                        helper.getLevel().setBlockAndUpdate(base.east().north(), Blocks.AIR.defaultBlockState());
                    }
                    helper.assertTrue(bridges[index].getHost().removePart(bridges[index]), "Remove every Federation access part");
                }
                helper.getLevel().setBlockAndUpdate(pos(state, "split").east(3), Blocks.AIR.defaultBlockState());
                var original = chest(helper, pos(state, "copied"));
                var copied = original.saveWithFullMetadata(helper.getLevel().registryAccess());
                placeChest(helper, pos(state, "copied").east(10));
                chest(helper, pos(state, "copied").east(10)).loadWithComponents(copied, helper.getLevel().registryAccess());
                var removed = pos(state, "permanent");
                for (int x = 0; x <= 6; x++) helper.getLevel().setBlockAndUpdate(removed.east(x), Blocks.AIR.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(removed.below(), Blocks.AIR.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(removed.east(6).below(), Blocks.AIR.defaultBlockState());
                phase[0] = 1;
                helper.fail("Wait for native split and copied-lineage detection before saving");
            }
            assertNoPolicy(helper);
            assertBoundaries(helper, state);
            for (var name : CASES) helper.assertValueEqual(ledger.transit(id(state, name), STOCK), 3L, "Exactly three transit items remain");
            helper.assertValueEqual(totalTransit(ledger), 12L, "No extra identity owns a copied transit balance");
            save(state);
        });
    }

    private static void recover(GameTestHelper helper) {
        var state = load();
        helper.assertTrue(ProcessHandle.current().pid() != Long.parseLong(state.getProperty("preparePid")), "Recovery runs in another JVM");
        int[] phase = {0};
        long[] sweeps = {0};
        helper.succeedWhen(() -> {
            loaded(helper, state);
            var ledger = CraftingReturnLedger.get(helper.getLevel());
            var service = CraftingProjectionService.get(helper.getLevel());
            if (phase[0] == 0) {
                assertBoundaries(helper, state);
                for (var name : CASES) {
                    helper.assertValueEqual(ledger.transit(id(state, name), STOCK), 3L, "Transit survived actual process restart");
                    if (!name.equals("permanent")) {
                        helper.assertValueEqual(stored(helper, pos(state, name)), 0L, "Stock was not inherited by native cells");
                        cell(helper, pos(state, name)).extract(FILLER, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
                        cell(helper, pos(state, name).east(6)).extract(FILLER, Long.MAX_VALUE, Actionable.MODULATE, IActionSource.empty());
                    }
                }
                helper.assertValueEqual(totalTransit(ledger), 12L, "Exactly twelve real transit items survive restart globally");
                sweeps[0] = service.ledgerSweeps();
                phase[0] = 1;
            }
            if (phase[0] == 1) {
                helper.assertTrue(service.ledgerSweeps() >= sweeps[0] + 4, "Wait four real sweeps without Federation access after restart");
                assertBoundaries(helper, state);
                for (var name : CASES) helper.assertValueEqual(ledger.transit(id(state, name), STOCK), 3L,
                        "Unobserved or ambiguous branches must not automatically receive transit");
                helper.assertValueEqual(totalTransit(ledger), 12L, "Sweeps cannot copy transit into another executing identity");
                var destination = pos(state, "permanent").east().north();
                var server = helper.getLevel().getServer();
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(helper.getLevel()).withPermission(0),
                        command(id(state, "permanent"), destination));
                helper.assertValueEqual(ledger.transit(id(state, "permanent"), STOCK), 3L, "Non-operator recovery cannot move stock");
                var unloaded = new BlockPos(20_000_000, 80, 20_000_000);
                helper.assertFalse(helper.getLevel().isLoaded(unloaded), "Recovery rejection target starts unloaded");
                recover(helper, id(state, "permanent"), unloaded);
                helper.assertFalse(helper.getLevel().isLoaded(unloaded), "Recovery must not load destination chunks");
                helper.assertValueEqual(ledger.transit(id(state, "permanent"), STOCK), 3L, "Unloaded target retains saved stock");
                var target = chest(helper, destination);
                var savedCell = target.getCell().copy();
                target.setCell(ItemStack.EMPTY);
                recover(helper, id(state, "permanent"), destination);
                helper.assertValueEqual(ledger.transit(id(state, "permanent"), STOCK), 3L, "Rejected recovery remains saved");
                target.setCell(savedCell);
                recover(helper, id(state, "permanent"), destination);
                recover(helper, id(state, "permanent"), destination);
                helper.assertValueEqual(cell(helper, destination).getAvailableStacks().get(STOCK), 3L, "Explicit repeated recovery transfers three items once");
                cable(helper, pos(state, "split").east(3));
                helper.getLevel().setBlockAndUpdate(pos(state, "copied").east(10), Blocks.AIR.defaultBlockState());
                helper.getLevel().setBlockAndUpdate(pos(state, "copied").east(10).below(), Blocks.AIR.defaultBlockState());
                for (var name : new String[]{"access", "split", "copied"}) {
                    placeChest(helper, pos(state, name).east().north());
                    bridge(helper, pos(state, name).east());
                }
                phase[0] = 2;
            }
            for (var name : CASES) {
                helper.assertValueEqual(ledger.transit(id(state, name), STOCK), 0L, "The three saved items have been delivered");
                if (name.equals("permanent")) continue;
                helper.assertTrue(confirmed(helper, pos(state, name)).filter(id(state, name)::equals).isPresent(),
                        "Legal repair restores the original confirmed identity without copying it to branches");
                helper.assertValueEqual(stored(helper, pos(state, name)), 3L, "Exactly three actual items return across native cells");
            }
            helper.assertValueEqual(totalTransit(ledger), 0L, "All twelve real transit units are now physically delivered once");
            state.setProperty("recoverPid", Long.toString(ProcessHandle.current().pid()));
            save(state);
        });
    }

    private static void verify(GameTestHelper helper) {
        var state = load();
        helper.assertTrue(ProcessHandle.current().pid() != Long.parseLong(state.getProperty("recoverPid"))
                && ProcessHandle.current().pid() != Long.parseLong(state.getProperty("preparePid")), "Final verification uses a third JVM");
        helper.succeedWhen(() -> {
            loaded(helper, state);
            var ledger = CraftingReturnLedger.get(helper.getLevel());
            for (var name : CASES) {
                var destination = name.equals("permanent") ? pos(state, name).east().north() : pos(state, name);
                helper.assertValueEqual(ledger.transit(id(state, name), STOCK), 0L, "Recovered transit does not reappear after another restart");
                recover(helper, id(state, name), destination);
                long physical = name.equals("permanent") ? cell(helper, destination).getAvailableStacks().get(STOCK) : stored(helper, pos(state, name));
                helper.assertValueEqual(physical, 3L, "Repeated post-restart recovery cannot duplicate delivered items");
            }
            helper.assertValueEqual(totalTransit(ledger), 0L, "No identity retains duplicated transit after final restart");
            RestartChunkTickets.force(helper.getLevel(), pos(state, "access"), chunks(BlockPos.of(Long.parseLong(state.getProperty("ticketLow"))),
                    BlockPos.of(Long.parseLong(state.getProperty("ticketHigh")))), false);
        });
    }

    private static void assertBoundaries(GameTestHelper helper, Properties state) {
        helper.assertTrue(confirmed(helper, pos(state, "access")).filter(id(state, "access")::equals).isPresent(), "Native identity survives access removal: expected " + id(state, "access") + " observed "
                + grid(helper, pos(state, "access")).getService(space.controlnet.ae2federation.identity.NetworkIdentityService.class).settlement());
        var split = pos(state, "split");
        helper.assertTrue(grid(helper, split) != null && grid(helper, split.east(6)) != null
                && grid(helper, split) != grid(helper, split.east(6)), "Actual cable removal splits initialized native Grids");
        helper.assertValueEqual(status(helper, split), space.controlnet.ae2federation.identity.IdentityStatus.AMBIGUOUS_SPLIT,
                "The first loaded split branch has actual ambiguous identity");
        helper.assertValueEqual(status(helper, split.east(6)), space.controlnet.ae2federation.identity.IdentityStatus.AMBIGUOUS_SPLIT,
                "The second loaded split branch has actual ambiguous identity");
        var copied = pos(state, "copied");
        helper.assertTrue(grid(helper, copied) != null && grid(helper, copied.east(10)) != null
                && grid(helper, copied) != grid(helper, copied.east(10)), "Copied lineage belongs to two loaded native Grids");
        helper.assertValueEqual(status(helper, copied), space.controlnet.ae2federation.identity.IdentityStatus.COPIED_LIVE_IDENTITY,
                "Original Grid detects actual copied live lineage");
        helper.assertValueEqual(status(helper, copied.east(10)), space.controlnet.ae2federation.identity.IdentityStatus.COPIED_LIVE_IDENTITY,
                "Copied Grid detects actual copied live lineage");
        helper.assertTrue(cell(helper, copied.east(10)) != null, "Copied native cell is initialized");
        helper.assertValueEqual(cell(helper, copied.east(10)).getAvailableStacks().get(STOCK), 0L,
                "NBT-copy fixture contains no conserved stone");
        for (int x = 0; x <= 6; x++) helper.assertTrue(helper.getLevel().isEmptyBlock(pos(state, "permanent").east(x)), "Permanently removed source has no nodes");
        helper.assertTrue(space.controlnet.ae2federation.identity.NetworkIdentityRegistry.get(helper.getLevel()).liveClaims()
                .values().stream().flatMap(java.util.Collection::stream).noneMatch(lineage -> lineage.networkId().equals(id(state, "permanent"))),
                "No surviving loaded native node claims the permanently removed executing identity");
    }
    private static void assertNoPolicy(GameTestHelper helper) {
        helper.assertTrue(space.controlnet.ae2federation.persistence.PolicySavedData.get(helper.getLevel()).snapshot().entries().isEmpty(),
                "Storage sharing was never configured and recovery must not create or inherit policies");
    }
    private static void loaded(GameTestHelper helper, Properties state) {
        assertNoPolicy(helper);
        for (var name : CASES) {
            var destination = pos(state, name).east().north();
            helper.assertTrue(helper.getLevel().isLoaded(destination), "Persistent test tickets restore the fixture");
            if (name.equals("permanent")) helper.assertTrue(
                    helper.getLevel().getBlockEntity(destination) instanceof MEChestBlockEntity && cell(helper, destination) != null,
                    "Waiting for saved native destination");
            if (!name.equals("permanent")) helper.assertTrue(grid(helper, pos(state, name)) != null && grid(helper, pos(state, name).east(6)) != null,
                    "Waiting for saved source Grids");
        }
    }
    private static void placeChest(GameTestHelper helper, BlockPos pos) {
        helper.getLevel().setBlockAndUpdate(pos.below(), AEBlocks.CREATIVE_ENERGY_CELL.block().defaultBlockState());
        helper.getLevel().setBlockAndUpdate(pos, AEBlocks.ME_CHEST.block().defaultBlockState());
        chest(helper, pos).setCell(AEItems.ITEM_CELL_1K.stack());
    }
    private static MEChestBlockEntity chest(GameTestHelper helper, BlockPos pos) { return (MEChestBlockEntity) helper.getLevel().getBlockEntity(pos); }
    private static appeng.api.storage.MEStorage cell(GameTestHelper helper, BlockPos pos) {
        var value = chest(helper, pos); return value == null ? null : value.getOriginalCellInventory(0);
    }
    private static appeng.api.networking.IGrid grid(GameTestHelper helper, BlockPos pos) {
        var value = chest(helper, pos); return value == null ? null : value.getMainNode().getGrid();
    }
    private static space.controlnet.ae2federation.identity.IdentityStatus status(GameTestHelper helper, BlockPos pos) {
        return grid(helper, pos).getService(space.controlnet.ae2federation.identity.NetworkIdentityService.class).settlement().status();
    }
    private static java.util.Optional<NetworkId> confirmed(GameTestHelper helper, BlockPos pos) {
        var value = grid(helper, pos); return value == null ? java.util.Optional.empty() : FederationDomainRegistryAccess.confirmedNetworkId(value);
    }
    private static long stored(GameTestHelper helper, BlockPos base) {
        return cell(helper, base).getAvailableStacks().get(STOCK) + cell(helper, base.east(6)).getAvailableStacks().get(STOCK);
    }
    private static void cable(GameTestHelper helper, BlockPos pos) {
        helper.assertTrue(PartHelper.setPart(helper.getLevel(), pos, null, null, AEParts.GLASS_CABLE.item(AEColor.BLUE)) != null, "Place actual native cable");
    }
    private static MultipartBridgePart bridge(GameTestHelper helper, BlockPos pos) {
        var part = PartHelper.setPart(helper.getLevel(), pos, Direction.NORTH, null, BridgeRegistration.BRIDGE.get());
        helper.assertTrue(part != null, "Place actual Federation Bridge"); return part;
    }
    private static long totalTransit(CraftingReturnLedger ledger) {
        try {
            var method = CraftingReturnLedger.class.getDeclaredMethod("transitNetworks");
            method.setAccessible(true);
            return ((java.util.Set<?>) method.invoke(ledger)).stream().map(NetworkId.class::cast)
                    .mapToLong(id -> ledger.transit(id, STOCK)).sum();
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static void addTransit(CraftingReturnLedger ledger, NetworkId id, long amount) {
        try {
            var method = CraftingReturnLedger.class.getDeclaredMethod("addTransit", NetworkId.class, appeng.api.stacks.AEKey.class, long.class);
            method.setAccessible(true); method.invoke(ledger, id, STOCK, amount);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }
    private static String command(NetworkId id, BlockPos pos) {
        return "ae2federation returns recover-transit " + id.value() + " " + pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }
    private static void recover(GameTestHelper helper, NetworkId id, BlockPos destination) {
        var server = helper.getLevel().getServer();
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withLevel(helper.getLevel()), command(id, destination));
    }
    private static NetworkId id(Properties state, String name) { return new NetworkId(UUID.fromString(state.getProperty(name + ".id"))); }
    private static BlockPos pos(Properties state, String name) { return BlockPos.of(Long.parseLong(state.getProperty(name + ".base"))); }
    private static Properties load() {
        var result = new Properties();
        try (var stream = Files.newInputStream(STATE)) { result.load(stream); return result; }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private static void save(Properties state) {
        try (var stream = Files.newOutputStream(STATE)) { state.store(stream, "Actual transit recovery restart matrix"); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private static java.util.List<ChunkPos> chunks(BlockPos low, BlockPos high) {
        var result = new ArrayList<ChunkPos>();
        for (int x = low.getX() >> 4; x <= high.getX() >> 4; x++) for (int z = low.getZ() >> 4; z <= high.getZ() >> 4; z++) result.add(new ChunkPos(x, z));
        return result;
    }
}
