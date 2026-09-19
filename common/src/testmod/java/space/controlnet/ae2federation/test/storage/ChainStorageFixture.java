package space.controlnet.ae2federation.test.storage;

import appeng.api.networking.IGrid;
import appeng.api.storage.IStorageProvider;
import appeng.api.storage.MEStorage;
import appeng.api.util.AEColor;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import space.controlnet.ae2federation.ae2.storage.NativeStorageProvenance;
import space.controlnet.ae2federation.bridge.MultipartBridgePart;
import space.controlnet.ae2federation.fabric.FabricRegistryAccess;
import space.controlnet.ae2federation.identity.NetworkId;
import space.controlnet.ae2federation.identity.NetworkIdentityService;
import space.controlnet.ae2federation.policy.PolicyCapability;
import space.controlnet.ae2federation.policy.PolicyKey;
import space.controlnet.ae2federation.test.bridge.BridgeFixtures;

public final class ChainStorageFixture implements AutoCloseable {
    private static final BlockPos AB = new BlockPos(3, 3, 3);
    private static final BlockPos AC = new BlockPos(6, 3, 3);
    private static final BlockPos BD = new BlockPos(2, 3, 7);
    private static final BlockPos CD = new BlockPos(7, 3, 7);
    private static final BlockPos A_CHEST = new BlockPos(4, 3, 4);
    private static final BlockPos B_CHEST = new BlockPos(1, 3, 5);
    private static final BlockPos C_CHEST = new BlockPos(8, 3, 5);
    private static final BlockPos D_CHEST = new BlockPos(4, 3, 6);

    private final GameTestHelper helper;
    private final BridgeFixtures fixtures;
    private final List<MultipartBridgePart> bridges = new ArrayList<>();
    private final List<CablePlacement> pendingCables = new ArrayList<>();

    public ChainStorageFixture(GameTestHelper helper) {
        this.helper = helper;
        fixtures = new BridgeFixtures(helper);
        placeStorage(A_CHEST);
        placeStorage(B_CHEST);
        placeStorage(C_CHEST);
        placeStorage(D_CHEST);
        lineX(3, 6, 4, 3, AEColor.RED);
        lineZ(2, 8, 5, 2, AEColor.BLUE);
        lineZ(2, 8, 5, 7, AEColor.GREEN);
        lineX(3, 6, 4, 7, AEColor.YELLOW);
    }

    public boolean networksSettled() {
        IGrid[] grids = { aGrid(), bGrid(), cGrid(), dGrid() };
        var distinctGrids = new HashSet<IGrid>();
        for (var grid : grids) {
            if (grid == null || !distinctGrids.add(grid) || FabricRegistryAccess.confirmedNetworkId(grid).isEmpty()) {
                return false;
            }
        }
        if (!pendingCables.isEmpty()) {
            var cable = pendingCables.removeFirst();
            fixtures.nativePorts().placeCable(cable.position(), cable.color());
            return false;
        }
        return true;
    }

    public void placeNextBridge() {
        switch (bridges.size()) {
            case 0 -> bridges.add(fixtures.placeBridge(AB, Direction.WEST));
            case 1 -> bridges.add(fixtures.placeBridge(AC, Direction.EAST));
            case 2 -> bridges.add(fixtures.placeBridge(BD, Direction.EAST));
            case 3 -> bridges.add(fixtures.placeBridge(CD, Direction.WEST));
            default -> throw new IllegalStateException("All chain bridges are already placed");
        }
    }

    public boolean latestBridgeReady() {
        if (bridges.isEmpty()) {
            return false;
        }
        var bridge = bridges.getLast();
        bridge.onUpdateShape(bridge.getSide());
        return bridge.membershipCandidate().isPresent();
    }

    public boolean bridgesReady() {
        bridges.forEach(bridge -> bridge.onUpdateShape(bridge.getSide()));
        if (bridges.size() != 4 || bridges.stream().anyMatch(bridge -> bridge.membershipCandidate().isEmpty())) {
            return false;
        }
        if (List.of(aGrid(), bGrid(), cGrid(), dGrid()).stream()
                .anyMatch(grid -> FabricRegistryAccess.confirmedNetworkId(grid).isEmpty())) {
            return false;
        }
        var registry = FabricRegistryAccess.get(helper.getLevel());
        return registry.fabricsFor(a()).size() == 2 && registry.fabricsFor(b()).size() == 2
                && registry.fabricsFor(c()).size() == 2 && registry.fabricsFor(d()).size() == 2
                && registry.snapshot().fabrics().size() >= 4;
    }

    public String readiness() {
        var reasons = bridges.stream().map(bridge -> bridge.operationalReason().name()).toList();
        var grids = java.util.Arrays.asList(aGrid(), bGrid(), cGrid(), dGrid());
        var statuses = grids.stream()
                .map(grid -> grid == null ? "MISSING"
                        : grid.getService(NetworkIdentityService.class).settlement().status().name())
                .toList();
        if (bridges.size() != 4 || bridges.stream().anyMatch(bridge -> bridge.membershipCandidate().isEmpty())) {
            return "bridgeReasons=" + reasons + ", identityStatuses=" + statuses;
        }
        var identities = grids.stream()
                .map(FabricRegistryAccess::confirmedNetworkId).toList();
        if (identities.stream().anyMatch(java.util.Optional::isEmpty)) {
            return "bridgeReasons=" + reasons + ", identityStatuses=" + statuses;
        }
        var registry = FabricRegistryAccess.get(helper.getLevel());
        return "bridgeReasons=" + reasons + ", fabricCounts=" + identities.stream()
                .map(identity -> registry.fabricsFor(identity.orElseThrow()).size()).toList()
                + ", totalFabrics=" + registry.snapshot().fabrics().size();
    }

    public PolicyKey aToB() { return key(b(), a()); }
    public PolicyKey aToC() { return key(c(), a()); }
    public PolicyKey bToD() { return key(d(), b()); }
    public PolicyKey cToD() { return key(d(), c()); }
    public PolicyKey aToD() { return key(d(), a()); }
    public PolicyKey bToA() { return key(a(), b()); }

    public IGrid aGrid() { return chest(A_CHEST).getMainNode().getGrid(); }
    public IGrid bGrid() { return chest(B_CHEST).getMainNode().getGrid(); }
    public IGrid cGrid() { return chest(C_CHEST).getMainNode().getGrid(); }
    public IGrid dGrid() { return chest(D_CHEST).getMainNode().getGrid(); }

    public NetworkId a() { return network(aGrid()); }
    public NetworkId b() { return network(bGrid()); }
    public NetworkId c() { return network(cGrid()); }
    public NetworkId d() { return network(dGrid()); }

    public MEStorage nativeSource(IGrid grid) {
        var provenance = new NativeStorageProvenance();
        var providers = new ArrayList<IStorageProvider>();
        for (var node : grid.getNodes()) {
            var provider = node.getService(IStorageProvider.class);
            if (provider != null) {
                provenance.qualify(node);
                providers.add(provider);
            }
        }
        return provenance.sources(providers).getFirst().storage();
    }

    private void lineX(int from, int to, int anchor, int z, AEColor color) {
        pendingCables.add(new CablePlacement(new BlockPos(anchor, 3, z), color));
        for (var x = anchor - 1; x >= from; x--) pendingCables.add(new CablePlacement(new BlockPos(x, 3, z), color));
        for (var x = anchor + 1; x <= to; x++) pendingCables.add(new CablePlacement(new BlockPos(x, 3, z), color));
    }

    private void lineZ(int from, int to, int anchor, int x, AEColor color) {
        pendingCables.add(new CablePlacement(new BlockPos(x, 3, anchor), color));
        for (var z = anchor - 1; z >= from; z--) pendingCables.add(new CablePlacement(new BlockPos(x, 3, z), color));
        for (var z = anchor + 1; z <= to; z++) pendingCables.add(new CablePlacement(new BlockPos(x, 3, z), color));
    }

    private record CablePlacement(BlockPos position, AEColor color) {}

    private void placeStorage(BlockPos position) {
        fixtures.nativePorts().placeChest(position);
        helper.setBlock(position.below(), AEBlocks.CREATIVE_ENERGY_CELL.block());
        chest(position).setCell(AEItems.ITEM_CELL_1K.stack());
    }

    private MEChestBlockEntity chest(BlockPos position) {
        return helper.getBlockEntity(position);
    }

    private NetworkId network(IGrid grid) {
        return FabricRegistryAccess.confirmedNetworkId(grid).orElseThrow();
    }

    private static PolicyKey key(NetworkId consumer, NetworkId provider) {
        return new PolicyKey(consumer, provider, PolicyCapability.STORAGE);
    }

    @Override
    public void close() {
        fixtures.close();
    }
}
